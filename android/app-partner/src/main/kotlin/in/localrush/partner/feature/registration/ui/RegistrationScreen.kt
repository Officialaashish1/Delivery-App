package `in`.localrush.partner.feature.registration.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.localrush.core.common.Constants
import `in`.localrush.core.common.geo.LatLng
import `in`.localrush.core.designsystem.component.InlineError
import `in`.localrush.core.designsystem.component.LRButton
import `in`.localrush.core.designsystem.component.LRButtonStyle
import `in`.localrush.core.designsystem.component.LRCard
import `in`.localrush.core.designsystem.component.LRFooter
import `in`.localrush.core.designsystem.component.LRImage
import `in`.localrush.core.designsystem.component.LRPhoneField
import `in`.localrush.core.designsystem.component.LRTextField
import `in`.localrush.core.designsystem.component.OfflineBanner
import `in`.localrush.core.maps.LRMap
import `in`.localrush.core.maps.rememberCameraState
import `in`.localrush.partner.R
import `in`.localrush.partner.feature.common.ui.LabeledSwitch
import `in`.localrush.partner.feature.common.ui.PartnerTopBar
import `in`.localrush.partner.feature.common.ui.SectionTitle
import `in`.localrush.partner.feature.common.ui.displayMessage
import `in`.localrush.partner.feature.registration.vm.RESTAURANT_TYPES
import `in`.localrush.partner.feature.registration.vm.RegFields
import `in`.localrush.partner.feature.registration.vm.RegStep
import `in`.localrush.partner.feature.registration.vm.RegistrationEvent
import `in`.localrush.partner.feature.registration.vm.RegistrationUiState
import `in`.localrush.partner.feature.registration.vm.RegistrationViewModel
import `in`.localrush.partner.feature.settings.ui.DocumentsContent
import `in`.localrush.partner.feature.settings.ui.HoursEditorContent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Multi-step restaurant registration wizard. */
@Composable
fun RegistrationScreen(onFinished: () -> Unit, onLogout: () -> Unit, viewModel: RegistrationViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                RegistrationEvent.Finished -> onFinished()
                is RegistrationEvent.Message -> snackbar.showSnackbar(e.text)
            }
        }
    }
    LaunchedEffect(state.submitError) { state.submitError?.let { snackbar.showSnackbar(it.displayMessage()) } }
    BackHandler(enabled = state.stepIndex > 0 && !state.registered) { viewModel.back() }

    val stepTitles = stringArrayResource(R.array.registration_steps)
    Scaffold(
        topBar = {
            PartnerTopBar(
                title = stepTitles.getOrElse(state.stepIndex) { "" },
                subtitle = stringResource(R.string.registration_step_of, state.stepIndex + 1, state.totalSteps),
                onBack = if (state.stepIndex > 0 && !state.registered) viewModel::back else null,
                actions = { TextButton(onClick = onLogout) { Text(stringResource(R.string.action_logout)) } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            LRFooter {
                LRButton(
                    text = stringResource(
                        when (state.step) {
                            RegStep.CATALOG -> R.string.registration_submit
                            RegStep.DOCUMENTS -> R.string.registration_finish
                            else -> R.string.action_continue
                        },
                    ),
                    onClick = viewModel::next, modifier = Modifier.fillMaxWidth(), loading = state.submitting, enabled = online || state.step != RegStep.CATALOG,
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OfflineBanner(visible = !online, text = stringResource(R.string.offline_message))
            LinearProgressIndicator(progress = { (state.stepIndex + 1f) / state.totalSteps }, modifier = Modifier.fillMaxWidth())
            when (state.step) {
                RegStep.ADDRESS -> AddressStep(state, viewModel)
                else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (state.step) {
                        RegStep.BUSINESS -> BusinessStep(state, viewModel)
                        RegStep.COMPLIANCE -> ComplianceStep(state, viewModel)
                        RegStep.BANK -> BankStep(state, viewModel)
                        RegStep.HOURS -> {
                            Text(stringResource(R.string.registration_hours_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            state.errors[RegFields.HOURS]?.let { InlineError(stringResource(R.string.hours_invalid)) }
                            HoursEditorContent(days = state.hours, onChange = viewModel::updateHours)
                        }
                        RegStep.CATALOG -> CatalogStep(state, viewModel)
                        RegStep.DOCUMENTS -> {
                            Text(stringResource(R.string.registration_documents_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            state.docError?.let { InlineError(it.message) }
                            DocumentsContent(documents = state.documents, uploading = state.uploadingDoc, onUpload = viewModel::uploadDocument, onDelete = viewModel::deleteDocument)
                        }
                        RegStep.ADDRESS -> Unit
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun fieldError(state: RegistrationUiState, key: String): String? {
    val code = state.errors[key] ?: return null
    return when (code) {
        "required" -> stringResource(R.string.error_required)
        "phone" -> stringResource(R.string.error_phone)
        "email" -> stringResource(R.string.error_email)
        "pin" -> stringResource(R.string.error_pin)
        "postal" -> stringResource(R.string.error_postal)
        "radius" -> stringResource(R.string.error_radius)
        "fssai" -> stringResource(R.string.error_fssai)
        "gst" -> stringResource(R.string.error_gst)
        "pan" -> stringResource(R.string.error_pan)
        "bank_or_upi" -> stringResource(R.string.error_bank_or_upi)
        "account" -> stringResource(R.string.error_account)
        "ifsc" -> stringResource(R.string.error_ifsc)
        "upi" -> stringResource(R.string.error_upi)
        "cuisines" -> stringResource(R.string.error_cuisines)
        "amount" -> stringResource(R.string.error_amount)
        "prep" -> stringResource(R.string.error_prep)
        else -> code
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BusinessStep(s: RegistrationUiState, vm: RegistrationViewModel) {
    val typeLabels = stringArrayResource(R.array.restaurant_types)
    LRTextField(s.name, { v -> vm.update { copy(name = v) } }, label = stringResource(R.string.reg_name), errorText = fieldError(s, RegFields.NAME))
    SectionTitle(stringResource(R.string.reg_type))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        RESTAURANT_TYPES.forEachIndexed { i, t -> FilterChip(selected = s.restaurantType == t, onClick = { vm.update { copy(restaurantType = t) } }, label = { Text(typeLabels.getOrElse(i) { t }) }) }
    }
    LRTextField(s.description, { v -> vm.update { copy(description = v) } }, label = stringResource(R.string.reg_description), singleLine = false, maxLines = 4)
    LRTextField(s.ownerName, { v -> vm.update { copy(ownerName = v) } }, label = stringResource(R.string.reg_owner_name), errorText = fieldError(s, RegFields.OWNER))
    LRPhoneField(s.phone, { v -> vm.update { copy(phone = v) } }, label = stringResource(R.string.reg_phone), errorText = fieldError(s, RegFields.PHONE))
    LRTextField(s.email, { v -> vm.update { copy(email = v) } }, label = stringResource(R.string.reg_email), errorText = fieldError(s, RegFields.EMAIL), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
    LRTextField(s.costForTwo, { v -> vm.update { copy(costForTwo = v.filter { c -> c.isDigit() }) } }, label = stringResource(R.string.reg_cost_for_two), prefix = "₹", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    LabeledSwitch(stringResource(R.string.reg_pure_veg), s.isPureVeg, { v -> vm.update { copy(isPureVeg = v) } })
}

@Composable
private fun AddressStep(s: RegistrationUiState, vm: RegistrationViewModel) {
    val camera = rememberCameraState { `in`.localrush.core.maps.CameraState(s.pin ?: LatLng(Constants.DEFAULT_LAT, Constants.DEFAULT_LNG), zoom = if (s.pin != null) 16.0 else 12.0) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()) {
        Box(Modifier.fillMaxWidth().height(300.dp)) {
            LRMap(
                modifier = Modifier.fillMaxSize(),
                cameraState = camera,
                onCameraIdle = { target, _ -> if (target.isValid()) vm.setPin(target) },
            )
            // Fixed centre pin: drag the map underneath it.
            Icon(Icons.Filled.Place, contentDescription = stringResource(R.string.reg_map_pin), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.Center).size(44.dp).padding(bottom = 20.dp))
            if (s.geocoding) CircularProgressIndicator(Modifier.align(Alignment.TopEnd).padding(12.dp).size(22.dp), strokeWidth = 2.dp)
            Column(Modifier.align(Alignment.TopCenter).padding(horizontal = 12.dp, vertical = 8.dp)) {
                LRTextField(s.searchQuery, vm::onSearchQuery, placeholder = stringResource(R.string.reg_search_address), leadingIcon = Icons.Filled.Place)
                if (s.searchResults.isNotEmpty()) {
                    LRCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                        s.searchResults.take(5).forEach { r ->
                            ListItem(
                                headlineContent = { Text(r.displayName, maxLines = 2) },
                                modifier = Modifier.clickable { vm.pickSearchResult(r); camera.animateTo(LatLng(r.latitude, r.longitude), 16.0) },
                            )
                        }
                    }
                }
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.reg_map_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            fieldError(s, RegFields.PIN)?.let { InlineError(it) }
            LRTextField(s.addressLine1, { v -> vm.update { copy(addressLine1 = v) } }, label = stringResource(R.string.reg_address1), errorText = fieldError(s, RegFields.ADDRESS1))
            LRTextField(s.addressLine2, { v -> vm.update { copy(addressLine2 = v) } }, label = stringResource(R.string.reg_address2))
            LRTextField(s.landmark, { v -> vm.update { copy(landmark = v) } }, label = stringResource(R.string.reg_landmark))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LRTextField(s.city, { v -> vm.update { copy(city = v) } }, label = stringResource(R.string.reg_city), errorText = fieldError(s, RegFields.CITY), modifier = Modifier.weight(1f))
                LRTextField(s.state, { v -> vm.update { copy(state = v) } }, label = stringResource(R.string.reg_state), modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LRTextField(s.postalCode, { v -> vm.update { copy(postalCode = v.filter { c -> c.isDigit() }.take(6)) } }, label = stringResource(R.string.reg_postal), errorText = fieldError(s, RegFields.POSTAL), modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                LRTextField(s.deliveryRadiusKm, { v -> vm.update { copy(deliveryRadiusKm = v) } }, label = stringResource(R.string.reg_radius), errorText = fieldError(s, RegFields.RADIUS), modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComplianceStep(s: RegistrationUiState, vm: RegistrationViewModel) {
    var showDate by remember { mutableStateOf(false) }
    Text(stringResource(R.string.reg_compliance_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    LRTextField(s.fssaiNumber, { v -> vm.update { copy(fssaiNumber = v.filter { c -> c.isDigit() }.take(14)) } }, label = stringResource(R.string.reg_fssai), errorText = fieldError(s, RegFields.FSSAI), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    LRButton(s.fssaiExpiry?.let { stringResource(R.string.reg_fssai_expiry_value, it) } ?: stringResource(R.string.reg_fssai_expiry), onClick = { showDate = true }, modifier = Modifier.fillMaxWidth(), style = LRButtonStyle.Outlined)
    LRTextField(s.gstNumber, { v -> vm.update { copy(gstNumber = v.uppercase().take(15)) } }, label = stringResource(R.string.reg_gst), errorText = fieldError(s, RegFields.GST), keyboardOptions = KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Characters))
    LRTextField(s.panNumber, { v -> vm.update { copy(panNumber = v.uppercase().take(10)) } }, label = stringResource(R.string.reg_pan), errorText = fieldError(s, RegFields.PAN), keyboardOptions = KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Characters))
    if (showDate) {
        val initial = s.fssaiExpiry?.let { runCatching { LocalDate.parse(it).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull() }
        val dp = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    dp.selectedDateMillis?.let { ms -> vm.update { copy(fssaiExpiry = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toString()) } }
                    showDate = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state = dp) }
    }
}

@Composable
private fun BankStep(s: RegistrationUiState, vm: RegistrationViewModel) {
    Text(stringResource(R.string.reg_bank_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    LRTextField(s.bankAccountName, { v -> vm.update { copy(bankAccountName = v) } }, label = stringResource(R.string.reg_bank_name), errorText = fieldError(s, RegFields.BANK_NAME))
    LRTextField(s.bankAccountNumber, { v -> vm.update { copy(bankAccountNumber = v.filter { c -> c.isDigit() }.take(18)) } }, label = stringResource(R.string.reg_bank_number), errorText = fieldError(s, RegFields.BANK_NO), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    LRTextField(s.bankIfsc, { v -> vm.update { copy(bankIfsc = v.uppercase().take(11)) } }, label = stringResource(R.string.reg_ifsc), errorText = fieldError(s, RegFields.IFSC), keyboardOptions = KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Characters))
    LRTextField(s.upiId, { v -> vm.update { copy(upiId = v.trim()) } }, label = stringResource(R.string.reg_upi), errorText = fieldError(s, RegFields.UPI), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CatalogStep(s: RegistrationUiState, vm: RegistrationViewModel) {
    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { vm.update { copy(logo = it) } } }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { vm.update { copy(cover = it) } } }
    SectionTitle(stringResource(R.string.reg_images))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ImagePickBox(s.logo?.toString(), stringResource(R.string.reg_logo), Modifier.size(110.dp)) { logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        ImagePickBox(s.cover?.toString(), stringResource(R.string.reg_cover), Modifier.weight(1f).height(110.dp)) { coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    }
    SectionTitle(stringResource(R.string.reg_cuisines))
    fieldError(s, RegFields.CUISINES)?.let { InlineError(it) }
    if (s.lookupsLoading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
    s.lookupsError?.let { InlineError(it.message); TextButton(onClick = vm::loadLookups) { Text(stringResource(R.string.action_retry)) } }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        s.cuisines.forEach { c -> FilterChip(selected = c.id in s.selectedCuisines, onClick = { vm.toggleCuisine(c.id) }, label = { Text(c.name) }) }
    }
    SectionTitle(stringResource(R.string.reg_categories))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        s.categories.forEach { c -> FilterChip(selected = c.id in s.selectedCategories, onClick = { vm.toggleCategory(c.id) }, label = { Text(c.name) }) }
    }
    SectionTitle(stringResource(R.string.reg_order_settings))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LRTextField(s.minOrderAmount, { v -> vm.update { copy(minOrderAmount = v) } }, label = stringResource(R.string.settings_min_order), prefix = "₹", errorText = fieldError(s, RegFields.MIN_ORDER), modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        LRTextField(s.packagingCharge, { v -> vm.update { copy(packagingCharge = v) } }, label = stringResource(R.string.settings_packaging), prefix = "₹", errorText = fieldError(s, RegFields.PACKAGING), modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
    }
    LRTextField(s.avgPreparationMinutes, { v -> vm.update { copy(avgPreparationMinutes = v.filter { c -> c.isDigit() }.take(3)) } }, label = stringResource(R.string.settings_prep_time), errorText = fieldError(s, RegFields.PREP), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
}

@Composable
fun ImagePickBox(uri: String?, label: String, modifier: Modifier, onClick: () -> Unit) {
    LRCard(modifier = modifier, onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (uri != null) LRImage(uri, contentDescription = label, modifier = Modifier.fillMaxSize(), radius = 16.dp)
            else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
