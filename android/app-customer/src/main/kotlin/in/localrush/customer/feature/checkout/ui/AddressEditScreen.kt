package `in`.localrush.customer.feature.checkout.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.localrush.core.common.geo.LatLng
import `in`.localrush.core.designsystem.component.InlineError
import `in`.localrush.core.designsystem.component.LRButton
import `in`.localrush.core.designsystem.component.LRFooter
import `in`.localrush.core.designsystem.component.LRTextField
import `in`.localrush.core.designsystem.component.LoadingState
import `in`.localrush.core.designsystem.icon.LRIcons
import `in`.localrush.core.maps.LRMap
import `in`.localrush.core.maps.rememberCameraState
import `in`.localrush.customer.R
import `in`.localrush.customer.feature.checkout.vm.AddressEditEvent
import `in`.localrush.customer.feature.checkout.vm.AddressEditViewModel
import `in`.localrush.customer.ui.common.LRTopBar

@Composable
fun AddressEditScreen(onBack: () -> Unit, onSaved: () -> Unit, viewModel: AddressEditViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val camera = rememberCameraState()
    var mapReady by remember { mutableStateOf(false) }
    var programmatic by remember { mutableStateOf<LatLng?>(null) }
    val locationUnavailable = stringResource(R.string.location_unavailable)
    LaunchedEffect(Unit) { viewModel.events.collect { e -> when (e) { AddressEditEvent.Saved -> onSaved(); AddressEditEvent.LocationUnavailable -> snackbar.showSnackbar(locationUnavailable) } } }
    LaunchedEffect(state.pin, mapReady) { val p = state.pin ?: return@LaunchedEffect; if (mapReady && p != camera.target && p != programmatic) { programmatic = p; camera.animateTo(p, zoom = 16.0) } }

    Scaffold(
        topBar = { LRTopBar(stringResource(if (state.editing) R.string.address_edit_title else R.string.address_add_title), onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            LRFooter {
                Column {
                    state.error?.let { InlineError(it.message, Modifier.padding(bottom = 8.dp)) }
                    LRButton(stringResource(R.string.address_save), onClick = viewModel::save, modifier = Modifier.fillMaxWidth(), enabled = state.canSave, loading = state.saving)
                }
            }
        },
    ) { padding ->
        if (state.loading) { LoadingState(Modifier.padding(padding)); return@Scaffold }
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).imePadding()) {
            Box(Modifier.fillMaxWidth().height(240.dp)) {
                LRMap(Modifier.fillMaxSize(), styleUrl = state.config.mapStyleUrl, attribution = null, cameraState = camera, onMapReady = { mapReady = true }, onCameraIdle = { t, _ -> if (t != programmatic) viewModel.onPinMoved(t, prefill = !state.editing) })
                Icon(LRIcons.LocationPin, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.Center).size(44.dp).offset(y = (-22).dp))
                SmallFloatingActionButton(onClick = viewModel::useCurrentLocation, modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp), containerColor = MaterialTheme.colorScheme.surface) {
                    if (state.locating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Default.MyLocation, stringResource(R.string.location_use_current), tint = MaterialTheme.colorScheme.primary)
                }
                if (state.resolving) Text(stringResource(R.string.location_resolving), Modifier.align(Alignment.TopCenter).padding(8.dp), style = MaterialTheme.typography.labelSmall)
            }
            Text(stringResource(R.string.address_pin_hint), Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val f = state.form
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("home" to R.string.address_label_home, "work" to R.string.address_label_work, "other" to R.string.address_label_other).forEach { (k, res) ->
                        FilterChip(selected = f.label == k, onClick = { viewModel.update { copy(label = k) } }, label = { Text(stringResource(res)) })
                    }
                }
                LRTextField(f.line1, { v -> viewModel.update { copy(line1 = v) } }, label = stringResource(R.string.address_line1), errorText = state.fieldErrors["address_line1"] ?: if ("address_line1" in state.localErrors) stringResource(R.string.validation_required) else null)
                LRTextField(f.line2, { v -> viewModel.update { copy(line2 = v) } }, label = stringResource(R.string.address_line2))
                LRTextField(f.landmark, { v -> viewModel.update { copy(landmark = v) } }, label = stringResource(R.string.address_landmark))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LRTextField(f.city, { v -> viewModel.update { copy(city = v) } }, label = stringResource(R.string.address_city), modifier = Modifier.weight(1f), errorText = state.fieldErrors["city"])
                    LRTextField(f.postalCode, { v -> viewModel.update { copy(postalCode = v.filter { c -> c.isDigit() }.take(6)) } }, label = stringResource(R.string.address_pincode), modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), errorText = state.fieldErrors["postal_code"])
                }
                LRTextField(f.state, { v -> viewModel.update { copy(state = v) } }, label = stringResource(R.string.address_state))
                LRTextField(f.contactName, { v -> viewModel.update { copy(contactName = v) } }, label = stringResource(R.string.address_contact_name))
                LRTextField(f.contactPhone, { v -> viewModel.update { copy(contactPhone = v.filter { c -> c.isDigit() }.take(10)) } }, label = stringResource(R.string.address_contact_phone), prefix = "+91 ", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), errorText = state.fieldErrors["contact_phone"] ?: if ("contact_phone" in state.localErrors) stringResource(R.string.validation_phone) else null)
                LRTextField(f.instructions, { v -> viewModel.update { copy(instructions = v) } }, label = stringResource(R.string.address_instructions), singleLine = false, maxLines = 3)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.address_set_default), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(f.isDefault, { viewModel.update { copy(isDefault = it) } })
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
