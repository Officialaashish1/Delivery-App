package `in`.localrush.partner.alert

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import `in`.localrush.core.common.Constants
import `in`.localrush.core.common.order.OrderStatus
import `in`.localrush.partner.R
import `in`.localrush.partner.data.PartnerOrderFeed
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loud, repeating new-order alert.
 *
 * - Foreground: loops the alert sound (`raw/new_order` if bundled, else the system alarm tone) on the alarm stream and
 *   vibrates until every new order has been acknowledged (opened/accepted/rejected).
 * - Background: when the socket delivers a `status.placed` (or FCM is not configured) we post a MAX-priority notification
 *   with a full-screen intent on `localrush_partner_orders` so the device rings/wakes like a call.
 */
@Singleton
class NewOrderAlerter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val feed: PartnerOrderFeed,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: MediaPlayer? = null
    private var started = false

    private val foreground get() = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    fun start() {
        if (started) return
        started = true
        scope.launch {
            combine(feed.state.map { it.alertActive }.distinctUntilChanged(), foregroundFlow()) { active, fg -> active && fg }
                .distinctUntilChanged()
                .collect { ring -> if (ring) startRinging() else stopRinging() }
        }
        scope.launch {
            feed.events.collect { e ->
                if (OrderStatus.fromEventType(e.type) == OrderStatus.PLACED && !foreground) postNewOrderNotification(e.order.id, e.order.orderNumber, e.order.items.size)
            }
        }
    }

    private fun foregroundFlow() = callbackFlow {
        val owner = ProcessLifecycleOwner.get()
        val obs = androidx.lifecycle.LifecycleEventObserver { _, _ -> trySend(foreground) }
        trySend(foreground)
        owner.lifecycle.addObserver(obs)
        awaitClose { owner.lifecycle.removeObserver(obs) }
    }.distinctUntilChanged()

    private fun startRinging() {
        if (player != null) return
        val uri = soundUri()
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        player = try {
            MediaPlayer().apply {
                setAudioAttributes(attrs)
                setDataSource(context, uri)
                isLooping = true
                setOnPreparedListener { it.start() }
                setOnErrorListener { _, _, _ -> stopRinging(); true }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.w(TAG, "alert sound unavailable", e); null
        }
        vibrator()?.let { v ->
            val pattern = longArrayOf(0, 600, 300, 600, 300, 900, 700)
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(pattern, 0)) else @Suppress("DEPRECATION") v.vibrate(pattern, 0)
        }
    }

    private fun stopRinging() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        vibrator()?.cancel()
    }

    private fun soundUri(): Uri {
        val id = context.resources.getIdentifier("new_order", "raw", context.packageName)
        if (id != 0) return Uri.parse("android.resource://${context.packageName}/raw/new_order")
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }

    private fun vibrator(): Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else @Suppress("DEPRECATION") (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)

    private suspend fun postNewOrderNotification(orderId: String, orderNumber: String, itemCount: Int) = withContext(Dispatchers.Default) {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)?.component
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(Constants.DeepLinks.ORDER + orderId)).apply {
            setPackage(context.packageName)
            if (launch != null) component = launch
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pi = PendingIntent.getActivity(context, orderId.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, Constants.Channels.PARTNER_ORDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFF26B1D.toInt())
            .setContentTitle(context.getString(R.string.alert_new_order_title, orderNumber))
            .setContentText(context.resources.getQuantityString(R.plurals.alert_new_order_body, itemCount, itemCount))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setOngoing(true)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(orderId.hashCode() and 0x7fffffff, n)
        } catch (e: SecurityException) {
            Log.w(TAG, "POST_NOTIFICATIONS not granted", e)
        }
    }

    /** Dismisses our own notification for an order once it is handled in-app. */
    fun dismissNotification(orderId: String) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)?.cancel(orderId.hashCode() and 0x7fffffff)
    }

    private companion object { const val TAG = "NewOrderAlerter" }
}
