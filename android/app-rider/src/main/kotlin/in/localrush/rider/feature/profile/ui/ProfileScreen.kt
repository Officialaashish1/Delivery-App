package `in`.localrush.rider.feature.profile.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.localrush.core.common.util.PhoneUtils
import `in`.localrush.core.common.util.initials
import `in`.localrush.core.designsystem.component.ChipTone
import `in`.localrush.core.designsystem.component.ErrorState
import `in`.localrush.core.designsystem.component.LRCard
import `in`.localrush.core.designsystem.component.LRImage
import `in`.localrush.core.designsystem.component.LoadingState
import `in`.localrush.core.designsystem.component.StatusChip
import `in`.localrush.core.designsystem.theme.LRTheme
import `in`.localrush.rider.R
import `in`.localrush.rider.data.DocStatus
import `in`.localrush.rider.data.RiderStatus
import `in`.localrush.rider.data.VehicleType
import `in`.localrush.rider.feature.onboarding.ui.vehicleLabel
import `in`.localrush.rider.feature.onboarding.vm.RegStep
import `in`.localrush.rider.feature.profile.vm.ProfileViewModel
import `in`.localrush.rider.ui.common.KeyValueRow
import `in`.localrush.rider.ui.common.RiderTopBar
import `in`.localrush.rider.ui.common.SectionTitle

@Composable
fun ProfileScreen(
    onEdit: (RegStep) -> Unit,
    onDocuments: () -> Unit,
    onPayouts: () -> Unit,
    onSupport: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmLogout by remember { mutableStateOf(false) }
    Scaffold(topBar = { RiderTopBar(stringResource(R.string.profile_title)) }) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            state.error != null -> ErrorState(state.error!!, onRetry = viewModel::load, modifier = Modifier.padding(padding))
            else -> {
                val p = state.profile ?: return@Scaffold
                Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (p.photo != null) LRImage(p.photo, contentDescription = null, modifier = Modifier.size(72.dp), radius = 36.dp)
                        else Box(Modifier.size(72.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                            Text(p.fullName.ifBlank { "R" }.initials(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.fullName.ifBlank { stringResource(R.string.profile_unnamed) }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(PhoneUtils.formatForDisplay(p.phone), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.Star, contentDescription = null, tint = LRTheme.colors.star, modifier = Modifier.size(16.dp))
                                Text(p.ratingAvg.toDoubleOrNull()?.let { String.format(java.util.Locale.ROOT, "%.1f", it) } ?: "—", style = MaterialTheme.typography.labelLarge)
                                Text(stringResource(R.string.dash_n_deliveries, p.totalDeliveries), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                StatusChip(riderStatusLabel(RiderStatus.fromWire(p.status)), if (p.isApproved) ChipTone.Success else ChipTone.Warning)
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    SectionTitle(stringResource(R.string.profile_vehicle)) { TextButton(onClick = { onEdit(RegStep.VEHICLE) }) { Text(stringResource(R.string.action_edit)) } }
                    LRCard(Modifier.fillMaxWidth()) {
                        KeyValueRow(stringResource(R.string.field_vehicle_type), vehicleLabel(VehicleType.fromWire(p.vehicleType)))
                        KeyValueRow(stringResource(R.string.field_vehicle_number), p.vehicleNumber.ifBlank { "—" })
                        KeyValueRow(stringResource(R.string.field_licence_number), p.drivingLicenceNumber.ifBlank { "—" })
                    }
                    Spacer(Modifier.height(12.dp))
                    SectionTitle(stringResource(R.string.profile_bank)) { TextButton(onClick = { onEdit(RegStep.BANK) }) { Text(stringResource(R.string.action_edit)) } }
                    LRCard(Modifier.fillMaxWidth()) {
                        KeyValueRow(stringResource(R.string.field_upi), p.upiId.ifBlank { "—" })
                        KeyValueRow(stringResource(R.string.field_account_number), p.bankAccountNumber.takeIf { it.isNotBlank() }?.let { "•••• " + it.takeLast(4) } ?: "—")
                        KeyValueRow(stringResource(R.string.field_ifsc), p.bankIfsc.ifBlank { "—" })
                    }
                    Spacer(Modifier.height(12.dp))
                    SectionTitle(stringResource(R.string.profile_emergency)) { TextButton(onClick = { onEdit(RegStep.EMERGENCY) }) { Text(stringResource(R.string.action_edit)) } }
                    LRCard(Modifier.fillMaxWidth()) {
                        KeyValueRow(stringResource(R.string.field_emergency_name), p.emergencyContactName.ifBlank { "—" })
                        KeyValueRow(stringResource(R.string.field_emergency_phone), p.emergencyContactPhone.takeIf { it.isNotBlank() }?.let { PhoneUtils.formatForDisplay(it) } ?: "—")
                    }
                    Spacer(Modifier.height(20.dp))
                    val pendingDocs = state.documents.count { DocStatus.fromWire(it.status) != DocStatus.VERIFIED }
                    MenuRow(Icons.Outlined.Description, stringResource(R.string.profile_documents), if (pendingDocs > 0) stringResource(R.string.profile_docs_pending, pendingDocs) else null, onDocuments)
                    MenuRow(Icons.Filled.Edit, stringResource(R.string.profile_edit_personal), null) { onEdit(RegStep.PERSONAL) }
                    MenuRow(Icons.Outlined.Payments, stringResource(R.string.payouts_title), null, onPayouts)
                    MenuRow(Icons.AutoMirrored.Filled.HelpOutline, stringResource(R.string.support_title), null, onSupport)
                    MenuRow(Icons.AutoMirrored.Filled.Logout, stringResource(R.string.action_logout), null) { confirmLogout = true }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text(stringResource(R.string.logout_confirm_title)) },
            text = { Text(stringResource(R.string.logout_confirm_body)) },
            confirmButton = { TextButton(onClick = { confirmLogout = false; viewModel.logout() }) { Text(stringResource(R.string.action_logout)) } },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun MenuRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    LRCard(Modifier.fillMaxWidth().padding(vertical = 4.dp), onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = LRTheme.colors.warning)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun riderStatusLabel(s: RiderStatus): String = stringResource(
    when (s) {
        RiderStatus.PENDING -> R.string.rider_status_pending
        RiderStatus.UNDER_REVIEW -> R.string.rider_status_under_review
        RiderStatus.APPROVED -> R.string.rider_status_approved
        RiderStatus.REJECTED -> R.string.rider_status_rejected
        RiderStatus.SUSPENDED -> R.string.rider_status_suspended
        RiderStatus.UNKNOWN -> R.string.rider_status_pending
    },
)

