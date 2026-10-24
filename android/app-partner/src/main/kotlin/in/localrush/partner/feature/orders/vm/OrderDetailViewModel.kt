package `in`.localrush.partner.feature.orders.vm

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import `in`.localrush.core.common.result.AppError
import `in`.localrush.core.common.result.Result
import `in`.localrush.core.common.util.UiState
import `in`.localrush.partner.alert.NewOrderAlerter
import `in`.localrush.partner.data.ConnectivityObserver
import `in`.localrush.partner.data.OrdersRepository
import `in`.localrush.partner.data.PartnerOrderDto
import `in`.localrush.partner.data.PartnerOrderFeed
import `in`.localrush.partner.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class OrderDetailData(val order: PartnerOrderDto, val action: OrderAction? = null, val now: Instant = Instant.now())

@HiltViewModel
class OrderDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val feed: PartnerOrderFeed,
    private val repo: OrdersRepository,
    private val alerter: NewOrderAlerter,
    connectivity: ConnectivityObserver,
) : ViewModel() {
    val orderId: String = savedState.toRoute<Routes.OrderDetail>().orderId
    private val _state = MutableStateFlow<UiState<OrderDetailData>>(UiState.Loading)
    val state: StateFlow<UiState<OrderDetailData>> = _state.asStateFlow()
    val online: StateFlow<Boolean> = connectivity.isOnline
    private val _events = MutableSharedFlow<OrdersEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<OrdersEvent> = _events.asSharedFlow()

    init {
        feed.acknowledge(orderId)
        alerter.dismissNotification(orderId)
        load()
        // Live updates: whenever the board's copy changes, mirror it here.
        viewModelScope.launch {
            feed.state.map { it.orders[orderId] }.collect { o -> if (o != null) _state.value = UiState.Success(OrderDetailData(o, (_state.value as? UiState.Success)?.data?.action)) }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(1_000)
                _state.value.let { s -> if (s is UiState.Success) _state.value = s.copy(data = s.data.copy(now = Instant.now())) }
            }
        }
    }

    fun load() {
        if (_state.value !is UiState.Success) _state.value = UiState.Loading
        viewModelScope.launch {
            when (val r = feed.refreshOrder(orderId)) {
                is Result.Ok -> _state.value = UiState.Success(OrderDetailData(r.value))
                is Result.Err -> if (_state.value !is UiState.Success) _state.value = UiState.Error(r.error)
            }
        }
    }

    fun accept(prepMinutes: Int) = act(OrderAction.ACCEPT) { repo.accept(orderId, prepMinutes) }
    fun reject(reason: String) = act(OrderAction.REJECT) { repo.reject(orderId, reason) }
    fun markPreparing() = act(OrderAction.PREPARING) { repo.preparing(orderId) }
    fun markReady() = act(OrderAction.READY) { repo.ready(orderId) }
    fun cancel(reason: String) = act(OrderAction.CANCEL) { repo.cancel(orderId, reason) }

    private fun act(action: OrderAction, call: suspend () -> Result<PartnerOrderDto, AppError>) {
        val s = _state.value as? UiState.Success ?: return
        if (s.data.action != null) return
        _state.value = s.copy(data = s.data.copy(action = action))
        viewModelScope.launch {
            when (val r = call()) {
                is Result.Ok -> { feed.applyActionResult(r.value); _state.value = UiState.Success(OrderDetailData(r.value)) }
                is Result.Err -> {
                    _events.tryEmit(OrdersEvent.Error(r.error))
                    feed.refreshOrder(orderId)
                    _state.value.let { cur -> if (cur is UiState.Success) _state.value = cur.copy(data = cur.data.copy(action = null)) }
                }
            }
        }
    }
}
