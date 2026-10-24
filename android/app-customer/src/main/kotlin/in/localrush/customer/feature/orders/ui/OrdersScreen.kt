package `in`.localrush.customer.feature.orders.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.localrush.core.common.money.toMoney
import `in`.localrush.core.common.order.OrderStatus
import `in`.localrush.core.common.time.DateTimeUtils
import `in`.localrush.core.common.time.toInstantOrNull
import `in`.localrush.core.designsystem.component.EmptyState
import `in`.localrush.core.designsystem.component.ErrorState
import `in`.localrush.core.designsystem.component.LRButton
import `in`.localrush.core.designsystem.component.LRButtonStyle
import `in`.localrush.core.designsystem.component.LRCard
import `in`.localrush.core.designsystem.component.LRImage
import `in`.localrush.core.designsystem.component.ShimmerListPlaceholder
import `in`.localrush.core.designsystem.component.StatusChip
import `in`.localrush.customer.R
import `in`.localrush.customer.feature.orders.vm.OrdersScope
import `in`.localrush.customer.feature.orders.vm.OrdersViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(onOrder: (String) -> Unit, onTrack: (String) -> Unit, onLogin: () -> Unit, onBrowse: () -> Unit, viewModel: OrdersViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) { viewModel.refresh(); onPauseOrDispose { } }
    val listState = rememberLazyListState()
    val nearEnd by remember { derivedStateOf { val info = listState.layoutInfo; (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 3 } }
    LaunchedEffect(nearEnd, state.scope) { if (nearEnd) viewModel.loadMore() }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Text(stringResource(R.string.orders_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp))
        if (!state.loggedIn) {
            EmptyState(stringResource(R.string.orders_login_title), description = stringResource(R.string.orders_login_body), icon = Icons.AutoMirrored.Outlined.ReceiptLong, actionText = stringResource(R.string.action_login), onAction = onLogin)
            return
        }
        TabRow(selectedTabIndex = state.scope.ordinal, containerColor = MaterialTheme.colorScheme.background) {
            Tab(state.scope == OrdersScope.ACTIVE, { viewModel.selectScope(OrdersScope.ACTIVE) }, text = { Text(stringResource(R.string.orders_tab_active)) })
            Tab(state.scope == OrdersScope.PAST, { viewModel.selectScope(OrdersScope.PAST) }, text = { Text(stringResource(R.string.orders_tab_past)) })
        }
        val tab = state.current
        PullToRefreshBox(isRefreshing = tab.refreshing, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
            when {
                tab.loading -> ShimmerListPlaceholder(5, Modifier.padding(top = 16.dp))
                tab.error != null && tab.items.isEmpty() -> ErrorState(tab.error!!, onRetry = viewModel::retry)
                tab.items.isEmpty() -> EmptyState(
                    stringResource(if (state.scope == OrdersScope.ACTIVE) R.string.orders_empty_active else R.string.orders_empty_past),
                    description = stringResource(R.string.orders_empty_body), icon = Icons.AutoMirrored.Outlined.ReceiptLong, actionText = stringResource(R.string.cart_browse), onAction = onBrowse,
                )
                else -> LazyColumn(state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(tab.items, key = { it.id }) { o ->
                        val status = OrderStatus.fromWire(o.status)
                        LRCard(onClick = { onOrder(o.id) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LRImage(o.restaurantLogo, o.restaurantName, Modifier.size(48.dp), radius = 12.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(o.restaurantName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(stringResource(R.string.order_number, o.orderNumber), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                StatusChip(status, o.statusDisplay.ifBlank { null })
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(o.itemsSummary, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text((o.placedAt ?: o.createdAt).toInstantOrNull()?.let { DateTimeUtils.formatRelativeDay(it) } ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                Text(o.totalAmount.toMoney().format(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            }
                            if (status.isActive && status != OrderStatus.PENDING_PAYMENT) {
                                Spacer(Modifier.height(10.dp))
                                LRButton(stringResource(R.string.order_track), onClick = { onTrack(o.id) }, modifier = Modifier.fillMaxWidth().height(44.dp), style = LRButtonStyle.Outlined, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp))
                            }
                        }
                    }
                    if (tab.loadingMore) item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                }
            }
        }
    }
}
