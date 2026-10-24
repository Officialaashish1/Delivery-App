package `in`.localrush.rider.feature.delivery.vm

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import `in`.localrush.core.common.geo.Geo
import `in`.localrush.core.common.geo.LatLng
import `in`.localrush.core.common.order.OrderEvents
import `in`.localrush.core.common.order.OrderStatus
import `in`.localrush.core.common.result.AppError
import `in`.localrush.core.common.result.ErrorCodes
import `in`.localrush.core.location.LocationClient
import `in`.localrush.core.network.dto.ChatMessageDto
import `in`.localrush.core.network.dto.OrderEventDto
import `in`.localrush.core.network.ws.WebSocketClient
import `in`.localrush.core.network.ws.type
import `in`.localrush.rider.R
import `in`.localrush.rider.data.ActiveDeliveryStore
import `in`.localrush.rider.data.CustomerContactDto
import `in`.localrush.rider.data.DeliveryRepository
import `in`.localrush.rider.data.DeliveryStep
import `in`.localrush.rider.data.LocationBus
import `in`.localrush.rider.data.NetworkMonitor
import `in`.localrush.rider.data.NextManeuver
import `in`.localrush.rider.data.ProblemCategory
import `in`.localrush.rider.data.Progress
import `in`.localrush.rider.data.RiderOrderDto
import `in`.localrush.rider.data.RiderRealtime
import `in`.localrush.rider.data.RouteGeometry
import `in`.localrush.rider.data.RouteSnapshot
import `in`.localrush.rider.data.SupportRepository
import `in`.localrush.rider.data.TrackingController
import `in`.localrush.rider.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

enum class DeliverySheet { PICKUP_OTP, DELIVER_OTP, PROBLEM, CHAT, FAIL_REASON }

data class DeliveryUiState(
    val orderId: String,
    val loading: Boolean = true,
    val error: AppError? = null,
    val order: RiderOrderDto? = null,
    val core: DeliveryCore = DeliveryCore(),
    // live position
    val position: LatLng? = null,
    val heading: Float? = null,
    val speedKmph: Float = 0f,
    val gpsAccuracyM: Float? = null,
    // route
    val route: RouteSnapshot? = null,
    val progress: Progress? = null,
    val routeLoading: Boolean = false,
    val routeError: AppError? = null,
    val following: Boolean = true,
    val voiceEnabled: Boolean = true,
    // actions
    val actionInProgress: Boolean = false,
    val actionError: AppError? = null,
    val sheet: DeliverySheet? = null,
    val otp: String = "",
    val otpError: String? = null,
    val otpTooShort: Boolean = false,
    val proofUri: Uri? = null,
    val contact: CustomerContactDto? = null,
    val contactLoading: Boolean = false,
    val chat: List<ChatMessageDto> = emptyList(),
    val chatSending: Boolean = false,
    val problemSubmitted: Boolean = false,
    val networkOnline: Boolean = true,
    /** Set once the order is delivered: payout for the earnings toast. */
    val completedPayout: String? = null,
) {
    val step: DeliveryStep get() = core.step
    val destination: LatLng? get() = DeliveryReducer.destination(order, core)
    val next: NextManeuver? get() = progress?.next
    val remainingM: Double? get() = progress?.remainingM ?: (position?.let { p -> destination?.let { Geo.haversineMeters(p, it) } })
    val remainingS: Double? get() = progress?.remainingS
    val offRoute: Boolean get() = RouteGeometry.isOffRoute(progress)
}

sealed interface DeliveryUiEvent {
    data class Speak(val text: String) : DeliveryUiEvent
    data class Message(val resId: Int) : DeliveryUiEvent
    data class Toast(val text: String) : DeliveryUiEvent
}

@HiltViewModel
class DeliveryViewModel @Inject constructor(
    private val repo: DeliveryRepository,
    private val support: SupportRepository,
    private val store: ActiveDeliveryStore,
    private val locationClient: LocationClient,
    private val tracking: TrackingController,
    private val wsFactory: WebSocketClient.Factory,
    private val json: Json,
    realtime: RiderRealtime,
    network: NetworkMonitor,
    bus: LocationBus,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val orderId: String = savedState.toRoute<Routes.Delivery>().orderId
    private val _state = MutableStateFlow(DeliveryUiState(orderId = orderId, core = DeliveryCore(arrivedAtRestaurant = store.active.value?.takeIf { it.orderId == orderId }?.arrivedAtRestaurant ?: false)))
    val state: StateFlow<DeliveryUiState> = _state
    private val _events = MutableSharedFlow<DeliveryUiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<DeliveryUiEvent> = _events

    private var ws: WebSocketClient? = null
    private var routeJob: Job? = null
    private var lastRouteLeg: String? = null
    private var announcedStep: Int = -1
    private var announcedNear: Int = -1

    init {
        load()
        viewModelScope.launch { network.isOnline.collect { on -> _state.update { it.copy(networkOnline = on) } } }
        viewModelScope.launch { realtime.orderEvents.filter { it.orderId == orderId }.collect { onOrderEvent(it) } }
        // Seed from the service's last fix so the map has a position immediately.
        bus.last.value?.let { onFix(LatLng(it.latitude, it.longitude), if (it.hasBearing()) it.bearing else null, it.speed * 3.6f, if (it.hasAccuracy()) it.accuracy else null) }
        viewModelScope.launch {
            locationClient.locationUpdates(intervalMs = 3_000, minDistanceM = 2f).catch { }.collect { loc ->
                onFix(LatLng(loc.latitude, loc.longitude), if (loc.hasBearing()) loc.bearing else null, loc.speed * 3.6f, if (loc.hasAccuracy()) loc.accuracy else null)
            }
        }
        viewModelScope.launch {
            // Periodic re-route (traffic / drift) every 60 s while navigating.
            while (isActive) { delay(RouteGeometry.REFRESH_INTERVAL_MS); if (!_state.value.step.isTerminal) fetchRoute(force = true) }
        }
        connectTracking()
    }

    fun load() {
        _state.update { it.copy(loading = it.order == null, error = null) }
        viewModelScope.launch {
            repo.order(orderId).fold(
                onOk = { applyOrder(it); _state.update { s -> s.copy(loading = false) }; fetchRoute(force = true) },
                onErr = { e -> _state.update { it.copy(loading = false, error = if (it.order == null) e else null) } },
            )
        }
    }

    private fun applyOrder(order: RiderOrderDto) {
        val status = OrderStatus.fromWire(order.status)
        _state.update { s -> s.copy(order = order, core = DeliveryReducer.reduce(s.core, DeliveryEvent.OrderUpdated(status))) }
        val core = _state.value.core
        when (core.step) {
            DeliveryStep.DELIVERED -> { store.clear(); _state.update { it.copy(completedPayout = order.riderPayout) } }
            DeliveryStep.FAILED -> store.clear()
            else -> { store.set(order.id, order.status, core.arrivedAtRestaurant); tracking.start() }
        }
        if (core.leg != lastRouteLeg) fetchRoute(force = true)
    }

    private fun onOrderEvent(ev: OrderEventDto) {
        val status = OrderStatus.fromEventType(ev.type) ?: ev.status?.let { OrderStatus.fromWire(it) }
        if (status != null && status != OrderStatus.UNKNOWN) load() // pull the full payload (OTP-less, has timestamps)
        if (ev.type == OrderEvents.ORDER_UNASSIGNED) { store.clear(); _events.tryEmit(DeliveryUiEvent.Message(R.string.delivery_unassigned)); load() }
    }

    private fun connectTracking() {
        val c = wsFactory.orderTracking(orderId)
        ws = c
        viewModelScope.launch {
            c.messages.collect { obj ->
                when (val t = obj.type) {
                    OrderEvents.SNAPSHOT -> runCatching { json.decodeFromJsonElement(ChatSnapshot.serializer(), obj) }.getOrNull()?.let { snap -> _state.update { it.copy(chat = snap.chat) } }
                    OrderEvents.CHAT_MESSAGE -> runCatching { json.decodeFromJsonElement(ChatMessageDto.serializer(), obj) }.getOrNull()?.let { m ->
                        _state.update { s -> if (s.chat.any { it.id == m.id }) s else s.copy(chat = s.chat + m) }
                        if (m.senderRole != "rider" && _state.value.sheet != DeliverySheet.CHAT) _events.tryEmit(DeliveryUiEvent.Toast(m.text))
                    }
                    OrderEvents.RIDER_LOCATION -> Unit
                    null -> Unit
                    else -> if (t.startsWith("status.") || t.startsWith("rider.") || t == OrderEvents.ORDER_UNASSIGNED) {
                        runCatching { json.decodeFromJsonElement(OrderEventDto.serializer(), obj) }.getOrNull()?.let { onOrderEvent(it) }
                    }
                }
            }
        }
        c.connect()
    }

    // ---------------- navigation ----------------

    private fun onFix(p: LatLng, bearing: Float?, speedKmph: Float, accuracy: Float?) {
        val s = _state.value
        val route = s.route
        val progress = route?.let { RouteGeometry.progress(it, p) }
        _state.update { it.copy(position = p, heading = bearing ?: it.heading, speedKmph = speedKmph, gpsAccuracyM = accuracy, progress = progress) }
        if (progress != null) announce(progress)
        if (route == null || RouteGeometry.isOffRoute(progress)) fetchRoute(force = false)
    }

    private fun announce(progress: Progress) {
        if (!_state.value.voiceEnabled) return
        val next = progress.next ?: return
        if (next.index != announcedStep) {
            announcedStep = next.index
            announcedNear = -1
            val d = next.distanceM
            val text = if (d > 80) "In ${roundedDistance(d)}, ${next.step.instruction}" else next.step.instruction
            _events.tryEmit(DeliveryUiEvent.Speak(text))
        } else if (next.distanceM < 60 && announcedNear != next.index) {
            announcedNear = next.index
            _events.tryEmit(DeliveryUiEvent.Speak(next.step.instruction))
        }
    }

    private fun roundedDistance(m: Double): String = when {
        m >= 1000 -> String.format(java.util.Locale.ROOT, "%.1f kilometres", m / 1000)
        else -> "${((m / 50).toInt() * 50).coerceAtLeast(50)} metres"
    }

    fun fetchRoute(force: Boolean) {
        val s = _state.value
        val p = s.position ?: return
        if (s.step.isTerminal || s.order == null) return
        if (routeJob?.isActive == true) return
        if (!force && s.route != null && System.currentTimeMillis() - s.route.fetchedAtMs < 10_000) return // debounce off-route refetches
        routeJob = viewModelScope.launch {
            _state.update { it.copy(routeLoading = true) }
            repo.route(orderId, p.latitude, p.longitude).fold(
                onOk = { dto ->
                    val snap = RouteSnapshot.from(dto)
                    lastRouteLeg = dto.leg ?: _state.value.core.leg
                    announcedStep = -1
                    _state.update { it.copy(route = snap, routeLoading = false, routeError = null, progress = RouteGeometry.progress(snap, p)) }
                },
                onErr = { e -> _state.update { it.copy(routeLoading = false, routeError = e) } },
            )
        }
    }

    fun setFollowing(v: Boolean) = _state.update { it.copy(following = v) }
    fun toggleVoice() = _state.update { it.copy(voiceEnabled = !it.voiceEnabled) }

    // ---------------- step actions ----------------

    fun arrivedAtRestaurant() {
        if (_state.value.actionInProgress) return
        _state.update { it.copy(actionInProgress = true, actionError = null) }
        viewModelScope.launch {
            repo.arrivedAtRestaurant(orderId).fold(
                onOk = { order ->
                    store.markArrived(orderId)
                    _state.update { s -> s.copy(actionInProgress = false, core = DeliveryReducer.reduce(s.core, DeliveryEvent.ArrivedAtRestaurant)) }
                    applyOrder(order)
                },
                onErr = { e -> _state.update { it.copy(actionInProgress = false, actionError = e) } },
            )
        }
    }

    fun openSheet(sheet: DeliverySheet) {
        _state.update { it.copy(sheet = sheet, otp = "", otpError = null, actionError = null, problemSubmitted = false) }
        if (sheet == DeliverySheet.CHAT) loadChat()
    }
    fun closeSheet() = _state.update { it.copy(sheet = null, otp = "", otpError = null, proofUri = null) }
    fun onOtpChange(v: String) = _state.update { it.copy(otp = v.filter { c -> c.isDigit() }.take(6), otpError = null, otpTooShort = false) }
    fun setProof(uri: Uri?) = _state.update { it.copy(proofUri = uri) }
    fun clearActionError() = _state.update { it.copy(actionError = null) }

    fun submitPickup() {
        val s = _state.value
        if (s.actionInProgress) return
        if (s.otp.length < 4) { _state.update { it.copy(otpTooShort = true) }; return }
        _state.update { it.copy(actionInProgress = true, actionError = null) }
        viewModelScope.launch {
            repo.pickup(orderId, s.otp).fold(
                onOk = { order -> _state.update { it.copy(actionInProgress = false, sheet = null, otp = "") }; applyOrder(order); _events.tryEmit(DeliveryUiEvent.Message(R.string.delivery_picked_up)) },
                onErr = { e -> handleOtpError(e) },
            )
        }
    }

    fun submitDeliver() {
        val s = _state.value
        if (s.actionInProgress) return
        if (s.otp.length < 4) { _state.update { it.copy(otpTooShort = true) }; return }
        _state.update { it.copy(actionInProgress = true, actionError = null) }
        viewModelScope.launch {
            repo.deliver(orderId, s.otp, s.proofUri).fold(
                onOk = { order -> _state.update { it.copy(actionInProgress = false, sheet = null, otp = "", proofUri = null) }; applyOrder(order) },
                onErr = { e -> handleOtpError(e) },
            )
        }
    }

    private fun handleOtpError(e: AppError) {
        val api = e as? AppError.Api
        when {
            api?.code == ErrorCodes.OTP_INVALID -> _state.update { it.copy(actionInProgress = false, otp = "", otpError = api.message) }
            api?.code == ErrorCodes.NOT_READY -> _state.update { it.copy(actionInProgress = false, sheet = null, actionError = e) }
            api?.code == ErrorCodes.INVALID_TRANSITION -> { _state.update { it.copy(actionInProgress = false, sheet = null, actionError = e) }; load() }
            else -> _state.update { it.copy(actionInProgress = false, actionError = e) }
        }
    }

    fun markDeliveryFailed(reason: String) {
        if (_state.value.actionInProgress) return
        _state.update { it.copy(actionInProgress = true, actionError = null) }
        viewModelScope.launch {
            repo.deliveryFailed(orderId, reason).fold(
                onOk = { order -> _state.update { it.copy(actionInProgress = false, sheet = null) }; applyOrder(order) },
                onErr = { e -> _state.update { it.copy(actionInProgress = false, actionError = e) } },
            )
        }
    }

    fun loadContact(onLoaded: (CustomerContactDto) -> Unit) {
        val cached = _state.value.contact
        if (cached?.phone != null) { onLoaded(cached); return }
        _state.update { it.copy(contactLoading = true) }
        viewModelScope.launch {
            repo.customerContact(orderId).fold(
                onOk = { c -> _state.update { it.copy(contactLoading = false, contact = c) }; onLoaded(c) },
                onErr = { e -> _state.update { it.copy(contactLoading = false, actionError = e) } },
            )
        }
    }

    private fun loadChat() {
        viewModelScope.launch { repo.chat(orderId).onSuccess { list -> _state.update { it.copy(chat = list) } } }
    }

    fun sendChat(text: String) {
        if (text.isBlank() || _state.value.chatSending) return
        _state.update { it.copy(chatSending = true) }
        viewModelScope.launch {
            repo.sendChat(orderId, text.trim()).fold(
                onOk = { m -> _state.update { s -> s.copy(chatSending = false, chat = if (s.chat.any { it.id == m.id }) s.chat else s.chat + m) } },
                onErr = { e -> _state.update { it.copy(chatSending = false, actionError = e) } },
            )
        }
    }

    fun reportProblem(category: ProblemCategory, description: String) {
        if (_state.value.actionInProgress) return
        _state.update { it.copy(actionInProgress = true, actionError = null) }
        val order = _state.value.order
        val subject = "${category.wire.replace('_', ' ').replaceFirstChar { it.uppercase() }} – ${order?.orderNumber ?: orderId}"
        viewModelScope.launch {
            support.create(orderId, category.wire, subject, description.ifBlank { subject }).fold(
                onOk = { _state.update { it.copy(actionInProgress = false, problemSubmitted = true) } },
                onErr = { e -> _state.update { it.copy(actionInProgress = false, actionError = e) } },
            )
        }
    }

    override fun onCleared() {
        ws?.close()
        super.onCleared()
    }

    @kotlinx.serialization.Serializable
    private data class ChatSnapshot(val chat: List<ChatMessageDto> = emptyList())
}
