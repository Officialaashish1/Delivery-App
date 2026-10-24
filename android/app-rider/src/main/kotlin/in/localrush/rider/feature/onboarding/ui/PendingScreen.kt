package `in`.localrush.rider.feature.onboarding.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.localrush.core.common.util.UiState
import `in`.localrush.core.designsystem.component.ChipTone
import `in`.localrush.core.designsystem.component.ErrorState
import `in`.localrush.core.designsystem.component.InlineError
import `in`.localrush.core.designsystem.component.LRButton
import `in`.localrush.core.designsystem.component.LRButtonStyle
import `in`.localrush.core.designsystem.component.LRCard
import `in`.localrush.core.designsystem.component.LoadingState
import `in`.localrush.core.designsystem.component.StatusChip
import `in`.localrush.core.designsystem.theme.LRTheme
import `in`.localrush.rider.R
import `in`.localrush.rider.data.DocStatus
import `in`.localrush.rider.data.DocType
import `in`.localrush.rider.data.RiderStatus
import `in`.localrush.rider.feature.onboarding.vm.PendingViewModel
import `in`.localrush.rider.ui.common.RiderTopBar

@Composable
fun PendingScreen(
    onApproved: () -> Unit,
    onEditDetails: () -> Unit,
    onFixDocuments: () -> Unit,
    onSupport: () -> Unit,
    onLogout: () -> Unit,
    viewModel: PendingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.approved.collect { onApproved() } }
    Scaffold(topBar = { RiderTopBar(stringResource(R.string.pending_title), actions = {
        LRButton(stringResource(R.string.action_logout), onClick = onLogout, style = LRButtonStyle.Text)
    }) }) { padding ->
        when (val s = state) {
            UiState.Loading -> LoadingState(Modifier.padding(padding))
            is UiState.Error -> ErrorState(s.error, onRetry = viewModel::refresh, modifier = Modifier.padding(padding))
            UiState.Empty -> ErrorState(`in`.localrush.core.common.result.AppError.Unknown(), onRetry = viewModel::refresh, modifier = Modifier.padding(padding))
            is UiState.Success -> {
                val p = s.data
                val status = RiderStatus.fromWire(p.status)
                Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(24.dp))
                    val (icon, tint) = when (status) {
                        RiderStatus.REJECTED -> Icons.Outlined.ErrorOutline to MaterialTheme.colorScheme.error
                        RiderStatus.SUSPENDED -> Icons.Outlined.Block to MaterialTheme.colorScheme.error
                        else -> Icons.Outlined.HourglassTop to MaterialTheme.colorScheme.primary
                    }
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(72.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(
                        when (status) {
                            RiderStatus.REJECTED -> stringResource(R.string.pending_rejected_title)
                            RiderStatus.SUSPENDED -> stringResource(R.string.pending_suspended_title)
                            RiderStatus.UNDER_REVIEW -> stringResource(R.string.pending_review_title)
                            else -> stringResource(R.string.pending_waiting_title)
                        },
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when (status) {
                            RiderStatus.REJECTED -> stringResource(R.string.pending_rejected_body)
                            RiderStatus.SUSPENDED -> stringResource(R.string.pending_suspended_body)
                            else -> stringResource(R.string.pending_waiting_body)
                        },
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    )
                    if (p.rejectionReason.isNotBlank()) {
                        Spacer(Modifier.height(16.dp))
                        InlineError(stringResource(R.string.pending_reason, p.rejectionReason))
                    }
                    Spacer(Modifier.height(24.dp))
                    LRCard(Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.pending_documents), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        val statuses = p.documentStatuses
                        DocType.uploadable.forEach { type ->
                            val st = DocStatus.fromWire(statuses[type.wire])
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(docTypeLabel(type), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                DocStatusChip(st, required = type.required)
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    if (status != RiderStatus.SUSPENDED) {
                        LRButton(stringResource(R.string.pending_fix_documents), onClick = onFixDocuments, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(10.dp))
                        LRButton(stringResource(R.string.pending_edit_details), onClick = onEditDetails, modifier = Modifier.fillMaxWidth(), style = LRButtonStyle.Outlined)
                        Spacer(Modifier.height(10.dp))
                    }
                    LRButton(stringResource(R.string.pending_contact_support), onClick = onSupport, modifier = Modifier.fillMaxWidth(), style = LRButtonStyle.Text)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.pending_auto_refresh), style = MaterialTheme.typography.bodySmall, color = LRTheme.colors.textTertiary, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
fun DocStatusChip(status: DocStatus, required: Boolean = true) {
    when (status) {
        DocStatus.VERIFIED -> StatusChip(stringResource(R.string.doc_status_verified), ChipTone.Success)
        DocStatus.PENDING -> StatusChip(stringResource(R.string.doc_status_pending), ChipTone.Warning)
        DocStatus.REJECTED -> StatusChip(stringResource(R.string.doc_status_rejected), ChipTone.Error)
        DocStatus.MISSING -> StatusChip(stringResource(if (required) R.string.doc_status_missing else R.string.doc_status_optional), if (required) ChipTone.Neutral else ChipTone.Info)
    }
}

@Composable
fun docTypeLabel(type: DocType): String = stringResource(
    when (type) {
        DocType.DRIVING_LICENCE -> R.string.doc_driving_licence
        DocType.AADHAAR -> R.string.doc_aadhaar
        DocType.PAN -> R.string.doc_pan
        DocType.VEHICLE_RC -> R.string.doc_vehicle_rc
        DocType.INSURANCE -> R.string.doc_insurance
        DocType.BANK_PROOF -> R.string.doc_bank_proof
        DocType.PHOTO -> R.string.doc_photo
        DocType.OTHER -> R.string.doc_other
    },
)
