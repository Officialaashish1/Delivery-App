package `in`.localrush.rider.data

import `in`.localrush.core.common.geo.Geo
import `in`.localrush.core.common.geo.LatLng
import `in`.localrush.core.common.geo.Polyline
import `in`.localrush.core.network.dto.RouteDto
import `in`.localrush.core.network.dto.RouteStepDto
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** One turn-by-turn instruction resolved against the rider's progress along the route. */
data class NextManeuver(
    val step: RouteStepDto,
    /** Metres from the rider to the maneuver point. */
    val distanceM: Double,
    val index: Int,
)

data class RouteSnapshot(
    val geometry: String,
    val points: List<LatLng>,
    val steps: List<RouteStepDto>,
    /** Vertex index on [points] of each step's maneuver location. */
    val stepVertex: List<Int>,
    val totalDistanceM: Double,
    val totalDurationS: Double,
    val leg: String?,
    val fetchedAtMs: Long,
) {
    companion object {
        fun from(route: RouteDto, nowMs: Long = System.currentTimeMillis()): RouteSnapshot {
            val pts = Polyline.decode(route.geometry)
            val vertices = route.steps.map { s ->
                val loc = s.location
                if (loc.size >= 2 && pts.isNotEmpty()) RouteGeometry.nearestVertex(pts, LatLng(loc[1], loc[0])) else 0
            }
            return RouteSnapshot(route.geometry, pts, route.steps, vertices, route.distanceM, route.durationS, route.leg, nowMs)
        }
    }
}

data class Progress(
    /** Index of the segment [i, i+1] the rider is on. */
    val segment: Int,
    /** Projected point on the polyline. */
    val onRoute: LatLng,
    /** Cross-track distance rider → polyline in metres. */
    val offRouteM: Double,
    /** Metres left from the projected point to the end of the route. */
    val remainingM: Double,
    /** Seconds left, scaled from the route's average speed. */
    val remainingS: Double,
    val next: NextManeuver?,
)

/** Pure geometry over a decoded polyline so it is unit-testable. */
object RouteGeometry {
    const val OFF_ROUTE_THRESHOLD_M = 100.0
    const val REFRESH_INTERVAL_MS = 60_000L

    fun nearestVertex(points: List<LatLng>, p: LatLng): Int {
        var best = 0
        var bestD = Double.MAX_VALUE
        for (i in points.indices) {
            val d = Geo.haversineMeters(points[i], p)
            if (d < bestD) { bestD = d; best = i }
        }
        return best
    }

    /** Projects [p] onto segment a→b (local equirectangular approximation). Returns (point, t). */
    fun projectOnSegment(a: LatLng, b: LatLng, p: LatLng): Pair<LatLng, Double> {
        val k = cos(Math.toRadians((a.latitude + b.latitude) / 2))
        val ax = a.longitude * k; val ay = a.latitude
        val bx = b.longitude * k; val by = b.latitude
        val px = p.longitude * k; val py = p.latitude
        val dx = bx - ax; val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0.0, 1.0)
        return LatLng(ay + dy * t, (ax + dx * t) / k) to t
    }

    fun progress(route: RouteSnapshot, position: LatLng): Progress? {
        val pts = route.points
        if (pts.size < 2) return null
        var bestSeg = 0
        var bestPoint = pts[0]
        var bestD = Double.MAX_VALUE
        // Search all segments; routes are a few hundred vertices at most.
        for (i in 0 until pts.size - 1) {
            val (proj, _) = projectOnSegment(pts[i], pts[i + 1], position)
            val d = Geo.haversineMeters(proj, position)
            if (d < bestD) { bestD = d; bestSeg = i; bestPoint = proj }
        }
        var remaining = Geo.haversineMeters(bestPoint, pts[bestSeg + 1])
        for (i in bestSeg + 1 until pts.size - 1) remaining += Geo.haversineMeters(pts[i], pts[i + 1])
        val avgSpeed = if (route.totalDurationS > 0) route.totalDistanceM / route.totalDurationS else 5.0
        val remainingS = if (avgSpeed > 0) remaining / avgSpeed else 0.0

        // Next maneuver: first step whose vertex lies strictly ahead of the current segment start.
        var next: NextManeuver? = null
        for (idx in route.steps.indices) {
            val v = route.stepVertex[idx]
            if (v > bestSeg || (v == bestSeg + 1)) {
                val step = route.steps[idx]
                if (step.maneuverType == "depart" && idx < route.steps.lastIndex) continue
                var d = Geo.haversineMeters(bestPoint, pts[min(bestSeg + 1, pts.lastIndex)])
                for (i in bestSeg + 1 until min(v, pts.lastIndex)) d += Geo.haversineMeters(pts[i], pts[i + 1])
                next = NextManeuver(step, max(0.0, d), idx)
                break
            }
        }
        if (next == null && route.steps.isNotEmpty()) {
            val last = route.steps.last()
            next = NextManeuver(last, remaining, route.steps.lastIndex)
        }
        return Progress(bestSeg, bestPoint, bestD, remaining, remainingS, next)
    }

    fun isOffRoute(progress: Progress?): Boolean = progress != null && progress.offRouteM > OFF_ROUTE_THRESHOLD_M

    /** Straight-line fallback when no route is available. */
    fun directDistance(from: LatLng, to: LatLng): Double = sqrt(Geo.haversineMeters(from, to).let { it * it })
}
