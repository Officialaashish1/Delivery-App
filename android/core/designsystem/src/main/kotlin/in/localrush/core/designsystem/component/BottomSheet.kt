package `in`.localrush.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import `in`.localrush.core.designsystem.theme.LRRadius

/**
 * Standard modal sheet: rounded 24dp top, drag handle, optional title row with close button,
 * content padded 16dp and inset for the navigation bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LRBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    showClose: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        shape = RoundedCornerShape(topStart = LRRadius.sheet, topEnd = LRRadius.sheet),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 40.dp, height = 4.dp).background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp)))
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding()) {
            if (title != null || showClose) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(title ?: "", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    if (showClose) IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
                }
                Spacer(Modifier.height(8.dp))
            }
            content()
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Fixed footer slot for sheets/screens: elevated surface with 16dp padding (place the CTA here). */
@Composable
fun LRFooter(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    androidx.compose.material3.Surface(modifier.fillMaxWidth(), tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Box(Modifier.padding(16.dp).navigationBarsPadding()) { content() }
    }
}

@Composable
fun HorizontalSpacer(width: androidx.compose.ui.unit.Dp) = Spacer(Modifier.width(width))

@Composable
fun VerticalSpacer(height: androidx.compose.ui.unit.Dp) = Spacer(Modifier.height(height))
