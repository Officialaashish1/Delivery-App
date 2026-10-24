package `in`.localrush.customer.feature.checkout.ui

import android.app.Activity
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.localrush.core.designsystem.component.InlineError
import `in`.localrush.core.designsystem.component.LRButton
import `in`.localrush.core.designsystem.component.LRCard
import `in`.localrush.core.designsystem.component.LRFooter
import `in`.localrush.core.designsystem.component.LRTextField
import `in`.localrush.core.designsystem.component.LoadingOverlay
import `in`.localrush.core.designsystem.component.LoadingState
import `in`.localrush.customer.R
import `in`.localrush.customer.feature.checkout.vm.CheckoutEvent
import `in`.localrush.customer.feature.checkout.vm.CheckoutStep
import `in`.localrush.customer.feature.checkout.vm.CheckoutViewModel
import `in`.localrush.customer.feature.checkout.vm.PaymentMethod
import `in`.localrush.customer.ui.common.BillBreakdown
import `in`.localrush.customer.ui.common.LRTopBar
import `in`.localrush.customer.ui.payments.RazorpayLauncher

@Composable
fun CheckoutScreen(onBack: () -> Unit, onAddAddress: () -> Unit, onPlaced: (orderId: String) -> Unit, onViewOrder: (orderId: String) -> Unit, viewModel: CheckoutViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                is CheckoutEvent.OpenRazorpay -> (context as? Activity)?.let { RazorpayLauncher.open(it, e.payload, state.method.wire) }
                is CheckoutEvent.OrderPlaced -> onPlaced(e.orderId)
                is CheckoutEvent.Message -> snackbar.showSnackbar(e.text)
            }
        }
    }

    Scaffold(
        topBar = { LRTopBar(stringResource(R.string.checkout_title), onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            LRFooter {
                Column {
                    state.error?.let { InlineError(it.message, Modifier.padding(bottom = 8.dp)) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(state.total.format(), style = MaterialTheme.typography.titleLarge)
                            Text(stringResource(R.string.cart_total_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        LRButton(
                            stringResource(if (state.method == PaymentMethod.COD) R.string.checkout_place_order else R.string.checkout_pay_and_place, state.total.format()),
                            onClick = viewModel::placeOrder, modifier = Modifier.weight(1.6f), enabled = state.canPlace, loading = state.step is CheckoutStep.Placing,
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val cart = state.cart
            if (cart == null) { LoadingState(); return@Box }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    LRCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.checkout_delivery_address), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            TextButton(onClick = onAddAddress) { Icon(Icons.Default.Add, null, Modifier.size(16.dp)); Text(stringResource(R.string.address_add)) }
                        }
                        val list = state.addresses
                        when {
                            list == null -> Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(22.dp)) }
                            list.isEmpty() -> Text(stringResource(R.string.address_none), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                            else -> list.forEach { a ->
                                Row(Modifier.fillMaxWidth().clickable { viewModel.selectAddress(a.id) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = state.selectedAddress?.id == a.id, onClick = null)
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(a.label.replaceFirstChar { it.uppercase() } + (a.contactName.takeIf { it.isNotBlank() }?.let { "  ·  $it" } ?: ""), style = MaterialTheme.typography.titleSmall)
                                        Text(a.formatted.ifBlank { listOf(a.addressLine1, a.addressLine2, a.city).filter { it.isNotBlank() }.joinToString(", ") }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                    if (state.addressBusy && state.selectedAddressId == a.id) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                }
                            }
                        }
                    }
                }
                item {
                    LRCard {
                        Text(stringResource(R.string.checkout_instructions_title), style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        LRTextField(state.instructions, viewModel::setInstructions, placeholder = stringResource(R.string.checkout_instructions_hint), singleLine = false, maxLines = 3)
                        Spacer(Modifier.height(8.dp))
                        LRTextField(state.note, viewModel::setNote, placeholder = stringResource(R.string.checkout_note_hint), singleLine = false, maxLines = 3)
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.checkout_contactless), style = MaterialTheme.typography.bodyLarge)
                                Text(stringResource(R.string.checkout_contactless_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(state.contactless, viewModel::setContactless)
                        }
                    }
                }
                item {
                    LRCard {
                        Text(stringResource(R.string.checkout_payment_title), style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        PaymentMethod.entries.forEach { m ->
                            val enabled = m in state.availableMethods
                            Row(Modifier.fillMaxWidth().clickable(enabled = enabled) { viewModel.selectMethod(m) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = state.method == m, onClick = null, enabled = enabled)
                                Spacer(Modifier.width(8.dp))
                                Icon(when (m) { PaymentMethod.COD -> Icons.Outlined.Payments; PaymentMethod.UPI -> Icons.Outlined.QrCode2; PaymentMethod.CARD -> Icons.Outlined.CreditCard; PaymentMethod.NETBANKING -> Icons.Outlined.AccountBalance }, null, tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(methodLabel(m), style = MaterialTheme.typography.bodyLarge, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (m == PaymentMethod.COD && !enabled) Text(
                                        if (!state.config.codEnabled) stringResource(R.string.checkout_cod_disabled) else stringResource(R.string.checkout_cod_limit, state.codMax.format()),
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (m.online) Text(stringResource(R.string.checkout_secured_by_razorpay), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
                cart.pricing?.let { p -> item { LRCard { Text(stringResource(R.string.bill_title), style = MaterialTheme.typography.titleSmall); Spacer(Modifier.height(8.dp)); BillBreakdown(p) } } }
            }
            LoadingOverlay(visible = state.step is CheckoutStep.Verifying || state.step is CheckoutStep.Retrying, message = stringResource(if (state.step is CheckoutStep.Verifying) R.string.checkout_verifying else R.string.checkout_retrying))
        }
    }

    (state.step as? CheckoutStep.PaymentFailed)?.let { failed ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.checkout_payment_failed_title)) },
            text = { Text(stringResource(R.string.checkout_payment_failed_body, failed.message ?: stringResource(R.string.checkout_payment_generic))) },
            confirmButton = { if (failed.canRetry) LRButton(stringResource(R.string.checkout_retry_payment), onClick = viewModel::retryPayment) },
            dismissButton = { TextButton(onClick = { viewModel.abandonPayment()?.let(onViewOrder) }) { Text(stringResource(R.string.checkout_view_order)) } },
        )
    }
}

@Composable
private fun methodLabel(m: PaymentMethod) = stringResource(when (m) { PaymentMethod.COD -> R.string.pay_cod; PaymentMethod.UPI -> R.string.pay_upi; PaymentMethod.CARD -> R.string.pay_card; PaymentMethod.NETBANKING -> R.string.pay_netbanking })

