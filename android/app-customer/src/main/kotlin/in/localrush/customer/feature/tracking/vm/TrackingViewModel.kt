package `in`.localrush.customer.feature.tracking.vm

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.localrush.core.auth.SessionManager
import `in`.localrush.core.common.order.OrderEvents
import `in`.localrush.core.network.dto.AppConfigDto
import `in`.localrush.core.network.dto.ChatMessageDto
import `in`.localrush.core.network.dto.OrderEventDto
import `in`.localrush.core.network.dto.RiderLocationEventDto
import `in`.localrush.core.network.ws.WebSocketClient
import `in`.localrush.core.network.ws.type
import `in`.localrush.customer.data.repo.ConfigRepository
import `in`.localrush.customer.data.repo.OrdersRepository
import `in`.localrush.customer.data.repo.TrackingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import java.time.Instant
import javax.inject.Inject

sealed interface TrackingEvent {
    data class Dial(val phone: String) : TrackingEvent
    data class Message(val text: String) : TrackingEvent
    data object ContactUnavailable : TrackingEvent
}

/**
 * `GET tracking/orders/{id}/` snapshot, then `/ws/orders/{id}/tracking/`; while the socket is not connected the
 * snapshot is re-polled every 15 s. Chat rides the same socket (`{"type":"chat"}`), with REST as fallback.
 */
@HiltViewModel
class TrackingViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val tracking: TrackingRepository,
    private val orders: OrdersRepository,
    private val configRepo: ConfigRepository,
    private val json: Json,
    session: SessionManager,
) : ViewModel() {
    val orderId: String = savedState.get<String>("orderId") ?: ""
    private val myUserId: String? = session.currentUser?.id

    private val _state = MutableStateFlow(TrackingUiState(now = Instant.now()))
    val state: StateFlow<TrackingUiState> = _state.asStateFlow()
    private val _events = MutableSharedFlow<TrackingEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<TrackingEvent> = _events.asSharedFlow()
    val config: StateFlow<AppConfigDto> = configRepo.config

    private var socket: WebSocketClient? = null
    private var pollJob: Job? = null
    private var chatOpen = false
    private val _chatSending = MutableStateFlow(false)
    val chatSending: StateFlow<Boolean> = _chatSending.asStateFlow()

    init {
        viewModelScope.launch { configRepo.load() }
        viewModelScope.launch { while (isActive) { _state.update { TrackingReducer.tick(it, Instant.now()) }; delay(30_000) } }
        loadSnapshot(initial = true)
        connect()
    }

    fun retry() { _state.update { it.copy(loading = true, error = null) }; loadSnapshot(initial = true); socket?.reconnectNow() ?: connect() }

    private fun loadSnapshot(initial: Boolean) {
        viewModelScope.launch {
            tracking.snapshot(orderId).fold(
                onOk = { snap -> _state.update { TrackingReducer.applySnapshot(it, snap, myUserId) } },
                onErr = { e -> if (initial && _state.value.order == null) _state.update { it.copy(loading = false, error = e) } },
            )
        }
    }

    private fun connect() {
        val ws = tracking.socket(orderId).also { socket = it }
        viewModelScope.launch { ws.messages.collect(::onMessage) }
        viewModelScope.launch {
            ws.state.collect { s ->
                val c = when (s) {
                    WebSocketClient.State.Connected -> ConnectionState.LIVE
                    is WebSocketClient.State.Connecting -> if (_state.value.order == null) ConnectionState.CONNECTING else ConnectionState.RECONNECTING
                    is WebSocketClient.State.WaitingToReconnect, is WebSocketClient.State.Closed -> ConnectionState.POLLING
                    WebSocketClient.State.Idle -> ConnectionState.CONNECTING
                }
                _state.update { TrackingReducer.connection(it, c) }
                if (c == ConnectionState.LIVE) stopPolling() else startPolling()
            }
        }
        ws.connect()
    }

    /** REST fallback every 15 s while the socket is down (stops once the order is terminal). */
    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (isActive && !_state.value.isTerminal) { delay(15_000); loadSnapshot(initial = false) }
        }
    }
    private fun stopPolling() { pollJob?.cancel(); pollJob = null }

    private fun onMessage(obj: JsonObject) {
        val type = obj.type ?: return
        when {
            type == OrderEvents.SNAPSHOT -> runCatching { json.decodeFromJsonElement<`in`.localrush.core.network.dto.TrackingSnapshotDto>(obj) }.getOrNull()?.let { s -> _state.update { TrackingReducer.applySnapshot(it, s, myUserId) } }
            type == OrderEvents.RIDER_LOCATION -> runCatching { json.decodeFromJsonElement<RiderLocationEventDto>(obj) }.getOrNull()?.let { e -> _state.update { TrackingReducer.applyLocation(it, e) } }
            type == OrderEvents.CHAT_MESSAGE -> runCatching { json.decodeFromJsonElement<ChatMessageDto>(obj) }.getOrNull()?.let { m -> _state.update { TrackingReducer.applyChat(it, m, myUserId, chatOpen) } }
            type.startsWith("status.") || type == OrderEvents.ORDER_PLACED || type == OrderEvents.RIDER_ASSIGNED || type == OrderEvents.RIDER_AT_RESTAURANT || type == OrderEvents.ORDER_UNASSIGNED || type == OrderEvents.RIDER_OFFERED -> {
                runCatching { json.decodeFromJsonElement<OrderEventDto>(obj) }.getOrNull()?.let { e ->
                    _state.update { TrackingReducer.applyOrderEvent(it, e) }
                    // Status changes may reveal/hide the OTP or the rider card: refresh the authoritative REST view.
                    loadSnapshot(initial = false)
                    if (_state.value.isTerminal) stopPolling()
                }
            }
        }
    }

    // ---- chat ----
    fun setChatOpen(open: Boolean) {
        chatOpen = open
        if (open) { _state.update { TrackingReducer.markChatRead(it) }; socket?.send { put("type", "chat_read") } }
    }

    fun sendChat(text: String) {
        val t = text.trim()
        if (t.isEmpty() || _chatSending.value) return
        _chatSending.value = true
        viewModelScope.launch {
            val ws = socket
            if (ws != null && ws.isConnected) {
                ws.send { put("type", "chat"); put("text", t) } // echo arrives as chat.message
                delay(300)
            } else {
                tracking.sendChat(orderId, t).fold(
                    onOk = { m -> _state.update { TrackingReducer.applyChat(it, m, myUserId, true) } },
                    onErr = { e -> _events.tryEmit(TrackingEvent.Message(e.message)) },
                )
            }
            _chatSending.value = false
        }
    }

    // ---- call ----
    fun callRider() {
        viewModelScope.launch {
            orders.riderContact(orderId).fold(
                onOk = { c -> (c.phone ?: c.maskedPhone)?.let { _events.tryEmit(TrackingEvent.Dial(it)) } ?: _events.tryEmit(TrackingEvent.ContactUnavailable) },
                onErr = { e -> _events.tryEmit(TrackingEvent.Message(e.message)) },
            )
        }
    }

    override fun onCleared() { socket?.close(); stopPolling(); super.onCleared() }
}
