package `in`.localrush.customer.feature.profile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.localrush.core.common.util.PhoneUtils
import `in`.localrush.core.common.util.initials
import `in`.localrush.core.designsystem.component.EmptyState
import `in`.localrush.core.designsystem.component.LRCard
import `in`.localrush.core.designsystem.component.LRImage
import `in`.localrush.core.designsystem.component.LoadingOverlay
import `in`.localrush.core.designsystem.theme.LRColors
import `in`.localrush.customer.R
import `in`.localrush.customer.feature.profile.vm.ProfileEvent
import `in`.localrush.customer.feature.profile.vm.ProfileViewModel

class ProfileActions(
    val onLogin: () -> Unit, val onEdit: () -> Unit, val onAddresses: () -> Unit, val onOrders: () -> Unit,
    val onNotifications: () -> Unit, val onHelp: () -> Unit, val onAbout: () -> Unit,
)

@Composable
fun ProfileScreen(actions: ProfileActions, viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.events.collect { e -> if (e is ProfileEvent.Message) snackbar.showSnackbar(e.text) } }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()) {
            Text(stringResource(R.string.profile_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp))
            val user = state.user
            if (user == null) {
                EmptyState(stringResource(R.string.profile_guest_title), description = stringResource(R.string.profile_guest_body), icon = Icons.Outlined.Person, actionText = stringResource(R.string.action_login), onAction = actions.onLogin, modifier = Modifier.height(320.dp))
            } else {
                LRCard(Modifier.padding(horizontal = 16.dp), onClick = actions.onEdit) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val avatar = state.profile?.avatar
                        if (!avatar.isNullOrBlank()) LRImage(avatar, user.fullName, Modifier.size(56.dp), radius = 28.dp)
                        else Box(Modifier.size(56.dp).background(LRColors.Orange, CircleShape), contentAlignment = Alignment.Center) { Text(user.fullName.ifBlank { "LR" }.initials(), color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(user.fullName.ifBlank { stringResource(R.string.profile_no_name) }, style = MaterialTheme.typography.titleMedium)
                            Text(PhoneUtils.formatForDisplay(user.phone), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            user.email?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            state.profile?.let { Text(stringResource(R.string.profile_orders_count, it.totalOrders), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        Text(stringResource(R.string.action_edit), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            LRCard(Modifier.padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                if (user != null) {
                    MenuRow(Icons.AutoMirrored.Outlined.ReceiptLong, stringResource(R.string.profile_my_orders), actions.onOrders)
                    MenuRow(Icons.Outlined.Place, stringResource(R.string.profile_addresses), actions.onAddresses)
                    MenuRow(Icons.Outlined.Notifications, stringResource(R.string.notifications_title), actions.onNotifications, badge = state.unread)
                }
                MenuRow(Icons.AutoMirrored.Outlined.HelpOutline, stringResource(R.string.profile_help), actions.onHelp)
                MenuRow(Icons.Outlined.Info, stringResource(R.string.profile_about), actions.onAbout, last = true)
            }
            if (user != null) {
                Spacer(Modifier.height(16.dp))
                LRCard(Modifier.padding(horizontal = 16.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                    MenuRow(Icons.AutoMirrored.Outlined.Logout, stringResource(R.string.action_logout), viewModel::logout)
                    MenuRow(Icons.Outlined.DeleteOutline, stringResource(R.string.profile_delete_account), { viewModel.showDelete(true) }, tint = MaterialTheme.colorScheme.error, last = true)
                }
            }
            Spacer(Modifier.height(96.dp))
        }
        LoadingOverlay(state.busy)
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
    if (state.deleteDialog) AlertDialog(
        onDismissRequest = { viewModel.showDelete(false) },
        title = { Text(stringResource(R.string.profile_delete_title)) },
        text = { Text(stringResource(R.string.profile_delete_body)) },
        confirmButton = { TextButton(onClick = viewModel::deleteAccount) { Text(stringResource(R.string.profile_delete_confirm), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { viewModel.showDelete(false) }) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit, badge: Int = 0, tint: Color = MaterialTheme.colorScheme.onSurface, last: Boolean = false) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (tint == MaterialTheme.colorScheme.onSurface) MaterialTheme.colorScheme.primary else tint)
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint, modifier = Modifier.weight(1f))
        if (badge > 0) { Badge { Text(badge.toString()) }; Spacer(Modifier.width(8.dp)) }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (!last) HorizontalDivider()
}

