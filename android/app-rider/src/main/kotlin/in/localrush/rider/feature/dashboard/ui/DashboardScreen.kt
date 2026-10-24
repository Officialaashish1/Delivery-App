package `in`.localrush.rider.feature.dashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.localrush.core.common.geo.Geo
import `in`.localrush.core.common.money.Money
import `in`.localrush.core.common.order.OrderStatus
import `in`.localrush.core.designsystem.component.ChipTone
import `in`.localrush.core.designsystem.component.ErrorState
import `in`.localrush.core.designsystem.component.LRButton
import `in`.localrush.core.designsystem.component.LRButtonStyle
import `in`.localrush.core.designsystem.component.LRCard
import `in`.localrush.core.designsystem.component.OfflineBanner
import `in`.localrush.core.designsystem.component.ShimmerListPlaceholder
import `in`.localrush.core.designsystem.component.StatusChip
import `in`.localrush.core.designsystem.theme.LRTheme
import `in`.localrush.rider.R
import `in`.localrush.rider.data.IncentiveDto
import `in`.localrush.rider.data.RiderOfferDto
import `in`.localrush.rider.data.RiderOrderDto
import `in`.localrush.rider.feature.dashboard.vm.DashboardEvent
import `in`.localrush.rider.feature.dashboard.vm.DashboardUiState
import `in`.localrush.rider.feature.dashboard.vm.DashboardViewModel
import `in`.localrush.rider.ui.common.IconLabel
import `in`.localrush.rider.ui.common.SectionTitle
import `in`.localrush.rider.ui.common.StatTile
import `in`.localrush.rider.ui.common.TwoUp
import `in`.localrush.rider.ui.common.openAppSettings
import `in`.localrush.rider.ui.common.rememberOnlinePermissionFlow
import `in`.localrush.rider.ui.common.rupees

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onOpenDelivery: (String) -> Unit,
    onOpenOffer: (String) -> Unit,
    onOpenEarnings: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val permissionFlow = rememberOnlinePermissionFlow(onReady = { viewModel.goOnline() }, onDenied = viewModel::permissionDenied)

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.onPause() }
    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                is DashboardEvent.Message -> snackbar.showSnackbar(context.getString(e.resId))
                is DashboardEvent.Error -> snackbar.showSnackbar(e.error.message)
                DashboardEvent.RequestPermissions -> permissionFlow.start()
            }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OfflineBanner(visible = !state.networkOnline)
            when {
                state.loading -> ShimmerListPlaceholder(count = 4, modifier = Modifier.padding(16.dp))
                state.error != null && state.data == null -> ErrorState(state.error!!, onRetry = { viewModel.refresh() })
                else -> PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { viewModel.refresh() }) {
                    DashboardContent(state, viewModel, onOpenDelivery, onOpenOffer, onOpenEarnings)
                }
            }
        }
    }
}

@Composable
private fun DashboardContent(
    state: DashboardUiState,
    vm: DashboardViewModel,
    onOpenDelivery: (String) -> Unit,
    onOpenOffer: (String) -> Unit,
    onOpenEarnings: () -> Unit,
) {
    val d = state.data ?: return
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { OnlineCard(state, onToggle = { if (state.isOnline) vm.goOffline() else vm.requestOnline() }) }
        if (state.isOnline && !state.backgroundPermission) {
            item { WarningCard(Icons.Outlined.LocationOff, stringResource(R.string.dash_bg_permission_warning), stringResource(R.string.action_open_settings)) { openAppSettings(context) } }
        }
        if (state.queuedFixes > 0) {
            item { WarningCard(Icons.Outlined.CloudOff, stringResource(R.string.dash_queued_fixes, state.queuedFixes), null, null) }
        }
        state.currentOrder?.let { order -> item { CurrentDeliveryCard(order) { onOpenDelivery(order.id) } } }
        if (d.pendingOffers.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.dash_pending_offers)) }
            items(d.pendingOffers, key = { it.id }) { offer -> OfferRow(offer) { onOpenOffer(offer.id) } }
        }
        item {
            SectionTitle(stringResource(R.string.dash_today)) {
                LRButton(stringResource(R.string.dash_view_earnings), onClick = onOpenEarnings, style = LRButtonStyle.Text, trailingIcon = Icons.Filled.ChevronRight)
            }
            TwoUp(
                left = { StatTile(stringResource(R.string.dash_earnings), d.todayEarnings.rupees(), accent = true) },
                right = { StatTile(stringResource(R.string.dash_deliveries), d.todayDeliveries.toString()) },
            )
        }
        item {
            TwoUp(
                left = { StatTile(stringResource(R.string.dash_this_week), d.weekEarnings.rupees(), caption = stringResource(R.string.dash_n_deliveries, d.weekDeliveries)) },
                right = { StatTile(stringResource(R.string.dash_this_month), d.monthEarnings.rupees(), caption = stringResource(R.string.dash_n_deliveries, d.monthDeliveries)) },
            )
        }
        item {
            TwoUp(
                left = { StatTile(stringResource(R.string.dash_pending_payout), d.pendingPayout.rupees(), caption = stringResource(R.string.dash_wallet, d.walletBalance.rupees())) },
                right = { StatTile(stringResource(R.string.dash_cod_in_hand), d.codCashInHand.rupees(), caption = stringResource(R.string.dash_cod_hint)) },
            )
        }
        if (d.incentives.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.dash_incentives)) }
            items(d.incentives, key = { it.name }) { IncentiveCard(it) }
        }
        item {
            LRCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Star, contentDescription = null, tint = LRTheme.colors.star)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.dash_rating), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(d.ratingAvg.toDoubleOrNull()?.let { String.format(java.util.Locale.ROOT, "%.1f", it) } ?: "—", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(stringResource(R.string.dash_lifetime), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.dash_n_deliveries, d.lifetimeDeliveries), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun OnlineCard(state: DashboardUiState, onToggle: () -> Unit) {
    val online = state.isOnline
    val bg = if (online) LRTheme.colors.successContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    Box(Modifier.fillMaxWidth().background(bg, RoundedCornerShape(20.dp)).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).background(if (online) LRTheme.colors.success else MaterialTheme.colorScheme.outline, CircleShape))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(if (online) R.string.dash_online else R.string.dash_offline),
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                )
                Text(
                    when {
                        !state.approved -> stringResource(R.string.dash_not_approved)
                        online && state.trackingRunning -> stringResource(R.string.dash_online_hint)
                        online -> stringResource(R.string.dash_online_no_tracking)
                        else -> stringResource(R.string.dash_offline_hint)
                    },
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = online, onCheckedChange = { onToggle() }, enabled = !state.toggling, modifier = Modifier.size(width = 64.dp, height = 40.dp))
        }
    }
}

@Composable
private fun WarningCard(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, actionText: String?, onAction: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().background(LRTheme.colors.warningContainer, RoundedCornerShape(14.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = LRTheme.colors.warning)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (actionText != null && onAction != null) LRButton(actionText, onClick = onAction, style = LRButtonStyle.Text)
    }
}

@Composable
fun CurrentDeliveryCard(order: RiderOrderDto, onClick: () -> Unit) {
    val status = OrderStatus.fromWire(order.status)
    LRCard(Modifier.fillMaxWidth(), onClick = onClick, large = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.dash_current_delivery), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            StatusChip(status, label = order.statusDisplay.ifBlank { null })
        }
        Spacer(Modifier.height(10.dp))
        IconLabel(Icons.Outlined.Restaurant, order.restaurantName)
        Spacer(Modifier.height(6.dp))
        IconLabel(Icons.Outlined.Place, order.deliveryAddressText)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.order_number, order.orderNumber), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.dash_payout, order.riderPayout.rupees()), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        LRButton(stringResource(if (status.riderOnTheWay) R.string.dash_continue_to_customer else R.string.dash_continue_to_restaurant), onClick = onClick, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun OfferRow(offer: RiderOfferDto, onClick: () -> Unit) {
    val order = offer.order
    LRCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(order?.restaurantName ?: stringResource(R.string.offer_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.offer_summary, offer.distanceToRestaurantKm?.toDoubleOrNull()?.let { Geo.formatDistanceKm(it) } ?: "—", order?.distanceKm?.toDoubleOrNull()?.let { Geo.formatDistanceKm(it) } ?: "—"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Money.parse(order?.riderPayout).format(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                StatusChip(stringResource(R.string.offer_seconds_left, offer.secondsLeft), ChipTone.Warning)
            }
        }
    }
}

@Composable
private fun IncentiveCard(inc: IncentiveDto) {
    val progress = if (inc.deliveriesRequired > 0) (inc.progress.toFloat() / inc.deliveriesRequired).coerceIn(0f, 1f) else 0f
    LRCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(inc.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("+${inc.bonusAmount.rupees()}", style = MaterialTheme.typography.titleSmall, color = LRTheme.colors.success, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.dash_incentive_progress, inc.progress, inc.deliveriesRequired), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
