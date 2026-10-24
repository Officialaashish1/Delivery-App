package `in`.localrush.core.maps

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import `in`.localrush.core.common.Constants
import `in`.localrush.core.common.geo.LatLng
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class CameraPadding(val left: Dp = 0.dp, val top: Dp = 0.dp, val right: Dp = 0.dp, val bottom: Dp = 0.dp) {
    companion object { fun all(v: Dp) = CameraPadding(v, v, v, v) }
}

/**
 * Compose-owned camera state. Mutate it (e.g. `camera.target = ...`) or call [animateTo]/[fitBounds]/
 * [follow]; [LRMap] applies pending moves to the underlying map.
 */
class CameraState(
    target: LatLng = LatLng(Constants.DEFAULT_LAT, Constants.DEFAULT_LNG),
    zoom: Double = 14.0,
    bearing: Double = 0.0,
    tilt: Double = 0.0,
) {
    var target by mutableStateOf(target)
    var zoom by mutableStateOf(zoom)
    var bearing by mutableStateOf(bearing)
    var tilt by mutableStateOf(tilt)

    /** Padding reserved for UI overlays (bottom sheet etc.), applied to fit/follow moves. */
    var padding by mutableStateOf(CameraPadding())

    internal sealed interface Move {
        data class To(val target: LatLng, val zoom: Double?, val bearing: Double?, val tilt: Double?, val durationMs: Int) : Move
        data class Fit(val points: List<LatLng>, val padding: CameraPadding, val durationMs: Int) : Move
    }

    internal var pendingMove by mutableStateOf<Move?>(null)
    internal var moveSeq by mutableStateOf(0)

    /** Follow mode: the map re-centres on every [followTarget] change. */
    var followTarget by mutableStateOf<LatLng?>(null)
    var followZoom by mutableStateOf<Double?>(null)
    var followBearing by mutableStateOf(false)

    fun animateTo(target: LatLng, zoom: Double? = null, bearing: Double? = null, tilt: Double? = null, durationMs: Int = 600) {
        followTarget = null
        pendingMove = Move.To(target, zoom, bearing, tilt, durationMs); moveSeq++
    }

    fun moveTo(target: LatLng, zoom: Double? = null) = animateTo(target, zoom, durationMs = 0)

    /** Fit 2–3 (or more) points with padding; extra [padding] is added to [CameraState.padding]. */
    fun fitBounds(points: List<LatLng>, padding: CameraPadding = CameraPadding.all(48.dp), durationMs: Int = 700) {
        followTarget = null
        pendingMove = Move.Fit(points, padding, durationMs); moveSeq++
    }

    /** Keep the camera on a moving marker (rider). Set [followTarget] to null to stop. */
    fun follow(target: LatLng, zoom: Double? = null, rotateWithBearing: Boolean = false) {
        followZoom = zoom; followBearing = rotateWithBearing; followTarget = target
    }

    fun stopFollowing() { followTarget = null }

    companion object {
        val Saver: Saver<CameraState, *> = listSaver(
            save = { listOf(it.target.latitude, it.target.longitude, it.zoom, it.bearing, it.tilt) },
            restore = { CameraState(LatLng(it[0], it[1]), it[2], it[3], it[4]) },
        )
    }
}

@Composable
fun rememberCameraState(initial: () -> CameraState = { CameraState() }): CameraState =
    rememberSaveable(saver = CameraState.Saver) { initial() }
