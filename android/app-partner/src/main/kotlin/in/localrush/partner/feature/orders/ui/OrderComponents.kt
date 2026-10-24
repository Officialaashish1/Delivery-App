package `in`.localrush.partner.feature.orders.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TwoWheeler
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.localrush.core.common.order.OrderStatus
import `in`.localrush.core.common.time.DateTimeUtils
import `in`.localrush.core.common.time.toInstantOrNull
import `in`.localrush.core.designsystem.component.ChipTone
import `in`.localrush.core.designsystem.component.LRBottomSheet
import `in`.localrush.core.designsystem.component.LRButton
import `in`.localrush.core.designsystem.component.LRButtonStyle
import `in`.localrush.core.designsystem.component.LRCard
import `in`.localrush.core.designsystem.component.LRImage
import `in`.localrush.core.designsystem.component.StatusChip
import `in`.localrush.core.designsystem.component.VegNonVegDot
import `in`.localrush.core.designsystem.theme.LRTheme
import `in`.localrush.core.network.dto.OrderItemDto
import `in`.localrush.core.network.dto.RiderPublicDto
import `in`.localrush.partner.R
import `in`.localrush.partner.data.PartnerOrderDto
import `in`.localrush.partner.feature.common.ui.money
import `in`.localrush.partner.feature.orders.vm.OrderAction
import java.time.Duration
import java.time.Instant

/** "03:12" since the order was placed. */
fun waitingLabel(order: PartnerOrderDto, now: Instant): String {
    val placed = (order.placedAt ?: order.createdAt).toInstantOrNull() ?: return DateTimeUtils.countdown(order.timeSincePlacedS)
    return DateTimeUtils.countdown(Duration.between(placed, now).seconds)
}

@Composable
fun statusLabel(status: OrderStatus, fallback: String): String = when (status) {
    OrderStatus.PLACED -> stringResource(R.string.status_new)
    OrderStatus.RESTAURANT_ACCEPTED -> stringResource(R.string.status_accepted)
    OrderStatus.PREPARING -> stringResource(R.string.status_preparing)
    OrderStatus.READY_FOR_PICKUP -> stringResource(R.string.status_ready)
    OrderStatus.RIDER_ASSIGNED -> stringResource(R.string.status_rider_assigned)
    OrderStatus.PICKED_UP -> stringResource(R.string.status_picked_up)
    OrderStatus.OUT_FOR_DELIVERY, OrderStatus.NEAR_DESTINATION -> stringResource(R.string.status_on_the_way)
    OrderStatus.DELIVERED -> stringResource(R.string.status_delivered)
    OrderStatus.REJECTED -> stringResource(R.string.status_rejected)
    OrderStatus.CANCELLED -> stringResource(R.string.status_cancelled)
    OrderStatus.DELIVERY_FAILED -> stringResource(R.string.status_delivery_failed)
    else -> fallback
}

@Composable
fun paymentLabel(order: PartnerOrderDto): String = when {
    order.paymentMethod == "cod" -> stringResource(R.string.payment_cod)
    order.isPaid -> stringResource(R.string.payment_paid_online)
    else -> stringResource(R.string.payment_online_pending)
}

/** Order card used on the board. Shows the pieces relevant to the tab; detail is one tap away. */
@Composable
fun OrderCard(
    order: PartnerOrderDto,
    now: Instant,
    pendingAction: OrderAction?,
    highlight: Boolean,
    onOpen: () -> Unit,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onPreparing: () -> Unit,
    onReady: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = OrderStatus.fromWire(order.status)
    LRCard(modifier = modifier.fillMaxWidth(), onClick = onOpen, large = highlight) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.order_number, order.orderNumber), style = MaterialTheme.typography.titleMedium)
                Text(order.customerName.ifBlank { stringResource(R.string.order_customer) }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (status == OrderStatus.PLACED) WaitingTimer(waitingLabel(order, now), urgent = (order.placedAt ?: order.createdAt).toInstantOrNull()?.let { Duration.between(it, now).toMinutes() >= 3 } == true)
            else StatusChip(status, statusLabel(status, order.statusDisplay))
        }
        Spacer(Modifier.height(10.dp))
        order.items.take(4).forEach { ItemLine(it) }
        if (order.items.size > 4) Text(stringResource(R.string.order_more_items, order.items.size - 4), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (order.customerNote.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            NoteBox(order.customerNote)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(order.totalAmount.money(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            StatusChip(paymentLabel(order), if (order.paymentMethod == "cod") ChipTone.Warning else ChipTone.Success)
            Spacer(Modifier.weight(1f))
            if (status != OrderStatus.PLACED) Text(stringResource(R.string.order_prep_minutes, order.preparationMinutes), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (status == OrderStatus.READY_FOR_PICKUP || status == OrderStatus.RIDER_ASSIGNED) {
            Spacer(Modifier.height(10.dp))
            RiderCard(order.rider, status, order.pickupOtp, compact = true)
        }
        Spacer(Modifier.height(12.dp))
        OrderActions(status, pendingAction, onAccept, onReject, onPreparing, onReady)
    }
}

@Composable
fun OrderActions(status: OrderStatus, pending: OrderAction?, onAccept: () -> Unit, onReject: () -> Unit, onPreparing: () -> Unit, onReady: () -> Unit) {
    when (status) {
        OrderStatus.PLACED -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LRButton(stringResource(R.string.action_reject), onClick = onReject, modifier = Modifier.weight(1f), style = LRButtonStyle.Outlined, loading = pending == OrderAction.REJECT, enabled = pending == null)
            LRButton(stringResource(R.string.action_accept), onClick = onAccept, modifier = Modifier.weight(2f), loading = pending == OrderAction.ACCEPT, enabled = pending == null)
        }
        OrderStatus.RESTAURANT_ACCEPTED -> LRButton(stringResource(R.string.action_start_preparing), onClick = onPreparing, modifier = Modifier.fillMaxWidth(), style = LRButtonStyle.Secondary, loading = pending == OrderAction.PREPARING, enabled = pending == null)
        OrderStatus.PREPARING -> LRButton(stringResource(R.string.action_mark_ready), onClick = onReady, modifier = Modifier.fillMaxWidth(), loading = pending == OrderAction.READY, enabled = pending == null)
        else -> Unit
    }
}

@Composable
fun WaitingTimer(label: String, urgent: Boolean) {
    val color = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Row(
        Modifier.background(color.copy(alpha = 0.12f), RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Timer, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ItemLine(item: OrderItemDto, showPrice: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        VegNonVegDot(item.foodType, Modifier.padding(top = 3.dp))
        Spacer(Modifier.width(8.dp))
        Text("${item.quantity} ×", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            val extras = listOfNotNull(item.variantName.ifBlank { null }) + item.addonNames
            if (extras.isNotEmpty()) Text(extras.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (item.specialInstructions.isNotBlank()) Text(stringResource(R.string.order_item_note, item.specialInstructions), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        if (showPrice) Text(item.lineTotal.money(), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun NoteBox(note: String) {
    Text(
        stringResource(R.string.order_customer_note, note),
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.fillMaxWidth().background(LRTheme.colors.warningContainer, RoundedCornerShape(8.dp)).padding(8.dp),
    )
}

/** Rider arrival status + the pickup OTP shown big enough to read across the counter. */
@Composable
fun RiderCard(rider: RiderPublicDto?, status: OrderStatus, pickupOtp: String?, compact: Boolean = false) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp)).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (rider?.photo != null) LRImage(rider.photo, contentDescription = null, modifier = Modifier.size(40.dp), radius = 20.dp)
            else Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.TwoWheeler, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                if (rider == null) {
                    Text(stringResource(R.string.rider_searching), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.rider_searching_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(rider.fullName, style = MaterialTheme.typography.titleSmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = LRTheme.colors.star, modifier = Modifier.size(14.dp))
                        Text(" ${rider.ratingAvg} · ${rider.vehicleNumber}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(stringResource(if (status == OrderStatus.RIDER_ASSIGNED) R.string.rider_on_the_way else R.string.rider_assigned_waiting), style = MaterialTheme.typography.bodySmall, color = LRTheme.colors.success)
                }
            }
        }
        if (!pickupOtp.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.pickup_otp_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                pickupOtp.chunked(1).joinToString(" "),
                style = MaterialTheme.typography.displayMedium.copy(fontSize = if (compact) 32.sp else 44.sp, letterSpacing = 4.sp),
                fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.pickup_otp_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Accept sheet with a preparation-time picker (5–120 min, backend limits). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AcceptSheet(defaultMinutes: Int, loading: Boolean, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var minutes by rememberSaveable { mutableIntStateOf(defaultMinutes.coerceIn(5, 120)) }
    LRBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.accept_title)) {
        Text(stringResource(R.string.accept_prep_question), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.accept_minutes, minutes), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Slider(value = minutes.toFloat(), onValueChange = { minutes = (it / 5).toInt() * 5 }, valueRange = 5f..120f, steps = 22)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(10, 15, 20, 30, 45).forEach { m ->
                LRButton("$m", onClick = { minutes = m }, modifier = Modifier.weight(1f), style = if (m == minutes) LRButtonStyle.Secondary else LRButtonStyle.Outlined, contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        LRButton(stringResource(R.string.accept_confirm, minutes), onClick = { onConfirm(minutes) }, modifier = Modifier.fillMaxWidth(), loading = loading)
        Spacer(Modifier.height(8.dp))
    }
}

/** Live banner shown on every screen while a new order is unacknowledged. */
@Composable
fun NewOrderBanner(count: Int, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(androidx.compose.ui.res.pluralStringResource(R.plurals.banner_new_orders, count, count), style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text(stringResource(R.string.banner_new_orders_hint), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.9f))
        }
        LRButton(stringResource(R.string.action_view), onClick = onOpen, style = LRButtonStyle.Secondary, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp))
    }
}
