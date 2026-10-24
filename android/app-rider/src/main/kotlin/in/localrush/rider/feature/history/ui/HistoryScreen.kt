package `in`.localrush.rider.feature.history.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.localrush.core.common.geo.Geo
import `in`.localrush.core.common.money.Money
import `in`.localrush.core.common.order.OrderStatus
import `in`.localrush.core.common.util.UiState
import `in`.localrush.core.designsystem.component.EmptyState
import `in`.localrush.core.designsystem.component.ErrorState
import `in`.localrush.core.designsystem.component.LRButton
import `in`.localrush.core.designsystem.component.LRCard
import `in`.localrush.core.designsystem.component.LoadingState
import `in`.localrush.core.designsystem.component.ShimmerListPlaceholder
import `in`.localrush.core.designsystem.component.StatusChip
import `in`.localrush.rider.R
import `in`.localrush.rider.data.RiderOrderDto
import `in`.localrush.rider.data.isRiderActive
import `in`.localrush.rider.feature.history.vm.HistoryScope
import `in`.localrush.rider.feature.history.vm.HistoryViewModel
import `in`.localrush.rider.feature.history.vm.OrderDetailViewModel
import `in`.localrush.rider.feature.offer.ui.OrderItemsCard
import `in`.localrush.rider.ui.common.IconLabel
import `in`.localrush.rider.ui.common.KeyValueRow
import `in`.localrush.rider.ui.common.RiderTopBar
import `in`.localrush.rider.ui.common.relativeDay
import `in`.localrush.rider.ui.common.rupees
import `in`.localrush.rider.ui.common.timeLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onOpenOrder: (String) -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { RiderTopBar(stringResource(R.string.history_title)) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = state.scope.ordinal) {
                HistoryScope.entries.forEach { s -> Tab(selected = state.scope == s, onClick = { viewModel.select(s) }, text = { Text(scopeLabel(s)) }) }
            }
            when {
                state.loading -> ShimmerListPlaceholder(count = 6, modifier = Modifier.padding(16.dp))
                state.error != null -> ErrorState(state.error!!, onRetry = viewModel::load)
                state.items.isEmpty() -> EmptyState(title = stringResource(R.string.history_empty), description = stringResource(R.string.history_empty_hint))
                else -> PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = viewModel::load) {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(state.items, key = { it.id }) { HistoryRow(it) { onOpenOrder(it.id) } }
                        if (state.hasMore) item { LaunchedEffect(Unit) { viewModel.loadMore() }; ShimmerListPlaceholder(count = 1) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(o: RiderOrderDto, onClick: () -> Unit) {
    val status = OrderStatus.fromWire(o.status)
    LRCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(o.restaurantName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.order_number, o.orderNumber) + " · " + (o.deliveredAt ?: o.placedAt).relativeDay(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(o.riderPayout.rupees(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                StatusChip(status, label = o.statusDisplay.ifBlank { null })
            }
        }
    }
}

@Composable
fun scopeLabel(s: HistoryScope): String = stringResource(
    when (s) {
        HistoryScope.ACTIVE -> R.string.history_active
        HistoryScope.TODAY -> R.string.period_today
        HistoryScope.COMPLETED -> R.string.history_completed
        HistoryScope.ALL -> R.string.period_all
    },
)

@Composable
fun OrderDetailScreen(onBack: () -> Unit, onResume: (String) -> Unit, viewModel: OrderDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { RiderTopBar(stringResource(R.string.order_detail_title), onBack = onBack) }) { padding ->
        when (val s = state) {
            UiState.Loading -> LoadingState(Modifier.padding(padding))
            is UiState.Error -> ErrorState(s.error, onRetry = viewModel::load, modifier = Modifier.padding(padding))
            UiState.Empty -> EmptyState(title = stringResource(R.string.history_empty), modifier = Modifier.padding(padding))
            is UiState.Success -> {
                val o = s.data
                val status = OrderStatus.fromWire(o.status)
                Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.order_number, o.orderNumber), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        StatusChip(status, label = o.statusDisplay.ifBlank { null })
                    }
                    if (status.isRiderActive()) LRButton(stringResource(R.string.history_resume), onClick = { onResume(o.id) }, modifier = Modifier.fillMaxWidth())
                    LRCard(Modifier.fillMaxWidth()) {
                        IconLabel(Icons.Outlined.Restaurant, "${o.restaurantName}\n${o.restaurantAddressText}", maxLines = 3)
                        Spacer(Modifier.height(10.dp))
                        IconLabel(Icons.Outlined.Place, "${o.deliveryName}\n${o.deliveryAddressText}", maxLines = 3)
                    }
                    OrderItemsCard(o)
                    LRCard(Modifier.fillMaxWidth()) {
                        KeyValueRow(stringResource(R.string.detail_payout), o.riderPayout.rupees(), valueColor = MaterialTheme.colorScheme.primary)
                        KeyValueRow(stringResource(R.string.detail_distance), o.distanceKm.toDoubleOrNull()?.let { Geo.formatDistanceKm(it) } ?: "—")
                        KeyValueRow(stringResource(R.string.detail_payment), if (Money.parse(o.collectAmount).isZero) stringResource(R.string.offer_prepaid) else stringResource(R.string.offer_collect_cod, o.collectAmount.rupees()))
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        KeyValueRow(stringResource(R.string.detail_placed), o.placedAt.timeLabel())
                        KeyValueRow(stringResource(R.string.detail_picked_up), o.pickedUpAt.timeLabel())
                        KeyValueRow(stringResource(R.string.detail_delivered), o.deliveredAt.timeLabel())
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}
