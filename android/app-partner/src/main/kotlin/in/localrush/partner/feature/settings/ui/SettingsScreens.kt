package `in`.localrush.partner.feature.settings.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.localrush.core.common.money.Money
import `in`.localrush.core.common.time.DateTimeUtils
import `in`.localrush.core.common.time.toInstantOrNull
import `in`.localrush.core.common.util.UiState
import `in`.localrush.core.designsystem.component.ChipTone
import `in`.localrush.core.designsystem.component.EmptyState
import `in`.localrush.core.designsystem.component.InlineError
import `in`.localrush.core.designsystem.component.LRBottomSheet
import `in`.localrush.core.designsystem.component.LRButton
import `in`.localrush.core.designsystem.component.LRButtonStyle
import `in`.localrush.core.designsystem.component.LRCard
import `in`.localrush.core.designsystem.component.LRFooter
import `in`.localrush.core.designsystem.component.LRPhoneField
import `in`.localrush.core.designsystem.component.LRTextField
import `in`.localrush.core.designsystem.component.StatusChip
import `in`.localrush.partner.R
import `in`.localrush.partner.feature.common.ui.KeyValueRow
import `in`.localrush.partner.feature.common.ui.LabeledSwitch
import `in`.localrush.partner.feature.common.ui.PartnerTopBar
import `in`.localrush.partner.feature.common.ui.SectionTitle
import `in`.localrush.partner.feature.common.ui.StateContent
import `in`.localrush.partner.feature.common.ui.money
import `in`.localrush.partner.feature.registration.ui.ImagePickBox
import `in`.localrush.partner.feature.registration.vm.RESTAURANT_TYPES
import `in`.localrush.partner.feature.settings.vm.DocumentsViewModel
import `in`.localrush.partner.feature.settings.vm.HolidaysViewModel
import `in`.localrush.partner.feature.settings.vm.HoursViewModel
import `in`.localrush.partner.feature.settings.vm.ProfileEditViewModel
import `in`.localrush.partner.feature.settings.vm.RestaurantSettingsViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// ------------------------------------------------------------------ settings hub

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestaurantSettingsScreen(
    onBack: () -> Unit, onHours: () -> Unit, onHolidays: () -> Unit, onDocuments: () -> Unit, onProfile: () -> Unit,
    viewModel: RestaurantSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val savedMsg = stringResource(R.string.settings_saved)
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) { viewModel.saved.collect { snackbar.showSnackbar(savedMsg) } }
    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { viewModel.updateImages(it, null) } }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { viewModel.updateImages(null, it) } }

    Scaffold(topBar = { PartnerTopBar(stringResource(R.string.settings_title), onBack = onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        StateContent(state = state, online = online, onRetry = viewModel::load, modifier = Modifier.padding(padding)) { r ->
            var minOrder by rememberSaveable(r.id) { mutableStateOf(Money.parse(r.minOrderAmount).rupees.stripTrailingZeros().toPlainString()) }
            var packaging by rememberSaveable(r.id) { mutableStateOf(Money.parse(r.packagingCharge).rupees.stripTrailingZeros().toPlainString()) }
            var radius by rememberSaveable(r.id) { mutableStateOf(r.deliveryRadiusKm.toDoubleOrNull()?.toString() ?: r.deliveryRadiusKm) }
            var prep by rememberSaveable(r.id) { mutableStateOf(r.avgPreparationMinutes.toString()) }
            var pureVeg by rememberSaveable(r.id) { mutableStateOf(r.isPureVeg) }
            var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
            var closure by rememberSaveable { mutableStateOf(false) }

            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LRCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ImagePickBox(r.logo, stringResource(R.string.reg_logo), Modifier.size(96.dp)) { logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                        ImagePickBox(r.coverImage, stringResource(R.string.reg_cover), Modifier.weight(1f).height(96.dp)) { coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                    }
                    Spacer(Modifier.height(8.dp))
                    ListItem(
                        headlineContent = { Text(r.name, style = MaterialTheme.typography.titleMedium) },
                        supportingContent = { Text(stringResource(R.string.settings_edit_profile_hint)) },
                        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                        modifier = Modifier.clickable(onClick = onProfile),
                    )
                }
                LRCard {
                    SectionTitle(stringResource(R.string.settings_status)) { StatusChip(stringResource(if (r.isOpenNow) R.string.dashboard_chip_open else R.string.dashboard_chip_closed), if (r.isOpenNow) ChipTone.Success else ChipTone.Error, dot = true) }
                    LabeledSwitch(stringResource(R.string.dashboard_switch_open), r.isOpen, viewModel::setOpen, enabled = !saving)
                    LabeledSwitch(stringResource(R.string.dashboard_switch_accepting), r.isAcceptingOrders, viewModel::setAccepting, enabled = !saving && r.isOpen)
                    r.temporarilyClosedUntil?.toInstantOrNull()?.takeIf { it.isAfter(Instant.now()) }?.let {
                        KeyValueRow(stringResource(R.string.settings_closed_until), DateTimeUtils.formatRelativeDay(it))
                    }
                    LRButton(stringResource(R.string.dashboard_temp_close), onClick = { closure = true }, modifier = Modifier.fillMaxWidth(), style = LRButtonStyle.Outlined, enabled = !saving)
                }
                LRCard {
                    SectionTitle(stringResource(R.string.settings_order_settings))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LRTextField(minOrder, { minOrder = it }, label = stringResource(R.string.settings_min_order), prefix = "₹", errorText = errors["min_order_amount"]?.let { stringResource(R.string.error_amount) }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        LRTextField(packaging, { packaging = it }, label = stringResource(R.string.settings_packaging), prefix = "₹", errorText = errors["packaging_charge"]?.let { stringResource(R.string.error_amount) }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LRTextField(radius, { radius = it }, label = stringResource(R.string.reg_radius), errorText = errors["delivery_radius_km"]?.let { stringResource(R.string.error_radius) }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        LRTextField(prep, { prep = it.filter { c -> c.isDigit() }.take(3) }, label = stringResource(R.string.settings_prep_time), errorText = errors["avg_preparation_minutes"]?.let { stringResource(R.string.error_prep) }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                    LabeledSwitch(stringResource(R.string.reg_pure_veg), pureVeg, { pureVeg = it })
                    LRButton(stringResource(R.string.action_save), onClick = { errors = viewModel.saveOrderSettings(minOrder, packaging, radius, prep, pureVeg) }, modifier = Modifier.fillMaxWidth(), loading = saving)
                }
                LRCard(contentPadding = PaddingValues(vertical = 4.dp)) {
                    NavRow(stringResource(R.string.hours_title), stringResource(R.string.settings_hours_hint), onHours)
                    NavRow(stringResource(R.string.holidays_title), stringResource(R.string.settings_holidays_hint), onHolidays)
                    NavRow(stringResource(R.string.documents_title), stringResource(R.string.settings_documents_hint), onDocuments)
                }
                LRCard {
                    SectionTitle(stringResource(R.string.settings_account))
                    KeyValueRow(stringResource(R.string.dashboard_commission), stringResource(R.string.percent_value, r.effectiveCommissionPercent))
                    KeyValueRow(stringResource(R.string.settings_status), r.status.replace('_', ' ').replaceFirstChar { it.uppercase() })
                    KeyValueRow(stringResource(R.string.dashboard_total_orders), r.totalOrders.toString())
                    if (r.fssaiNumber.isNotBlank()) KeyValueRow(stringResource(R.string.reg_fssai), r.fssaiNumber)
                    if (r.gstNumber.isNotBlank()) KeyValueRow(stringResource(R.string.reg_gst), r.gstNumber)
                    if (r.bankAccountNumber.isNotBlank()) KeyValueRow(stringResource(R.string.reg_bank_number), "•••• " + r.bankAccountNumber.takeLast(4))
                    if (r.upiId.isNotBlank()) KeyValueRow(stringResource(R.string.reg_upi), r.upiId)
                }
                Spacer(Modifier.height(24.dp))
            }
            if (closure) {
                LRBottomSheet(onDismiss = { closure = false }, title = stringResource(R.string.dashboard_temp_close)) {
                    listOf(30, 60, 120, 240).forEach { m -> LRButton(stringResource(R.string.dashboard_close_for_minutes, m), onClick = { viewModel.closeFor(m); closure = false }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), style = LRButtonStyle.Outlined) }
                    if (r.temporarilyClosedUntil != null) LRButton(stringResource(R.string.dashboard_reopen_now), onClick = { viewModel.closeFor(null); closure = false }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun NavRow(title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(headlineContent = { Text(title) }, supportingContent = { Text(subtitle) }, trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) }, modifier = Modifier.clickable(onClick = onClick))
}

// ------------------------------------------------------------------ hours

@Composable
fun HoursScreen(onBack: () -> Unit, viewModel: HoursViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val savedMsg = stringResource(R.string.settings_saved)
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) { viewModel.saved.collect { snackbar.showSnackbar(savedMsg); onBack() } }
    Scaffold(
        topBar = { PartnerTopBar(stringResource(R.string.hours_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { if (draft != null) LRFooter { LRButton(stringResource(R.string.action_save), onClick = viewModel::save, modifier = Modifier.fillMaxWidth(), loading = saving, enabled = viewModel.isValid && online) } },
    ) { padding ->
        StateContent(state = state, online = online, onRetry = viewModel::load, modifier = Modifier.padding(padding)) {
            val days = draft
            if (days != null) Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                Text(stringResource(R.string.registration_hours_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                HoursEditorContent(days, viewModel::edit)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// ------------------------------------------------------------------ holidays

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HolidaysScreen(onBack: () -> Unit, viewModel: HolidaysViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var adding by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    Scaffold(
        topBar = { PartnerTopBar(stringResource(R.string.holidays_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = { FloatingActionButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.holiday_add)) } },
    ) { padding ->
        StateContent(
            state = state, online = online, onRetry = viewModel::load, modifier = Modifier.padding(padding),
            empty = { EmptyState(stringResource(R.string.holidays_empty), description = stringResource(R.string.holidays_empty_hint), icon = Icons.Outlined.EventBusy, actionText = stringResource(R.string.holiday_add), onAction = { adding = true }) },
        ) { data ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(data.holidays, key = { it.id ?: it.date.hashCode() }) { h ->
                    LRCard(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(DateTimeUtils.parseDate(h.date)?.let { DateTimeUtils.formatDate(it) } ?: h.date, style = MaterialTheme.typography.titleSmall)
                                if (h.reason.isNotBlank()) Text(h.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { h.id?.let(viewModel::delete) }, enabled = !data.busy && h.id != null) { Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_delete)) }
                        }
                    }
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }
    if (adding) {
        var reason by rememberSaveable { mutableStateOf("") }
        var showDate by rememberSaveable { mutableStateOf(false) }
        var date by rememberSaveable { mutableStateOf<String?>(null) }
        LRBottomSheet(onDismiss = { adding = false }, title = stringResource(R.string.holiday_add)) {
            LRButton(date?.let { d -> DateTimeUtils.parseDate(d)?.let { DateTimeUtils.formatDate(it) } ?: d } ?: stringResource(R.string.holiday_pick_date), onClick = { showDate = true }, modifier = Modifier.fillMaxWidth(), style = LRButtonStyle.Outlined)
            Spacer(Modifier.height(8.dp))
            LRTextField(reason, { reason = it }, label = stringResource(R.string.holiday_reason))
            Spacer(Modifier.height(12.dp))
            LRButton(stringResource(R.string.action_save), onClick = { date?.let { viewModel.add(it, reason.trim()) }; adding = false }, modifier = Modifier.fillMaxWidth(), enabled = date != null)
            Spacer(Modifier.height(8.dp))
        }
        if (showDate) {
            val dp = rememberDatePickerState(initialSelectedDateMillis = Instant.now().toEpochMilli())
            DatePickerDialog(
                onDismissRequest = { showDate = false },
                confirmButton = { TextButton(onClick = { dp.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }; showDate = false }) { Text(stringResource(R.string.action_ok)) } },
                dismissButton = { TextButton(onClick = { showDate = false }) { Text(stringResource(R.string.action_cancel)) } },
            ) { DatePicker(state = dp) }
        }
    }
}

// ------------------------------------------------------------------ documents

@Composable
fun DocumentsScreen(onBack: () -> Unit, viewModel: DocumentsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    Scaffold(topBar = { PartnerTopBar(stringResource(R.string.documents_title), onBack = onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        StateContent(state = state, online = online, onRetry = viewModel::load, modifier = Modifier.padding(padding)) { data ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                Text(stringResource(R.string.registration_documents_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                DocumentsContent(documents = data.documents, uploading = data.uploading, onUpload = viewModel::upload, onDelete = viewModel::delete)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// ------------------------------------------------------------------ profile edit

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileEditScreen(onBack: () -> Unit, viewModel: ProfileEditViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) { viewModel.saved.collect { onBack() } }
    val data = (state as? UiState.Success)?.data
    val typeLabels = stringArrayResource(R.array.restaurant_types)

    Scaffold(
        topBar = { PartnerTopBar(stringResource(R.string.profile_edit_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { if (data != null) LRFooter { LRButton(stringResource(R.string.action_save), onClick = viewModel::save, modifier = Modifier.fillMaxWidth(), loading = data.saving, enabled = online) } },
    ) { padding ->
        StateContent(state = state, online = online, onRetry = viewModel::load, modifier = Modifier.padding(padding)) { d ->
            val f = d.form
            val e = d.errors
            @Composable fun err(key: String, res: Int = R.string.error_required): String? = e[key]?.let { code -> when (code) { "required" -> stringResource(R.string.error_required); "phone" -> stringResource(R.string.error_phone); "email" -> stringResource(R.string.error_email); "cuisines" -> stringResource(R.string.error_cuisines); else -> if (res == R.string.error_required) code else stringResource(res) } }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle(stringResource(R.string.reg_step_business))
                LRTextField(f.name, { v -> viewModel.edit { it.copy(name = v) } }, label = stringResource(R.string.reg_name), errorText = err("name"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    RESTAURANT_TYPES.forEachIndexed { i, t -> FilterChip(selected = f.restaurantType == t, onClick = { viewModel.edit { it.copy(restaurantType = t) } }, label = { Text(typeLabels.getOrElse(i) { t }) }) }
                }
                LRTextField(f.description, { v -> viewModel.edit { it.copy(description = v) } }, label = stringResource(R.string.reg_description), singleLine = false, maxLines = 4)
                LRTextField(f.ownerName, { v -> viewModel.edit { it.copy(ownerName = v) } }, label = stringResource(R.string.reg_owner_name), errorText = err("owner_name"))
                LRPhoneField(f.phone, { v -> viewModel.edit { it.copy(phone = v) } }, label = stringResource(R.string.reg_phone), errorText = err("phone"))
                LRTextField(f.email, { v -> viewModel.edit { it.copy(email = v) } }, label = stringResource(R.string.reg_email), errorText = err("email"), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                LRTextField(f.costForTwo, { v -> viewModel.edit { it.copy(costForTwo = v.filter { c -> c.isDigit() }) } }, label = stringResource(R.string.reg_cost_for_two), prefix = "₹", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))

                SectionTitle(stringResource(R.string.reg_step_address))
                LRTextField(f.addressLine1, { v -> viewModel.edit { it.copy(addressLine1 = v) } }, label = stringResource(R.string.reg_address1), errorText = err("address_line1"))
                LRTextField(f.addressLine2, { v -> viewModel.edit { it.copy(addressLine2 = v) } }, label = stringResource(R.string.reg_address2))
                LRTextField(f.landmark, { v -> viewModel.edit { it.copy(landmark = v) } }, label = stringResource(R.string.reg_landmark))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LRTextField(f.city, { v -> viewModel.edit { it.copy(city = v) } }, label = stringResource(R.string.reg_city), errorText = err("city"), modifier = Modifier.weight(1f))
                    LRTextField(f.state, { v -> viewModel.edit { it.copy(state = v) } }, label = stringResource(R.string.reg_state), modifier = Modifier.weight(1f))
                }
                LRTextField(f.postalCode, { v -> viewModel.edit { it.copy(postalCode = v.filter { c -> c.isDigit() }.take(6)) } }, label = stringResource(R.string.reg_postal), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))

                SectionTitle(stringResource(R.string.reg_cuisines))
                err("cuisine_ids")?.let { InlineError(it) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    d.cuisines.forEach { c -> FilterChip(selected = c.id in f.cuisineIds, onClick = { viewModel.edit { it.copy(cuisineIds = if (c.id in it.cuisineIds) it.cuisineIds - c.id else it.cuisineIds + c.id) } }, label = { Text(c.name) }) }
                }
                SectionTitle(stringResource(R.string.reg_categories))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    d.categories.forEach { c -> FilterChip(selected = c.id in f.categoryIds, onClick = { viewModel.edit { it.copy(categoryIds = if (c.id in it.categoryIds) it.categoryIds - c.id else it.categoryIds + c.id) } }, label = { Text(c.name) }) }
                }

                SectionTitle(stringResource(R.string.reg_step_compliance))
                if (d.complianceLocked) InlineError(stringResource(R.string.profile_locked_hint))
                LRTextField(f.fssaiNumber, { v -> viewModel.edit { it.copy(fssaiNumber = v.filter { c -> c.isDigit() }.take(14)) } }, label = stringResource(R.string.reg_fssai), enabled = !d.complianceLocked, errorText = e["fssai_number"], keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                LRTextField(f.gstNumber, { v -> viewModel.edit { it.copy(gstNumber = v.uppercase().take(15)) } }, label = stringResource(R.string.reg_gst), enabled = !d.complianceLocked, errorText = e["gst_number"], keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
                LRTextField(f.panNumber, { v -> viewModel.edit { it.copy(panNumber = v.uppercase().take(10)) } }, label = stringResource(R.string.reg_pan), enabled = !d.complianceLocked, errorText = e["pan_number"], keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))

                SectionTitle(stringResource(R.string.reg_step_bank))
                LRTextField(f.bankAccountName, { v -> viewModel.edit { it.copy(bankAccountName = v) } }, label = stringResource(R.string.reg_bank_name), enabled = !d.complianceLocked, errorText = e["bank_account_name"])
                LRTextField(f.bankAccountNumber, { v -> viewModel.edit { it.copy(bankAccountNumber = v.filter { c -> c.isDigit() }.take(18)) } }, label = stringResource(R.string.reg_bank_number), enabled = !d.complianceLocked, errorText = e["bank_account_number"], keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                LRTextField(f.bankIfsc, { v -> viewModel.edit { it.copy(bankIfsc = v.uppercase().take(11)) } }, label = stringResource(R.string.reg_ifsc), enabled = !d.complianceLocked, errorText = e["bank_ifsc"], keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
                LRTextField(f.upiId, { v -> viewModel.edit { it.copy(upiId = v.trim()) } }, label = stringResource(R.string.reg_upi), errorText = e["upi_id"], keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
