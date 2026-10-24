package `in`.localrush.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.automirrored.rounded.StarHalf
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import `in`.localrush.core.common.money.Money
import `in`.localrush.core.common.order.OrderStatus
import `in`.localrush.core.common.util.ratingLabel
import `in`.localrush.core.designsystem.theme.LRTheme

enum class ChipTone { Neutral, Success, Warning, Error, Info, Primary }

/** Pill status chip. Use [StatusChip] with an [OrderStatus] for the standard mapping. */
@Composable
fun StatusChip(text: String, tone: ChipTone = ChipTone.Neutral, modifier: Modifier = Modifier, dot: Boolean = false) {
    val ext = LRTheme.colors
    val (bg, fg) = when (tone) {
        ChipTone.Neutral -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
        ChipTone.Success -> ext.successContainer to ext.onSuccessContainer
        ChipTone.Warning -> ext.warningContainer to Color(0xFF8A5A00)
        ChipTone.Error -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        ChipTone.Info -> ext.infoContainer to ext.info
        ChipTone.Primary -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    }
    Row(
        modifier = modifier.background(bg, RoundedCornerShape(999.dp)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot) {
            androidx.compose.foundation.layout.Box(Modifier.size(6.dp).background(fg, CircleShape))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun StatusChip(status: OrderStatus, label: String? = null, modifier: Modifier = Modifier) {
    val tone = when (status) {
        OrderStatus.DELIVERED -> ChipTone.Success
        OrderStatus.CANCELLED, OrderStatus.REJECTED, OrderStatus.PAYMENT_FAILED, OrderStatus.DELIVERY_FAILED -> ChipTone.Error
        OrderStatus.PENDING_PAYMENT -> ChipTone.Warning
        OrderStatus.PLACED -> ChipTone.Info
        OrderStatus.UNKNOWN -> ChipTone.Neutral
        else -> ChipTone.Primary
    }
    StatusChip(label ?: status.wire.replace('_', ' ').replaceFirstChar { it.uppercase() }, tone, modifier, dot = status.isActive)
}

/** Green/red square with a dot — the Indian FSSAI veg / non-veg marker. [foodType]: veg | non_veg | egg. */
@Composable
fun VegNonVegDot(foodType: String, modifier: Modifier = Modifier, size: Dp = 14.dp) {
    val color = when (foodType.lowercase()) {
        "veg", "vegetarian" -> LRTheme.colors.veg
        "egg" -> LRTheme.colors.egg
        else -> LRTheme.colors.nonVeg
    }
    androidx.compose.foundation.layout.Box(
        modifier = modifier.size(size).border(1.5.dp, color, RoundedCornerShape(2.dp)).padding(size / 4),
    ) {
        androidx.compose.foundation.layout.Box(Modifier.size(size / 2).background(color, CircleShape))
    }
}

/** Compact rating pill (green bg, star, "4.3") — Zomato style. */
@Composable
fun RatingBadge(rating: Double, modifier: Modifier = Modifier, count: Int? = null) {
    Row(
        modifier = modifier.background(LRTheme.colors.success, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(rating.ratingLabel(), style = MaterialTheme.typography.labelMedium, color = Color.White, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(2.dp))
        Icon(Icons.Filled.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
        if (count != null) {
            Spacer(Modifier.width(4.dp))
            Text("($count)", style = MaterialTheme.typography.labelSmall, color = Color.White)
        }
    }
}

/** 5-star bar; [onRate] makes it interactive. */
@Composable
fun RatingBar(
    rating: Float,
    modifier: Modifier = Modifier,
    starSize: Dp = 24.dp,
    onRate: ((Int) -> Unit)? = null,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 1..5) {
            val icon = when {
                rating >= i -> Icons.Filled.Star
                rating >= i - 0.5f -> Icons.AutoMirrored.Rounded.StarHalf
                else -> Icons.Outlined.StarOutline
            }
            val tint = if (rating >= i - 0.5f) LRTheme.colors.star else MaterialTheme.colorScheme.outlineVariant
            val m = Modifier.size(starSize).let { if (onRate != null) it.clickableNoRipple { onRate(i) } else it }
            Icon(icon, contentDescription = if (onRate != null) "$i stars" else null, tint = tint, modifier = m)
        }
    }
}

/** Price text with optional strike-through original price. */
@Composable
fun PriceText(
    price: Money,
    modifier: Modifier = Modifier,
    originalPrice: Money? = null,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(price.format(), style = style, color = color, fontWeight = FontWeight.SemiBold)
        if (originalPrice != null && originalPrice > price) {
            Spacer(Modifier.width(6.dp))
            Text(
                originalPrice.format(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough,
            )
        }
    }
}
