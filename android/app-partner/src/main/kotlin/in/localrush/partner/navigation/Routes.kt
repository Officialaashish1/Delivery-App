package `in`.localrush.partner.navigation

import kotlinx.serialization.Serializable

/** Type-safe routes (Navigation 2.8). */
object Routes {
    @Serializable data object Splash
    @Serializable data object Auth
    /** Decides between Registration / PendingApproval / Main after login. */
    @Serializable data object Gate
    @Serializable data object Registration
    @Serializable data object PendingApproval
    /** Bottom-nav shell: Dashboard, Orders, Menu, Finance, More. [tab] is the initial tab. */
    @Serializable data class Main(val tab: String = "dashboard")
    @Serializable data class OrderDetail(val orderId: String)
    @Serializable data object MenuCategories
    @Serializable data class MenuItems(val categoryId: String? = null)
    @Serializable data class ItemEditor(val itemId: String? = null, val categoryId: String? = null)
    @Serializable data object RestaurantSettings
    @Serializable data object HoursEditor
    @Serializable data object Holidays
    @Serializable data object Documents
    @Serializable data object ProfileEdit
    @Serializable data object FinanceLines
    @Serializable data object Settlements
    @Serializable data object Reviews
    @Serializable data object Notifications
    @Serializable data object Support
    @Serializable data class Ticket(val ticketId: String)
    @Serializable data object NewTicket
}
