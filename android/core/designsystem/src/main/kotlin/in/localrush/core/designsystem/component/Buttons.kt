package `in`.localrush.core.designsystem.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import `in`.localrush.core.designsystem.theme.LRRadius
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke

enum class LRButtonStyle { Primary, Secondary, Outlined, Text }

/**
 * The one button. `style` picks Primary (orange), Secondary (charcoal), Outlined, Text.
 * Shows a spinner and disables itself while [loading].
 */
@Composable
fun LRButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: LRButtonStyle = LRButtonStyle.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
) {
    val shape = RoundedCornerShape(LRRadius.button)
    val isEnabled = enabled && !loading
    val content: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
                Spacer(Modifier.width(10.dp))
            } else if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge)
            if (trailingIcon != null && !loading) {
                Spacer(Modifier.width(8.dp))
                Icon(trailingIcon, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
    val m = modifier.height(52.dp)
    when (style) {
        LRButtonStyle.Primary -> Button(onClick, m, isEnabled, shape, ButtonDefaults.buttonColors(), contentPadding = contentPadding) { content() }
        LRButtonStyle.Secondary -> Button(
            onClick, m, isEnabled, shape,
            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary),
            contentPadding = contentPadding,
        ) { content() }
        LRButtonStyle.Outlined -> OutlinedButton(
            onClick, m, isEnabled, shape,
            ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
            border = BorderStroke(1.dp, if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
            contentPadding = contentPadding,
        ) { content() }
        LRButtonStyle.Text -> TextButton(onClick, m, isEnabled, shape, contentPadding = contentPadding) { content() }
    }
}
