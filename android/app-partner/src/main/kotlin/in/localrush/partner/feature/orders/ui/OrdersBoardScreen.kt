package `in`.localrush.partner.feature.orders.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.localrush.core.designsystem.component.ChipTone
import `in`.localrush.core.designsystem.component.EmptyState
import `in`.localrush.core.designsystem.component.ErrorState
import `in`.localrush.core.designsystem.component.OfflineBanner
import `in`.localrush.core.designsystem.component.StatusChip
import `in`.localrush.partner.R
import `in`.localrush.partner.feature.common.ui.PartnerTopBar
import `in`.localrush.partner.feature.common.ui.ReasonSheet
import `in`.localrush.partner.feature.common.ui.displayMessage
import `in`.localrush.partner.feature.orders.vm.OrderAction
import `in`.localrush.partner.feature.orders.vm.OrderTab
import `in`.localrush.partner.feature.orders.vm.OrdersEvent
import `in`.localrush.partner.feature.orders.vm.OrdersViewModel

/** The live orders board: New / Preparing / Ready / Past. Two columns on tablets. */
@Composable
private fun RingingRow(onSilence: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.orders_ringing), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.weight(1f))
        TextButton(onClick = onSilence) { Text(stringResource(R.string.action_silence)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersBoardScreen(onOpenOrder: (String) -> Unit, viewModel: OrdersViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var acceptFor by rememberSaveable { mutableStateOf<String?>(null) }
    var rejectFor by rememberSaveable { mutableStateOf<String?>(null) }
    val newOrderMsg = stringResource(R.string.orders_new_order_arrived)

    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                is OrdersEvent.Error -> snackbar.showSnackbar(e.error.displayMessage())
                is OrdersEvent.Message -> snackbar.showSnackbar(e.text)
                is OrdersEvent.NewOrder -> if (state.selectedTab != OrderTab.NEW) snackbar.showSnackbar(newOrderMsg)
            }
        }
    }

    val tabTitles = stringArrayResource(R.array.order_tabs)
    Scaffold(
        topBar = {
            PartnerTopBar(
                title = stringResource(R.string.orders_title),
                actions = {
                    StatusChip(
                        stringResource(if (state.wsLive) R.string.orders_live else R.string.orders_reconnecting),
                        if (state.wsLive) ChipTone.Success else ChipTone.Warning, dot = true,
                    )
                    IconButton(onClick = viewModel::refresh) { Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.action_refresh)) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OfflineBanner(visible = !state.online, text = stringResource(R.string.offline_message))
            if (state.board.alertActive) {
                if (state.selectedTab != OrderTab.NEW) NewOrderBanner(state.board.unacknowledged.size, onOpen = { viewModel.selectTab(OrderTab.NEW) })
                else RingingRow(onSilence = viewModel::acknowledgeAll)
            }
            TabRow(selectedTabIndex = state.selectedTab.ordinal) {
                OrderTab.entries.forEach { tab ->
                    val count = state.board.count(tab)
                    Tab(
                        selected = state.selectedTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = {
                            BadgedBox(badge = { if (tab != OrderTab.PAST && count > 0) Badge { Text(count.toString()) } }) {
                                Text(tabTitles.getOrElse(tab.ordinal) { tab.name }, maxLines = 1)
                            }
                        },
                    )
                }
            }
            val refreshing = state.selectedTab in state.refreshing
            if (refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
            val orders = state.board.ordersIn(state.selectedTab)
            val error = state.errors[state.selectedTab]
            val loaded = state.selectedTab in state.board.loadedTabs
            PullToRefreshBox(isRefreshing = refreshing, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
                when {
                    !loaded && error != null -> ErrorState(error, onRetry = viewModel::refresh)
                    !loaded -> Box(Modifier.fillMaxSize())
                    orders.isEmpty() -> EmptyState(
                        title = stringResource(
                            when (state.selectedTab) {
                                OrderTab.NEW -> R.string.orders_empty_new
                                OrderTab.PREPARING -> R.string.orders_empty_preparing
                                OrderTab.READY -> R.string.orders_empty_ready
                                OrderTab.PAST -> R.string.orders_empty_past
                            },
                        ),
                        description = if (error != null) error.message else stringResource(R.string.orders_empty_hint),
                        icon = Icons.Outlined.RestaurantMenu,
                        actionText = if (error != null) stringResource(R.string.action_retry) else null,
                        onAction = if (error != null) ({ viewModel.refresh() }) else null,
                    )
                    else -> {
                        val wide = LocalConfiguration.current.screenWidthDp >= 720
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(if (wide) 2 else 1),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(orders, key = { it.id }) { order ->
                                OrderCard(
                                    order = order,
                                    now = state.now,
                                    pendingAction = state.pending[order.id],
                                    highlight = order.id in state.board.unacknowledged,
                                    onOpen = { viewModel.acknowledge(order.id); onOpenOrder(order.id) },
                                    onAccept = { acceptFor = order.id },
                                    onReject = { rejectFor = order.id },
                                    onPreparing = { viewModel.markPreparing(order.id) },
                                    onReady = { viewModel.markReady(order.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    acceptFor?.let { id ->
        val order = state.board.orders[id]
        AcceptSheet(
            defaultMinutes = order?.preparationMinutes ?: 20,
            loading = state.pending[id] == OrderAction.ACCEPT,
            onConfirm = { m -> viewModel.accept(id, m); acceptFor = null },
            onDismiss = { acceptFor = null },
        )
    }
    rejectFor?.let { id ->
        ReasonSheet(
            title = stringResource(R.string.reject_title),
            confirmText = stringResource(R.string.action_reject),
            presets = stringArrayResource(R.array.reject_reasons).toList(),
            loading = state.pending[id] == OrderAction.REJECT,
            onConfirm = { reason -> viewModel.reject(id, reason); rejectFor = null },
            onDismiss = { rejectFor = null },
        )
    }
}
