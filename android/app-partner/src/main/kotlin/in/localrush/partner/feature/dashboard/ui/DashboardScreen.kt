package `in`.localrush.partner.feature.dashboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.localrush.core.common.money.Money
import `in`.localrush.core.common.money.MoneyFormatter
import `in`.localrush.core.common.time.DateTimeUtils
import `in`.localrush.core.common.time.toInstantOrNull
import `in`.localrush.core.designsystem.component.ChipTone
import `in`.localrush.core.designsystem.component.LRBottomSheet
import `in`.localrush.core.designsystem.component.LRButton
import `in`.localrush.core.designsystem.component.LRButtonStyle
import `in`.localrush.core.designsystem.component.LRCard
import `in`.localrush.core.designsystem.component.LRImage
import `in`.localrush.core.designsystem.component.RatingBadge
import `in`.localrush.core.designsystem.component.StatusChip
import `in`.localrush.core.designsystem.component.VegNonVegDot
import `in`.localrush.partner.R
import `in`.localrush.partner.feature.common.ui.KeyValueRow
import `in`.localrush.partner.feature.common.ui.LabeledSwitch
import `in`.localrush.partner.feature.common.ui.PartnerTopBar
import `in`.localrush.partner.feature.common.ui.SectionTitle
import `in`.localrush.partner.feature.common.ui.StateContent
import `in`.localrush.partner.feature.common.ui.money
import `in`.localrush.partner.feature.dashboard.vm.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onOpenOrders: () -> Unit,
    onOpenFinance: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var closureSheet by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        topBar = {
            PartnerTopBar(
                title = (state as? `in`.localrush.core.common.util.UiState.Success)?.data?.restaurant?.name ?: stringResource(R.string.dashboard_title),
                actions = { IconButton(onClick = onOpenNotifications) { Icon(Icons.Outlined.Notifications, contentDescription = stringResource(R.string.notifications_title)) } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        StateContent(state = state, online = online, onRetry = viewModel::load, modifier = Modifier.padding(padding)) { data ->
            val d = data.dashboard
            val r = data.restaurant
            PullToRefreshBox(isRefreshing = data.refreshing, onRefresh = viewModel::load) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LRCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LRImage(r?.logo, contentDescription = null, modifier = Modifier.size(52.dp), radius = 14.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(if (d.isOpenNow) R.string.dashboard_open_now else R.string.dashboard_closed_now), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    when {
                                        !d.isOpen -> stringResource(R.string.dashboard_switch_hint_closed)
                                        !d.isAcceptingOrders -> stringResource(R.string.dashboard_not_accepting)
                                        r?.temporarilyClosedUntil?.toInstantOrNull()?.let { it.isAfter(java.time.Instant.now()) } == true -> stringResource(R.string.dashboard_temp_closed_until, DateTimeUtils.formatRelativeDay(r.temporarilyClosedUntil.toInstantOrNull()!!))
                                        !d.isOpenNow -> stringResource(R.string.dashboard_outside_hours)
                                        else -> stringResource(R.string.dashboard_accepting)
                                    },
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            StatusChip(stringResource(if (d.isOpenNow) R.string.dashboard_chip_open else R.string.dashboard_chip_closed), if (d.isOpenNow) ChipTone.Success else ChipTone.Error, dot = true)
                        }
                        Spacer(Modifier.height(8.dp))
                        LabeledSwitch(stringResource(R.string.dashboard_switch_open), d.isOpen, viewModel::setOpen, enabled = !data.toggling)
                        LabeledSwitch(stringResource(R.string.dashboard_switch_accepting), d.isAcceptingOrders, viewModel::setAccepting, enabled = !data.toggling && d.isOpen)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LRButton(stringResource(R.string.dashboard_temp_close), onClick = { closureSheet = true }, modifier = Modifier.weight(1f), style = LRButtonStyle.Outlined, enabled = !data.toggling)
                            LRButton(stringResource(R.string.settings_title), onClick = onOpenSettings, modifier = Modifier.weight(1f), style = LRButtonStyle.Text)
                        }
                    }

                    SectionTitle(stringResource(R.string.dashboard_today)) { LRButton(stringResource(R.string.dashboard_view_orders), onClick = onOpenOrders, style = LRButtonStyle.Text) }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(stringResource(R.string.dashboard_orders), d.today.orders.toString(), Modifier.weight(1f))
                        StatTile(stringResource(R.string.dashboard_revenue), MoneyFormatter.compact(Money.parse(d.today.revenue)), Modifier.weight(1f))
                        StatTile(stringResource(R.string.dashboard_aov), MoneyFormatter.compact(Money.parse(d.today.avgOrderValue)), Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(stringResource(R.string.dashboard_completed), d.today.completed.toString(), Modifier.weight(1f))
                        StatTile(stringResource(R.string.dashboard_pending), d.today.pending.toString(), Modifier.weight(1f), highlight = d.today.new > 0)
                        StatTile(stringResource(R.string.dashboard_cancelled), d.today.cancelled.toString(), Modifier.weight(1f))
                    }
                    LRCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.dashboard_rating), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(stringResource(R.string.dashboard_rating_count, d.ratingCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            RatingBadge(d.ratingAvg.toDoubleOrNull() ?: 0.0)
                        }
                        Spacer(Modifier.height(8.dp))
                        KeyValueRow(stringResource(R.string.dashboard_week), stringResource(R.string.dashboard_period_value, d.week.orders, d.week.revenue.money()))
                        KeyValueRow(stringResource(R.string.dashboard_month), stringResource(R.string.dashboard_period_value, d.month.orders, d.month.revenue.money()))
                        KeyValueRow(stringResource(R.string.dashboard_total_orders), d.totalOrders.toString())
                        KeyValueRow(stringResource(R.string.dashboard_commission), stringResource(R.string.percent_value, d.commissionPercent))
                    }
                    LRCard(onClick = onOpenFinance) {
                        SectionTitle(stringResource(R.string.dashboard_payouts))
                        KeyValueRow(stringResource(R.string.dashboard_unsettled), d.unsettledAmount.money(), emphasize = true)
                        d.lastSettlement?.let { ls ->
                            KeyValueRow(stringResource(R.string.dashboard_last_settlement), stringResource(R.string.dashboard_last_settlement_value, ls.amount.money(), ls.status.replaceFirstChar { it.uppercase() }, ls.periodEnd ?: ""))
                        } ?: Text(stringResource(R.string.dashboard_no_settlement), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (data.bestSellers.isNotEmpty()) {
                        SectionTitle(stringResource(R.string.dashboard_best_sellers))
                        LRCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                            data.bestSellers.take(5).forEach { item ->
                                Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    VegNonVegDot(item.foodType)
                                    Spacer(Modifier.width(8.dp))
                                    Text(item.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    Text(stringResource(R.string.dashboard_sold_count, item.orderCount), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
            if (closureSheet) {
                LRBottomSheet(onDismiss = { closureSheet = false }, title = stringResource(R.string.dashboard_temp_close)) {
                    Text(stringResource(R.string.dashboard_temp_close_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    listOf(30, 60, 120, 240).forEach { m ->
                        LRButton(stringResource(R.string.dashboard_close_for_minutes, m), onClick = { viewModel.temporaryClosure(m); closureSheet = false }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), style = LRButtonStyle.Outlined)
                    }
                    if (r?.temporarilyClosedUntil != null) {
                        LRButton(stringResource(R.string.dashboard_reopen_now), onClick = { viewModel.temporaryClosure(null); closureSheet = false }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
    LRCard(modifier = modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}
