package `in`.localrush.rider.feature.delivery.ui

import android.speech.tts.TextToSpeech
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.TurnSlightLeft
import androidx.compose.material.icons.filled.TurnSlightRight
import androidx.compose.material.icons.filled.UTurnLeft
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.RoundaboutLeft
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.localrush.core.common.geo.Geo
import `in`.localrush.core.common.geo.LatLng
import `in`.localrush.core.common.money.Money
import `in`.localrush.core.common.time.DateTimeUtils
import `in`.localrush.core.designsystem.component.ErrorState
import `in`.localrush.core.designsystem.component.InlineError
import `in`.localrush.core.designsystem.component.LRButton
import `in`.localrush.core.designsystem.component.LRButtonStyle
import `in`.localrush.core.designsystem.component.LoadingState
import `in`.localrush.core.designsystem.component.OfflineBanner
import `in`.localrush.core.designsystem.theme.LRColors
import `in`.localrush.core.designsystem.theme.LRTheme
import `in`.localrush.core.maps.CameraPadding
import `in`.localrush.core.maps.LRMap
import `in`.localrush.core.maps.MapIcons
import `in`.localrush.core.maps.Marker
import `in`.localrush.core.maps.RoutePolyline
import `in`.localrush.core.maps.rememberCameraState
import `in`.localrush.core.network.dto.RouteStepDto
import `in`.localrush.rider.R
import `in`.localrush.rider.ui.common.LocalMapPlaceholder
import `in`.localrush.rider.ui.common.MapPlaceholder
import `in`.localrush.rider.data.DeliveryStep
import `in`.localrush.rider.feature.delivery.vm.DeliverySheet
import `in`.localrush.rider.feature.delivery.vm.DeliveryUiEvent
import `in`.localrush.rider.feature.delivery.vm.DeliveryUiState
import `in`.localrush.rider.feature.delivery.vm.DeliveryViewModel
import `in`.localrush.rider.feature.delivery.vm.PrimaryAction
import `in`.localrush.rider.ui.common.dial
import `in`.localrush.rider.ui.common.durationLabel
import org.maplibre.android.style.layers.Property
import java.time.Instant
import java.util.Locale

@Composable
fun DeliveryScreen(onBack: () -> Unit, onCompleted: () -> Unit, mapStyleUrl: String, viewModel: DeliveryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val tts = rememberTts()

    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                is DeliveryUiEvent.Speak -> tts.speak(e.text)
                is DeliveryUiEvent.Message -> snackbar.showSnackbar(context.getString(e.resId))
                is DeliveryUiEvent.Toast -> snackbar.showSnackbar(e.text)
            }
        }
    }
    LaunchedEffect(state.actionError) { state.actionError?.takeIf { state.sheet == null }?.let { snackbar.showSnackbar(it.message); viewModel.clearActionError() } }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Box(Modifier.fillMaxSize()) {
            when {
                state.loading -> LoadingState()
                state.error != null -> ErrorState(state.error!!, onRetry = viewModel::load)
                else -> NavigationContent(state, viewModel, mapStyleUrl, onBack)
            }
            OfflineBanner(visible = !state.networkOnline, modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding())
            Spacer(Modifier.padding(padding))
        }
    }

    when (state.sheet) {
        DeliverySheet.PICKUP_OTP -> OtpSheet(state, viewModel, pickup = true)
        DeliverySheet.DELIVER_OTP -> OtpSheet(state, viewModel, pickup = false)
        DeliverySheet.PROBLEM -> ProblemSheet(state, viewModel)
        DeliverySheet.CHAT -> ChatSheet(state, viewModel)
        DeliverySheet.FAIL_REASON -> FailReasonSheet(state, viewModel)
        null -> Unit
    }

    if (state.completedPayout != null) {
        AlertDialog(
            onDismissRequest = onCompleted,
            title = { Text(stringResource(R.string.delivery_done_title)) },
            text = { Text(stringResource(R.string.delivery_done_body, Money.parse(state.completedPayout).format())) },
            confirmButton = { TextButton(onClick = onCompleted) { Text(stringResource(R.string.action_done)) } },
        )
    }
    if (state.step == DeliveryStep.FAILED && state.completedPayout == null && state.order != null) {
        AlertDialog(
            onDismissRequest = onCompleted,
            title = { Text(stringResource(R.string.delivery_failed_title)) },
            text = { Text(stringResource(R.string.delivery_failed_body, state.order?.statusDisplay ?: "")) },
            confirmButton = { TextButton(onClick = onCompleted) { Text(stringResource(R.string.action_done)) } },
        )
    }
}

@Composable
private fun NavigationContent(state: DeliveryUiState, vm: DeliveryViewModel, mapStyleUrl: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val camera = rememberCameraState { `in`.localrush.core.maps.CameraState(zoom = 16.0) }
    val riderIcon = remember { MapIcons.fromDrawable(context, R.drawable.ic_marker_navigation, sizeDp = 44f) }
    val restaurantIcon = remember { MapIcons.badge(context, R.drawable.ic_marker_restaurant, bg = android.graphics.Color.parseColor("#F26B1D")) }
    val homeIcon = remember { MapIcons.badge(context, R.drawable.ic_marker_home, bg = android.graphics.Color.parseColor("#1FA85A")) }
    var lastProgrammatic by remember { mutableStateOf<LatLng?>(null) }
    val panelHeight = 236.dp
    val position = state.position
    val destination = state.destination

    LaunchedEffect(Unit) { camera.padding = CameraPadding(top = 140.dp, bottom = panelHeight) }
    // Follow the rider with heading-up camera while navigating; fit both points when not following.
    LaunchedEffect(position, state.following, state.heading) {
        if (position == null) return@LaunchedEffect
        if (state.following) {
            lastProgrammatic = position
            camera.animateTo(position, zoom = 17.0, bearing = (state.heading ?: 0f).toDouble(), tilt = 35.0, durationMs = 900)
        }
    }
    LaunchedEffect(destination, state.route?.geometry) {
        if (!state.following && position != null && destination != null) camera.fitBounds(listOf(position, destination))
    }

    Box(Modifier.fillMaxSize()) {
        if (LocalMapPlaceholder.current) MapPlaceholder(Modifier.fillMaxSize()) else LRMap(
            modifier = Modifier.fillMaxSize(),
            styleUrl = mapStyleUrl,
            cameraState = camera,
            onCameraIdle = { target, _ ->
                val anchor = lastProgrammatic
                if (state.following && anchor != null && Geo.haversineMeters(anchor, target) > 60) vm.setFollowing(false)
            },
        ) {
            state.route?.geometry?.let { RoutePolyline(it, color = LRColors.Blue, widthDp = 6f) }
            state.order?.restaurantLocation?.let { Marker("restaurant", LatLng(it.latitude, it.longitude), restaurantIcon, anchor = Property.ICON_ANCHOR_BOTTOM, rotateWithMap = false) }
            state.order?.deliveryLocation?.let { Marker("customer", LatLng(it.latitude, it.longitude), homeIcon, anchor = Property.ICON_ANCHOR_BOTTOM, rotateWithMap = false) }
            position?.let { Marker("rider", it, riderIcon, bearing = state.heading ?: 0f, animate = true) }
        }

        // Top: back + instruction banner
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                }
                Spacer(Modifier.width(8.dp))
                InstructionBanner(state, Modifier.weight(1f))
            }
        }

        // Right: voice + recenter
        Column(Modifier.align(Alignment.CenterEnd).padding(end = 12.dp, bottom = panelHeight), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FloatingActionButton(onClick = vm::toggleVoice, containerColor = MaterialTheme.colorScheme.surface) {
                Icon(if (state.voiceEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff, contentDescription = stringResource(R.string.nav_voice))
            }
            AnimatedVisibility(visible = !state.following) {
                FloatingActionButton(onClick = { vm.setFollowing(true) }, containerColor = MaterialTheme.colorScheme.primary) {
                    Icon(Icons.Filled.MyLocation, contentDescription = stringResource(R.string.nav_recenter), tint = Color.White)
                }
            }
        }

        // Bottom panel
        BottomPanel(state, vm, Modifier.align(Alignment.BottomCenter), onCall = { phone -> dial(context, phone) })
    }
}

@Composable
private fun InstructionBanner(state: DeliveryUiState, modifier: Modifier = Modifier) {
    val next = state.next
    val remainingM = state.remainingM
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = LRColors.Charcoal, shadowElevation = 6.dp) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(maneuverIcon(next?.step), contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            next != null -> Geo.formatDistance(next.distanceM)
                            state.routeLoading -> stringResource(R.string.nav_calculating)
                            state.position == null -> stringResource(R.string.nav_waiting_gps)
                            else -> remainingM?.let { Geo.formatDistance(it) } ?: ""
                        },
                        style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold,
                    )
                    Text(
                        next?.step?.instruction?.ifBlank { null } ?: destinationLabel(state),
                        style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.9f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val eta = state.remainingS?.let { DateTimeUtils.formatTime(Instant.now().plusSeconds(it.toLong())) }
                Text(
                    listOfNotNull(remainingM?.let { Geo.formatDistance(it) }, state.remainingS?.let { durationLabel(it) }, eta?.let { stringResource(R.string.nav_eta, it) }).joinToString(" · "),
                    style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.weight(1f),
                )
                if (state.offRoute) Text(stringResource(R.string.nav_rerouting), style = MaterialTheme.typography.labelMedium, color = LRColors.Amber)
                else if (state.routeError != null) Text(stringResource(R.string.nav_route_unavailable), style = MaterialTheme.typography.labelMedium, color = LRColors.Amber)
            }
        }
    }
}

@Composable
private fun destinationLabel(state: DeliveryUiState): String = when (state.step) {
    DeliveryStep.TO_CUSTOMER -> stringResource(R.string.nav_to_customer)
    DeliveryStep.DELIVERED -> stringResource(R.string.delivery_done_title)
    DeliveryStep.FAILED -> stringResource(R.string.delivery_failed_title)
    else -> stringResource(R.string.nav_to_restaurant, state.order?.restaurantName ?: "")
}

private fun maneuverIcon(step: RouteStepDto?): ImageVector {
    if (step == null) return Icons.Filled.Navigation
    val mod = step.modifier?.lowercase(Locale.ROOT) ?: ""
    return when (step.maneuverType.lowercase(Locale.ROOT)) {
        "arrive" -> Icons.Filled.Flag
        "depart" -> Icons.Filled.Navigation
        "roundabout", "rotary", "roundabout turn", "exit roundabout" -> Icons.Filled.RoundaboutLeft
        else -> when {
            mod.contains("uturn") -> Icons.Filled.UTurnLeft
            mod == "sharp left" || mod == "left" -> Icons.Filled.TurnLeft
            mod == "sharp right" || mod == "right" -> Icons.Filled.TurnRight
            mod == "slight left" -> Icons.Filled.TurnSlightLeft
            mod == "slight right" -> Icons.Filled.TurnSlightRight
            else -> Icons.Filled.Straight
        }
    }
}

@Composable
private fun BottomPanel(state: DeliveryUiState, vm: DeliveryViewModel, modifier: Modifier, onCall: (String) -> Unit) {
    val order = state.order ?: return
    val toCustomer = state.step.isToCustomer
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 12.dp) {
        Column(Modifier.padding(16.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (toCustomer) order.deliveryName.ifBlank { stringResource(R.string.offer_customer) } else order.restaurantName,
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (toCustomer) listOf(order.deliveryAddressText, order.deliveryLandmark).filter { it.isNotBlank() }.joinToString(" · ") else order.restaurantAddressText,
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.order_number, order.orderNumber), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val collect = Money.parse(order.collectAmount)
            if (toCustomer && (order.deliveryInstructions.isNotBlank() || !collect.isZero || order.isContactless)) {
                Spacer(Modifier.height(8.dp))
                Text(
                    listOfNotNull(
                        if (!collect.isZero) stringResource(R.string.offer_collect_cod, collect.format()) else null,
                        if (order.isContactless) stringResource(R.string.delivery_contactless) else null,
                        order.deliveryInstructions.ifBlank { null },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium, color = LRTheme.colors.warning, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (toCustomer) {
                    LRButton(stringResource(R.string.action_call), onClick = { vm.loadContact { c -> c.phone?.let(onCall) } }, modifier = Modifier.weight(1f), style = LRButtonStyle.Outlined, leadingIcon = Icons.Filled.Call, loading = state.contactLoading)
                    LRButton(stringResource(R.string.action_chat), onClick = { vm.openSheet(DeliverySheet.CHAT) }, modifier = Modifier.weight(1f), style = LRButtonStyle.Outlined, leadingIcon = Icons.AutoMirrored.Filled.Chat)
                } else {
                    val phone = order.restaurantPhone
                    LRButton(stringResource(R.string.action_call_restaurant), onClick = { phone?.let(onCall) }, modifier = Modifier.weight(1f), style = LRButtonStyle.Outlined, leadingIcon = Icons.Filled.Call, enabled = !phone.isNullOrBlank())
                }
                LRButton(stringResource(R.string.action_problem), onClick = { vm.openSheet(DeliverySheet.PROBLEM) }, modifier = Modifier.weight(1f), style = LRButtonStyle.Outlined, leadingIcon = Icons.Filled.ReportProblem)
            }
            Spacer(Modifier.height(10.dp))
            when (state.core.primaryAction) {
                PrimaryAction.ARRIVED_AT_RESTAURANT -> LRButton(stringResource(R.string.action_arrived_restaurant), onClick = vm::arrivedAtRestaurant, modifier = Modifier.fillMaxWidth().height(58.dp), loading = state.actionInProgress)
                PrimaryAction.WAITING_FOR_FOOD -> {
                    Box(Modifier.fillMaxWidth().background(LRTheme.colors.warningContainer, RoundedCornerShape(14.dp)).padding(14.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.delivery_waiting_food, order.statusDisplay), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    }
                }
                PrimaryAction.ENTER_PICKUP_OTP -> LRButton(stringResource(R.string.action_enter_pickup_otp), onClick = { vm.openSheet(DeliverySheet.PICKUP_OTP) }, modifier = Modifier.fillMaxWidth().height(58.dp))
                PrimaryAction.ARRIVED_AT_CUSTOMER -> LRButton(stringResource(R.string.action_arrived_customer), onClick = { vm.openSheet(DeliverySheet.DELIVER_OTP) }, modifier = Modifier.fillMaxWidth().height(58.dp))
                PrimaryAction.NONE -> Unit
            }
            state.actionError?.takeIf { state.sheet == null }?.let { Spacer(Modifier.height(8.dp)); InlineError(it.message) }
        }
    }
}

/** Minimal TextToSpeech holder tied to the composition. */
class TtsController(private val tts: TextToSpeech?, private val ready: () -> Boolean) {
    fun speak(text: String) {
        val engine = tts ?: return
        if (!ready()) return
        runCatching { engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nav-${System.currentTimeMillis()}") }
    }
}

@Composable
fun rememberTts(): TtsController {
    val context = LocalContext.current
    var ready by remember { mutableStateOf(false) }
    val tts = remember {
        var instance: TextToSpeech? = null
        // No TTS engine (e.g. JVM screenshot rendering) must not crash navigation; speak() is a no-op without one.
        instance = runCatching {
            TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    runCatching { instance?.language = Locale.forLanguageTag("en-IN") }
                    ready = true
                }
            }
        }.getOrNull()
        instance
    }
    DisposableEffect(Unit) { onDispose { runCatching { tts?.stop(); tts?.shutdown() } } }
    return remember(tts) { TtsController(tts) { ready } }
}
