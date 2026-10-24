package `in`.localrush.partner.feature.orders.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.localrush.core.common.order.OrderStatus
import `in`.localrush.core.common.time.DateTimeUtils
import `in`.localrush.core.common.time.toInstantOrNull
import `in`.localrush.core.designsystem.component.ChipTone
import `in`.localrush.core.designsystem.component.LRButton
import `in`.localrush.core.designsystem.component.LRButtonStyle
import `in`.localrush.core.designsystem.component.LRCard
import `in`.localrush.core.designsystem.component.StatusChip
import `in`.localrush.partner.R
import `in`.localrush.partner.feature.common.ui.KeyValueRow
import `in`.localrush.partner.feature.common.ui.PartnerTopBar
import `in`.localrush.partner.feature.common.ui.ReasonSheet
import `in`.localrush.partner.feature.common.ui.SectionTitle
import `in`.localrush.partner.feature.common.ui.StateContent
import `in`.localrush.partner.feature.common.ui.displayMessage
import `in`.localrush.partner.feature.common.ui.money
import `in`.localrush.partner.feature.orders.vm.OrderAction
import `in`.localrush.partner.feature.orders.vm.OrderDetailViewModel
import `in`.localrush.partner.feature.orders.vm.OrdersEvent

@Composable
fun OrderDetailScreen(onBack: () -> Unit, viewModel: OrderDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var accept by rememberSaveable { mutableStateOf(false) }
    var reject by rememberSaveable { mutableStateOf(false) }
    var cancel by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.events.collect { e -> if (e is OrdersEvent.Error) snackbar.showSnackbar(e.error.displayMessage()) } }

    Scaffold(topBar = { PartnerTopBar(stringResource(R.string.order_detail_title), onBack = onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        StateContent(state = state, online = online, onRetry = viewModel::load, modifier = Modifier.padding(padding)) { data ->
            val order = data.order
            val status = OrderStatus.fromWire(order.status)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LRCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.order_number, order.orderNumber), style = MaterialTheme.typography.headlineSmall)
                            Text(order.customerName.ifBlank { stringResource(R.string.order_customer) }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (status == OrderStatus.PLACED) WaitingTimer(waitingLabel(order, data.now), urgent = false) else StatusChip(status, statusLabel(status, order.statusDisplay))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusChip(paymentLabel(order), if (order.paymentMethod == "cod") ChipTone.Warning else ChipTone.Success)
                        if (status != OrderStatus.PLACED) StatusChip(stringResource(R.string.order_prep_minutes, order.preparationMinutes), ChipTone.Neutral)
                    }
                    if (order.customerNote.isNotBlank()) { Spacer(Modifier.height(10.dp)); NoteBox(order.customerNote) }
                }
                if (status == OrderStatus.READY_FOR_PICKUP || status == OrderStatus.RIDER_ASSIGNED || status == OrderStatus.PREPARING || status == OrderStatus.RESTAURANT_ACCEPTED) {
                    RiderCard(order.rider, status, order.pickupOtp)
                }
                LRCard {
                    SectionTitle(stringResource(R.string.order_items_title))
                    order.items.forEach { ItemLine(it, showPrice = true) }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    KeyValueRow(stringResource(R.string.order_item_total), order.itemTotal.money())
                    KeyValueRow(stringResource(R.string.order_packaging), order.packagingCharge.money())
                    if (order.couponDiscount.money() != "₹0") KeyValueRow(stringResource(R.string.order_coupon_discount), "-${order.couponDiscount.money()}")
                    KeyValueRow(stringResource(R.string.order_total), order.totalAmount.money(), emphasize = true)
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    KeyValueRow(stringResource(R.string.order_commission), "-${order.commissionAmount.money()}")
                    KeyValueRow(stringResource(R.string.order_payout), order.restaurantPayout.money(), emphasize = true)
                }
                LRCard {
                    SectionTitle(stringResource(R.string.order_history_title))
                    TimelineRow(stringResource(R.string.timeline_placed), order.placedAt ?: order.createdAt)
                    TimelineRow(stringResource(R.string.timeline_accepted), order.acceptedAt)
                    TimelineRow(stringResource(R.string.timeline_ready), order.readyAt)
                    TimelineRow(stringResource(R.string.timeline_picked_up), order.pickedUpAt)
                    TimelineRow(stringResource(R.string.timeline_delivered), order.deliveredAt)
                    order.estimatedDeliveryAt?.toInstantOrNull()?.let { KeyValueRow(stringResource(R.string.timeline_eta), DateTimeUtils.formatRelativeDay(it)) }
                    if (order.rejectionReason.isNotBlank()) KeyValueRow(stringResource(R.string.order_rejection_reason), order.rejectionReason)
                    if (order.cancellationReason.isNotBlank()) KeyValueRow(stringResource(R.string.order_cancellation_reason, order.cancelledBy), order.cancellationReason)
                }
                OrderActions(status, data.action, onAccept = { accept = true }, onReject = { reject = true }, onPreparing = viewModel::markPreparing, onReady = viewModel::markReady)
                if (status == OrderStatus.RESTAURANT_ACCEPTED || status == OrderStatus.PREPARING || status == OrderStatus.READY_FOR_PICKUP) {
                    LRButton(stringResource(R.string.action_cancel_order), onClick = { cancel = true }, modifier = Modifier.fillMaxWidth(), style = LRButtonStyle.Text, loading = data.action == OrderAction.CANCEL)
                }
                Spacer(Modifier.height(24.dp))
            }
            if (accept) AcceptSheet(order.preparationMinutes, data.action == OrderAction.ACCEPT, onConfirm = { viewModel.accept(it); accept = false }, onDismiss = { accept = false })
            if (reject) ReasonSheet(stringResource(R.string.reject_title), stringResource(R.string.action_reject), stringArrayResource(R.array.reject_reasons).toList(), data.action == OrderAction.REJECT, onConfirm = { viewModel.reject(it); reject = false }, onDismiss = { reject = false })
            if (cancel) ReasonSheet(stringResource(R.string.cancel_order_title), stringResource(R.string.action_cancel_order), stringArrayResource(R.array.cancel_reasons).toList(), data.action == OrderAction.CANCEL, onConfirm = { viewModel.cancel(it); cancel = false }, onDismiss = { cancel = false })
        }
    }
}

@Composable
private fun TimelineRow(label: String, iso: String?) {
    val at = iso?.toInstantOrNull() ?: return
    KeyValueRow(label, DateTimeUtils.formatRelativeDay(at))
}
