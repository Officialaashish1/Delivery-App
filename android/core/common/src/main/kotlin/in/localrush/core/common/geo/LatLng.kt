package `in`.localrush.core.common.geo

import kotlinx.serialization.Serializable

@Serializable
data class LatLng(val latitude: Double, val longitude: Double) {
    fun distanceTo(other: LatLng): Double = Geo.haversineMeters(this, other)
    fun bearingTo(other: LatLng): Float = Geo.bearing(this, other)
    fun isValid(): Boolean = latitude in -90.0..90.0 && longitude in -180.0..180.0 && !(latitude == 0.0 && longitude == 0.0)
}
