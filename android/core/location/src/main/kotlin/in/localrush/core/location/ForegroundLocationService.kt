package `in`.localrush.core.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * Base foreground service (type `location`) that streams fused location fixes with a battery-aware
 * interval. The rider app subclasses it, declares it in its manifest and implements [onFix]
 * (e.g. POST `rider/location/` or stream over `/ws/rider/`).
 *
 * Start with [start]/[stop] helpers; the subclass must provide [buildNotification] and [channelId].
 */
abstract class ForegroundLocationService : LifecycleService() {

    protected abstract val channelId: String
    protected abstract val channelName: String
    protected abstract val notificationId: Int

    /** Build (or rebuild) the persistent notification. [status] is subclass-defined text. */
    protected abstract fun buildNotification(status: String?): Notification

    /** Called for every accepted fix, on the main thread. */
    protected abstract suspend fun onFix(location: Location)

    protected open val policy: BatteryAwareLocationPolicy = BatteryAwareLocationPolicy()
    protected lateinit var locationClient: LocationClient
    private var updatesJob: Job? = null

    private val _lastLocation = MutableStateFlow<Location?>(null)
    val lastLocation: StateFlow<Location?> = _lastLocation

    override fun onCreate() {
        super.onCreate()
        locationClient = LocationClient(applicationContext)
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> { stopSelf(); return Service.START_NOT_STICKY }
            else -> {
                startInForeground(intent?.getStringExtra(EXTRA_STATUS))
                if (updatesJob == null) subscribe(policy.currentIntervalMs)
            }
        }
        return Service.START_STICKY
    }

    private fun startInForeground(status: String?) {
        val notification = buildNotification(status)
        if (Build.VERSION.SDK_INT >= 29) {
            ServiceCompat.startForeground(this, notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(notificationId, notification)
        }
    }

    /** Update the notification text (e.g. "Heading to Dosa Hub · 1.2 km"). */
    protected fun updateNotification(status: String?) {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(notificationId, buildNotification(status))
    }

    private fun subscribe(intervalMs: Long) {
        updatesJob?.cancel()
        updatesJob = lifecycleScope.launch {
            locationClient.locationUpdates(intervalMs = intervalMs, minDistanceM = if (intervalMs > policy.movingIntervalMs) 0f else 3f)
                .catch { onError(it) }
                .collect { fix ->
                    _lastLocation.value = fix
                    onFix(fix)
                    if (policy.onLocation(fix)) {
                        // Interval changed (moving <-> stationary): resubscribe with the new interval.
                        subscribe(policy.currentIntervalMs)
                    }
                }
        }
    }

    protected open fun onError(t: Throwable) { if (t is SecurityException) stopSelf() }

    override fun onDestroy() {
        updatesJob?.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(channelId) == null) {
            nm.createNotificationChannel(NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
                description = "Shows while you are online and sharing your location"
            })
        }
    }

    /** Default notification builder subclasses can use. */
    protected fun baseNotification(title: String, text: String?, smallIcon: Int, contentIntent: PendingIntent?): NotificationCompat.Builder =
        NotificationCompat.Builder(this, channelId)
            .setSmallIcon(smallIcon)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(contentIntent)
            .addAction(0, "Stop", stopIntent(this, this::class.java))

    companion object {
        const val ACTION_START = "in.localrush.location.START"
        const val ACTION_STOP = "in.localrush.location.STOP"
        const val EXTRA_STATUS = "status"

        fun <T : ForegroundLocationService> start(context: Context, cls: Class<T>, status: String? = null) {
            val intent = Intent(context, cls).setAction(ACTION_START).putExtra(EXTRA_STATUS, status)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
        }

        fun <T : ForegroundLocationService> stop(context: Context, cls: Class<T>) {
            context.startService(Intent(context, cls).setAction(ACTION_STOP))
        }

        fun stopIntent(context: Context, cls: Class<out ForegroundLocationService>): PendingIntent =
            PendingIntent.getService(context, 901, Intent(context, cls).setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
