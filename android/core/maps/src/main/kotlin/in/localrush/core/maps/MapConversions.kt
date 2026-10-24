package `in`.localrush.core.maps

import `in`.localrush.core.common.geo.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.geojson.Point

typealias MlLatLng = org.maplibre.android.geometry.LatLng

fun LatLng.toMl(): MlLatLng = MlLatLng(latitude, longitude)
fun MlLatLng.toLatLng(): LatLng = LatLng(latitude, longitude)
fun LatLng.toPoint(): Point = Point.fromLngLat(longitude, latitude)

/** Bounds for 2–3+ points; a single point is padded by ~200 m so the camera still fits. */
fun boundsOf(points: List<LatLng>): LatLngBounds? {
    val valid = points.filter { it.isValid() }
    if (valid.isEmpty()) return null
    if (valid.size == 1) {
        val p = valid.first()
        val d = 0.002
        return LatLngBounds.Builder().include(MlLatLng(p.latitude + d, p.longitude + d)).include(MlLatLng(p.latitude - d, p.longitude - d)).build()
    }
    val b = LatLngBounds.Builder()
    valid.forEach { b.include(it.toMl()) }
    return b.build()
}
