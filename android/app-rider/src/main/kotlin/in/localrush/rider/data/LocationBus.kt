package `in`.localrush.rider.data

import android.location.Location
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Latest fix published by [`in`.localrush.rider.service.RiderLocationService] plus upload health, for the UI. */
@Singleton
class LocationBus @Inject constructor() {
    private val _last = MutableStateFlow<Location?>(null)
    val last: StateFlow<Location?> = _last

    private val _queued = MutableStateFlow(0)
    /** Fixes waiting in the offline queue. */
    val queued: StateFlow<Int> = _queued

    private val _serviceRunning = MutableStateFlow(false)
    val serviceRunning: StateFlow<Boolean> = _serviceRunning

    fun publish(location: Location) { _last.value = location }
    fun setQueued(n: Int) { _queued.value = n }
    fun setRunning(running: Boolean) { _serviceRunning.value = running }
}
