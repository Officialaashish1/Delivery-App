package `in`.localrush.rider.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.util.Log
import androidx.lifecycle.lifecycleScope
import `in`.localrush.core.auth.SessionManager
import `in`.localrush.core.common.Constants
import `in`.localrush.core.common.order.OrderStatus
import `in`.localrush.core.common.time.DateTimeUtils
import `in`.localrush.core.database.PendingLocationDao
import `in`.localrush.core.location.BatteryAwareLocationPolicy
import `in`.localrush.core.location.ForegroundLocationService
import `in`.localrush.core.network.dto.LocationFixDto
import `in`.localrush.rider.MainActivity
import `in`.localrush.rider.R
import `in`.localrush.rider.data.ActiveDeliveryStore
import `in`.localrush.rider.data.LocationBus
import `in`.localrush.rider.data.LocationUploader
import `in`.localrush.rider.data.NetworkMonitor
import `in`.localrush.rider.data.RiderRepository
import `in`.localrush.rider.data.UploadOutcome
import `in`.localrush.rider.di.AppScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/**
 * Rider foreground location service (type `location`, persistent notification).
 *
 * - GPS cadence: 4 s while moving, 30 s once stationary for a minute ([BatteryAwareLocationPolicy]).
 * - Upload cadence: every fix while on an active delivery (tagged with `order_id`), at most one per
 *   [IDLE_UPLOAD_INTERVAL_MS] while online-idle.
 * - Offline: fixes queue in Room and are replayed as a `{locations:[...]}` batch when connectivity returns.
 * - Stops itself on logout; the notification's Stop action also takes the rider offline on the server.
 */
@AndroidEntryPoint
class RiderLocationService : ForegroundLocationService() {

    @Inject lateinit var uploader: LocationUploader
    @Inject lateinit var pendingDao: PendingLocationDao
    @Inject lateinit var session: SessionManager
    @Inject lateinit var activeDelivery: ActiveDeliveryStore
    @Inject lateinit var network: NetworkMonitor
    @Inject lateinit var bus: LocationBus
    @Inject lateinit var riderRepository: RiderRepository
    @Inject lateinit var appScope: AppScope

    override val channelId = Constants.Channels.RIDER_TRACKING
    override val channelName = "Location sharing"
    override val notificationId = 1001

    override val policy = BatteryAwareLocationPolicy(movingIntervalMs = MOVING_INTERVAL_MS, stationaryIntervalMs = STATIONARY_INTERVAL_MS)

    private var lastUploadMs = 0L
    private var lastStatusText: String? = null

    override fun onCreate() {
        super.onCreate()
        bus.setRunning(true)
        // Replay the offline queue whenever connectivity comes back.
        lifecycleScope.launch {
            network.isOnline.collect { online ->
                if (online && pendingDao.oldest(1).isNotEmpty()) {
                    val n = uploader.flush()
                    Log.i(TAG, "Replayed $n queued fixes")
                    bus.setQueued(pendingDao.oldest(LocationUploader.MAX_QUEUED).size)
                }
            }
        }
        // Keep the notification in sync with what the rider is doing.
        lifecycleScope.launch {
            combine(activeDelivery.active, network.isOnline, bus.queued) { active, online, queued -> Triple(active, online, queued) }
                .distinctUntilChanged()
                .collect { (active, online, queued) -> updateNotification(statusText(active?.status, online, queued)) }
        }
        lifecycleScope.launch {
            session.state.collect { if (!session.isLoggedIn) stopSelf() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP && intent.getBooleanExtra(EXTRA_FROM_USER, true)) {
            // "Stop" from the notification: mirror it on the server so dispatch stops offering orders.
            activeDelivery.setWantsOnline(false)
            appScope.launch { riderRepository.setOnline(false, null, null) }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun buildNotification(status: String?): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = status ?: lastStatusText ?: getString(R.string.location_sharing_active)
        lastStatusText = text
        return baseNotification(getString(R.string.app_name), text, R.drawable.ic_notification, open).build()
    }

    private fun statusText(orderStatus: String?, online: Boolean, queued: Int): String = when {
        !online && queued > 0 -> getString(R.string.tracking_offline_queued, queued)
        !online -> getString(R.string.tracking_offline)
        orderStatus == null -> getString(R.string.location_sharing_active)
        OrderStatus.fromWire(orderStatus).riderOnTheWay -> getString(R.string.tracking_to_customer)
        else -> getString(R.string.tracking_to_restaurant)
    }

    override suspend fun onFix(location: Location) {
        if (!session.isLoggedIn) { stopSelf(); return }
        bus.publish(location)
        val active = activeDelivery.active.value
        val now = System.currentTimeMillis()
        if (active == null && now - lastUploadMs < IDLE_UPLOAD_INTERVAL_MS) return
        lastUploadMs = now
        val fix = LocationFixDto(
            orderId = active?.orderId,
            latitude = location.latitude,
            longitude = location.longitude,
            accuracy = if (location.hasAccuracy()) location.accuracy else null,
            speed = if (location.hasSpeed()) location.speed * 3.6f else null, // m/s -> km/h
            bearing = if (location.hasBearing()) location.bearing else null,
            timestamp = DateTimeUtils.formatIso(Instant.ofEpochMilli(location.time)),
        )
        when (val outcome = uploader.upload(fix)) {
            is UploadOutcome.Sent -> {
                bus.setQueued(0)
                if (!outcome.accepted) Log.d(TAG, "Fix not accepted: ${outcome.reason}")
            }
            is UploadOutcome.Queued -> bus.setQueued(outcome.queueSize)
            is UploadOutcome.Rejected -> Log.w(TAG, "Fix rejected: ${outcome.error.message}")
        }
    }

    override fun onDestroy() {
        bus.setRunning(false)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "RiderLocationService"
        const val EXTRA_FROM_USER = "from_user"
        const val MOVING_INTERVAL_MS = 4_000L
        const val STATIONARY_INTERVAL_MS = 30_000L
        const val IDLE_UPLOAD_INTERVAL_MS = 15_000L

        fun start(context: Context, status: String? = null) {
            val intent = Intent(context, RiderLocationService::class.java).setAction(ACTION_START).putExtra(EXTRA_STATUS, status)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
        }

        /** Programmatic stop (rider toggled offline / logged out): does not re-post `is_online=false`. */
        fun stop(context: Context) {
            context.startService(Intent(context, RiderLocationService::class.java).setAction(ACTION_STOP).putExtra(EXTRA_FROM_USER, false))
        }
    }
}
