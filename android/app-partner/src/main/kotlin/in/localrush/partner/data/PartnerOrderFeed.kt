package `in`.localrush.partner.data

import `in`.localrush.core.auth.SessionManager
import `in`.localrush.core.auth.SessionState
import `in`.localrush.core.common.order.OrderEvents
import `in`.localrush.core.common.order.OrderStatus
import `in`.localrush.core.common.result.AppError
import `in`.localrush.core.common.result.Result
import `in`.localrush.core.common.util.AppDispatchers
import `in`.localrush.core.network.ws.WebSocketClient
import `in`.localrush.core.network.ws.type
import `in`.localrush.partner.feature.orders.vm.OrderTab
import `in`.localrush.partner.feature.orders.vm.OrdersBoardReducer
import `in`.localrush.partner.feature.orders.vm.OrdersBoardState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** A live order event the UI may want to react to (snackbar, haptics). */
data class LiveOrderEvent(val type: String, val order: PartnerOrderDto)

/**
 * Process-wide orders board. Owns the `/ws/partner/` socket (reconnect/backoff/pong handled by [WebSocketClient]),
 * merges REST snapshots and live events through [OrdersBoardReducer], and survives screen changes so the alert keeps
 * ringing until the owner acts. Started by the Application once; connects whenever a partner session exists.
 */
@Singleton
class PartnerOrderFeed @Inject constructor(
    private val repo: OrdersRepository,
    private val wsFactory: WebSocketClient.Factory,
    private val session: SessionManager,
    private val restaurantRepo: RestaurantRepository,
    private val dispatchers: AppDispatchers,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)

    private val _state = MutableStateFlow(OrdersBoardState())
    val state: StateFlow<OrdersBoardState> = _state.asStateFlow()

    private val _connection = MutableStateFlow<WebSocketClient.State>(WebSocketClient.State.Idle)
    val connection: StateFlow<WebSocketClient.State> = _connection.asStateFlow()

    private val _events = MutableSharedFlow<LiveOrderEvent>(extraBufferCapacity = 64)
    /** Every order event after the order has been refreshed from REST (new orders included). */
    val events: SharedFlow<LiveOrderEvent> = _events.asSharedFlow()

    private val _tabErrors = MutableStateFlow<Map<OrderTab, AppError>>(emptyMap())
    val tabErrors: StateFlow<Map<OrderTab, AppError>> = _tabErrors.asStateFlow()

    private val _refreshing = MutableStateFlow<Set<OrderTab>>(emptySet())
    val refreshing: StateFlow<Set<OrderTab>> = _refreshing.asStateFlow()

    private var socket: WebSocketClient? = null
    private var socketJobs: List<Job> = emptyList()
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            session.state.collect { s ->
                when (s) {
                    is SessionState.LoggedIn -> connectIfNeeded()
                    SessionState.LoggedOut -> disconnect(clearBoard = true)
                    SessionState.Unknown -> Unit
                }
            }
        }
    }

    /** Called when the restaurant becomes approved / the Orders screen opens, so a stale socket is retried right away. */
    fun ensureConnected() {
        val ws = socket
        if (ws == null) connectIfNeeded() else if (ws.state.value is WebSocketClient.State.Closed) { disconnect(clearBoard = false); connectIfNeeded() }
    }

    private fun connectIfNeeded() {
        if (socket != null || !session.isLoggedIn) return
        val ws = wsFactory.partner()
        socket = ws
        socketJobs = listOf(
            scope.launch { ws.state.collect { _connection.value = it } },
            scope.launch { ws.messages.collect { msg -> handleMessage(msg.type, (msg["order_id"] as? JsonPrimitive)?.contentOrNull) } },
            scope.launch {
                // Resync active orders every time the socket (re)connects: events may have been missed while offline.
                var wasConnected = false
                ws.state.collect { st ->
                    val connected = st is WebSocketClient.State.Connected
                    if (connected && !wasConnected) refreshActive(fromReconnect = true)
                    wasConnected = connected
                }
            },
        )
        ws.connect()
    }

    private fun disconnect(clearBoard: Boolean) {
        socketJobs.forEach { it.cancel() }
        socketJobs = emptyList()
        socket?.close()
        socket = null
        _connection.value = WebSocketClient.State.Idle
        if (clearBoard) { _state.value = OrdersBoardState(); _tabErrors.value = emptyMap(); restaurantRepo.clear() }
    }

    private fun handleMessage(type: String?, orderId: String?) {
        if (type == null || orderId == null) return
        val isOrderEvent = type.startsWith("status.") || type == OrderEvents.ORDER_PLACED || type == OrderEvents.RIDER_ASSIGNED ||
            type == OrderEvents.RIDER_AT_RESTAURANT || type == OrderEvents.ORDER_UNASSIGNED || type == OrderEvents.RIDER_OFFERED
        if (!isOrderEvent) return
        scope.launch { refreshOrder(orderId, eventType = type) }
    }

    /** Push (FCM) arrived for an order: same path as a socket event, so the board is right even if the socket was down. */
    fun onPushEvent(type: String, orderId: String) = handleMessage(type, orderId)

    /** Fetches the order and merges it; emits a [LiveOrderEvent] when it came from a live source. */
    suspend fun refreshOrder(orderId: String, eventType: String? = null): Result<PartnerOrderDto, AppError> {
        val r = repo.detail(orderId)
        if (r is Result.Ok) {
            _state.update { OrdersBoardReducer.upsert(it, r.value, fromLiveEvent = eventType != null) }
            if (eventType != null) _events.tryEmit(LiveOrderEvent(eventType, r.value))
        }
        return r
    }

    /** Pulls the REST snapshot for a tab. */
    suspend fun refreshTab(tab: OrderTab) {
        _refreshing.update { it + tab }
        when (val r = repo.list(OrdersBoardReducer.scopeFor(tab), pageSize = if (tab == OrderTab.PAST) 50 else 100)) {
            is Result.Ok -> {
                _state.update { OrdersBoardReducer.applySnapshot(it, tab, r.value.results) }
                _tabErrors.update { it - tab }
            }
            is Result.Err -> _tabErrors.update { it + (tab to r.error) }
        }
        _refreshing.update { it - tab }
    }

    /** New + Preparing + Ready in one call (`scope=active` also returns on-the-way orders, which land in Past). */
    suspend fun refreshActive(fromReconnect: Boolean = false) {
        val tabs = listOf(OrderTab.NEW, OrderTab.PREPARING, OrderTab.READY)
        _refreshing.update { it + tabs }
        when (val r = repo.list("active", pageSize = 100)) {
            is Result.Ok -> {
                val byTab = r.value.results.groupBy { OrdersBoardReducer.tabFor(OrderStatus.fromWire(it.status)) }
                _state.update { s ->
                    var next = s
                    tabs.forEach { t -> next = OrdersBoardReducer.applySnapshot(next, t, byTab[t] ?: emptyList()) }
                    // On-the-way orders: merge (don't wipe the Past snapshot).
                    byTab[OrderTab.PAST]?.forEach { o -> next = OrdersBoardReducer.upsert(next, o, fromLiveEvent = false) }
                    // New orders that appeared while the socket was down must still ring.
                    if (fromReconnect) {
                        val unseen = (byTab[OrderTab.NEW] ?: emptyList()).filter { s.orders[it.id] == null }.map { it.id }
                        if (unseen.isNotEmpty()) next = next.copy(unacknowledged = next.unacknowledged + unseen)
                    }
                    next
                }
                _tabErrors.update { it - tabs }
            }
            is Result.Err -> _tabErrors.update { it + tabs.associateWith { _ -> r.error } }
        }
        _refreshing.update { it - tabs }
    }

    fun acknowledge(orderId: String) = _state.update { OrdersBoardReducer.acknowledge(it, orderId) }
    fun acknowledgeAll() = _state.update { OrdersBoardReducer.acknowledgeAll(it) }

    /** Apply the result of an owner action (accept/reject/...) immediately. */
    fun applyActionResult(order: PartnerOrderDto) = _state.update { OrdersBoardReducer.upsert(it, order, fromLiveEvent = false) }
}
