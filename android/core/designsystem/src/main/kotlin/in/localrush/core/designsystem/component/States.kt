package `in`.localrush.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import `in`.localrush.core.common.result.AppError
import `in`.localrush.core.designsystem.R
import `in`.localrush.core.designsystem.theme.LRTheme

/** Full-screen translucent overlay with a spinner; place last in a Box. */
@Composable
fun LoadingOverlay(visible: Boolean, modifier: Modifier = Modifier, message: String? = null) {
    if (!visible) return
    Box(
        modifier = modifier.fillMaxSize().zIndex(10f).background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)).clickableNoRipple { },
        contentAlignment = Alignment.Center,
    ) {
        Surface(shape = MaterialTheme.shapes.medium, tonalElevation = 6.dp) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                if (message != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(message, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector = Icons.Outlined.Inbox,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    illustration: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (illustration != null) illustration() else {
            Box(Modifier.size(88.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.extraLarge), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        if (description != null) {
            Spacer(Modifier.height(8.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(24.dp))
            LRButton(actionText, onAction, style = LRButtonStyle.Outlined)
        }
    }
}

/** Error with retry. Picks an offline icon/message when [error] is a network error. */
@Composable
fun ErrorState(
    error: AppError,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
    title: String? = null,
) {
    val offline = error.isOffline
    EmptyState(
        title = title ?: if (offline) "No internet connection" else stringResource(R.string.ds_error_generic),
        description = if (offline) "Check your connection and try again." else error.message,
        icon = if (offline) Icons.Outlined.WifiOff else Icons.Outlined.ErrorOutline,
        actionText = if (onRetry != null) stringResource(R.string.ds_retry) else null,
        onAction = onRetry,
        modifier = modifier,
    )
}

/** Slim top banner; animate in/out with [visible]. */
@Composable
fun OfflineBanner(visible: Boolean, modifier: Modifier = Modifier, text: String = stringResource(R.string.ds_offline)) {
    AnimatedVisibility(visible, modifier, enter = expandVertically(), exit = shrinkVertically()) {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondary).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = Color.White)
        }
    }
}

/** Inline (non-blocking) error row, e.g. under a form. */
@Composable
fun InlineError(message: String?, modifier: Modifier = Modifier) {
    if (message.isNullOrBlank()) return
    Row(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.small).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

@Composable
fun SuccessNote(message: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().background(LRTheme.colors.successContainer, MaterialTheme.shapes.small).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(message, style = MaterialTheme.typography.bodySmall, color = LRTheme.colors.onSuccessContainer)
    }
}
