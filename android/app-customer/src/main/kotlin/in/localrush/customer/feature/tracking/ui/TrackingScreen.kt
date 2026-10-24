package `in`.localrush.customer.feature.tracking.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.localrush.core.common.geo.Geo
import `in`.localrush.core.common.money.toMoney
import `in`.localrush.core.common.order.OrderStatus
import `in`.localrush.core.common.time.DateTimeUtils
import `in`.localrush.core.common.time.toInstantOrNull
import `in`.localrush.core.designsystem.component.ErrorState
import `in`.localrush.core.designsystem.component.LRBottomSheet
import `in`.localrush.core.designsystem.component.LRButton
import `in`.localrush.core.designsystem.component.LRImage
import `in`.localrush.core.designsystem.component.LRTextField
import `in`.localrush.core.designsystem.component.LoadingState
import `in`.localrush.core.designsystem.component.OrderStepProgressBar
import `in`.localrush.core.designsystem.component.StatusChip
import `in`.localrush.core.designsystem.component.ChipTone
import `in`.localrush.core.designsystem.icon.LRIcons
import `in`.localrush.core.designsystem.theme.LRColors
import `in`.localrush.core.designsystem.theme.LRTheme
import `in`.localrush.core.maps.CameraPadding
import `in`.localrush.core.maps.LRMap
import `in`.localrush.core.maps.MapIcons
import `in`.localrush.core.maps.MapScope
import `in`.localrush.core.maps.Marker
import `in`.localrush.core.maps.MarkerAnimator
import `in`.localrush.core.maps.RoutePolyline
import `in`.localrush.core.maps.rememberCameraState
import `in`.localrush.customer.R
import `in`.localrush.customer.ui.common.LocalMapPlaceholder
import `in`.localrush.customer.ui.common.MapPlaceholder
import `in`.localrush.core.common.geo.LatLng
import `in`.localrush.customer.feature.tracking.vm.ConnectionState
import `in`.localrush.customer.feature.tracking.vm.RiderFix
import `in`.localrush.customer.feature.tracking.vm.TrackingEvent
import `in`.localrush.customer.feature.tracking.vm.TrackingUiState
import `in`.localrush.customer.feature.tracking.vm.TrackingViewModel
import `in`.localrush.customer.ui.common.RoundAction
import org.maplibre.android.style.layers.Property

private const val SHEET_PEEK_DP = 300

/**
 * The showcase: step header, MapLibre map with animated scooter marker + solid route, bottom sheet with the big ETA,
 * rider card (Call / Chat), delivery OTP and the order total.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackingScreen(onBack: () -> Unit, onOrderDetail: (String) -> Unit, viewModel: TrackingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val config by viewModel.config.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val contactUnavailable = stringResource(R.string.tracking_contact_unavailable)
    var chatOpen by remember { mutableStateOf(false) }
    LaunchedEffect(chatOpen) { viewModel.setChatOpen(chatOpen) }
    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                is TrackingEvent.Dial -> runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${e.phone}"))) }
                is TrackingEvent.Message -> snackbar.showSnackbar(e.text)
                TrackingEvent.ContactUnavailable -> snackbar.showSnackbar(contactUnavailable)
            }
        }
    }

    when {
        state.loading -> Box(Modifier.fillMaxSize()) { LoadingState(); BackButton(onBack, Modifier.align(Alignment.TopStart)) }
        state.error != null && state.order == null -> Box(Modifier.fillMaxSize()) { ErrorState(state.error!!, onRetry = viewModel::retry); BackButton(onBack, Modifier.align(Alignment.TopStart)) }
        else -> {
            val scaffold = rememberBottomSheetScaffoldState()
            BottomSheetScaffold(
                scaffoldState = scaffold,
                sheetPeekHeight = SHEET_PEEK_DP.dp,
                sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                sheetContainerColor = MaterialTheme.colorScheme.surface,
                snackbarHost = { SnackbarHost(snackbar) },
                sheetContent = { TrackingSheet(state, onCall = viewModel::callRider, onChat = { chatOpen = true }, onOrderDetail = { onOrderDetail(viewModel.orderId) }) },
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    TrackingMap(state, config.mapStyleUrl, config.mapAttribution)
                    Column(Modifier.fillMaxWidth()) {
                        Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
                            Column(Modifier.statusBarsPadding().padding(bottom = 12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                                    Column(Modifier.weight(1f)) {
                                        Text(state.statusDisplay.ifBlank { stringResource(R.string.tracking_title) }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        state.order?.let { Text(stringResource(R.string.order_number, it.orderNumber), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    }
                                    ConnectionChip(state.connection)
                                    Spacer(Modifier.width(12.dp))
                                }
                                OrderStepProgressBar(state.status, Modifier.padding(horizontal = 20.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (chatOpen) ChatSheet(state, viewModel, onDismiss = { chatOpen = false })
}

@Composable
private fun BackButton(onBack: () -> Unit, modifier: Modifier) {
    IconButton(onClick = onBack, modifier.statusBarsPadding().padding(4.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
}

@Composable
private fun ConnectionChip(c: ConnectionState) {
    val (label, tone) = when (c) {
        ConnectionState.LIVE -> R.string.tracking_live to ChipTone.Success
        ConnectionState.CONNECTING -> R.string.tracking_connecting to ChipTone.Neutral
        ConnectionState.RECONNECTING -> R.string.tracking_reconnecting to ChipTone.Warning
        ConnectionState.POLLING -> R.string.tracking_polling to ChipTone.Warning
    }
    StatusChip(stringResource(label), tone, dot = c == ConnectionState.LIVE)
}

@Composable
private fun TrackingMap(state: TrackingUiState, styleUrl: String, attribution: String) {
    val context = LocalContext.current
    val camera = rememberCameraState()
    var ready by remember { mutableStateOf(false) }
    var following by remember { mutableStateOf(true) }
    var fitted by remember { mutableStateOf(false) }
    val scooter = remember { MapIcons.fromDrawable(context, R.drawable.ic_map_scooter, sizeDp = 52f) }
    val restaurantPin = remember { MapIcons.badge(context, R.drawable.ic_map_restaurant, LRColors.Charcoal.toArgb(), sizeDp = 40f) }
    val homePin = remember { MapIcons.badge(context, R.drawable.ic_map_home, LRColors.Orange.toArgb(), sizeDp = 40f) }

    // Centre we last asked the camera to go to; an idle far away from it means the user panned.
    var expectedCenter by remember { mutableStateOf<LatLng?>(null) }
    var skipNextIdle by remember { mutableStateOf(false) }
    fun fitAll() { if (state.focusPoints.isNotEmpty()) { skipNextIdle = true; expectedCenter = null; camera.fitBounds(state.focusPoints, CameraPadding.all(16.dp)); fitted = true } }

    // Keep the sheet area clear of the markers.
    LaunchedEffect(Unit) { camera.padding = CameraPadding(top = 150.dp, bottom = (SHEET_PEEK_DP + 24).dp, left = 24.dp, right = 24.dp) }
    // Fit everything on first load and whenever the leg changes (rider picks up → restaurant pin leaves the focus set).
    LaunchedEffect(ready, state.riderOnTheWay, state.destination) { if (ready) { following = true; fitAll() } }
    // Follow the scooter with padding while on the way, unless the user panned away.
    LaunchedEffect(ready, following, state.riderFix, state.riderOnTheWay) {
        if (ready && following && fitted && state.riderOnTheWay) state.riderFix?.let { expectedCenter = it.point; camera.follow(it.point, zoom = 15.5) }
    }

    Box(Modifier.fillMaxSize()) {
        if (LocalMapPlaceholder.current) MapPlaceholder(Modifier.fillMaxSize()) else LRMap(
            modifier = Modifier.fillMaxSize(),
            styleUrl = styleUrl,
            attribution = attribution.ifBlank { null },
            cameraState = camera,
            onMapReady = { ready = true },
            onCameraIdle = { target, _ ->
                if (skipNextIdle) skipNextIdle = false
                else expectedCenter?.let { if (Geo.haversineMeters(it, target) > 60.0) following = false }
            },
        ) {
            state.restaurant?.let { Marker("restaurant", it, restaurantPin, anchor = Property.ICON_ANCHOR_BOTTOM, rotateWithMap = false) }
            state.destination?.let { Marker("home", it, homePin, anchor = Property.ICON_ANCHOR_BOTTOM, rotateWithMap = false) }
            RoutePolyline(state.route?.geometry, id = "route", color = LRColors.Orange, widthDp = 5f, dashed = false, outlineColor = androidx.compose.ui.graphics.Color.White)
            state.riderFix?.let { fix -> RiderMarker(fix, scooter) }
        }
        // Distance label near the destination when we know the route.
        state.route?.takeIf { it.distanceM > 0 && state.riderOnTheWay }?.let { r ->
            Surface(Modifier.align(Alignment.TopEnd).padding(top = 170.dp, end = 12.dp), shape = RoundedCornerShape(10.dp), shadowElevation = 4.dp) {
                Text(stringResource(R.string.tracking_distance_away, Geo.formatDistance(r.distanceM)), Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
        }
        SmallFloatingActionButton(
            onClick = { following = true; fitAll() },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = (SHEET_PEEK_DP + 16).dp),
            containerColor = MaterialTheme.colorScheme.surface,
        ) { Icon(Icons.Default.MyLocation, stringResource(R.string.tracking_recenter), tint = MaterialTheme.colorScheme.primary) }
    }
}

/**
 * Scooter marker that glides between GPS fixes over ~3 s (position + bearing, eased) using core's [MarkerAnimator]
 * directly so the duration can be longer than the default `Marker` composable's.
 */
@Composable
private fun MapScope.RiderMarker(fix: RiderFix, icon: android.graphics.Bitmap) {
    val scope = rememberCoroutineScope()
    val animator = remember { MarkerAnimator(controller, RIDER_MARKER, scope) }
    LaunchedEffect(Unit) {
        controller.setMarker(RIDER_MARKER, fix.point, icon, bearing = fix.bearing ?: 0f, anchor = Property.ICON_ANCHOR_CENTER, rotateWithMap = true, zIndexAbove = "home")
        animator.jumpTo(fix.point, fix.bearing)
    }
    LaunchedEffect(fix.point, fix.bearing) { animator.animateTo(fix.point, fix.bearing, durationMs = RIDER_ANIMATION_MS) }
    DisposableEffect(Unit) { onDispose { animator.cancel(); controller.removeMarker(RIDER_MARKER) } }
}

private const val RIDER_MARKER = "rider"
private const val RIDER_ANIMATION_MS = 3_000L

private fun Color.toArgb(): Int = android.graphics.Color.argb((alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())

@Composable
private fun TrackingSheet(state: TrackingUiState, onCall: () -> Unit, onChat: () -> Unit, onOrderDetail: () -> Unit) {
    val order = state.order ?: return
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
        // ETA
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                AnimatedContent(state.etaLabel, transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) }, label = "eta") { eta ->
                    when {
                        state.isTerminal -> Text(state.statusDisplay, style = MaterialTheme.typography.headlineMedium, color = if (state.status == OrderStatus.DELIVERED) LRTheme.colors.success else MaterialTheme.colorScheme.error)
                        eta != null -> Text(eta, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                        else -> Text(stringResource(R.string.tracking_eta_pending), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(
                    when {
                        state.isTerminal -> order.deliveredAt.toInstantOrNull()?.let { stringResource(R.string.tracking_delivered_at, DateTimeUtils.formatTime(it)) } ?: ""
                        state.riderOnTheWay -> stringResource(R.string.tracking_eta_caption_on_way)
                        else -> stringResource(R.string.tracking_eta_caption_preparing)
                    },
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.etaAt?.takeIf { !state.isTerminal }?.let {
                Column(horizontalAlignment = Alignment.End) {
                    Text(stringResource(R.string.tracking_arrives_by), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(DateTimeUtils.formatTime(it), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        // OTP
        AnimatedVisibility(state.showOtp, enter = fadeIn() + slideInVertically()) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp).background(LRColors.OrangeContainer, RoundedCornerShape(14.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.tracking_otp_title), style = MaterialTheme.typography.titleSmall, color = LRColors.OrangeDark)
                    Text(stringResource(R.string.tracking_otp_body), style = MaterialTheme.typography.bodySmall, color = LRColors.TextSecondary)
                }
                Text(state.deliveryOtp ?: "", style = MaterialTheme.typography.headlineMedium, color = LRColors.OrangeDark, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 14.dp))
        // Order summary row
        Row(Modifier.fillMaxWidth().clickable(onClick = onOrderDetail), verticalAlignment = Alignment.CenterVertically) {
            LRImage(order.restaurantLogo, order.restaurantName, Modifier.size(44.dp), radius = 12.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(order.restaurantName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(order.itemsSummary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(stringResource(R.string.tracking_view_order), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        // Rider card
        state.rider?.let { r ->
            HorizontalDivider(Modifier.padding(vertical = 14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape), contentAlignment = Alignment.Center) {
                    if (!r.photo.isNullOrBlank()) LRImage(r.photo, r.fullName, Modifier.size(56.dp), radius = 28.dp) else Icon(LRIcons.Rider, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.fullName, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.tracking_rider_meta, r.ratingAvg, r.totalDeliveries), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (r.vehicleNumber.isNotBlank()) Text("${r.vehicleType.replaceFirstChar { it.uppercase() }} · ${r.vehicleNumber}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (state.canContactRider) {
                    RoundAction(Icons.Default.Call, stringResource(R.string.tracking_call), onCall)
                    Spacer(Modifier.width(12.dp))
                    BadgedBox(badge = { if (state.unreadChat > 0) Badge { Text(state.unreadChat.toString()) } }) {
                        RoundAction(Icons.Outlined.ChatBubbleOutline, stringResource(R.string.tracking_chat), onChat)
                    }
                }
            }
        } ?: run {
            if (!state.isTerminal) {
                HorizontalDivider(Modifier.padding(vertical = 14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.tracking_assigning_rider), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.tracking_order_total), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(order.totalAmount.toMoney().format(), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(8.dp))
            StatusChip(if (order.isPaid) stringResource(R.string.order_paid) else stringResource(R.string.pay_cod), if (order.isPaid) ChipTone.Success else ChipTone.Warning)
        }
        if (order.deliveryAddressText.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.tracking_delivering_to, order.deliveryAddressText), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatSheet(state: TrackingUiState, vm: TrackingViewModel, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val sending by vm.chatSending.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    LaunchedEffect(state.chat.size) { if (state.chat.isNotEmpty()) listState.animateScrollToItem(state.chat.lastIndex) }
    LRBottomSheet(onDismiss = onDismiss, title = state.rider?.fullName ?: stringResource(R.string.tracking_chat)) {
        if (state.chat.isEmpty()) Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.chat_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        } else LazyColumn(Modifier.fillMaxWidth().height(360.dp), state = listState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(state.chat, key = { it.id }) { m ->
                val mine = m.senderRole == "customer"
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                    Column(
                        Modifier.widthIn(max = 280.dp).background(if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = if (mine) 14.dp else 4.dp, bottomEnd = if (mine) 4.dp else 14.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(m.text, color = if (mine) Color.White else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
                        m.createdAt.toInstantOrNull()?.let { Text(DateTimeUtils.formatTime(it), style = MaterialTheme.typography.labelSmall, color = if (mine) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (!state.canContactRider) Text(stringResource(R.string.chat_unavailable), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            LRTextField(text, { text = it.take(500) }, placeholder = stringResource(R.string.chat_hint), modifier = Modifier.weight(1f), enabled = state.canContactRider)
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { vm.sendChat(text); text = "" }, enabled = text.isNotBlank() && !sending && state.canContactRider) {
                if (sending) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.chat_send), tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(4.dp))
        LRButton(stringResource(R.string.action_close), onClick = onDismiss, style = `in`.localrush.core.designsystem.component.LRButtonStyle.Text, modifier = Modifier.fillMaxWidth())
    }
}

