package `in`.localrush.core.maps

import android.graphics.Bitmap
import androidx.annotation.ColorInt
import `in`.localrush.core.common.geo.LatLng
import `in`.localrush.core.common.geo.Polyline
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString

/**
 * Imperative facade over a loaded [MapLibreMap] + [Style]. One GeoJSON source/layer pair per marker or
 * route id, so updating a marker is just `source.setGeoJson(...)` (cheap, 60 fps safe).
 * All calls must happen on the main thread. Obtain it via [LRMap]'s `onMapReady`/`MapScope`.
 */
class LRMapController internal constructor(val map: MapLibreMap, val style: Style) {

    private val markerIds = linkedSetOf<String>()
    private val routeIds = linkedSetOf<String>()
    private val images = mutableMapOf<String, Int>() // image name -> bitmap identity hash

    // ---------- Markers ----------

    /**
     * Adds or moves a marker. [icon] is registered once per [iconName] (changing the bitmap for a name updates it).
     * [anchor] e.g. [Property.ICON_ANCHOR_BOTTOM] for pins, CENTER for rider/scooter.
     */
    fun setMarker(
        id: String,
        position: LatLng,
        icon: Bitmap,
        iconName: String = "icon-$id",
        bearing: Float = 0f,
        anchor: String = Property.ICON_ANCHOR_CENTER,
        rotateWithMap: Boolean = true,
        scale: Float = 1f,
        zIndexAbove: String? = null,
    ) {
        ensureImage(iconName, icon)
        val feature = Feature.fromGeometry(position.toPoint()).apply { addNumberProperty(PROP_BEARING, bearing) }
        val source = style.getSourceAs<GeoJsonSource>(srcId(id))
        if (source == null) {
            style.addSource(GeoJsonSource(srcId(id), feature))
            val layer = SymbolLayer(layerId(id), srcId(id)).withProperties(
                PropertyFactory.iconImage(iconName),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
                PropertyFactory.iconAnchor(anchor),
                PropertyFactory.iconSize(scale),
                PropertyFactory.iconRotate(Expression.get(PROP_BEARING)),
                PropertyFactory.iconRotationAlignment(if (rotateWithMap) Property.ICON_ROTATION_ALIGNMENT_MAP else Property.ICON_ROTATION_ALIGNMENT_VIEWPORT),
            )
            if (zIndexAbove != null && style.getLayer(layerId(zIndexAbove)) != null) style.addLayerAbove(layer, layerId(zIndexAbove)) else style.addLayer(layer)
            markerIds += id
        } else {
            source.setGeoJson(feature)
            (style.getLayer(layerId(id)) as? SymbolLayer)?.setProperties(PropertyFactory.iconImage(iconName), PropertyFactory.iconSize(scale))
        }
    }

    /** Fast path used by [MarkerAnimator]: update position/bearing only. */
    fun moveMarker(id: String, position: LatLng, bearing: Float? = null) {
        val source = style.getSourceAs<GeoJsonSource>(srcId(id)) ?: return
        val feature = Feature.fromGeometry(position.toPoint())
        if (bearing != null) feature.addNumberProperty(PROP_BEARING, bearing)
        source.setGeoJson(feature)
    }

    fun removeMarker(id: String) {
        style.removeLayer(layerId(id)); style.removeSource(srcId(id)); markerIds -= id
    }

    fun setMarkerVisible(id: String, visible: Boolean) {
        style.getLayer(layerId(id))?.setProperties(PropertyFactory.visibility(if (visible) Property.VISIBLE else Property.NONE))
    }

    // ---------- Routes ----------

    /** Draws/updates a route from a precision-5 polyline (OSRM `geometry`). */
    fun setRoutePolyline(id: String, geometryPolyline5: String?, @ColorInt color: Int, widthDp: Float = 5f, dashed: Boolean = false, outlineColor: Int? = null) =
        setRoute(id, Polyline.decode(geometryPolyline5), color, widthDp, dashed, outlineColor)

    fun setRoute(id: String, points: List<LatLng>, @ColorInt color: Int, widthDp: Float = 5f, dashed: Boolean = false, @ColorInt outlineColor: Int? = null) {
        if (points.size < 2) { removeRoute(id); return }
        val line = LineString.fromLngLats(points.map { it.toPoint() })
        val fc = FeatureCollection.fromFeature(Feature.fromGeometry(line))
        val source = style.getSourceAs<GeoJsonSource>(srcId(id))
        if (source == null) {
            style.addSource(GeoJsonSource(srcId(id), fc))
            if (outlineColor != null) {
                val casing = LineLayer(layerId("$id-casing"), srcId(id)).withProperties(
                    PropertyFactory.lineColor(outlineColor), PropertyFactory.lineWidth(widthDp + 3f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND), PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                )
                addBelowMarkers(casing)
            }
            val layer = LineLayer(layerId(id), srcId(id)).withProperties(
                PropertyFactory.lineColor(color), PropertyFactory.lineWidth(widthDp),
                PropertyFactory.lineCap(if (dashed) Property.LINE_CAP_BUTT else Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
            if (dashed) layer.setProperties(PropertyFactory.lineDasharray(arrayOf(1.5f, 1.5f)))
            addBelowMarkers(layer)
            routeIds += id
        } else {
            source.setGeoJson(fc)
            (style.getLayer(layerId(id)) as? LineLayer)?.setProperties(PropertyFactory.lineColor(color), PropertyFactory.lineWidth(widthDp))
        }
    }

    fun removeRoute(id: String) {
        style.removeLayer(layerId(id)); style.removeLayer(layerId("$id-casing")); style.removeSource(srcId(id)); routeIds -= id
    }

    private fun addBelowMarkers(layer: org.maplibre.android.style.layers.Layer) {
        val firstMarker = markerIds.firstOrNull()?.let { layerId(it) }
        if (firstMarker != null && style.getLayer(firstMarker) != null) style.addLayerBelow(layer, firstMarker) else style.addLayer(layer)
    }

    // ---------- Camera ----------

    fun animateTo(target: LatLng, zoom: Double? = null, bearing: Double? = null, tilt: Double? = null, durationMs: Int = 600, padding: CameraPadding? = null, density: Float = 1f) {
        val current = map.cameraPosition
        val builder = CameraPosition.Builder().target(target.toMl())
            .zoom(zoom ?: current.zoom).bearing(bearing ?: current.bearing).tilt(tilt ?: current.tilt)
        if (padding != null) builder.padding(padding.toPx(density))
        val update = CameraUpdateFactory.newCameraPosition(builder.build())
        if (durationMs <= 0) map.moveCamera(update) else map.animateCamera(update, durationMs)
    }

    /** Fit all points (2–3 typical: rider, restaurant, customer) with pixel padding. */
    fun fitBounds(points: List<LatLng>, padding: CameraPadding, density: Float, durationMs: Int = 700) {
        val bounds = boundsOf(points) ?: return
        val p = padding.toPx(density)
        val update = CameraUpdateFactory.newLatLngBounds(bounds, p[0].toInt(), p[1].toInt(), p[2].toInt(), p[3].toInt())
        if (durationMs <= 0) map.moveCamera(update) else map.animateCamera(update, durationMs)
    }

    fun clear() {
        markerIds.toList().forEach { removeMarker(it) }
        routeIds.toList().forEach { removeRoute(it) }
    }

    private fun ensureImage(name: String, bitmap: Bitmap) {
        val hash = System.identityHashCode(bitmap)
        if (images[name] != hash) { style.addImage(name, bitmap); images[name] = hash }
    }

    private fun srcId(id: String) = "lr-src-$id"
    private fun layerId(id: String) = "lr-layer-$id"

    private fun CameraPadding.toPx(density: Float) = doubleArrayOf(left.value * density.toDouble(), top.value * density.toDouble(), right.value * density.toDouble(), bottom.value * density.toDouble())

    companion object { const val PROP_BEARING = "bearing" }
}
