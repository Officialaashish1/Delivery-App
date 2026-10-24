package `in`.localrush.partner.feature.orders.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.localrush.core.common.order.OrderStatus
import `in`.localrush.core.common.result.AppError
import `in`.localrush.core.common.result.Result
import `in`.localrush.core.network.ws.WebSocketClient
import `in`.localrush.partner.alert.NewOrderAlerter
import `in`.localrush.partner.data.ConnectivityObserver
import `in`.localrush.partner.data.OrdersRepository
import `in`.localrush.partner.data.PartnerOrderDto
import `in`.localrush.partner.data.PartnerOrderFeed
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/** Which owner action is in flight for an order (disables the matching button). */
enum class OrderAction { ACCEPT, REJECT, PREPARING, READY, CANCEL }

data class OrdersUiState(
    val board: OrdersBoardState = OrdersBoardState(),
    val selectedTab: OrderTab = OrderTab.NEW,
    val refreshing: Set<OrderTab> = emptySet(),
    val errors: Map<OrderTab, AppError> = emptyMap(),
    val connection: WebSocketClient.State = WebSocketClient.State.Idle,
    val online: Boolean = true,
    /** Ticks every second while there are orders waiting; drives "waiting 03:12" timers. */
    val now: Instant = Instant.now(),
    val pending: Map<String, OrderAction> = emptyMap(),
) {
    val wsLive: Boolean get() = connection is WebSocketClient.State.Connected
}

sealed interface OrdersEvent {
    data class Message(val text: String) : OrdersEvent
    data class Error(val error: AppError) : OrdersEvent
    data class NewOrder(val order: PartnerOrderDto) : OrdersEvent
}

@HiltViewModel
class OrdersViewModel @Inject constructor(
    private val feed: PartnerOrderFeed,
    private val repo: OrdersRepository,
    private val alerter: NewOrderAlerter,
    connectivity: ConnectivityObserver,
) : ViewModel() {
    private val tab = MutableStateFlow(OrderTab.NEW)
    private val pending = MutableStateFlow<Map<String, OrderAction>>(emptyMap())
    private val now = MutableStateFlow(Instant.now())
    private val _events = MutableSharedFlow<OrdersEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<OrdersEvent> = _events.asSharedFlow()

    val state: StateFlow<OrdersUiState> = combine(
        combine(feed.state, feed.refreshing, feed.tabErrors) { b, r, e -> Triple(b, r, e) },
        combine(tab, pending, now) { t, p, n -> Triple(t, p, n) },
        feed.connection, connectivity.isOnline,
    ) { (board, refreshing, errors), (selected, pend, tick), conn, online ->
        OrdersUiState(board, selected, refreshing, errors, conn, online, tick, pend)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OrdersUiState())

    init {
        feed.ensureConnected()
        viewModelScope.launch { feed.refreshActive(); feed.refreshTab(OrderTab.PAST) }
        viewModelScope.launch { while (isActive) { now.value = Instant.now(); delay(1_000) } }
        viewModelScope.launch {
            feed.events.collect { e ->
                if (OrderStatus.fromEventType(e.type) == OrderStatus.PLACED) _events.tryEmit(OrdersEvent.NewOrder(e.order))
            }
        }
    }

    fun selectTab(t: OrderTab) {
        tab.value = t
        if (t !in feed.state.value.loadedTabs) viewModelScope.launch { feed.refreshTab(t) }
    }

    fun refresh() = viewModelScope.launch {
        feed.ensureConnected()
        if (tab.value == OrderTab.PAST) feed.refreshTab(OrderTab.PAST) else feed.refreshActive()
    }

    /** The owner has seen the order (opened it / tapped the banner): stop ringing for it. */
    fun acknowledge(orderId: String) { feed.acknowledge(orderId); alerter.dismissNotification(orderId) }
    fun acknowledgeAll() = feed.acknowledgeAll()

    fun accept(orderId: String, prepMinutes: Int) = act(orderId, OrderAction.ACCEPT) { repo.accept(orderId, prepMinutes) }
    fun reject(orderId: String, reason: String) = act(orderId, OrderAction.REJECT) { repo.reject(orderId, reason) }
    fun markPreparing(orderId: String) = act(orderId, OrderAction.PREPARING) { repo.preparing(orderId) }
    fun markReady(orderId: String) = act(orderId, OrderAction.READY) { repo.ready(orderId) }
    fun cancel(orderId: String, reason: String) = act(orderId, OrderAction.CANCEL) { repo.cancel(orderId, reason) }

    private fun act(orderId: String, action: OrderAction, call: suspend () -> Result<PartnerOrderDto, AppError>) {
        if (pending.value.containsKey(orderId)) return
        pending.update { it + (orderId to action) }
        acknowledge(orderId)
        viewModelScope.launch {
            when (val r = call()) {
                is Result.Ok -> feed.applyActionResult(r.value)
                is Result.Err -> {
                    _events.tryEmit(OrdersEvent.Error(r.error))
                    // invalid_transition etc.: the server state moved on; resync this order.
                    feed.refreshOrder(orderId)
                }
            }
            pending.update { it - orderId }
        }
    }
}
