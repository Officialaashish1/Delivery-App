package `in`.localrush.core.maps

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import `in`.localrush.core.common.Constants
import `in`.localrush.core.common.geo.LatLng
import kotlinx.coroutines.flow.filterNotNull
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.Property

/** Scope for [LRMap] content: gives children the controller once the style is loaded. */
class MapScope internal constructor(val controller: LRMapController)

val LocalMapController = staticCompositionLocalOf<LRMapController?> { null }

/**
 * MapLibre map as a composable.
 *
 * @param styleUrl from `GET config/` (`map_style_url`); defaults to OpenFreeMap Liberty.
 * @param attribution text from `GET config/` (`map_attribution`), drawn bottom-start.
 * @param content composables such as [Marker] / [RoutePolyline] that run once the style is ready.
 */
@Composable
fun LRMap(
    modifier: Modifier = Modifier,
    styleUrl: String = Constants.DEFAULT_MAP_STYLE_URL,
    cameraState: CameraState = rememberCameraState(),
    attribution: String? = Constants.DEFAULT_MAP_ATTRIBUTION,
    gesturesEnabled: Boolean = true,
    onMapReady: (LRMapController) -> Unit = {},
    onMapClick: ((LatLng) -> Unit)? = null,
    onCameraIdle: ((LatLng, Double) -> Unit)? = null,
    content: @Composable MapScope.() -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current.density
    remember { MapLibre.getInstance(context) }

    val mapView = remember {
        MapView(context, MapLibreMapOptions.createFromAttributes(context).apply {
            textureMode(true) // plays nicer with Compose transitions/bottom sheets
            attributionEnabled(false)
            logoEnabled(false)
            camera(org.maplibre.android.camera.CameraPosition.Builder().target(cameraState.target.toMl()).zoom(cameraState.zoom).build())
        })
    }
    var controller by remember { mutableStateOf<LRMapController?>(null) }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        mapView.onCreate(null)
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause(); mapView.onStop(); mapView.onDestroy()
        }
    }

    // Load (or swap) the style.
    LaunchedEffect(mapView, styleUrl) {
        mapView.getMapAsync { map ->
            map.uiSettings.apply {
                isAttributionEnabled = false; isLogoEnabled = false
                isRotateGesturesEnabled = gesturesEnabled; isTiltGesturesEnabled = false
                isScrollGesturesEnabled = gesturesEnabled; isZoomGesturesEnabled = gesturesEnabled
            }
            map.setStyle(Style.Builder().fromUri(styleUrl)) { style ->
                val c = LRMapController(map, style)
                controller = c
                onMapReady(c)
            }
            map.addOnMapClickListener { p -> onMapClick?.invoke(p.toLatLng()); onMapClick != null }
            map.addOnCameraIdleListener {
                val pos = map.cameraPosition
                pos.target?.let { t ->
                    cameraState.target = t.toLatLng(); cameraState.zoom = pos.zoom; cameraState.bearing = pos.bearing; cameraState.tilt = pos.tilt
                    onCameraIdle?.invoke(t.toLatLng(), pos.zoom)
                }
            }
        }
    }

    // Apply explicit camera moves.
    val ctrl = controller
    if (ctrl != null) {
        LaunchedEffect(ctrl) {
            snapshotFlow { cameraState.moveSeq to cameraState.pendingMove }.collect { (_, move) ->
                when (move) {
                    is CameraState.Move.To -> ctrl.animateTo(move.target, move.zoom, move.bearing, move.tilt, move.durationMs, cameraState.padding, density)
                    is CameraState.Move.Fit -> ctrl.fitBounds(move.points, cameraState.padding + move.padding, density, move.durationMs)
                    null -> Unit
                }
            }
        }
        // Camera-follow with padding.
        LaunchedEffect(ctrl) {
            snapshotFlow { cameraState.followTarget }.filterNotNull().collect { target ->
                ctrl.animateTo(target, cameraState.followZoom, if (cameraState.followBearing) null else ctrl.map.cameraPosition.bearing, durationMs = 900, padding = cameraState.padding, density = density)
            }
        }
    }

    Box(modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        if (ctrl != null) {
            CompositionLocalProvider(LocalMapController provides ctrl) { MapScope(ctrl).content() }
        }
        if (!attribution.isNullOrBlank()) {
            Text(
                attribution,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF444444),
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                    .background(Color.White.copy(alpha = 0.75f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

private operator fun CameraPadding.plus(o: CameraPadding) = CameraPadding(left + o.left, top + o.top, right + o.right, bottom + o.bottom)

/** Declarative marker. Removed from the map when it leaves composition. */
@Composable
fun MapScope.Marker(
    id: String,
    position: LatLng,
    icon: Bitmap,
    bearing: Float = 0f,
    anchor: String = Property.ICON_ANCHOR_CENTER,
    rotateWithMap: Boolean = true,
    scale: Float = 1f,
    animate: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    val animator = remember(id) { MarkerAnimator(controller, id, scope) }
    LaunchedEffect(id, icon, anchor) {
        controller.setMarker(id, animator.current ?: position, icon, bearing = bearing, anchor = anchor, rotateWithMap = rotateWithMap, scale = scale)
        if (animator.current == null) animator.jumpTo(position, bearing)
    }
    LaunchedEffect(position, bearing, animate) {
        if (animate) animator.animateTo(position, bearing) else animator.jumpTo(position, bearing)
    }
    DisposableEffect(id) { onDispose { animator.cancel(); controller.removeMarker(id) } }
}

/** Declarative route line from an encoded polyline (precision 5). */
@Composable
fun MapScope.RoutePolyline(
    geometryPolyline5: String?,
    id: String = "route",
    color: Color = MaterialTheme.colorScheme.primary,
    widthDp: Float = 5f,
    dashed: Boolean = false,
    outlineColor: Color? = Color.White,
) {
    val colorInt = android.graphics.Color.argb((color.alpha * 255).toInt(), (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt())
    val outlineInt = outlineColor?.let { android.graphics.Color.argb((it.alpha * 255).toInt(), (it.red * 255).toInt(), (it.green * 255).toInt(), (it.blue * 255).toInt()) }
    LaunchedEffect(geometryPolyline5, colorInt, dashed, widthDp) {
        controller.setRoutePolyline(id, geometryPolyline5, colorInt, widthDp, dashed, outlineInt)
    }
    DisposableEffect(id) { onDispose { controller.removeRoute(id) } }
}
