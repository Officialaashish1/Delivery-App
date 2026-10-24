package `in`.localrush.rider.feature.onboarding.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.localrush.core.designsystem.component.ErrorState
import `in`.localrush.core.designsystem.component.InlineError
import `in`.localrush.core.designsystem.component.LRButton
import `in`.localrush.core.designsystem.component.LRButtonStyle
import `in`.localrush.core.designsystem.component.LRCard
import `in`.localrush.core.designsystem.component.LRImage
import `in`.localrush.core.designsystem.component.LRPhoneField
import `in`.localrush.core.designsystem.component.LRTextField
import `in`.localrush.core.designsystem.component.LoadingState
import `in`.localrush.core.designsystem.component.StepProgressBar
import `in`.localrush.rider.R
import `in`.localrush.rider.data.DocStatus
import `in`.localrush.rider.data.DocType
import `in`.localrush.rider.data.VehicleType
import `in`.localrush.rider.feature.onboarding.vm.DocRow
import `in`.localrush.rider.feature.onboarding.vm.RegForm
import `in`.localrush.rider.feature.onboarding.vm.RegStep
import `in`.localrush.rider.feature.onboarding.vm.RegistrationEvent
import `in`.localrush.rider.feature.onboarding.vm.RegistrationUiState
import `in`.localrush.rider.feature.onboarding.vm.RegistrationViewModel
import `in`.localrush.rider.ui.common.RiderTopBar
import `in`.localrush.rider.ui.common.rememberImagePicker

@Composable
fun RegistrationScreen(onFinished: () -> Unit, onExit: () -> Unit, onLogout: () -> Unit, viewModel: RegistrationViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.events.collect { if (it is RegistrationEvent.Finished) onFinished() } }
    LaunchedEffect(state.generalError) { state.generalError?.let { snackbar.showSnackbar(it.message); viewModel.clearError() } }

    val stepTitles = listOf(
        stringResource(R.string.reg_step_personal), stringResource(R.string.reg_step_vehicle), stringResource(R.string.reg_step_documents),
        stringResource(R.string.reg_step_bank), stringResource(R.string.reg_step_emergency),
    )
    Scaffold(
        topBar = {
            RiderTopBar(stringResource(R.string.reg_title), onBack = { if (state.step.ordinal == 0) onExit() else viewModel.back() }) {
                LRButton(stringResource(R.string.action_logout), onClick = onLogout, style = LRButtonStyle.Text)
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            state.loadError != null -> ErrorState(state.loadError!!, onRetry = viewModel::load, modifier = Modifier.padding(padding))
            else -> Column(Modifier.fillMaxSize().padding(padding)) {
                StepProgressBar(steps = stepTitles, activeIndex = state.stepIndex, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).imePadding()) {
                    Spacer(Modifier.height(8.dp))
                    Text(stepTitles[state.stepIndex], style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when (state.step) {
                            RegStep.PERSONAL -> stringResource(R.string.reg_personal_hint)
                            RegStep.VEHICLE -> stringResource(R.string.reg_vehicle_hint)
                            RegStep.DOCUMENTS -> stringResource(R.string.reg_documents_hint)
                            RegStep.BANK -> stringResource(R.string.reg_bank_hint)
                            RegStep.EMERGENCY -> stringResource(R.string.reg_emergency_hint)
                        },
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(20.dp))
                    when (state.step) {
                        RegStep.PERSONAL -> PersonalStep(state, viewModel)
                        RegStep.VEHICLE -> VehicleStep(state, viewModel)
                        RegStep.DOCUMENTS -> DocumentsStep(state, viewModel)
                        RegStep.BANK -> BankStep(state, viewModel)
                        RegStep.EMERGENCY -> EmergencyStep(state, viewModel)
                    }
                    Spacer(Modifier.height(24.dp))
                }
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    if (state.step == RegStep.DOCUMENTS && !state.requiredDocsDone) {
                        Text(stringResource(R.string.reg_documents_required_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                    }
                    LRButton(
                        text = stringResource(if (state.step == RegStep.EMERGENCY) R.string.reg_submit else R.string.action_continue),
                        onClick = viewModel::next, modifier = Modifier.fillMaxWidth(), loading = state.saving,
                        enabled = state.step != RegStep.DOCUMENTS || state.requiredDocsDone,
                    )
                }
            }
        }
    }
}

@Composable
private fun RegistrationUiState.error(key: String): String? = fieldErrors[key] ?: localErrors[key]?.let { stringResource(it) }

@Composable
private fun PersonalStep(state: RegistrationUiState, vm: RegistrationViewModel) {
    val f = state.form
    val picker = rememberImagePicker { vm.setPhoto(it) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(84.dp)) {
            val model = f.photoUri?.toString() ?: f.photoUrl
            if (model != null) LRImage(model, contentDescription = null, modifier = Modifier.size(84.dp), radius = 42.dp)
            else Box(Modifier.size(84.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(stringResource(R.string.reg_photo_label), style = MaterialTheme.typography.titleSmall)
            Row {
                LRButton(stringResource(R.string.action_camera), onClick = picker.fromCamera, style = LRButtonStyle.Text, leadingIcon = Icons.Filled.CameraAlt)
                LRButton(stringResource(R.string.action_gallery), onClick = picker.fromGallery, style = LRButtonStyle.Text, leadingIcon = Icons.Filled.PhotoLibrary)
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    LRTextField(f.fullName, { v -> vm.update { copy(fullName = v) } }, label = stringResource(R.string.field_full_name), errorText = state.error("full_name"), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
    Spacer(Modifier.height(12.dp))
    LRTextField(f.email, { v -> vm.update { copy(email = v) } }, label = stringResource(R.string.field_email_optional), errorText = state.error("email"), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
    Spacer(Modifier.height(12.dp))
    LRTextField(f.dateOfBirth, { v -> vm.update { copy(dateOfBirth = v) } }, label = stringResource(R.string.field_dob), placeholder = "1995-08-15", errorText = state.error("date_of_birth"), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    Spacer(Modifier.height(12.dp))
    LRTextField(f.address, { v -> vm.update { copy(address = v) } }, label = stringResource(R.string.field_address), errorText = state.error("address"), singleLine = false, maxLines = 3)
    Spacer(Modifier.height(12.dp))
    LRTextField(f.city, { v -> vm.update { copy(city = v) } }, label = stringResource(R.string.field_city), errorText = state.error("city"), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
}

@Composable
private fun VehicleStep(state: RegistrationUiState, vm: RegistrationViewModel) {
    val f = state.form
    Text(stringResource(R.string.field_vehicle_type), style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        VehicleType.entries.forEach { t ->
            FilterChip(selected = f.vehicleType == t, onClick = { vm.update { copy(vehicleType = t) } }, label = { Text(vehicleLabel(t)) })
        }
    }
    if (state.approvedLocked) {
        Spacer(Modifier.height(12.dp))
        InlineError(stringResource(R.string.reg_locked_note))
    }
    Spacer(Modifier.height(16.dp))
    LRTextField(f.vehicleNumber, { v -> vm.update { copy(vehicleNumber = v.uppercase()) } }, label = stringResource(R.string.field_vehicle_number), placeholder = "KA01AB1234", errorText = state.error("vehicle_number"), enabled = !state.approvedLocked, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
    Spacer(Modifier.height(12.dp))
    LRTextField(f.licenceNumber, { v -> vm.update { copy(licenceNumber = v.uppercase()) } }, label = stringResource(R.string.field_licence_number), errorText = state.error("driving_licence_number"), enabled = !state.approvedLocked, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
    Spacer(Modifier.height(12.dp))
    LRTextField(f.licenceExpiry, { v -> vm.update { copy(licenceExpiry = v) } }, label = stringResource(R.string.field_licence_expiry), placeholder = "2030-12-31", errorText = state.error("driving_licence_expiry"), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
}

@Composable
fun vehicleLabel(t: VehicleType): String = stringResource(
    when (t) {
        VehicleType.BIKE -> R.string.vehicle_bike
        VehicleType.SCOOTER -> R.string.vehicle_scooter
        VehicleType.BICYCLE -> R.string.vehicle_bicycle
        VehicleType.EV -> R.string.vehicle_ev
    },
)

@Composable
private fun DocumentsStep(state: RegistrationUiState, vm: RegistrationViewModel) {
    if (state.docsLoading) { LoadingState(Modifier.height(120.dp)); return }
    state.docs.forEach { row ->
        DocumentCard(row, onUpload = { number, uri -> vm.uploadDocument(row.type, number, uri) }, onDelete = { vm.deleteDocument(row.type) })
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun DocumentCard(row: DocRow, onUpload: (String, Uri) -> Unit, onDelete: () -> Unit) {
    var number by rememberSaveable(row.type) { mutableStateOf(row.document?.number ?: "") }
    val picker = rememberImagePicker { uri -> onUpload(number, uri) }
    LRCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(docTypeLabel(row.type), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (!row.type.required) Text(stringResource(R.string.doc_optional), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DocStatusChip(row.status, required = row.type.required)
        }
        if (row.document?.rejectionReason?.isNotBlank() == true) {
            Spacer(Modifier.height(8.dp))
            InlineError(row.document.rejectionReason)
        }
        row.error?.let { Spacer(Modifier.height(8.dp)); InlineError(it) }
        Spacer(Modifier.height(10.dp))
        if (row.status != DocStatus.VERIFIED) {
            LRTextField(number, { number = it.uppercase() }, label = stringResource(R.string.doc_number_optional), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                LRButton(stringResource(R.string.action_camera), onClick = picker.fromCamera, style = LRButtonStyle.Outlined, leadingIcon = Icons.Filled.CameraAlt, loading = row.uploading, modifier = Modifier.weight(1f))
                LRButton(stringResource(R.string.action_gallery), onClick = picker.fromGallery, style = LRButtonStyle.Outlined, leadingIcon = Icons.Filled.PhotoLibrary, loading = row.uploading, modifier = Modifier.weight(1f))
                if (row.document != null) IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete)) }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.doc_verified_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun BankStep(state: RegistrationUiState, vm: RegistrationViewModel) {
    val f = state.form
    LRTextField(f.upiId, { v -> vm.update { copy(upiId = v) } }, label = stringResource(R.string.field_upi), placeholder = "name@upi", errorText = state.error("upi_id"), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
    Spacer(Modifier.height(16.dp))
    Text(stringResource(R.string.reg_bank_or), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(12.dp))
    if (state.approvedLocked) { InlineError(stringResource(R.string.reg_locked_note)); Spacer(Modifier.height(12.dp)) }
    LRTextField(f.bankAccountName, { v -> vm.update { copy(bankAccountName = v) } }, label = stringResource(R.string.field_account_name), errorText = state.error("bank_account_name"), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
    Spacer(Modifier.height(12.dp))
    LRTextField(f.bankAccountNumber, { v -> vm.update { copy(bankAccountNumber = v.filter { it.isDigit() }) } }, label = stringResource(R.string.field_account_number), errorText = state.error("bank_account_number"), enabled = !state.approvedLocked, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    Spacer(Modifier.height(12.dp))
    LRTextField(f.bankIfsc, { v -> vm.update { copy(bankIfsc = v.uppercase().take(11)) } }, label = stringResource(R.string.field_ifsc), errorText = state.error("bank_ifsc"), enabled = !state.approvedLocked, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
    Spacer(Modifier.height(20.dp))
    Text(stringResource(R.string.reg_identity_title), style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(8.dp))
    LRTextField(f.panNumber, { v -> vm.update { copy(panNumber = v.uppercase().take(10)) } }, label = stringResource(R.string.field_pan), errorText = state.error("pan_number"), enabled = !state.approvedLocked, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
    Spacer(Modifier.height(12.dp))
    LRTextField(f.aadhaarLast4, { v -> vm.update { copy(aadhaarLast4 = v.filter { it.isDigit() }.take(4)) } }, label = stringResource(R.string.field_aadhaar_last4), errorText = state.error("aadhaar_last4"), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
}

@Composable
private fun EmergencyStep(state: RegistrationUiState, vm: RegistrationViewModel) {
    val f = state.form
    LRTextField(f.emergencyName, { v -> vm.update { copy(emergencyName = v) } }, label = stringResource(R.string.field_emergency_name), errorText = state.error("emergency_contact_name"), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
    Spacer(Modifier.height(12.dp))
    LRPhoneField(f.emergencyPhone, { v -> vm.update { copy(emergencyPhone = v) } }, label = stringResource(R.string.field_emergency_phone), errorText = state.error("emergency_contact_phone"))
}
