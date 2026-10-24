package `in`.localrush.core.push

import `in`.localrush.core.common.Constants
import `in`.localrush.core.common.order.OrderEvents
import `in`.localrush.core.common.order.OrderStatus

/**
 * Parsed FCM data payload. Keys per docs/API_CONTRACTS.md:
 * `type, order_id, order_number, status, assignment_id, expires_at, title, body`.
 */
data class PushPayload(
    val type: String,
    val orderId: String?,
    val orderNumber: String?,
    val status: String?,
    val assignmentId: String?,
    val expiresAt: String?,
    val title: String?,
    val body: String?,
    val raw: Map<String, String>,
) {
    val orderStatus: OrderStatus? get() = OrderStatus.fromEventType(type) ?: status?.let { OrderStatus.fromWire(it) }?.takeIf { it != OrderStatus.UNKNOWN }
    val isStatusEvent: Boolean get() = type.startsWith("status.")
    val isRiderOffer: Boolean get() = type == OrderEvents.RIDER_OFFERED
    val isChat: Boolean get() = type == OrderEvents.CHAT_MESSAGE
    val isPromo: Boolean get() = type == OrderEvents.PROMO
    val isTicketReply: Boolean get() = type == OrderEvents.TICKET_REPLY
    val ticketId: String? get() = raw["ticket_id"]

    /** Deep link the notification should open. */
    val deepLink: String get() = when {
        isRiderOffer && assignmentId != null -> Constants.DeepLinks.OFFER + assignmentId
        orderId != null -> Constants.DeepLinks.ORDER + orderId
        isTicketReply && ticketId != null -> Constants.DeepLinks.TICKET + ticketId
        isPromo -> Constants.DeepLinks.PROMO
        else -> Constants.DeepLinks.HOME
    }

    /** Stable notification id so repeated updates for one order replace each other. */
    val notificationId: Int get() = (orderId ?: assignmentId ?: ticketId ?: type).hashCode() and 0x7fffffff

    companion object {
        fun from(data: Map<String, String>): PushPayload = PushPayload(
            type = data["type"] ?: "unknown",
            orderId = data["order_id"],
            orderNumber = data["order_number"],
            status = data["status"],
            assignmentId = data["assignment_id"],
            expiresAt = data["expires_at"],
            title = data["title"],
            body = data["body"],
            raw = data,
        )
    }
}
