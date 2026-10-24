package `in`.localrush.partner.feature.finance.ui

import android.content.Intent
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDateRangePickerState
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
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.localrush.core.common.time.DateTimeUtils
import `in`.localrush.core.common.time.toInstantOrNull
import `in`.localrush.core.designsystem.component.ChipTone
import `in`.localrush.core.designsystem.component.EmptyState
import `in`.localrush.core.designsystem.component.LRButton
import `in`.localrush.core.designsystem.component.LRButtonStyle
import `in`.localrush.core.designsystem.component.LRCard
import `in`.localrush.core.designsystem.component.StatusChip
import `in`.localrush.partner.R
import `in`.localrush.partner.data.SettlementDto
import `in`.localrush.partner.data.SettlementLineDto
import `in`.localrush.partner.feature.common.ui.KeyValueRow
import `in`.localrush.partner.feature.common.ui.LabeledSwitch
import `in`.localrush.partner.feature.common.ui.PartnerTopBar
import `in`.localrush.partner.feature.common.ui.SectionTitle
import `in`.localrush.partner.feature.common.ui.StateContent
import `in`.localrush.partner.feature.common.ui.money
import `in`.localrush.partner.feature.dashboard.ui.StatTile
import `in`.localrush.partner.feature.finance.vm.DatePreset
import `in`.localrush.partner.feature.finance.vm.FinanceEvent
import `in`.localrush.partner.feature.finance.vm.FinanceLinesViewModel
import `in`.localrush.partner.feature.finance.vm.FinanceViewModel
import `in`.localrush.partner.feature.finance.vm.SettlementsViewModel
import java.io.File
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceScreen(onOpenLines: () -> Unit, onOpenSettlements: () -> Unit, viewModel: FinanceViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var customRange by rememberSaveable { mutableStateOf(false) }
    val shareTitle = stringResource(R.string.finance_share_statement)
    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                is FinanceEvent.Message -> snackbar.showSnackbar(e.text)
                is FinanceEvent.StatementReady -> shareCsv(context, e.file, shareTitle)
            }
        }
    }
    val presetLabels = stringArrayResource(R.array.finance_presets)

    Scaffold(topBar = { PartnerTopBar(stringResource(R.string.finance_title)) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        StateContent(state = state, online = online, onRetry = viewModel::load, modifier = Modifier.padding(padding)) { d ->
            PullToRefreshBox(isRefreshing = d.refreshing, onRefresh = viewModel::load) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(DatePreset.entries) { i, p ->
                            FilterChip(selected = d.preset == p, onClick = { if (p == DatePreset.CUSTOM) customRange = true else viewModel.setPreset(p) }, label = { Text(presetLabels.getOrElse(i) { p.name }) })
                        }
                    }
                    Text(
                        if (d.range.from == null) stringResource(R.string.finance_range_all) else stringResource(R.string.finance_range, DateTimeUtils.formatDate(d.range.from), DateTimeUtils.formatDate(d.range.to ?: d.range.from)),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val s = d.summary
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatTile(stringResource(R.string.finance_net_earnings), s.netEarnings.format(), Modifier.weight(1.4f), highlight = true)
                            StatTile(stringResource(R.string.dashboard_orders), s.orders.toString(), Modifier.weight(1f))
                        }
                        LRCard {
                            SectionTitle(stringResource(R.string.finance_breakdown))
                            KeyValueRow(stringResource(R.string.finance_gross_sales), s.grossSales.format())
                            KeyValueRow(stringResource(R.string.finance_commission_pct, String.format(java.util.Locale.ROOT, "%.1f", s.commissionPercentOfGross)), "-${s.commission.format()}")
                            KeyValueRow(stringResource(R.string.finance_coupon_deductions), "-${s.couponDeductions.format()}")
                            KeyValueRow(stringResource(R.string.finance_refund_deductions), "-${s.refundDeductions.format()}")
                            HorizontalDivider(Modifier.padding(vertical = 6.dp))
                            KeyValueRow(stringResource(R.string.finance_net_earnings), s.netEarnings.format(), emphasize = true)
                            KeyValueRow(stringResource(R.string.finance_tax_collected), s.taxCollected.format())
                            KeyValueRow(stringResource(R.string.dashboard_aov), s.averageOrderValue.format())
                        }
                        LRButton(stringResource(R.string.finance_download_csv), onClick = viewModel::downloadStatement, modifier = Modifier.fillMaxWidth(), style = LRButtonStyle.Outlined, leadingIcon = Icons.Outlined.Download, loading = d.downloading, enabled = online)
                        LRCard(onClick = onOpenLines) {
                            SectionTitle(stringResource(R.string.finance_unsettled_title)) { Text(stringResource(R.string.action_view_all), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge) }
                            val total = d.unsettledLines.fold(`in`.localrush.core.common.money.Money.ZERO) { acc, l -> acc + `in`.localrush.core.common.money.Money.parse(l.net) }
                            KeyValueRow(stringResource(R.string.finance_unsettled_amount), total.format(), emphasize = true)
                            Text(stringResource(R.string.finance_unsettled_count, d.unsettledLines.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        LRCard(onClick = onOpenSettlements) {
                            SectionTitle(stringResource(R.string.finance_settlements_title)) { Text(stringResource(R.string.action_view_all), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge) }
                            if (d.lastSettlement == null) Text(stringResource(R.string.dashboard_no_settlement), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            else SettlementRow(d.lastSettlement)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
    if (customRange) {
        val dr = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { customRange = false },
            confirmButton = {
                TextButton(onClick = {
                    val a = dr.selectedStartDateMillis; val b = dr.selectedEndDateMillis ?: a
                    if (a != null && b != null) viewModel.setCustomRange(Instant.ofEpochMilli(a).atZone(ZoneOffset.UTC).toLocalDate(), Instant.ofEpochMilli(b).atZone(ZoneOffset.UTC).toLocalDate())
                    customRange = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { customRange = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DateRangePicker(state = dr, modifier = Modifier.height(480.dp)) }
    }
}

private fun shareCsv(context: android.content.Context, file: File, title: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, file.name)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
fun SettlementRow(s: SettlementDto) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.finance_period, s.periodStart ?: "", s.periodEnd ?: ""), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.finance_settlement_orders, s.orderCount) + if (s.reference.isNotBlank()) " · ${s.reference}" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (s.note.isNotBlank()) Text(s.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(s.netPayable.money(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            StatusChip(s.status.replace('_', ' ').replaceFirstChar { it.uppercase() }, when (s.status) { "paid" -> ChipTone.Success; "failed", "on_hold" -> ChipTone.Error; "processing" -> ChipTone.Info; else -> ChipTone.Warning })
        }
    }
}

@Composable
fun FinanceLinesScreen(onBack: () -> Unit, viewModel: FinanceLinesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var unsettledOnly by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    Scaffold(topBar = { PartnerTopBar(stringResource(R.string.finance_lines_title), onBack = onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.padding(padding)) {
            LabeledSwitch(stringResource(R.string.finance_unsettled_only), unsettledOnly, { unsettledOnly = it; viewModel.setUnsettledOnly(it) }, Modifier.padding(horizontal = 16.dp))
            StateContent(state = state, online = online, onRetry = viewModel::load, empty = { EmptyState(stringResource(R.string.finance_lines_empty), description = stringResource(R.string.finance_lines_empty_hint)) }) { d ->
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(d.items, key = { _, it -> it.id }) { i, line ->
                        if (i >= d.items.size - 3) LaunchedEffect(d.items.size) { viewModel.loadMore() }
                        LineRow(line)
                    }
                    if (d.loadingMore) item { CircularProgressIndicator(Modifier.padding(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun LineRow(l: SettlementLineDto) {
    LRCard(contentPadding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.order_number, l.orderNumber), style = MaterialTheme.typography.titleSmall)
                l.deliveredAt?.toInstantOrNull()?.let { Text(DateTimeUtils.formatRelativeDay(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(l.net.money(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                StatusChip(stringResource(if (l.isCod) R.string.payment_cod else R.string.payment_paid_online), if (l.isCod) ChipTone.Warning else ChipTone.Success)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.finance_line_breakdown, l.gross.money(), l.commissionPercent, l.commission.money(), l.couponDeduction.money(), l.refundDeduction.money()),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun SettlementsScreen(onBack: () -> Unit, viewModel: SettlementsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    Scaffold(topBar = { PartnerTopBar(stringResource(R.string.finance_settlements_title), onBack = onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        StateContent(state = state, online = online, onRetry = viewModel::load, modifier = Modifier.padding(padding), empty = { EmptyState(stringResource(R.string.finance_settlements_empty), description = stringResource(R.string.finance_settlements_empty_hint)) }) { d ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(d.items, key = { _, it -> it.id }) { i, s ->
                    if (i >= d.items.size - 3) LaunchedEffect(d.items.size) { viewModel.loadMore() }
                    LRCard(contentPadding = PaddingValues(12.dp)) {
                        SettlementRow(s)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.finance_settlement_breakdown, s.grossSales.money(), s.commissionAmount.money(), s.couponDeductions.money(), s.refundDeductions.money(), s.adjustments.money()),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        s.processedAt?.toInstantOrNull()?.let { Text(stringResource(R.string.finance_processed_at, DateTimeUtils.formatDateTime(it)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                if (d.loadingMore) item { CircularProgressIndicator(Modifier.padding(16.dp)) }
            }
        }
    }
}
