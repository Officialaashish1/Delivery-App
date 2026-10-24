package `in`.localrush.partner.feature.registration.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.localrush.core.designsystem.component.ChipTone
import `in`.localrush.core.designsystem.component.InlineError
import `in`.localrush.core.designsystem.component.LRButton
import `in`.localrush.core.designsystem.component.LRButtonStyle
import `in`.localrush.core.designsystem.component.LRCard
import `in`.localrush.core.designsystem.component.StatusChip
import `in`.localrush.partner.R
import `in`.localrush.partner.data.RestaurantStatus
import `in`.localrush.partner.feature.common.ui.PartnerTopBar
import `in`.localrush.partner.feature.common.ui.SectionTitle
import `in`.localrush.partner.feature.common.ui.StateContent
import `in`.localrush.partner.feature.registration.vm.PendingApprovalViewModel
import `in`.localrush.partner.feature.settings.ui.DocumentsContent

/** Shown while the restaurant is pending / under review / rejected / suspended. Polls for approval. */
@Composable
fun PendingApprovalScreen(onApproved: () -> Unit, onEditProfile: () -> Unit, onLogout: () -> Unit, viewModel: PendingApprovalViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.approved.collect { onApproved() } }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        topBar = { PartnerTopBar(stringResource(R.string.approval_title), actions = { TextButton(onClick = onLogout) { Text(stringResource(R.string.action_logout)) } }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        StateContent(state = state, online = online, onRetry = viewModel::refresh, modifier = Modifier.padding(padding)) { data ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val status = RestaurantStatus.fromWire(data.restaurant.status)
                val look = when (status) {
                    RestaurantStatus.REJECTED -> StatusLook(Icons.Outlined.ErrorOutline, R.string.approval_rejected_title, R.string.approval_rejected_body, ChipTone.Error)
                    RestaurantStatus.SUSPENDED -> StatusLook(Icons.Outlined.Block, R.string.approval_suspended_title, R.string.approval_suspended_body, ChipTone.Error)
                    RestaurantStatus.UNDER_REVIEW -> StatusLook(Icons.Outlined.HourglassTop, R.string.approval_review_title, R.string.approval_pending_body, ChipTone.Info)
                    else -> StatusLook(Icons.Outlined.HourglassTop, R.string.approval_pending_title, R.string.approval_pending_body, ChipTone.Warning)
                }
                Spacer(Modifier.height(24.dp))
                Icon(look.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.height(64.dp).fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                Text(stringResource(look.title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                StatusChip(data.restaurant.status.replace('_', ' ').replaceFirstChar { it.uppercase() }, look.tone)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(look.body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                if (data.restaurant.rejectionReason.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    InlineError(stringResource(R.string.approval_reason, data.restaurant.rejectionReason))
                }
                Spacer(Modifier.height(20.dp))
                LRCard(Modifier.fillMaxWidth()) {
                    Text(data.restaurant.name, style = MaterialTheme.typography.titleMedium)
                    Text(listOf(data.restaurant.addressLine1, data.restaurant.city).filter { it.isNotBlank() }.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(12.dp))
                if (status == RestaurantStatus.REJECTED) {
                    LRButton(stringResource(R.string.approval_edit_resubmit), onClick = onEditProfile, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                }
                LRButton(stringResource(R.string.approval_check_again), onClick = viewModel::refresh, modifier = Modifier.fillMaxWidth(), style = LRButtonStyle.Outlined, loading = data.refreshing)
                Spacer(Modifier.height(20.dp))
                SectionTitle(stringResource(R.string.documents_title), Modifier.fillMaxWidth())
                Text(stringResource(R.string.approval_documents_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                DocumentsContent(documents = data.documents, uploading = data.uploading, onUpload = viewModel::uploadDocument, onDelete = viewModel::deleteDocument, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

private data class StatusLook(val icon: androidx.compose.ui.graphics.vector.ImageVector, val title: Int, val body: Int, val tone: ChipTone)
