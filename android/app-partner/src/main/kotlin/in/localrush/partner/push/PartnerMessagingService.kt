package `in`.localrush.partner.push

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import `in`.localrush.core.auth.AuthRepository
import `in`.localrush.core.common.AppRole
import `in`.localrush.core.common.order.OrderStatus
import `in`.localrush.core.push.LRMessagingService
import `in`.localrush.core.push.PushPayload
import `in`.localrush.partner.R
import `in`.localrush.partner.data.PartnerOrderFeed
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PartnerMessagingService : LRMessagingService() {
    @Inject lateinit var injectedAuthRepository: AuthRepository
    @Inject lateinit var orderFeed: PartnerOrderFeed
    override val role: AppRole = AppRole.PARTNER
    override val authRepository: AuthRepository get() = injectedAuthRepository
    override val smallIconRes: Int = R.drawable.ic_notification

    /** Feed the board even when the socket is down; the board then rings in-app or via the full-screen notification. */
    override fun onPayload(payload: PushPayload) {
        val id = payload.orderId ?: return
        orderFeed.onPushEvent(payload.type, id)
    }

    /** While the app is on screen the in-app alert (sound + banner) covers new orders; avoid a duplicate heads-up. */
    override fun shouldShowNotification(payload: PushPayload): Boolean {
        val foreground = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        return !(foreground && OrderStatus.fromEventType(payload.type) == OrderStatus.PLACED)
    }
}
