package `in`.localrush.partner.feature.settings.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import `in`.localrush.core.designsystem.component.ChipTone
import `in`.localrush.core.designsystem.component.LRBottomSheet
import `in`.localrush.core.designsystem.component.LRButton
import `in`.localrush.core.designsystem.component.LRButtonStyle
import `in`.localrush.core.designsystem.component.LRCard
import `in`.localrush.core.designsystem.component.LRTextField
import `in`.localrush.core.designsystem.component.StatusChip
import `in`.localrush.partner.R
import `in`.localrush.partner.data.RestaurantDocumentDto
import `in`.localrush.partner.feature.registration.vm.DOCUMENT_TYPES
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi

@Composable
fun documentTypeLabel(docType: String): String {
    val labels = stringArrayResource(R.array.document_types)
    val idx = DOCUMENT_TYPES.indexOf(docType)
    return if (idx >= 0) labels.getOrElse(idx) { docType } else docType
}

/** Document list + "upload" sheet; used in the registration wizard and in Settings → Documents. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DocumentsContent(
    documents: List<RestaurantDocumentDto>,
    uploading: Boolean,
    onUpload: (docType: String, number: String, file: android.net.Uri) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sheet by rememberSaveable { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (documents.isEmpty()) {
            Text(stringResource(R.string.documents_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        documents.forEach { d ->
            LRCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(documentTypeLabel(d.docType), style = MaterialTheme.typography.titleSmall)
                        if (d.number.isNotBlank()) Text(d.number, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (d.rejectionReason.isNotBlank()) Text(d.rejectionReason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    val tone = when (d.status) { "verified" -> ChipTone.Success; "rejected" -> ChipTone.Error; else -> ChipTone.Warning }
                    StatusChip(stringResource(when (d.status) { "verified" -> R.string.doc_status_verified; "rejected" -> R.string.doc_status_rejected; else -> R.string.doc_status_pending }), tone)
                    if (d.status != "verified") {
                        IconButton(onClick = { onDelete(d.id) }) { Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_delete)) }
                    }
                }
            }
        }
        LRButton(stringResource(R.string.documents_upload), onClick = { sheet = true }, modifier = Modifier.fillMaxWidth(), style = LRButtonStyle.Outlined, leadingIcon = Icons.Outlined.UploadFile, loading = uploading)
    }
    if (sheet) {
        var docType by rememberSaveable { mutableStateOf(DOCUMENT_TYPES.first()) }
        var number by rememberSaveable { mutableStateOf("") }
        var file by rememberSaveable { mutableStateOf<String?>(null) }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> file = uri?.toString() }
        val labels = stringArrayResource(R.array.document_types)
        LRBottomSheet(onDismiss = { sheet = false }, title = stringResource(R.string.documents_upload)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DOCUMENT_TYPES.forEachIndexed { i, t -> FilterChip(selected = docType == t, onClick = { docType = t }, label = { Text(labels.getOrElse(i) { t }) }) }
            }
            Spacer(Modifier.height(8.dp))
            LRTextField(value = number, onValueChange = { number = it }, label = stringResource(R.string.documents_number))
            Spacer(Modifier.height(8.dp))
            LRButton(
                if (file == null) stringResource(R.string.documents_choose_file) else stringResource(R.string.documents_file_selected),
                onClick = { picker.launch(arrayOf("image/*", "application/pdf")) },
                modifier = Modifier.fillMaxWidth(), style = LRButtonStyle.Outlined,
            )
            Spacer(Modifier.height(8.dp))
            LRButton(
                stringResource(R.string.action_upload),
                onClick = { file?.let { onUpload(docType, number.trim(), android.net.Uri.parse(it)); sheet = false } },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), enabled = file != null,
            )
        }
    }
}
