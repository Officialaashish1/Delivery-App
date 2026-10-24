package `in`.localrush.customer.feature.tracking.vm

import `in`.localrush.core.common.geo.LatLng
import `in`.localrush.core.common.order.OrderEvents
import `in`.localrush.core.common.order.OrderStatus
import `in`.localrush.core.common.order.TimelineKey
import `in`.localrush.core.common.time.DateTimeUtils
import `in`.localrush.core.network.dto.ChatMessageDto
import `in`.localrush.core.network.dto.LatLngDto
import `in`.localrush.core.network.dto.OrderDetailDto
import `in`.localrush.core.network.dto.OrderEventDto
import `in`.localrush.core.network.dto.RiderLocationDto
import `in`.localrush.core.network.dto.RiderLocationEventDto
import `in`.localrush.core.network.dto.RiderPublicDto
import `in`.localrush.core.network.dto.RouteDto
import `in`.localrush.core.network.dto.TimelineEntryDto
import `in`.localrush.core.network.dto.TrackingSnapshotDto
import java.time.Instant

/** Rider GPS fix as shown on the map (the marker animates from the previous one). */
data class RiderFix(val point: LatLng, val bearing: Float?, val speedKmph: Float?, val recordedAt: Instant?)

enum class ConnectionState { CONNECTING, LIVE, RECONNECTING, POLLING }

/** Everything the tracking screen renders. Produced only by [TrackingReducer], so it is unit-testable without Android. */
data class TrackingUiState(
    val loading: Boolean = true,
    val error: `in`.localrush.core.common.result.AppError? = null,
    val order: OrderDetailDto? = null,
    val status: OrderStatus = OrderStatus.UNKNOWN,
    val statusDisplay: String = "",
    val timeline: List<TimelineEntryDto> = emptyList(),
    val rider: RiderPublicDto? = null,
    val riderFix: RiderFix? = null,
    val route: RouteDto? = null,
    val restaurant: LatLng? = null,
    val destination: LatLng? = null,
    val estimatedDeliveryAt: Instant? = null,
    val deliveryOtp: String? = null,
    val chat: List<ChatMessageDto> = emptyList(),
    val unreadChat: Int = 0,
    val connection: ConnectionState = ConnectionState.CONNECTING,
    /** Live "now" ticked by the VM so the ETA label re-renders every 30 s. */
    val now: Instant = Instant.EPOCH,
) {
    /** 0..3 for the 4-step header, -1 when failed. */
    val headerStep: Int get() = TimelineKey.headerStepIndex(status)
    val isTerminal: Boolean get() = status.isTerminal
    val riderOnTheWay: Boolean get() = status.riderOnTheWay
    val showOtp: Boolean get() = deliveryOtp != null && (status == OrderStatus.RIDER_ASSIGNED || status.riderOnTheWay)
    val canContactRider: Boolean get() = rider != null && (status == OrderStatus.RIDER_ASSIGNED || status.riderOnTheWay)

    /** ETA instant: `route.eta_at` (fresh, from OSRM) beats `estimated_delivery_at`. */
    val etaAt: Instant? get() = if (status.riderOnTheWay) (DateTimeUtils.parseInstant(route?.etaAt) ?: estimatedDeliveryAt) else estimatedDeliveryAt

    /** "12 min" / "Arriving now" / "5 min late"; null when unknown or terminal. */
    val etaLabel: String? get() = if (isTerminal) null else etaAt?.let { DateTimeUtils.etaLabel(it, if (now == Instant.EPOCH) Instant.now() else now) }

    /** Points the camera should keep in view. */
    val focusPoints: List<LatLng> get() = listOfNotNull(riderFix?.point, destination, if (!status.riderOnTheWay) restaurant else null)
}

/** Pure reducer: snapshot + socket events → [TrackingUiState]. */
object TrackingReducer {

    fun applySnapshot(state: TrackingUiState, snap: TrackingSnapshotDto, myUserId: String?): TrackingUiState {
        val o = snap.order
        val status = OrderStatus.fromWire(o.status)
        return state.copy(
            loading = false, error = null, order = o, status = status, statusDisplay = o.statusDisplay, timeline = o.timeline, rider = o.rider,
            riderFix = snap.riderLocation?.toFix() ?: state.riderFix?.takeIf { status.riderOnTheWay || status == OrderStatus.RIDER_ASSIGNED },
            route = snap.route ?: state.route,
            restaurant = snap.restaurantLocation?.toLatLng() ?: o.restaurantLocation?.toLatLng() ?: state.restaurant,
            destination = snap.deliveryLocation?.toLatLng() ?: o.deliveryLocation?.toLatLng() ?: state.destination,
            estimatedDeliveryAt = DateTimeUtils.parseInstant(o.estimatedDeliveryAt) ?: state.estimatedDeliveryAt,
            deliveryOtp = o.deliveryOtp,
            chat = mergeChat(state.chat, snap.chat),
            unreadChat = countUnread(mergeChat(state.chat, snap.chat), myUserId, state.chat.size),
        )
    }

    fun applyLocation(state: TrackingUiState, e: RiderLocationEventDto): TrackingUiState {
        val fix = RiderFix(LatLng(e.latitude, e.longitude), e.bearing, e.speed, DateTimeUtils.parseInstant(e.recordedAt))
        // Ignore out-of-order fixes.
        val prev = state.riderFix
        if (prev?.recordedAt != null && fix.recordedAt != null && fix.recordedAt.isBefore(prev.recordedAt)) return state
        return state.copy(riderFix = fix, route = e.route ?: state.route)
    }

    /** `status.*`, `order.placed`, `rider.assigned`, `rider.at_restaurant`, `rider.offered` events. */
    fun applyOrderEvent(state: TrackingUiState, e: OrderEventDto): TrackingUiState {
        val status = OrderStatus.fromEventType(e.type) ?: e.status?.let { OrderStatus.fromWire(it) }?.takeIf { it != OrderStatus.UNKNOWN } ?: state.status
        val rider = e.rider ?: state.rider
        val clearsRider = e.type == OrderEvents.ORDER_UNASSIGNED
        return state.copy(
            status = status,
            statusDisplay = e.statusDisplay ?: state.statusDisplay,
            timeline = e.timeline.ifEmpty { state.timeline },
            rider = if (clearsRider) null else rider,
            riderFix = if (clearsRider || status.isTerminal) null else state.riderFix,
            estimatedDeliveryAt = DateTimeUtils.parseInstant(e.estimatedDeliveryAt) ?: state.estimatedDeliveryAt,
            // The OTP is only in the REST payload; drop it once the order is no longer en route.
            deliveryOtp = if (status == OrderStatus.RIDER_ASSIGNED || status.riderOnTheWay) state.deliveryOtp else null,
            order = state.order?.copy(status = status.wire, statusDisplay = e.statusDisplay ?: state.order.statusDisplay, timeline = e.timeline.ifEmpty { state.order.timeline }, rider = if (clearsRider) null else rider, estimatedDeliveryAt = e.estimatedDeliveryAt ?: state.order.estimatedDeliveryAt),
        )
    }

    fun applyChat(state: TrackingUiState, m: ChatMessageDto, myUserId: String?, chatOpen: Boolean): TrackingUiState {
        if (state.chat.any { it.id == m.id }) return state
        val mine = m.senderId == myUserId || m.senderRole == "customer"
        return state.copy(chat = state.chat + m, unreadChat = if (mine || chatOpen) state.unreadChat else state.unreadChat + 1)
    }

    fun markChatRead(state: TrackingUiState): TrackingUiState = state.copy(unreadChat = 0)

    fun connection(state: TrackingUiState, c: ConnectionState): TrackingUiState = state.copy(connection = c)

    fun tick(state: TrackingUiState, now: Instant): TrackingUiState = state.copy(now = now)

    private fun mergeChat(current: List<ChatMessageDto>, incoming: List<ChatMessageDto>): List<ChatMessageDto> =
        (current + incoming).distinctBy { it.id }.sortedBy { DateTimeUtils.parseInstant(it.createdAt) ?: Instant.EPOCH }

    private fun countUnread(all: List<ChatMessageDto>, myUserId: String?, previouslySeen: Int): Int =
        all.drop(previouslySeen).count { !it.isRead && it.senderId != myUserId && it.senderRole != "customer" }

    private fun RiderLocationDto.toFix() = RiderFix(LatLng(latitude, longitude), bearing, speed, DateTimeUtils.parseInstant(recordedAt))
    private fun LatLngDto.toLatLng() = LatLng(latitude, longitude)
}
