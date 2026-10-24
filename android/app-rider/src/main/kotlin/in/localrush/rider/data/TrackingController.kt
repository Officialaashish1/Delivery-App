package `in`.localrush.rider.data

import android.content.Context
import `in`.localrush.core.location.LocationPermissions
import `in`.localrush.rider.service.RiderLocationService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Starts/stops the foreground tracking service; the only place UI-free code touches the service. */
@Singleton
class TrackingController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: ActiveDeliveryStore,
    private val bus: LocationBus,
) {
    /** Idempotent: a running service is left alone (the dashboard calls this on every refresh). */
    fun start() {
        if (!LocationPermissions.hasForeground(context)) return
        if (bus.serviceRunning.value) return
        RiderLocationService.start(context)
    }

    fun stop() = RiderLocationService.stop(context)

    /** After process death / app relaunch: resume tracking if the rider meant to be online and we may. */
    fun ensureRunningIfWanted() {
        if ((store.wantsOnline.value || store.active.value != null) && !bus.serviceRunning.value) start()
    }

    fun hasBackgroundPermission(): Boolean = LocationPermissions.hasBackground(context)
    fun hasForegroundPermission(): Boolean = LocationPermissions.hasForeground(context)
}
