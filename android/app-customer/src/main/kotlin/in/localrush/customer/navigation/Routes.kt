package `in`.localrush.customer.navigation

import kotlinx.serialization.Serializable

/** Type-safe routes (Navigation 2.8). One NavHost; the Main shell owns the 4 bottom tabs. */
object Routes {
    @Serializable data object Splash
    @Serializable data object Onboarding
    /** Pick/confirm delivery location. [first] = part of first launch (no back). */
    @Serializable data class PickLocation(val first: Boolean = false)
    @Serializable data object Auth
    @Serializable data object Terms
    @Serializable data class Main(val tab: Int = 0)
    @Serializable data object Search
    /** Filtered list. [sort] is a `RestaurantSort.wire` value used as the initial sort (home sections). */
    @Serializable data class RestaurantList(val title: String, val category: String? = null, val cuisine: String? = null, val query: String? = null, val sort: String? = null, val openNow: Boolean = false)
    @Serializable data class Restaurant(val slug: String, val itemId: String? = null)
    @Serializable data object Cart
    @Serializable data object Checkout
    @Serializable data class AddressEdit(val addressId: String? = null, val forCheckout: Boolean = false)
    @Serializable data class OrderSuccess(val orderId: String)
    @Serializable data class OrderDetail(val orderId: String)
    @Serializable data class Tracking(val orderId: String)
    @Serializable data class Chat(val orderId: String)
    @Serializable data class Rate(val orderId: String)
    @Serializable data object Notifications
    @Serializable data object EditProfile
    @Serializable data object Addresses
    @Serializable data object Help
    @Serializable data class NewTicket(val orderId: String? = null)
    @Serializable data class TicketDetail(val ticketId: String)
    @Serializable data object About
}

object MainTabs { const val HOME = 0; const val SEARCH = 1; const val ORDERS = 2; const val PROFILE = 3 }
