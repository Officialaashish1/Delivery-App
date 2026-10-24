package `in`.localrush.rider.feature.offer.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.localrush.core.common.geo.Geo
import `in`.localrush.core.common.money.Money
import `in`.localrush.core.designsystem.component.ErrorState
import `in`.localrush.core.designsystem.component.InlineError
import `in`.localrush.core.designsystem.component.LRBottomSheet
import `in`.localrush.core.designsystem.component.LRButton
import `in`.localrush.core.designsystem.component.LRButtonStyle
import `in`.localrush.core.designsystem.component.LRCard
import `in`.localrush.core.designsystem.component.LoadingState
import `in`.localrush.core.designsystem.component.EmptyState
import `in`.localrush.core.designsystem.theme.LRTheme
import `in`.localrush.rider.R
import `in`.localrush.rider.data.RejectReason
import `in`.localrush.rider.data.RiderOrderDto
import `in`.localrush.rider.feature.offer.vm.GoneReason
import `in`.localrush.rider.feature.offer.vm.OfferUiState
import `in`.localrush.rider.feature.offer.vm.OfferViewModel
import `in`.localrush.rider.ui.common.IconLabel
import `in`.localrush.rider.ui.common.rupees

@Composable
fun OfferScreen(
    assignmentId: String,
    onAccepted: (orderId: String) -> Unit,
    onClose: () -> Unit,
    viewModel: OfferViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(assignmentId) { viewModel.load(assignmentId) }
    LaunchedEffect(state) {
        when (val s = state) {
            is OfferUiState.Accepted -> onAccepted(s.order.id)
            OfferUiState.Rejected -> onClose()
            else -> Unit
        }
    }
    val context = LocalContext.current
    // Sound + vibration while the offer is pending (full-screen alerts must be noticed on a moving bike).
    DisposableEffect(state is OfferUiState.Ready) {
        val alert = if (state is OfferUiState.Ready) OfferAlert.start(context) else null
        onDispose { alert?.stop() }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (val s = state) {
            OfferUiState.Loading -> LoadingState()
            is OfferUiState.Error -> ErrorState(s.error, onRetry = viewModel::retry)
            is OfferUiState.Gone -> EmptyState(
                title = stringResource(if (s.reason == GoneReason.TIMED_OUT) R.string.offer_expired_title else R.string.offer_unavailable_title),
                description = stringResource(R.string.offer_gone_body),
                actionText = stringResource(R.string.action_close), onAction = onClose,
            )
            is OfferUiState.Ready -> OfferContent(s, viewModel)
            is OfferUiState.Accepted, OfferUiState.Rejected -> LoadingState()
        }
    }
}

@Composable
private fun OfferContent(s: OfferUiState.Ready, vm: OfferViewModel) {
    val order = s.offer.order
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.offer_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { s.progress }, modifier = Modifier.size(132.dp), strokeWidth = 10.dp, color = if (s.secondsLeft <= 10) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.secondsLeft.toString(), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black)
                    Text(stringResource(R.string.offer_seconds), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(Money.parse(order?.riderPayout).format(), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.offer_payout_label), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            LRCard(Modifier.fillMaxWidth(), large = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Restaurant, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(order?.restaurantName ?: "", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(order?.restaurantAddressText ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                    }
                    Text(s.offer.distanceToRestaurantKm?.toDoubleOrNull()?.let { Geo.formatDistanceKm(it) } ?: "—", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Place, contentDescription = null, tint = LRTheme.colors.success)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.offer_customer), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(order?.deliveryAddressText ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                    }
                    Text(order?.distanceKm?.toDoubleOrNull()?.let { Geo.formatDistanceKm(it) } ?: "—", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(12.dp))
            if (order != null) OrderItemsCard(order)
            s.actionError?.let { Spacer(Modifier.height(12.dp)); InlineError(it.message) }
            Spacer(Modifier.height(16.dp))
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LRButton(stringResource(R.string.offer_reject), onClick = vm::openReject, modifier = Modifier.weight(1f).height(60.dp), style = LRButtonStyle.Outlined, enabled = !s.busy, loading = s.rejecting)
            LRButton(stringResource(R.string.offer_accept), onClick = vm::accept, modifier = Modifier.weight(2f).height(60.dp), enabled = !s.busy, loading = s.accepting)
        }
    }
    if (s.showRejectSheet) RejectSheet(onDismiss = vm::closeReject, onReject = vm::reject)
}

@Composable
fun OrderItemsCard(order: RiderOrderDto) {
    LRCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Route, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.offer_items, order.items.sumOf { it.quantity }), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.order_number, order.orderNumber), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        order.items.take(6).forEach { item ->
            Text("${item.quantity} × ${item.name}${if (item.variantName.isNotBlank()) " (${item.variantName})" else ""}", style = MaterialTheme.typography.bodyMedium)
        }
        if (order.items.size > 6) Text(stringResource(R.string.offer_more_items, order.items.size - 6), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val collect = Money.parse(order.collectAmount)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().background(if (collect.isZero) LRTheme.colors.successContainer else LRTheme.colors.warningContainer, RoundedCornerShape(10.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Payments, contentDescription = null, tint = if (collect.isZero) LRTheme.colors.success else LRTheme.colors.warning)
            Spacer(Modifier.width(8.dp))
            Text(
                if (collect.isZero) stringResource(R.string.offer_prepaid) else stringResource(R.string.offer_collect_cod, collect.format()),
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RejectSheet(onDismiss: () -> Unit, onReject: (String) -> Unit) {
    LRBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.offer_reject_title)) {
        RejectReason.entries.forEach { r ->
            LRButton(rejectReasonLabel(r), onClick = { onReject(r.wire) }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), style = LRButtonStyle.Outlined)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun rejectReasonLabel(r: RejectReason): String = stringResource(
    when (r) {
        RejectReason.TOO_FAR -> R.string.reject_too_far
        RejectReason.LOW_PAYOUT -> R.string.reject_low_payout
        RejectReason.WRONG_DIRECTION -> R.string.reject_wrong_direction
        RejectReason.TAKING_BREAK -> R.string.reject_taking_break
        RejectReason.VEHICLE_ISSUE -> R.string.reject_vehicle_issue
        RejectReason.OTHER -> R.string.reject_other
    },
)

/** Plays the notification ringtone in a loop-ish pattern and vibrates until stopped. */
private class OfferAlert(private val ringtone: android.media.Ringtone?, private val vibrator: Vibrator?) {
    fun stop() {
        runCatching { ringtone?.stop() }
        runCatching { vibrator?.cancel() }
    }

    companion object {
        fun start(context: Context): OfferAlert {
            val ringtone = runCatching {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                RingtoneManager.getRingtone(context, uri)?.apply {
                    audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                    if (Build.VERSION.SDK_INT >= 28) isLooping = true
                    play()
                }
            }.getOrNull()
            val vibrator = runCatching {
                val v = if (Build.VERSION.SDK_INT >= 31) (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
                else @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                val pattern = longArrayOf(0, 500, 300, 500, 1200)
                if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(pattern, 0)) else @Suppress("DEPRECATION") v.vibrate(pattern, 0)
                v
            }.getOrNull()
            return OfferAlert(ringtone, vibrator)
        }
    }
}

