package `in`.localrush.customer.feature.cart.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.localrush.core.common.money.toMoney
import `in`.localrush.core.designsystem.component.EmptyState
import `in`.localrush.core.designsystem.component.ErrorState
import `in`.localrush.core.designsystem.component.InlineError
import `in`.localrush.core.designsystem.component.LRBottomSheet
import `in`.localrush.core.designsystem.component.LRButton
import `in`.localrush.core.designsystem.component.LRButtonStyle
import `in`.localrush.core.designsystem.component.LRCard
import `in`.localrush.core.designsystem.component.LRFooter
import `in`.localrush.core.designsystem.component.LRImage
import `in`.localrush.core.designsystem.component.LRTextField
import `in`.localrush.core.designsystem.component.ShimmerListPlaceholder
import `in`.localrush.core.designsystem.component.SuccessNote
import `in`.localrush.core.designsystem.component.VegNonVegDot
import `in`.localrush.core.designsystem.theme.LRTheme
import `in`.localrush.customer.R
import `in`.localrush.customer.data.dto.CartItemDto
import `in`.localrush.customer.feature.cart.vm.CartEvent
import `in`.localrush.customer.feature.cart.vm.CartViewModel
import `in`.localrush.customer.ui.common.BillBreakdown
import `in`.localrush.customer.ui.common.LRTopBar
import `in`.localrush.customer.ui.common.QuantityStepper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartScreen(onBack: () -> Unit, onBrowse: () -> Unit, onRestaurant: (slug: String) -> Unit, onCheckout: () -> Unit, onAddAddress: () -> Unit, viewModel: CartViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.events.collect { e -> when (e) { CartEvent.GoToCheckout -> onCheckout(); CartEvent.GoToAddAddress -> onAddAddress() } } }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() } }

    Scaffold(
        topBar = { LRTopBar(stringResource(R.string.cart_title), onBack, subtitle = state.cart?.restaurant?.name, actions = { if (!state.isEmpty) TextButton(onClick = viewModel::clearCart) { Text(stringResource(R.string.cart_clear)) } }) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!state.isEmpty && state.pricing != null) LRFooter {
                Column {
                    if (!state.meetsMinOrder) InlineError(stringResource(R.string.cart_min_order, state.pricing!!.minOrderAmount.toMoney().format()), Modifier.padding(bottom = 8.dp))
                    state.blockingErrors.filter { !it.startsWith("Minimum order") }.forEach { InlineError(it, Modifier.padding(bottom = 8.dp)) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(state.pricing!!.total.toMoney().format(), style = MaterialTheme.typography.titleLarge)
                            Text(stringResource(R.string.cart_total_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        LRButton(
                            stringResource(if (state.needsAddress) R.string.cart_select_address else R.string.cart_checkout),
                            onClick = viewModel::checkout, modifier = Modifier.weight(1.4f), enabled = state.needsAddress || state.canCheckout,
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> ShimmerListPlaceholder(4, Modifier.padding(top = 16.dp))
                state.error != null && state.cart == null -> ErrorState(state.error!!, onRetry = viewModel::refresh)
                state.isEmpty -> EmptyState(stringResource(R.string.cart_empty_title), description = stringResource(R.string.cart_empty_body), icon = Icons.Outlined.ShoppingBag, actionText = stringResource(R.string.cart_browse), onAction = onBrowse)
                else -> CartContent(state, viewModel, onRestaurant)
            }
        }
    }

    if (state.offersOpen) OffersSheet(state, viewModel)
    if (state.addressSheetOpen) AddressSheet(state, viewModel)
}

@Composable
private fun CartContent(state: `in`.localrush.customer.feature.cart.vm.CartUiState, vm: CartViewModel, onRestaurant: (String) -> Unit) {
    val cart = state.cart!!
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        cart.restaurant?.let { r ->
            item {
                Row(Modifier.fillMaxWidth().clickable { onRestaurant(r.slug) }, verticalAlignment = Alignment.CenterVertically) {
                    LRImage(r.logo, r.name, Modifier.size(44.dp), radius = 12.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.name, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.restaurant_eta_min, r.deliveryTimeMin), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                }
            }
        }
        item {
            LRCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                cart.items.forEachIndexed { i, line ->
                    CartLine(line, busy = state.busyItem == line.id, onInc = { vm.increment(line.id) }, onDec = { vm.decrement(line.id) }, onRemove = { vm.remove(line.id) })
                    if (i < cart.items.lastIndex) HorizontalDivider()
                }
            }
        }
        item {
            LRCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.LocalOffer, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.cart_offers), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = vm::openOffers) { Text(stringResource(R.string.cart_view_offers)) }
                }
                val applied = cart.pricing?.couponCode?.takeIf { it.isNotBlank() }
                if (applied != null) {
                    Row(Modifier.fillMaxWidth().background(LRTheme.colors.successContainer, RoundedCornerShape(10.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.cart_coupon_applied, applied), style = MaterialTheme.typography.labelLarge, color = LRTheme.colors.onSuccessContainer)
                            CartViewModel.savings(cart.pricing!!).takeIf { !it.isZero }?.let { Text(stringResource(R.string.cart_you_save, it.format()), style = MaterialTheme.typography.bodySmall, color = LRTheme.colors.onSuccessContainer) }
                        }
                        IconButton(onClick = vm::removeCoupon, enabled = !state.couponBusy) { Icon(Icons.Default.Close, stringResource(R.string.action_remove)) }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LRTextField(value = state.couponInput, onValueChange = vm::onCouponInput, placeholder = stringResource(R.string.cart_coupon_hint), modifier = Modifier.weight(1f), errorText = state.couponError)
                        Spacer(Modifier.width(8.dp))
                        LRButton(stringResource(R.string.cart_apply), onClick = { vm.applyCoupon() }, style = LRButtonStyle.Outlined, enabled = state.couponInput.isNotBlank(), loading = state.couponBusy)
                    }
                }
            }
        }
        item {
            LRCard(onClick = vm::openAddressSheet) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Place, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        val a = cart.deliveryAddress
                        Text(if (a == null) stringResource(R.string.cart_select_address) else stringResource(R.string.cart_deliver_to, a.label.replaceFirstChar { it.uppercase() }), style = MaterialTheme.typography.titleSmall)
                        Text(a?.formatted?.ifBlank { null } ?: a?.addressLine1 ?: stringResource(R.string.cart_address_needed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (state.addressBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.action_change), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        cart.pricing?.let { p ->
            item {
                LRCard {
                    Text(stringResource(R.string.bill_title), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    BillBreakdown(p)
                    p.distanceKm?.toDoubleOrNull()?.takeIf { it > 0 }?.let { Text(stringResource(R.string.bill_distance, `in`.localrush.core.common.geo.Geo.formatDistanceKm(it)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)) }
                }
            }
        }
        if (cart.items.any { !it.isAvailable }) item { InlineError(stringResource(R.string.cart_unavailable_items)) }
    }
}

@Composable
private fun CartLine(line: CartItemDto, busy: Boolean, onInc: () -> Unit, onDec: () -> Unit, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
        VegNonVegDot(line.foodType, Modifier.padding(top = 3.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(line.name, style = MaterialTheme.typography.titleSmall, color = if (line.isAvailable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            val custom = listOfNotNull(line.variant?.name) + line.addons.map { it.name }
            if (custom.isNotEmpty()) Text(custom.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (line.specialInstructions.isNotBlank()) Text("“${line.specialInstructions}”", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!line.isAvailable) Text(stringResource(R.string.menu_unavailable), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            Text(line.unitPrice.toMoney().format(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        }
        Column(horizontalAlignment = Alignment.End) {
            if (busy) Box(Modifier.size(width = 86.dp, height = 30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) }
            else if (line.isAvailable) QuantityStepper(line.quantity, onIncrement = onInc, onDecrement = onDec, compact = true)
            else TextButton(onClick = onRemove) { Text(stringResource(R.string.action_remove)) }
            Spacer(Modifier.height(4.dp))
            Text(line.lineTotal.toMoney().format(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OffersSheet(state: `in`.localrush.customer.feature.cart.vm.CartUiState, vm: CartViewModel) {
    LRBottomSheet(onDismiss = vm::closeOffers, title = stringResource(R.string.cart_offers)) {
        when {
            state.offersLoading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.offersError != null -> ErrorState(state.offersError!!, onRetry = vm::openOffers, modifier = Modifier.height(240.dp))
            state.offers.isNullOrEmpty() -> EmptyState(stringResource(R.string.cart_no_offers), modifier = Modifier.height(240.dp), icon = Icons.Outlined.LocalOffer)
            else -> LazyColumn(Modifier.height(440.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.offers!!, key = { it.coupon.id }) { o ->
                    LRCard(contentPadding = PaddingValues(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(o.coupon.code, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                Text(o.coupon.title.ifBlank { o.coupon.shortText }, style = MaterialTheme.typography.bodyMedium)
                                if (o.coupon.description.isNotBlank()) Text(o.coupon.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (o.applicable && o.discount.toMoney().isZero.not()) SuccessNote(stringResource(R.string.cart_offer_saves, o.discount.toMoney().format()), Modifier.padding(top = 6.dp))
                                if (!o.applicable && o.reason.isNotBlank()) Text(o.reason, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
                            }
                            Spacer(Modifier.width(8.dp))
                            LRButton(stringResource(R.string.cart_apply), onClick = { vm.applyCoupon(o.coupon.code) }, style = LRButtonStyle.Outlined, enabled = o.applicable, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp), modifier = Modifier.height(40.dp))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddressSheet(state: `in`.localrush.customer.feature.cart.vm.CartUiState, vm: CartViewModel) {
    LRBottomSheet(onDismiss = vm::closeAddressSheet, title = stringResource(R.string.cart_select_address)) {
        val list = state.addresses
        when {
            list == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> Text(stringResource(R.string.address_none), Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> Column {
                list.forEach { a ->
                    Row(Modifier.fillMaxWidth().clickable(enabled = !state.addressBusy) { vm.selectAddress(a.id) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = state.cart?.deliveryAddress?.id == a.id, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.label.replaceFirstChar { it.uppercase() } + (if (a.isDefault) "  ·  " + stringResource(R.string.address_default) else ""), style = MaterialTheme.typography.titleSmall)
                            Text(a.formatted.ifBlank { listOf(a.addressLine1, a.addressLine2, a.city).filter { it.isNotBlank() }.joinToString(", ") }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        LRButton(stringResource(R.string.address_add_new), onClick = vm::addAddress, style = LRButtonStyle.Outlined, modifier = Modifier.fillMaxWidth())
    }
}
