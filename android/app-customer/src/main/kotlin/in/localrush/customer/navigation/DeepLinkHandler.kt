package `in`.localrush.customer.navigation

import android.content.Intent
import android.net.Uri
import `in`.localrush.core.common.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface DeepLink {
    data class Order(val orderId: String) : DeepLink
    data class Restaurant(val slug: String) : DeepLink
    data class Ticket(val ticketId: String) : DeepLink
    data object Promo : DeepLink
    data object Home : DeepLink
}

/**
 * Buffers the launch/notification deep link until the NavHost is ready.
 * `localrush://order/{id}`, `localrush://orders/{id}`, `localrush://restaurant/{slug}`, `localrush://ticket/{id}`, `localrush://promo`.
 */
@Singleton
class DeepLinkHandler @Inject constructor() {
    private val _pending = MutableStateFlow<DeepLink?>(null)
    val pending: StateFlow<DeepLink?> = _pending

    fun offer(intent: Intent?) {
        val uri = intent?.data ?: return
        parse(uri)?.let { _pending.value = it }
    }

    fun consume() { _pending.value = null }

    fun parse(uri: Uri): DeepLink? {
        if (uri.scheme != Constants.DeepLinks.SCHEME) return null
        val id = uri.pathSegments.firstOrNull()
        return when (uri.host) {
            "order", "orders" -> id?.let { DeepLink.Order(it) }
            "restaurant", "restaurants" -> id?.let { DeepLink.Restaurant(it) }
            "ticket", "tickets" -> id?.let { DeepLink.Ticket(it) }
            "promo" -> DeepLink.Promo
            else -> DeepLink.Home
        }
    }
}
