package `in`.localrush.core.push

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import `in`.localrush.core.auth.AuthRepository
import `in`.localrush.core.common.AppRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Base FCM service. Each app subclasses it (annotated `@AndroidEntryPoint`), injects [AuthRepository]
 * and declares the service in its manifest. Subclasses may override [shouldShowNotification] to
 * suppress notifications while the relevant screen is in the foreground, and [onPayload] to
 * forward events to in-app state (e.g. rider offer popup).
 */
abstract class LRMessagingService : FirebaseMessagingService() {

    protected abstract val role: AppRole
    protected abstract val authRepository: AuthRepository
    /** Small icon resource (white-on-transparent). */
    protected abstract val smallIconRes: Int
    /** Accent colour (ARGB) for the notification. */
    protected open val accentColor: Int = 0xFFF26B1D.toInt()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch { authRepository.registerDevice(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val payload = PushPayload.from(message.data)
        // Fall back to the notification block when the server sends one (data-only is the contract).
        val title = payload.title ?: message.notification?.title
        val body = payload.body ?: message.notification?.body
        onPayload(payload)
        if (shouldShowNotification(payload)) {
            showNotification(payload, title ?: defaultTitle(payload), body)
        }
    }

    /** Called for every message before any notification is posted (main-thread safe-ish: FCM thread). */
    protected open fun onPayload(payload: PushPayload) {}

    protected open fun shouldShowNotification(payload: PushPayload): Boolean = true

    protected open fun defaultTitle(payload: PushPayload): String = when {
        payload.isRiderOffer -> "New delivery request"
        payload.isChat -> "New message"
        payload.isTicketReply -> "Support replied"
        payload.orderNumber != null -> "Order #${payload.orderNumber}"
        else -> "LocalRush"
    }

    /** Build the launch intent for a deep link; the app's MainActivity handles `localrush://` URIs. */
    protected open fun contentIntent(payload: PushPayload): PendingIntent {
        val launch = packageManager.getLaunchIntentForPackage(packageName)?.component
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(payload.deepLink)).apply {
            setPackage(packageName)
            if (launch != null) component = launch
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_PUSH_TYPE, payload.type)
            payload.orderId?.let { putExtra(EXTRA_ORDER_ID, it) }
            payload.assignmentId?.let { putExtra(EXTRA_ASSIGNMENT_ID, it) }
        }
        return PendingIntent.getActivity(this, payload.notificationId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    protected open fun showNotification(payload: PushPayload, title: String, body: String?) {
        val channel = NotificationChannels.channelFor(role, payload)
        val urgent = payload.isRiderOffer || (role == AppRole.PARTNER && payload.type == "status.placed")
        val builder = NotificationCompat.Builder(this, channel)
            .setSmallIcon(smallIconRes)
            .setColor(accentColor)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(contentIntent(payload))
            .setPriority(if (urgent) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (payload.isChat) NotificationCompat.CATEGORY_MESSAGE else NotificationCompat.CATEGORY_STATUS)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
        if (urgent) {
            // Heads-up + full-screen on supported devices so riders/partners notice time-limited requests.
            builder.setFullScreenIntent(contentIntent(payload), true)
            payload.expiresAt?.let { builder.setTimeoutAfter(60_000) }
        }
        try {
            NotificationManagerCompat.from(this).notify(payload.notificationId, builder.build())
        } catch (e: SecurityException) {
            Log.w(TAG, "POST_NOTIFICATIONS not granted", e)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "LRMessaging"
        const val EXTRA_PUSH_TYPE = "push_type"
        const val EXTRA_ORDER_ID = "order_id"
        const val EXTRA_ASSIGNMENT_ID = "assignment_id"
    }
}
