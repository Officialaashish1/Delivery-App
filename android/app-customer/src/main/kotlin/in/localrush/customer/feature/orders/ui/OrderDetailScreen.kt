package `in`.localrush.customer.feature.orders.ui

import android.app.Activity
import android.content.Intent
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.localrush.core.common.money.toMoney
import `in`.localrush.core.common.order.OrderStatus
import `in`.localrush.core.common.order.TimelineKey
import `in`.localrush.core.common.time.DateTimeUtils
import `in`.localrush.core.common.time.toInstantOrNull
import `in`.localrush.core.designsystem.component.ErrorState
import `in`.localrush.core.designsystem.component.InlineError
import `in`.localrush.core.designsystem.component.LRButton
import `in`.localrush.core.designsystem.component.LRButtonStyle
import `in`.localrush.core.designsystem.component.LRCard
import `in`.localrush.core.designsystem.component.LRImage
import `in`.localrush.core.designsystem.component.OrderStepProgressBar
import `in`.localrush.core.designsystem.component.ShimmerListPlaceholder
import `in`.localrush.core.designsystem.component.StatusChip
import `in`.localrush.core.designsystem.component.ChipTone
import `in`.localrush.core.designsystem.component.SuccessNote
import `in`.localrush.core.designsystem.component.VegNonVegDot
import `in`.localrush.core.designsystem.theme.LRTheme
import `in`.localrush.core.network.dto.OrderDetailDto
import `in`.localrush.customer.R
import `in`.localrush.customer.feature.orders.vm.OrderDetailEvent
import `in`.localrush.customer.feature.orders.vm.OrderDetailViewModel
import `in`.localrush.customer.ui.common.BillBreakdown
import `in`.localrush.customer.ui.common.LRTopBar
import `in`.localrush.customer.ui.payments.RazorpayLauncher

@Composable
fun OrderDetailScreen(onBack: () -> Unit, onTrack: (String) -> Unit, onCart: () -> Unit, onRate: (String) -> Unit, onHelp: (String) -> Unit, onRestaurant: (String) -> Unit, viewModel: OrderDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val noViewer = stringResource(R.string.order_invoice_no_app)
    val skippedPrefix = stringResource(R.string.order_reorder_skipped)
    val paymentUnavailable = stringResource(R.string.checkout_payment_unavailable)
    val paymentConfirmed = stringResource(R.string.order_payment_confirmed)
    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                is OrderDetailEvent.Message -> snackbar.showSnackbar(e.text)
                is OrderDetailEvent.ItemsSkipped -> snackbar.showSnackbar(skippedPrefix + " " + e.names.joinToString(", "))
                OrderDetailEvent.PaymentUnavailable -> snackbar.showSnackbar(paymentUnavailable)
                OrderDetailEvent.PaymentConfirmed -> snackbar.showSnackbar(paymentConfirmed)
                OrderDetailEvent.GoToCart -> onCart()
                is OrderDetailEvent.OpenInvoice -> {
                    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", e.file)
                    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "text/html").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    runCatching { context.startActivity(Intent.createChooser(intent, e.file.name)) }.onFailure { snackbar.showSnackbar(noViewer) }
                }
                is OrderDetailEvent.RetryPayment -> (context as? Activity)?.let { RazorpayLauncher.open(it, e.payload, state.order?.paymentMethod) }
            }
        }
    }

    Scaffold(
        topBar = { LRTopBar(state.order?.let { stringResource(R.string.order_number, it.orderNumber) } ?: stringResource(R.string.order_detail_title), onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> ShimmerListPlaceholder(4, Modifier.padding(top = 16.dp))
                state.error != null && state.order == null -> ErrorState(state.error!!, onRetry = viewModel::load)
                state.order != null -> Detail(state.order!!, state, viewModel, onTrack, onRate, onHelp, onRestaurant)
            }
        }
    }
    if (state.cancelDialog) CancelDialog(onDismiss = { viewModel.showCancel(false) }, onConfirm = viewModel::cancel)
}

@Composable
private fun Detail(o: OrderDetailDto, state: `in`.localrush.customer.feature.orders.vm.OrderDetailUiState, vm: OrderDetailViewModel, onTrack: (String) -> Unit, onRate: (String) -> Unit, onHelp: (String) -> Unit, onRestaurant: (String) -> Unit) {
    val status = OrderStatus.fromWire(o.status)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            LRCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LRImage(o.restaurantLogo, o.restaurantName, Modifier.size(48.dp), radius = 12.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(o.restaurantName, style = MaterialTheme.typography.titleMedium)
                        Text((o.placedAt ?: o.createdAt).toInstantOrNull()?.let { DateTimeUtils.formatRelativeDay(it) } ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    StatusChip(status, o.statusDisplay.ifBlank { null })
                }
                if (!status.isFailure && status != OrderStatus.PENDING_PAYMENT) { Spacer(Modifier.height(16.dp)); OrderStepProgressBar(status) }
                if (status.isFailure) {
                    Spacer(Modifier.height(12.dp))
                    InlineError(listOf(o.cancellationReason, o.rejectionReason).firstOrNull { it.isNotBlank() } ?: o.statusDisplay)
                }
                if (state.awaitingPayment) {
                    Spacer(Modifier.height(12.dp))
                    LRButton(stringResource(R.string.checkout_retry_payment), onClick = vm::retryPayment, modifier = Modifier.fillMaxWidth(), loading = state.payingAgain)
                }
                if (state.canTrack) {
                    Spacer(Modifier.height(12.dp))
                    LRButton(stringResource(R.string.order_track_live), onClick = { onTrack(o.id) }, modifier = Modifier.fillMaxWidth())
                }
                o.estimatedDeliveryAt.toInstantOrNull()?.takeIf { status.isActive }?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.order_eta_at, DateTimeUtils.formatTime(it)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item {
            LRCard {
                Text(stringResource(R.string.order_items_title), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                o.items.forEach { it ->
                    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                        VegNonVegDot(it.foodType, Modifier.padding(top = 3.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${it.quantity} × ${it.name}", style = MaterialTheme.typography.bodyLarge)
                            val extras = listOfNotNull(it.variantName.takeIf { v -> v.isNotBlank() }) + it.addonNames
                            if (extras.isNotEmpty()) Text(extras.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (it.specialInstructions.isNotBlank()) Text("“${it.specialInstructions}”", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(it.lineTotal.toMoney().format(), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                o.pricing?.let { HorizontalDivider(Modifier.padding(vertical = 8.dp)); BillBreakdown(it) }
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.order_paid_via, paymentLabel(o.paymentMethod), if (o.isPaid) stringResource(R.string.order_paid) else stringResource(R.string.order_unpaid)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        o.refundStatus?.let { r ->
            item {
                LRCard {
                    Text(stringResource(R.string.order_refund_title), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(r.amount.toMoney().format(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        StatusChip(r.status.replace('_', ' ').replaceFirstChar { it.uppercase() }, if (r.status == "processed" || r.status == "completed") ChipTone.Success else ChipTone.Warning)
                    }
                    r.processedAt.toInstantOrNull()?.let { Text(stringResource(R.string.order_refund_processed, DateTimeUtils.formatDateTime(it)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (!r.reason.isNullOrBlank()) Text(r.reason!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            LRCard {
                Text(stringResource(R.string.order_timeline_title), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                o.timeline.forEach { t ->
                    val key = TimelineKey.fromWire(t.key)
                    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(22.dp).background(if (t.done) LRTheme.colors.success else MaterialTheme.colorScheme.outlineVariant, CircleShape), contentAlignment = Alignment.Center) {
                            if (t.done) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(t.label.ifBlank { key?.wire ?: t.key }, style = MaterialTheme.typography.bodyMedium, fontWeight = if (t.current) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
                        t.at.toInstantOrNull()?.let { Text(DateTimeUtils.formatTime(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
        item {
            LRCard {
                Text(stringResource(R.string.order_delivery_title), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                Text(o.deliveryName, style = MaterialTheme.typography.bodyMedium)
                Text(o.deliveryAddressText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (o.deliveryInstructions.isNotBlank()) Text(stringResource(R.string.order_instructions, o.deliveryInstructions), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (o.isContactless) SuccessNote(stringResource(R.string.checkout_contactless), Modifier.padding(top = 6.dp))
                o.rider?.let { r ->
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LRImage(r.photo, r.fullName, Modifier.size(40.dp), radius = 20.dp)
                        Spacer(Modifier.width(10.dp))
                        Column { Text(r.fullName, style = MaterialTheme.typography.titleSmall); Text("★ ${r.ratingAvg}  ·  ${r.vehicleNumber}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.canRate) LRButton(stringResource(R.string.order_rate), onClick = { onRate(o.id) }, modifier = Modifier.fillMaxWidth(), leadingIcon = Icons.Outlined.StarOutline)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LRButton(stringResource(R.string.order_reorder), onClick = vm::reorder, modifier = Modifier.weight(1f), style = LRButtonStyle.Outlined, loading = state.reordering, leadingIcon = Icons.Outlined.Replay)
                    LRButton(stringResource(R.string.order_invoice), onClick = vm::invoice, modifier = Modifier.weight(1f), style = LRButtonStyle.Outlined, loading = state.invoiceLoading, leadingIcon = Icons.Outlined.Receipt)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LRButton(stringResource(R.string.order_help), onClick = { onHelp(o.id) }, modifier = Modifier.weight(1f), style = LRButtonStyle.Text, leadingIcon = Icons.AutoMirrored.Outlined.HelpOutline)
                    o.restaurantSlug?.let { TextButton(onClick = { onRestaurant(it) }, Modifier.weight(1f)) { Text(stringResource(R.string.order_view_restaurant)) } }
                }
                if (o.canCancel) LRButton(stringResource(R.string.order_cancel), onClick = { vm.showCancel(true) }, modifier = Modifier.fillMaxWidth(), style = LRButtonStyle.Text, loading = state.cancelling)
            }
        }
    }
}

@Composable
private fun paymentLabel(method: String) = stringResource(when (method) { "cod" -> R.string.pay_cod; "upi" -> R.string.pay_upi; "card" -> R.string.pay_card; "netbanking" -> R.string.pay_netbanking; else -> R.string.pay_online })

@Composable
fun CancelDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val reasons = listOf(R.string.cancel_reason_mistake, R.string.cancel_reason_long, R.string.cancel_reason_change_mind, R.string.cancel_reason_other).map { stringResource(it) }
    var selected by remember { mutableStateOf(reasons.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.order_cancel_title)) },
        text = {
            Column {
                Text(stringResource(R.string.order_cancel_body), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                reasons.forEach { r ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selected == r, onClick = { selected = r }); Text(r)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected) }) { Text(stringResource(R.string.order_cancel_confirm), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_keep_order)) } },
    )
}
