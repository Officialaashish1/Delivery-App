package `in`.localrush.core.maps

import `in`.localrush.core.common.Constants
import `in`.localrush.core.common.geo.Geo
import `in`.localrush.core.common.geo.LatLng
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Smoothly slides a marker between GPS fixes and rotates it toward the travel direction.
 * Call [animateTo] for every new fix; a new call cancels the in-flight animation and continues
 * from the current interpolated position (no jumps).
 */
class MarkerAnimator(
    private val controller: LRMapController,
    private val markerId: String,
    private val scope: CoroutineScope,
    private val frameMs: Long = 16,
) {
    var current: LatLng? = null
        private set
    var currentBearing: Float = 0f
        private set
    private var job: Job? = null

    /** Snap without animation (first fix). */
    fun jumpTo(position: LatLng, bearing: Float? = null) {
        job?.cancel()
        current = position
        bearing?.let { currentBearing = it }
        controller.moveMarker(markerId, position, currentBearing)
    }

    /**
     * @param bearing explicit heading from the GPS fix; when null the bearing is derived from the movement
     * direction (ignored for tiny moves < 2 m to avoid jitter).
     */
    fun animateTo(target: LatLng, bearing: Float? = null, durationMs: Long = Constants.MARKER_ANIMATION_MS) {
        val start = current ?: run { jumpTo(target, bearing); return }
        val distance = Geo.haversineMeters(start, target)
        val targetBearing = bearing ?: if (distance >= 2.0) Geo.bearing(start, target) else currentBearing
        val startBearing = currentBearing
        job?.cancel()
        if (distance < 0.5 && kotlin.math.abs(targetBearing - startBearing) < 1f) return
        job = scope.launch {
            val startTime = System.currentTimeMillis()
            while (isActive) {
                val t = ((System.currentTimeMillis() - startTime).toDouble() / durationMs).coerceIn(0.0, 1.0)
                val eased = easeInOut(t)
                val pos = Geo.lerp(start, target, eased)
                val b = Geo.lerpBearing(startBearing, targetBearing, eased.toFloat())
                current = pos; currentBearing = b
                controller.moveMarker(markerId, pos, b)
                if (t >= 1.0) break
                delay(frameMs)
            }
        }
    }

    fun cancel() { job?.cancel() }

    private fun easeInOut(t: Double): Double = if (t < 0.5) 2 * t * t else -1 + (4 - 2 * t) * t
}
