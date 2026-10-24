package `in`.localrush.rider.data

import `in`.localrush.core.auth.SessionManager
import `in`.localrush.core.auth.SessionState
import `in`.localrush.core.common.order.OrderEvents
import `in`.localrush.core.network.dto.OrderEventDto
import `in`.localrush.core.network.ws.WebSocketClient
import `in`.localrush.core.network.ws.type
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pending offer inbox fed by FCM (`rider.offered`) and the `/ws/rider/` socket. The NavHost observes
 * [pending] and opens the full-screen offer UI; the offer screen calls [consume] when it is handled.
 */
@Singleton
class OfferInbox @Inject constructor() {
    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending
    private val handled = ArrayDeque<String>()

    fun post(assignmentId: String) {
        if (assignmentId in handled) return
        _pending.value = assignmentId
    }

    fun consume(assignmentId: String) {
        handled.addLast(assignmentId)
        while (handled.size > 50) handled.removeFirst()
        _pending.update { if (it == assignmentId) null else it }
    }
}

/**
 * Keeps the `/ws/rider/` socket open while logged in and either online or in the foreground; fans out
 * offers to [OfferInbox] and order status events to [orderEvents] (dashboard + delivery screen refresh).
 */
@Singleton
class RiderRealtime @Inject constructor(
    private val factory: WebSocketClient.Factory,
    private val session: SessionManager,
    private val inbox: OfferInbox,
    private val foreground: AppForeground,
    private val activeDelivery: ActiveDeliveryStore,
    private val json: Json,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var client: WebSocketClient? = null
    private var collectJob: Job? = null

    private val _orderEvents = MutableSharedFlow<OrderEventDto>(extraBufferCapacity = 32)
    val orderEvents: SharedFlow<OrderEventDto> = _orderEvents.asSharedFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    fun start() {
        scope.launch {
            combine(session.state, foreground.isForeground, activeDelivery.wantsOnline, activeDelivery.active) { s, fg, online, active ->
                s is SessionState.LoggedIn && (fg || online || active != null)
            }.distinctUntilChanged().collect { shouldConnect -> if (shouldConnect) connect() else disconnect() }
        }
    }

    private fun connect() {
        if (client != null) return
        val c = factory.rider()
        client = c
        collectJob = scope.launch {
            launch { c.state.collect { _connected.value = it is WebSocketClient.State.Connected } }
            c.messages.collect { obj ->
                when (val t = obj.type) {
                    OrderEvents.RIDER_OFFERED -> {
                        val ev = runCatching { json.decodeFromJsonElement(OrderEventDto.serializer(), obj) }.getOrNull()
                        ev?.assignmentId?.let { inbox.post(it) }
                        ev?.let { _orderEvents.tryEmit(it) }
                    }
                    null, OrderEvents.LOCATION_ACK, "location.error", "error" -> Unit
                    else -> runCatching { json.decodeFromJsonElement(OrderEventDto.serializer(), obj) }.getOrNull()?.let { _orderEvents.tryEmit(it) }
                        ?: _orderEvents.tryEmit(OrderEventDto(type = t))
                }
            }
        }
        c.connect()
    }

    private fun disconnect() {
        collectJob?.cancel(); collectJob = null
        client?.close(); client = null
        _connected.value = false
    }
}
