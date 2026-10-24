package `in`.localrush.partner.feature.common.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `in`.localrush.core.common.money.Money
import `in`.localrush.core.common.result.AppError
import `in`.localrush.core.common.util.UiState
import `in`.localrush.core.designsystem.component.EmptyState
import `in`.localrush.core.designsystem.component.ErrorState
import `in`.localrush.core.designsystem.component.LRBottomSheet
import `in`.localrush.core.designsystem.component.LRButton
import `in`.localrush.core.designsystem.component.LRButtonStyle
import `in`.localrush.core.designsystem.component.LRTextField
import `in`.localrush.core.designsystem.component.LoadingState
import `in`.localrush.core.designsystem.component.OfflineBanner
import `in`.localrush.partner.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnerTopBar(title: String, onBack: (() -> Unit)? = null, subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = {
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) } },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

/**
 * Renders the four mandatory states for a [UiState]. [empty] is shown for [UiState.Empty]; the offline banner
 * appears above the content whenever [online] is false.
 */
@Composable
fun <T> StateContent(
    state: UiState<T>,
    online: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    empty: @Composable () -> Unit = { EmptyState(stringResource(R.string.common_empty)) },
    content: @Composable (T) -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        OfflineBanner(visible = !online, text = stringResource(R.string.offline_message))
        Box(Modifier.weight(1f)) {
            when (state) {
                UiState.Loading -> LoadingState()
                UiState.Empty -> empty()
                is UiState.Error -> ErrorState(state.error, onRetry = if (state.retryable) onRetry else null)
                is UiState.Success -> content(state.data)
            }
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun KeyValueRow(label: String, value: String, modifier: Modifier = Modifier, emphasize: Boolean = false) {
    Row(modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = if (emphasize) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium, fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
fun LabeledSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, description: String? = null, enabled: Boolean = true) {
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (description != null) Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirmText: String, onConfirm: () -> Unit, onDismiss: () -> Unit, destructive: Boolean = false) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Bottom sheet asking for a free-text reason (reject / cancel). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReasonSheet(
    title: String,
    confirmText: String,
    presets: List<String>,
    loading: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var reason by rememberSaveable { mutableStateOf("") }
    LRBottomSheet(onDismiss = onDismiss, title = title) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { preset ->
                LRButton(preset, onClick = { reason = preset }, modifier = Modifier.fillMaxWidth(), style = if (reason == preset) LRButtonStyle.Secondary else LRButtonStyle.Outlined)
            }
            LRTextField(value = reason, onValueChange = { reason = it }, label = stringResource(R.string.common_reason), singleLine = false, maxLines = 3)
            Spacer(Modifier.height(4.dp))
            LRButton(confirmText, onClick = { onConfirm(reason.trim()) }, modifier = Modifier.fillMaxWidth(), enabled = reason.isNotBlank(), loading = loading)
        }
    }
}

fun String?.money(): String = Money.parse(this).format()

/** Error message for snackbars; validation field errors are joined so the owner sees what to fix. */
fun AppError.displayMessage(): String = when (this) {
    is AppError.Api -> if (fields.isNotEmpty()) fields.entries.joinToString("\n") { (k, v) -> "${k.replace('_', ' ')}: ${v.joinToString()}" } else message
    else -> message
}
