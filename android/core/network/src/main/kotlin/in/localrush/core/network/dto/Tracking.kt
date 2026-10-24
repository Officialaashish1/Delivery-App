package `in`.localrush.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `GET tracking/orders/{id}/` and the WS `snapshot` message (minus `type`). */
@Serializable
data class TrackingSnapshotDto(
    val order: OrderDetailDto,
    @SerialName("rider_location") val riderLocation: RiderLocationDto? = null,
    val route: RouteDto? = null,
    @SerialName("restaurant_location") val restaurantLocation: LatLngDto? = null,
    @SerialName("delivery_location") val deliveryLocation: LatLngDto? = null,
    val chat: List<ChatMessageDto> = emptyList(),
)

@Serializable
data class RiderLocationDto(
    val latitude: Double,
    val longitude: Double,
    val bearing: Float? = null,
    /** km/h as sent by riders. */
    val speed: Float? = null,
    val accuracy: Float? = null,
    @SerialName("recorded_at") val recordedAt: String? = null,
)

@Serializable
data class RouteDto(
    /** Encoded polyline, precision 5. */
    val geometry: String = "",
    @SerialName("distance_m") val distanceM: Double = 0.0,
    @SerialName("duration_s") val durationS: Double = 0.0,
    @SerialName("duration_min") val durationMin: Double? = null,
    val leg: String? = null,
    @SerialName("eta_at") val etaAt: String? = null,
    @SerialName("computed_at") val computedAt: String? = null,
    val steps: List<RouteStepDto> = emptyList(),
)

@Serializable
data class RouteStepDto(
    val instruction: String = "",
    val name: String = "",
    @SerialName("distance_m") val distanceM: Double = 0.0,
    @SerialName("duration_s") val durationS: Double = 0.0,
    @SerialName("maneuver_type") val maneuverType: String = "",
    val modifier: String? = null,
    /** [lng, lat] as OSRM emits. */
    val location: List<Double> = emptyList(),
)

@Serializable
data class ChatMessageDto(
    val id: String,
    @SerialName("order_id") val orderId: String? = null,
    @SerialName("sender_id") val senderId: String = "",
    @SerialName("sender_role") val senderRole: String = "",
    val text: String = "",
    @SerialName("is_read") val isRead: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class ChatSendDto(val text: String)

/** `POST rider/location/` single fix (also the WS `{"type":"location", ...}` body). */
@Serializable
data class LocationFixDto(
    @SerialName("order_id") val orderId: String? = null,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float? = null,
    /** km/h */
    val speed: Float? = null,
    val bearing: Float? = null,
    /** ISO-8601 */
    val timestamp: String,
)

@Serializable
data class LocationBatchDto(val locations: List<LocationFixDto>)

@Serializable
data class LocationAckDto(
    val accepted: Boolean = false,
    val reason: String? = null,
    @SerialName("order_tracked") val orderTracked: Boolean? = null,
)

/** WS order event (`status.*`, `order.placed`, `rider.assigned`, ...). */
@Serializable
data class OrderEventDto(
    val type: String,
    @SerialName("order_id") val orderId: String? = null,
    @SerialName("order_number") val orderNumber: String? = null,
    val status: String? = null,
    @SerialName("status_display") val statusDisplay: String? = null,
    @SerialName("estimated_delivery_at") val estimatedDeliveryAt: String? = null,
    val rider: RiderPublicDto? = null,
    val timeline: List<TimelineEntryDto> = emptyList(),
    @SerialName("assignment_id") val assignmentId: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
)

/** WS `rider.location` event. */
@Serializable
data class RiderLocationEventDto(
    val type: String = "rider.location",
    @SerialName("order_id") val orderId: String? = null,
    @SerialName("rider_id") val riderId: String? = null,
    val latitude: Double,
    val longitude: Double,
    val bearing: Float? = null,
    val speed: Float? = null,
    val accuracy: Float? = null,
    @SerialName("recorded_at") val recordedAt: String? = null,
    val route: RouteDto? = null,
)
