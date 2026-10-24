package `in`.localrush.customer.feature.location.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.localrush.core.common.geo.LatLng
import `in`.localrush.core.designsystem.component.InlineError
import `in`.localrush.core.designsystem.component.LRButton
import `in`.localrush.core.designsystem.component.LRTextField
import `in`.localrush.core.designsystem.icon.LRIcons
import `in`.localrush.core.location.LocationPermissions
import `in`.localrush.core.maps.LRMap
import `in`.localrush.core.maps.rememberCameraState
import `in`.localrush.customer.R
import `in`.localrush.customer.data.repo.addressLine
import `in`.localrush.customer.data.repo.shortTitle
import `in`.localrush.customer.feature.location.vm.PickLocationEvent
import `in`.localrush.customer.feature.location.vm.PickLocationViewModel

/**
 * Map with a fixed centre pin; moving the map reverse-geocodes (`config/geocode/reverse`), the search field hits
 * `config/geocode/search`, the FAB uses the fused location (asking for permission first).
 */
@Composable
fun PickLocationScreen(onSaved: () -> Unit, onBack: (() -> Unit)?, viewModel: PickLocationViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val camera = rememberCameraState()
    var searchOpen by remember { mutableStateOf(false) }
    var mapReady by remember { mutableStateOf(false) }
    val noLocationMsg = stringResource(R.string.location_unavailable)
    val permissionDeniedMsg = stringResource(R.string.location_permission_denied)

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) viewModel.useCurrentLocation() else viewModel.message(permissionDeniedMsg)
    }
    fun requestCurrent() {
        if (LocationPermissions.hasForeground(context)) viewModel.useCurrentLocation()
        else permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                PickLocationEvent.Saved -> onSaved()
                is PickLocationEvent.Message -> snackbar.showSnackbar(e.text)
                PickLocationEvent.LocationUnavailable -> snackbar.showSnackbar(noLocationMsg)
            }
        }
    }
    // Centre the map whenever the pin is set programmatically (current location / search result).
    var lastProgrammatic by remember { mutableStateOf<LatLng?>(null) }
    LaunchedEffect(state.pin, mapReady) {
        val p = state.pin ?: return@LaunchedEffect
        if (mapReady && p != camera.target && p != lastProgrammatic) { lastProgrammatic = p; camera.animateTo(p, zoom = 16.0) }
    }
    LaunchedEffect(Unit) { if (!LocationPermissions.hasForeground(context)) permissionLauncher.launch(LocationPermissions.FOREGROUND) }

    Box(Modifier.fillMaxSize()) {
        LRMap(
            modifier = Modifier.fillMaxSize(),
            styleUrl = state.config.mapStyleUrl,
            attribution = state.config.mapAttribution.ifBlank { null },
            cameraState = camera,
            onMapReady = { mapReady = true },
            onCameraIdle = { target, _ -> if (target != lastProgrammatic) viewModel.onPinMoved(target) },
        )
        // Fixed centre pin (tip at the centre of the map).
        Icon(LRIcons.LocationPin, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.Center).size(48.dp).offset(y = (-24).dp))

        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    Surface(shape = CircleShape, shadowElevation = 4.dp) { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } }
                    Spacer(Modifier.width(8.dp))
                }
                Surface(shape = RoundedCornerShape(14.dp), shadowElevation = 4.dp, modifier = Modifier.weight(1f).clickable { searchOpen = true }) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.location_search_hint), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(end = 16.dp, bottom = 12.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
                SmallFloatingActionButton(onClick = { requestCurrent() }, containerColor = MaterialTheme.colorScheme.surface) {
                    if (state.locating) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Default.MyLocation, stringResource(R.string.location_use_current), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Surface(shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), shadowElevation = 12.dp) {
                Column(Modifier.padding(20.dp).navigationBarsPadding()) {
                    Text(stringResource(R.string.location_deliver_to), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Place, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            when {
                                state.resolving -> Text(stringResource(R.string.location_resolving), style = MaterialTheme.typography.titleMedium)
                                state.address != null -> {
                                    Text(state.address!!.shortTitle(), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(state.address!!.addressLine(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                else -> Text(stringResource(R.string.location_move_pin), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (state.resolving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                    state.resolveError?.let {
                        Spacer(Modifier.height(8.dp))
                        InlineError(it.message)
                        Spacer(Modifier.height(4.dp))
                        LRButton(stringResource(R.string.action_retry), onClick = viewModel::retryResolve, style = `in`.localrush.core.designsystem.component.LRButtonStyle.Text)
                    }
                    Spacer(Modifier.height(16.dp))
                    LRButton(stringResource(R.string.location_confirm), onClick = viewModel::confirm, modifier = Modifier.fillMaxWidth(), enabled = state.canConfirm, loading = state.saving)
                }
            }
        }

        AnimatedVisibility(searchOpen, Modifier.fillMaxSize()) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxSize().statusBarsPadding()) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { searchOpen = false; viewModel.onQueryChange("") }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                        LRTextField(value = state.query, onValueChange = viewModel::onQueryChange, placeholder = stringResource(R.string.location_search_hint), modifier = Modifier.weight(1f), leadingIcon = Icons.Default.Search)
                    }
                    Row(Modifier.fillMaxWidth().clickable { searchOpen = false; requestCurrent() }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MyLocation, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.location_use_current), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
                    }
                    HorizontalDivider()
                    if (state.searching) Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    state.searchError?.let { InlineError(it.message, Modifier.padding(16.dp)) }
                    if (!state.searching && state.query.length >= 3 && state.results.isEmpty() && state.searchError == null) {
                        Text(stringResource(R.string.location_no_results), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    LazyColumn {
                        items(state.results, key = { it.placeId ?: it.displayName.hashCode().toLong() }) { r ->
                            Row(Modifier.fillMaxWidth().clickable { viewModel.pickResult(r); searchOpen = false }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Place, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(r.shortTitle(), style = MaterialTheme.typography.titleSmall)
                                    Text(r.addressLine(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 180.dp))
    }
}
