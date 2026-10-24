package `in`.localrush.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import `in`.localrush.core.common.order.OrderStatus
import `in`.localrush.core.common.order.TimelineKey
import `in`.localrush.core.designsystem.R
import `in`.localrush.core.designsystem.icon.LRIcons
import `in`.localrush.core.designsystem.theme.LRTheme

/**
 * Horizontal order-stage stepper (Zomato/Meituan tracking header):
 * completed steps are green with a tick, the active step shows an orange scooter badge,
 * upcoming steps are grey; connectors fill green up to the active step.
 *
 * @param steps labels in order; @param activeIndex 0-based active step (steps < activeIndex are done).
 * @param failed renders everything grey/red (cancelled/rejected).
 */
@Composable
fun StepProgressBar(
    steps: List<String>,
    activeIndex: Int,
    modifier: Modifier = Modifier,
    completedAll: Boolean = false,
    failed: Boolean = false,
    nodeSize: Dp = 28.dp,
) {
    require(steps.isNotEmpty())
    val done = LRTheme.colors.success
    val todo = MaterialTheme.colorScheme.outlineVariant
    val active = MaterialTheme.colorScheme.primary
    val fail = MaterialTheme.colorScheme.error
    val n = steps.size
    val progressTarget = when {
        failed -> 0f
        completedAll -> 1f
        else -> (activeIndex.coerceIn(0, n - 1)).toFloat() / (n - 1).coerceAtLeast(1)
    }
    val progress by animateFloatAsState(progressTarget, tween(600), label = "stepProgress")

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val totalWidth = maxWidth
        val segment = (totalWidth - nodeSize) / (n - 1).coerceAtLeast(1)
        Column {
            Box(Modifier.fillMaxWidth().height(nodeSize + 12.dp), contentAlignment = Alignment.CenterStart) {
                // connectors
                Canvas(Modifier.fillMaxWidth().height(nodeSize).padding(horizontal = nodeSize / 2)) {
                    val y = size.height / 2
                    val stroke = 3.dp.toPx()
                    drawLine(todo, Offset(0f, y), Offset(size.width, y), stroke, StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
                    if (progress > 0f) drawLine(if (failed) fail else done, Offset(0f, y), Offset(size.width * progress, y), stroke, StrokeCap.Round)
                }
                // nodes
                for (i in 0 until n) {
                    val isDone = !failed && (completedAll || i < activeIndex)
                    val isActive = !failed && !completedAll && i == activeIndex
                    val x = segment * i
                    when {
                        isActive -> ScooterBadge(Modifier.offset(x = x - 6.dp), size = nodeSize + 12.dp, color = active)
                        else -> Box(
                            Modifier.offset(x = x).size(nodeSize)
                                .background(if (isDone) done else if (failed && i == 0) fail else MaterialTheme.colorScheme.surface, CircleShape)
                                .let { if (isDone || failed) it else it.then(Modifier.background(todo, CircleShape).padding(2.dp).background(MaterialTheme.colorScheme.surface, CircleShape)) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isDone) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(nodeSize * 0.6f))
                            else if (failed && i == 0) Icon(LRIcons.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(nodeSize * 0.6f))
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth()) {
                steps.forEachIndexed { i, label ->
                    val isDone = !failed && (completedAll || i < activeIndex)
                    val isActive = !failed && !completedAll && i == activeIndex
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                        color = when {
                            isActive -> active
                            isDone -> MaterialTheme.colorScheme.onSurface
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        textAlign = when (i) { 0 -> TextAlign.Start; n - 1 -> TextAlign.End; else -> TextAlign.Center },
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Convenience overload driven by an [OrderStatus] using the 4 header steps from the contract. */
@Composable
fun OrderStepProgressBar(status: OrderStatus, modifier: Modifier = Modifier) {
    val labels = listOf(
        stringResource(R.string.ds_step_placed), stringResource(R.string.ds_step_preparing),
        stringResource(R.string.ds_step_on_the_way), stringResource(R.string.ds_step_delivered),
    )
    val idx = TimelineKey.headerStepIndex(status)
    StepProgressBar(
        steps = labels,
        activeIndex = idx.coerceAtLeast(0),
        completedAll = status == OrderStatus.DELIVERED,
        failed = status.isFailure,
        modifier = modifier,
    )
}

@Composable
private fun ScooterBadge(modifier: Modifier = Modifier, size: Dp, color: Color) {
    Box(
        modifier.size(size).shadow(6.dp, CircleShape).background(color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(LRIcons.Scooter, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.62f))
    }
}
