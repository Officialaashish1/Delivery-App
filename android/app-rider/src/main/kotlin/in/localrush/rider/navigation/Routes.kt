package `in`.localrush.rider.navigation

import kotlinx.serialization.Serializable

/** Type-safe routes (Navigation 2.8). */
object Routes {
    @Serializable data object Splash
    @Serializable data object Auth
    /** Post-login decision point (registration / pending / main). */
    @Serializable data object Gate
    @Serializable data class Registration(val step: Int = 0)
    @Serializable data object Pending
    @Serializable data object Main
    @Serializable data class Offer(val assignmentId: String)
    @Serializable data class Delivery(val orderId: String)
    @Serializable data class OrderDetail(val orderId: String)
    @Serializable data object Payouts
    @Serializable data object Documents
    @Serializable data object Support
    @Serializable data class NewTicket(val orderId: String? = null)
    @Serializable data class Ticket(val ticketId: String)
}
