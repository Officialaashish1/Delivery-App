package `in`.localrush.core.location

import android.location.Location
import `in`.localrush.core.common.Constants

/**
 * Decides the update interval from motion: once speed stays below [stationarySpeedMps] for
 * [stationaryAfterMs], switch to the slow interval; any movement snaps back to fast.
 * Pure (no Android deps beyond [Location] fields) so it is unit-testable.
 */
class BatteryAwareLocationPolicy(
    val movingIntervalMs: Long = Constants.LOCATION_INTERVAL_MOVING_MS,
    val stationaryIntervalMs: Long = Constants.LOCATION_INTERVAL_STATIONARY_MS,
    private val stationarySpeedMps: Float = Constants.LOCATION_STATIONARY_SPEED_MPS,
    private val stationaryAfterMs: Long = Constants.LOCATION_STATIONARY_AFTER_MS,
) {
    enum class Mode { Moving, Stationary }

    var mode: Mode = Mode.Moving
        private set

    private var slowSince: Long? = null
    private var last: Location? = null

    val currentIntervalMs: Long get() = if (mode == Mode.Stationary) stationaryIntervalMs else movingIntervalMs

    /** Feed each fix; returns true when the mode (and therefore interval) changed. */
    fun onLocation(location: Location, nowMs: Long = System.currentTimeMillis()): Boolean {
        val speed = effectiveSpeed(location, nowMs)
        last = location
        val previous = mode
        if (speed < stationarySpeedMps) {
            val since = slowSince ?: nowMs.also { slowSince = it }
            if (nowMs - since >= stationaryAfterMs) mode = Mode.Stationary
        } else {
            slowSince = null
            mode = Mode.Moving
        }
        return previous != mode
    }

    fun reset() { mode = Mode.Moving; slowSince = null; last = null }

    private fun effectiveSpeed(location: Location, nowMs: Long): Float {
        if (location.hasSpeed() && location.speed > 0f) return location.speed
        val prev = last ?: return Float.MAX_VALUE // first fix: assume moving
        val dt = (location.time - prev.time).coerceAtLeast(1L) / 1000f
        return prev.distanceTo(location) / dt
    }
}
