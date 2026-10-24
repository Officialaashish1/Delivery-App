package `in`.localrush.rider.push

import `in`.localrush.core.auth.AuthRepository
import `in`.localrush.core.common.AppRole
import `in`.localrush.core.push.LRMessagingService
import `in`.localrush.core.push.PushPayload
import `in`.localrush.rider.R
import `in`.localrush.rider.data.AppForeground
import `in`.localrush.rider.data.OfferInbox
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class RiderMessagingService : LRMessagingService() {
    @Inject lateinit var injectedAuthRepository: AuthRepository
    @Inject lateinit var inbox: OfferInbox
    @Inject lateinit var foreground: AppForeground

    override val role: AppRole = AppRole.RIDER
    override val authRepository: AuthRepository get() = injectedAuthRepository
    override val smallIconRes: Int = R.drawable.ic_notification

    /** `rider.offered` → in-app offer screen (the NavHost observes the inbox). */
    override fun onPayload(payload: PushPayload) {
        if (payload.isRiderOffer) payload.assignmentId?.let { inbox.post(it) }
    }

    /** In the foreground the offer screen itself rings and vibrates; the full-screen notification is only for background. */
    override fun shouldShowNotification(payload: PushPayload): Boolean = !(payload.isRiderOffer && foreground.isForeground.value)
}
