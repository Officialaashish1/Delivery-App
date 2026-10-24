package `in`.localrush.customer.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import `in`.localrush.core.common.geo.Geo
import `in`.localrush.core.common.money.Money
import `in`.localrush.core.common.money.toMoney
import `in`.localrush.core.designsystem.component.LRButton
import `in`.localrush.core.designsystem.component.LRCard
import `in`.localrush.core.designsystem.component.LRImage
import `in`.localrush.core.designsystem.component.PriceText
import `in`.localrush.core.designsystem.component.RatingBadge
import `in`.localrush.core.designsystem.component.StatusChip
import `in`.localrush.core.designsystem.component.ChipTone
import `in`.localrush.core.designsystem.component.VegNonVegDot
import `in`.localrush.core.designsystem.theme.LRTheme
import `in`.localrush.core.network.dto.OrderPricingDto
import `in`.localrush.customer.R
import `in`.localrush.customer.data.dto.MenuItemDto
import `in`.localrush.customer.data.dto.RestaurantCardDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LRTopBar(title: String, onBack: (() -> Unit)?, modifier: Modifier = Modifier, subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = {
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) } },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier,
    )
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action) }
    }
}

fun String?.rating(): Double = this?.toDoubleOrNull() ?: 0.0

/** Horizontal home-section card: cover, logo, name, rating, ETA, fee, offer chip. */
@Composable
fun RestaurantCard(card: RestaurantCardDto, onClick: () -> Unit, modifier: Modifier = Modifier, width: androidx.compose.ui.unit.Dp = 220.dp) {
    val closed = !card.isOpenNow
    LRCard(modifier = modifier.width(width), onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
        Box {
            LRImage(card.coverImage ?: card.logo, card.name, Modifier.fillMaxWidth().height(120.dp), radius = 0.dp)
            Box(Modifier.fillMaxWidth().height(120.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)))))
            if (closed) Box(Modifier.fillMaxWidth().height(120.dp).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.restaurant_closed), color = Color.White, fontWeight = FontWeight.Bold)
            }
            if (!card.offerText.isNullOrBlank()) {
                Row(Modifier.align(Alignment.BottomStart).padding(8.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.LocalOffer, null, tint = Color.White, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(card.offerText, style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (card.isPromoted) StatusChip(stringResource(R.string.restaurant_promoted), ChipTone.Neutral, Modifier.align(Alignment.TopEnd).padding(8.dp))
            LRImage(card.logo, null, Modifier.align(Alignment.BottomEnd).padding(8.dp).size(40.dp).border(2.dp, Color.White, RoundedCornerShape(10.dp)), radius = 10.dp)
        }
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(card.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(6.dp))
                RatingBadge(card.ratingAvg.rating())
            }
            Spacer(Modifier.height(2.dp))
            Text(card.cuisines.joinToString(", ").ifBlank { card.addressLine2 }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            MetaRow(card)
        }
    }
}

@Composable
private fun MetaRow(card: RestaurantCardDto) {
    val fee = card.deliveryFee.toMoney()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Schedule, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(3.dp))
        Text(stringResource(R.string.restaurant_eta_min, card.deliveryTimeMin), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        card.distanceKm?.let {
            Text("  ·  " + Geo.formatDistanceKm(it), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("  ·  ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            if (fee.isZero) stringResource(R.string.restaurant_free_delivery) else stringResource(R.string.restaurant_delivery_fee, fee.format()),
            style = MaterialTheme.typography.labelMedium,
            color = if (fee.isZero) LRTheme.colors.success else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Full-width list row used in search results / filtered lists. */
@Composable
fun RestaurantRow(card: RestaurantCardDto, onClick: () -> Unit, modifier: Modifier = Modifier) {
    LRCard(modifier = modifier.fillMaxWidth(), onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
        Row {
            Box {
                LRImage(card.coverImage ?: card.logo, card.name, Modifier.size(96.dp), radius = 12.dp)
                if (!card.isOpenNow) Box(Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.restaurant_closed), color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (card.isPureVeg) { VegNonVegDot("veg"); Spacer(Modifier.width(6.dp)) }
                    Text(card.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    RatingBadge(card.ratingAvg.rating(), count = card.ratingCount.takeIf { it > 0 })
                }
                Text(card.cuisines.joinToString(", ").ifBlank { card.addressLine2 }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.restaurant_cost_for_two, card.costForTwo.toMoney().format()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                MetaRow(card)
                if (!card.offerText.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.LocalOffer, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(card.offerText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** ± stepper used on menu rows and the cart. */
@Composable
fun QuantityStepper(quantity: Int, onIncrement: () -> Unit, onDecrement: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, compact: Boolean = false) {
    val h = if (compact) 30.dp else 36.dp
    Row(
        modifier.height(h).border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)).alpha(if (enabled) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(h).clickable(enabled = enabled, onClick = onDecrement), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Remove, stringResource(R.string.action_decrease), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        }
        AnimatedContent(quantity, transitionSpec = { (slideInVertically { if (targetState > initialState) it else -it }) togetherWith (slideOutVertically { if (targetState > initialState) -it else it }) }, label = "qty") { q ->
            Text(q.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center, modifier = Modifier.width(24.dp))
        }
        Box(Modifier.size(h).clickable(enabled = enabled, onClick = onIncrement), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Add, stringResource(R.string.action_increase), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        }
    }
}

/** Menu row: veg dot, name, price (with strike-through), description, image with ADD / stepper. */
@Composable
fun MenuItemRow(item: MenuItemDto, quantityInCart: Int, onAdd: () -> Unit, onIncrement: () -> Unit, onDecrement: () -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val available = item.inStock && item.isAvailable
    Row(modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp).alpha(if (available) 1f else 0.55f)) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VegNonVegDot(item.foodType)
                if (item.isBestseller) { Spacer(Modifier.width(6.dp)); StatusChip(stringResource(R.string.menu_bestseller), ChipTone.Warning) }
                if (item.isRecommended) { Spacer(Modifier.width(6.dp)); StatusChip(stringResource(R.string.menu_recommended), ChipTone.Success) }
            }
            Spacer(Modifier.height(6.dp))
            Text(item.name, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            PriceText(item.effectivePrice.toMoney(), originalPrice = item.discountedPrice?.let { item.price.toMoney() }, style = MaterialTheme.typography.bodyMedium)
            if (item.ratingCount > 0) {
                Spacer(Modifier.height(2.dp))
                Text("★ ${item.ratingAvg} (${item.ratingCount})", style = MaterialTheme.typography.labelSmall, color = LRTheme.colors.success)
            }
            if (item.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(item.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (!item.image.isNullOrBlank()) LRImage(item.image, item.name, Modifier.size(110.dp), radius = 14.dp)
            else Box(Modifier.size(110.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(14.dp)))
            Box(Modifier.offset(y = (-18).dp)) {
                when {
                    !available -> Text(stringResource(R.string.menu_unavailable), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 22.dp))
                    quantityInCart > 0 && item.variantGroups.isEmpty() && item.addonGroups.isEmpty() -> QuantityStepper(quantityInCart, onIncrement, onDecrement, Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)))
                    else -> AddButton(onAdd, customisable = item.variantGroups.isNotEmpty() || item.addonGroups.isNotEmpty(), count = quantityInCart)
                }
            }
        }
    }
}

@Composable
private fun AddButton(onClick: () -> Unit, customisable: Boolean, count: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.width(96.dp).height(36.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)).border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (count > 0) stringResource(R.string.menu_add_count, count) else stringResource(R.string.menu_add), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        if (customisable) Text(stringResource(R.string.menu_customisable), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Bill rows exactly from `pricing`. */
@Composable
fun BillBreakdown(pricing: OrderPricingDto, modifier: Modifier = Modifier, showTotal: Boolean = true) {
    val rows = buildList {
        add(stringResource(R.string.bill_item_total) to pricing.itemTotal.toMoney())
        pricing.packagingCharge.toMoney().takeIf { !it.isZero }?.let { add(stringResource(R.string.bill_packaging) to it) }
        add(stringResource(R.string.bill_delivery_fee) to pricing.deliveryFee.toMoney())
        pricing.deliveryDiscount.toMoney().takeIf { !it.isZero }?.let { add(stringResource(R.string.bill_delivery_discount) to -it) }
        pricing.platformFee.toMoney().takeIf { !it.isZero }?.let { add(stringResource(R.string.bill_platform_fee) to it) }
        pricing.taxAmount.toMoney().takeIf { !it.isZero }?.let { add(stringResource(R.string.bill_taxes, pricing.taxPercent.trimEnd('0').trimEnd('.')) to it) }
        pricing.couponDiscount.toMoney().takeIf { !it.isZero }?.let { add(stringResource(R.string.bill_coupon, pricing.couponCode ?: "") to -it) }
    }
    Column(modifier) {
        rows.forEach { (label, amount) -> BillRow(label, amount, discount = amount.isNegative) }
        if (showTotal) {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.bill_total), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(pricing.total.toMoney().format(), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun BillRow(label: String, amount: Money, discount: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(
            if (discount) "- " + (-amount).format() else if (amount.isZero && label == stringResource(R.string.bill_delivery_fee)) stringResource(R.string.restaurant_free) else amount.format(),
            style = MaterialTheme.typography.bodyMedium,
            color = if (discount || (amount.isZero)) LRTheme.colors.success else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** "Log in to continue" dialog shown to guests when they tap cart/orders/profile actions. */
@Composable
fun LoginRequiredDialog(onLogin: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(R.string.login_required_title)) },
        text = { Text(stringResource(R.string.login_required_body)) },
        confirmButton = { LRButton(stringResource(R.string.action_login), onClick = onLogin) },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_not_now)) } },
    )
}

/** Round icon button with a label beneath (Call / Chat). */
@Composable
fun RoundAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(48.dp).background(tint.copy(alpha = 0.12f), CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = tint)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
