package `in`.localrush.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import `in`.localrush.core.designsystem.theme.LRRadius
import androidx.compose.foundation.shape.RoundedCornerShape
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip

/** Default white card with 16dp radius and a hairline border (no heavy shadows). */
@Composable
fun LRCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    large: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(if (large) LRRadius.cardLarge else LRRadius.card)
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = shape, colors = colors, border = border, elevation = CardDefaults.cardElevation(0.dp)) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    } else {
        Card(modifier = modifier, shape = shape, colors = colors, border = border, elevation = CardDefaults.cardElevation(0.dp)) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    }
}

/** Coil image with rounded corners and shimmer placeholder. */
@Composable
fun LRImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    radius: androidx.compose.ui.unit.Dp = 12.dp,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val shape = RoundedCornerShape(radius)
    if (url.isNullOrBlank()) {
        androidx.compose.foundation.layout.Box(modifier.clip(shape).shimmer(shape))
    } else {
        AsyncImage(
            model = url,
            contentDescription = contentDescription,
            modifier = modifier.clip(shape),
            contentScale = contentScale,
        )
    }
}
