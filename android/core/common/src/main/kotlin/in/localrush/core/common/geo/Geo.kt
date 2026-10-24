package `in`.localrush.core.common.geo

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    const val EARTH_RADIUS_M = 6_371_008.8

    /** Great-circle distance in metres. */
    fun haversineMeters(a: LatLng, b: LatLng): Double =
        haversineMeters(a.latitude, a.longitude, b.latitude, b.longitude)

    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(h))
    }

    /** Initial bearing in degrees [0, 360). */
    fun bearing(from: LatLng, to: LatLng): Float {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLon = Math.toRadians(to.longitude - from.longitude)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        val deg = Math.toDegrees(atan2(y, x))
        return (((deg % 360) + 360) % 360).toFloat()
    }

    /** Linear interpolation between two points (fine for short GPS hops). */
    fun lerp(a: LatLng, b: LatLng, t: Double): LatLng {
        val tt = t.coerceIn(0.0, 1.0)
        return LatLng(a.latitude + (b.latitude - a.latitude) * tt, a.longitude + (b.longitude - a.longitude) * tt)
    }

    /** Shortest-arc interpolation of a bearing. */
    fun lerpBearing(from: Float, to: Float, t: Float): Float {
        var delta = ((to - from) % 360 + 540) % 360 - 180
        return ((from + delta * t) % 360 + 360) % 360
    }

    /** Total length of a path in metres. */
    fun pathLengthMeters(points: List<LatLng>): Double =
        points.zipWithNext().sumOf { (a, b) -> haversineMeters(a, b) }

    /** "850 m" / "2.3 km". */
    fun formatDistance(meters: Double): String = when {
        meters < 1000 -> "${meters.toInt()} m"
        meters < 10_000 -> String.format(java.util.Locale.ROOT, "%.1f km", meters / 1000)
        else -> "${(meters / 1000).toInt()} km"
    }

    fun formatDistanceKm(km: Double): String = formatDistance(km * 1000)
}
