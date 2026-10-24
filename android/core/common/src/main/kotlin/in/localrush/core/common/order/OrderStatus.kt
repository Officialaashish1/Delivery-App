package `in`.localrush.core.common.order

/** Order statuses exactly as in docs/API_CONTRACTS.md. [wire] is the backend value. */
enum class OrderStatus(val wire: String, val isTerminal: Boolean = false) {
    PENDING_PAYMENT("pending_payment"),
    PLACED("placed"),
    RESTAURANT_ACCEPTED("restaurant_accepted"),
    PREPARING("preparing"),
    READY_FOR_PICKUP("ready_for_pickup"),
    RIDER_ASSIGNED("rider_assigned"),
    PICKED_UP("picked_up"),
    OUT_FOR_DELIVERY("out_for_delivery"),
    NEAR_DESTINATION("near_destination"),
    DELIVERED("delivered", isTerminal = true),
    REJECTED("rejected", isTerminal = true),
    CANCELLED("cancelled", isTerminal = true),
    PAYMENT_FAILED("payment_failed", isTerminal = true),
    DELIVERY_FAILED("delivery_failed", isTerminal = true),
    UNKNOWN("unknown");

    val isActive: Boolean get() = !isTerminal && this != UNKNOWN
    val isFailure: Boolean get() = this == REJECTED || this == CANCELLED || this == PAYMENT_FAILED || this == DELIVERY_FAILED
    val riderOnTheWay: Boolean get() = this == PICKED_UP || this == OUT_FOR_DELIVERY || this == NEAR_DESTINATION

    /** Which customer timeline step this status completes (null for failure/pending). */
    val timelineKey: TimelineKey? get() = when (this) {
        PLACED -> TimelineKey.PLACED
        RESTAURANT_ACCEPTED -> TimelineKey.ACCEPTED
        PREPARING -> TimelineKey.PREPARING
        READY_FOR_PICKUP -> TimelineKey.READY
        RIDER_ASSIGNED -> TimelineKey.RIDER_ASSIGNED
        PICKED_UP -> TimelineKey.PICKED_UP
        OUT_FOR_DELIVERY -> TimelineKey.ON_THE_WAY
        NEAR_DESTINATION -> TimelineKey.NEAR
        DELIVERED -> TimelineKey.DELIVERED
        else -> null
    }

    /** "status.<status>" WebSocket / push event type for this status. */
    val eventType: String get() = "status.$wire"

    companion object {
        fun fromWire(value: String?): OrderStatus = entries.firstOrNull { it.wire == value } ?: UNKNOWN

        /** "status.out_for_delivery" → OUT_FOR_DELIVERY, else null. */
        fun fromEventType(type: String?): OrderStatus? =
            type?.takeIf { it.startsWith("status.") }?.removePrefix("status.")?.let { fromWire(it) }?.takeIf { it != UNKNOWN }
    }
}

/** Timeline keys (customer UI) exactly as in docs/API_CONTRACTS.md, in display order. */
enum class TimelineKey(val wire: String) {
    PLACED("placed"),
    ACCEPTED("accepted"),
    PREPARING("preparing"),
    READY("ready"),
    RIDER_ASSIGNED("rider_assigned"),
    PICKED_UP("picked_up"),
    ON_THE_WAY("on_the_way"),
    NEAR("near"),
    DELIVERED("delivered");

    companion object {
        fun fromWire(value: String?): TimelineKey? = entries.firstOrNull { it.wire == value }

        /** Compact 4-step header used by StepProgressBar: Placed → Preparing → On the way → Delivered. */
        val headerSteps: List<TimelineKey> = listOf(PLACED, PREPARING, ON_THE_WAY, DELIVERED)

        /** Index into [headerSteps] for the current status (-1 for failure/pending). */
        fun headerStepIndex(status: OrderStatus): Int = when (status) {
            OrderStatus.PLACED, OrderStatus.RESTAURANT_ACCEPTED -> 0
            OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP, OrderStatus.RIDER_ASSIGNED -> 1
            OrderStatus.PICKED_UP, OrderStatus.OUT_FOR_DELIVERY, OrderStatus.NEAR_DESTINATION -> 2
            OrderStatus.DELIVERED -> 3
            else -> -1
        }
    }
}

/** Order lifecycle event names on WebSocket/push beyond status.*. */
object OrderEvents {
    const val ORDER_PLACED = "order.placed"
    const val RIDER_ASSIGNED = "rider.assigned"
    const val RIDER_OFFERED = "rider.offered"
    const val RIDER_AT_RESTAURANT = "rider.at_restaurant"
    const val RIDER_LOCATION = "rider.location"
    const val ORDER_UNASSIGNED = "order.unassigned"
    const val CHAT_MESSAGE = "chat.message"
    const val LOCATION_ACK = "location.ack"
    const val SNAPSHOT = "snapshot"
    const val PING = "ping"
    const val PONG = "pong"
    const val PROMO = "promo"
    const val TICKET_REPLY = "ticket.reply"
}
