package `in`.localrush.customer.push

import `in`.localrush.core.auth.AuthRepository
import `in`.localrush.core.common.AppRole
import `in`.localrush.core.push.LRMessagingService
import `in`.localrush.core.push.PushPayload
import `in`.localrush.customer.R
import `in`.localrush.customer.data.repo.NotificationsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Customer FCM service: channels `localrush_orders` / `localrush_promos`; taps deep-link to `localrush://order/{id}`
 * (handled by MainActivity → tracking screen). Also bumps the unread badge when the app is in the foreground.
 */
@AndroidEntryPoint
class CustomerMessagingService : LRMessagingService() {
    @Inject lateinit var injectedAuthRepository: AuthRepository
    @Inject lateinit var notifications: NotificationsRepository
    override val role: AppRole = AppRole.CUSTOMER
    override val authRepository: AuthRepository get() = injectedAuthRepository
    override val smallIconRes: Int = R.drawable.ic_notification
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onPayload(payload: PushPayload) {
        scope.launch { notifications.refreshUnread() }
    }
}
