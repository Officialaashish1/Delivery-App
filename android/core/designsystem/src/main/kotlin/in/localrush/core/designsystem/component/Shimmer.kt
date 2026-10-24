package `in`.localrush.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import `in`.localrush.core.designsystem.theme.LRTheme

/** Animated shimmer background for skeleton placeholders. */
fun Modifier.shimmer(shape: Shape = RoundedCornerShape(8.dp)): Modifier = composed {
    val base = LRTheme.colors.shimmerBase
    val highlight = LRTheme.colors.shimmerHighlight
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = -1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerProgress",
    )
    val brush = Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(progress * 600f, 0f),
        end = Offset(progress * 600f + 600f, 300f),
    )
    this.clip(shape).background(brush)
}

@Composable
fun ShimmerBox(modifier: Modifier = Modifier, height: Dp = 16.dp, width: Dp? = null, shape: Shape = RoundedCornerShape(8.dp)) {
    val m = if (width != null) modifier.width(width) else modifier.fillMaxWidth()
    Spacer(m.height(height).shimmer(shape))
}

/** Generic list-row skeleton: thumbnail + two lines. Repeat [count] times. */
@Composable
fun ShimmerListPlaceholder(count: Int = 6, modifier: Modifier = Modifier, thumbnail: Dp = 72.dp) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(count) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Spacer(Modifier.size(thumbnail).shimmer(RoundedCornerShape(12.dp)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShimmerBox(height = 16.dp, width = 160.dp)
                    ShimmerBox(height = 12.dp, width = 110.dp)
                    ShimmerBox(height = 12.dp, width = 80.dp)
                }
            }
        }
    }
}

/** Card skeleton (banner image + title). */
@Composable
fun ShimmerCardPlaceholder(modifier: Modifier = Modifier, imageHeight: Dp = 160.dp) {
    Column(modifier.fillMaxWidth()) {
        Spacer(Modifier.fillMaxWidth().height(imageHeight).shimmer(RoundedCornerShape(16.dp)))
        Spacer(Modifier.height(10.dp))
        ShimmerBox(height = 16.dp, width = 180.dp)
        Spacer(Modifier.height(6.dp))
        ShimmerBox(height = 12.dp, width = 120.dp)
    }
}
