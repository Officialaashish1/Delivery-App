package `in`.localrush.rider.feature.earnings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.localrush.core.common.money.Money
import `in`.localrush.core.designsystem.component.ChipTone
import `in`.localrush.core.designsystem.component.EmptyState
import `in`.localrush.core.designsystem.component.ErrorState
import `in`.localrush.core.designsystem.component.InlineError
import `in`.localrush.core.designsystem.component.LRBottomSheet
import `in`.localrush.core.designsystem.component.LRButton
import `in`.localrush.core.designsystem.component.LRButtonStyle
import `in`.localrush.core.designsystem.component.LRCard
import `in`.localrush.core.designsystem.component.LRTextField
import `in`.localrush.core.designsystem.component.ShimmerListPlaceholder
import `in`.localrush.core.designsystem.component.StatusChip
import `in`.localrush.core.designsystem.theme.LRTheme
import `in`.localrush.rider.R
import `in`.localrush.rider.data.EarningType
import `in`.localrush.rider.data.EarningsPeriod
import `in`.localrush.rider.data.PayoutStatus
import `in`.localrush.rider.data.RiderEarningDto
import `in`.localrush.rider.data.RiderPayoutDto
import `in`.localrush.rider.feature.earnings.vm.EarningsEvent
import `in`.localrush.rider.feature.earnings.vm.EarningsUiState
import `in`.localrush.rider.feature.earnings.vm.EarningsViewModel
import `in`.localrush.rider.ui.common.RiderTopBar
import `in`.localrush.rider.ui.common.SectionTitle
import `in`.localrush.rider.ui.common.StatTile
import `in`.localrush.rider.ui.common.TwoUp
import `in`.localrush.rider.ui.common.dateLabel
import `in`.localrush.rider.ui.common.relativeDay
import `in`.localrush.rider.ui.common.rupees

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EarningsScreen(onOpenPayouts: () -> Unit, onBack: (() -> Unit)? = null, viewModel: EarningsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                is EarningsEvent.PayoutRequested -> snackbar.showSnackbar(context.getString(R.string.payout_requested, e.amount.rupees()))
                is EarningsEvent.Error -> snackbar.showSnackbar(e.error.message)
            }
        }
    }
    Scaffold(topBar = { RiderTopBar(stringResource(R.string.earnings_title), onBack = onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = state.period.ordinal) {
                EarningsPeriod.entries.forEach { p ->
                    Tab(selected = state.period == p, onClick = { viewModel.selectPeriod(p) }, text = { Text(periodLabel(p)) })
                }
            }
            when {
                state.loading -> ShimmerListPlaceholder(count = 5, modifier = Modifier.padding(16.dp))
                state.error != null -> ErrorState(state.error!!, onRetry = viewModel::load)
                else -> PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { viewModel.load(); viewModel.loadPayouts() }) {
                    EarningsContent(state, viewModel, onOpenPayouts)
                }
            }
        }
    }
    if (state.showPayoutSheet) PayoutSheet(state, viewModel)
}

@Composable
private fun EarningsContent(state: EarningsUiState, vm: EarningsViewModel, onOpenPayouts: () -> Unit) {
    val d = state.data ?: return
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            TwoUp(
                left = { StatTile(stringResource(R.string.earnings_total), state.totals?.earnings.rupees(), accent = true) },
                right = { StatTile(stringResource(R.string.dash_deliveries), (state.totals?.deliveries ?: 0).toString()) },
            )
        }
        item {
            LRCard(Modifier.fillMaxWidth(), large = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.earnings_withdrawable), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(state.withdrawable.format(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.earnings_wallet_line, d.summary.walletBalance.rupees(), d.summary.codCashInHand.rupees()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    LRButton(stringResource(R.string.payout_request), onClick = vm::openPayoutSheet, enabled = !state.withdrawable.isZero)
                }
                if (Money.parse(d.summary.pendingPayout).paise > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.earnings_pending_payout, d.summary.pendingPayout.rupees()), style = MaterialTheme.typography.bodySmall, color = LRTheme.colors.warning)
                }
            }
        }
        if (Money.parse(d.summary.codCashInHand).paise > 0) {
            item {
                LRCard(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.earnings_cod_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.earnings_cod_body, d.summary.codCashInHand.rupees()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (d.summary.incentives.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.dash_incentives)) }
            items(d.summary.incentives, key = { it.name }) { inc ->
                LRCard(Modifier.fillMaxWidth()) {
                    Row {
                        Text(inc.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text("+${inc.bonusAmount.rupees()}", color = LRTheme.colors.success, fontWeight = FontWeight.Bold)
                    }
                    Text(stringResource(R.string.dash_incentive_progress, inc.progress, inc.deliveriesRequired), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            SectionTitle(stringResource(R.string.payouts_title)) {
                LRButton(stringResource(R.string.action_view_all), onClick = onOpenPayouts, style = LRButtonStyle.Text, trailingIcon = Icons.Filled.ChevronRight)
            }
            state.payouts.take(2).forEach { PayoutRow(it); Spacer(Modifier.height(8.dp)) }
            if (state.payouts.isEmpty() && !state.payoutsLoading) Text(stringResource(R.string.payouts_empty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { SectionTitle(stringResource(R.string.earnings_breakdown)) }
        if (state.entries.isEmpty()) {
            item { EmptyState(title = stringResource(R.string.earnings_empty), description = stringResource(R.string.earnings_empty_hint), modifier = Modifier.height(240.dp)) }
        } else {
            items(state.entries, key = { it.id }) { EarningRow(it) }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun EarningRow(e: RiderEarningDto) {
    val type = EarningType.fromWire(e.earningType)
    val amount = Money.parse(e.amount)
    LRCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(earningTypeLabel(type), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    listOfNotNull(e.orderNumber?.let { stringResource(R.string.order_number, it) }, e.description.takeIf { it.isNotBlank() && !it.startsWith("incentive:") }, e.createdAt.relativeDay()).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    (if (type.isDeduction || amount.isNegative) "−" else "+") + Money(kotlin.math.abs(amount.paise)).format(),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    color = if (type.isDeduction || amount.isNegative) MaterialTheme.colorScheme.error else LRTheme.colors.success,
                )
                if (e.isSettled) Text(stringResource(R.string.earnings_settled), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun PayoutRow(p: RiderPayoutDto) {
    val status = PayoutStatus.fromWire(p.status)
    LRCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.amount.rupees(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    listOfNotNull(p.createdAt.dateLabel(), p.reference.takeIf { it.isNotBlank() }?.let { stringResource(R.string.payout_ref, it) }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (p.note.isNotBlank()) Text(p.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusChip(
                payoutStatusLabel(status),
                when (status) {
                    PayoutStatus.PAID -> ChipTone.Success
                    PayoutStatus.FAILED, PayoutStatus.REJECTED -> ChipTone.Error
                    PayoutStatus.PROCESSING -> ChipTone.Info
                    else -> ChipTone.Warning
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PayoutSheet(state: EarningsUiState, vm: EarningsViewModel) {
    var amountText by rememberSaveable { mutableStateOf("") }
    val amount = Money.parseOrNull(amountText)
    val valid = amountText.isBlank() || (amount != null && amount.paise > 0 && amount <= state.withdrawable)
    LRBottomSheet(onDismiss = vm::closePayoutSheet, title = stringResource(R.string.payout_request)) {
        Column(Modifier.imePadding()) {
            Text(stringResource(R.string.payout_available, state.withdrawable.format()), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            LRTextField(
                amountText, { amountText = it.filter { c -> c.isDigit() || c == '.' } }, label = stringResource(R.string.payout_amount_label), prefix = "₹ ",
                helperText = stringResource(R.string.payout_amount_hint), errorText = if (!valid) stringResource(R.string.payout_amount_invalid) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            state.payoutRequestError?.let { Spacer(Modifier.height(12.dp)); InlineError(it.message) }
            Spacer(Modifier.height(16.dp))
            LRButton(
                stringResource(R.string.payout_confirm, (if (amountText.isBlank()) state.withdrawable else amount ?: Money.ZERO).format()),
                onClick = { vm.requestPayout(if (amountText.isBlank()) null else amount) }, modifier = Modifier.fillMaxWidth(), loading = state.requestingPayout, enabled = valid,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun periodLabel(p: EarningsPeriod): String = stringResource(
    when (p) {
        EarningsPeriod.TODAY -> R.string.period_today
        EarningsPeriod.WEEK -> R.string.period_week
        EarningsPeriod.MONTH -> R.string.period_month
        EarningsPeriod.ALL -> R.string.period_all
    },
)

@Composable
fun earningTypeLabel(t: EarningType): String = stringResource(
    when (t) {
        EarningType.DELIVERY -> R.string.earning_delivery
        EarningType.DISTANCE -> R.string.earning_distance
        EarningType.INCENTIVE -> R.string.earning_incentive
        EarningType.BONUS -> R.string.earning_bonus
        EarningType.TIP -> R.string.earning_tip
        EarningType.PENALTY -> R.string.earning_penalty
        EarningType.ADJUSTMENT -> R.string.earning_adjustment
        EarningType.COD_DEPOSIT -> R.string.earning_cod_deposit
        EarningType.UNKNOWN -> R.string.earning_other
    },
)

@Composable
fun payoutStatusLabel(s: PayoutStatus): String = stringResource(
    when (s) {
        PayoutStatus.REQUESTED -> R.string.payout_status_requested
        PayoutStatus.PROCESSING -> R.string.payout_status_processing
        PayoutStatus.PAID -> R.string.payout_status_paid
        PayoutStatus.FAILED -> R.string.payout_status_failed
        PayoutStatus.REJECTED -> R.string.payout_status_rejected
        PayoutStatus.UNKNOWN -> R.string.payout_status_requested
    },
)
