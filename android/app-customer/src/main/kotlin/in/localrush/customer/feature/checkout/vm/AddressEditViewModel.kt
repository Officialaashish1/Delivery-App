package `in`.localrush.customer.feature.checkout.vm

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import `in`.localrush.core.common.geo.LatLng
import `in`.localrush.core.common.result.AppError
import `in`.localrush.core.common.util.PhoneUtils
import `in`.localrush.core.location.LocationClient
import `in`.localrush.core.network.dto.AddressWriteDto
import `in`.localrush.core.network.dto.AppConfigDto
import `in`.localrush.customer.data.repo.AddressRepository
import `in`.localrush.customer.data.repo.AppPreferences
import `in`.localrush.customer.data.repo.CartRepository
import `in`.localrush.customer.data.repo.ConfigRepository
import `in`.localrush.customer.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddressForm(
    val label: String = "home",
    val contactName: String = "",
    val contactPhone: String = "",
    val line1: String = "",
    val line2: String = "",
    val landmark: String = "",
    val city: String = "",
    val state: String = "",
    val postalCode: String = "",
    val instructions: String = "",
    val isDefault: Boolean = false,
)

data class AddressEditUiState(
    val editing: Boolean = false,
    val loading: Boolean = true,
    val config: AppConfigDto = AppConfigDto(),
    val pin: LatLng? = null,
    val resolving: Boolean = false,
    val form: AddressForm = AddressForm(),
    /** Server-side field errors (already human readable). */
    val fieldErrors: Map<String, String> = emptyMap(),
    /** Client-side validation failures by field key; the screen maps them to string resources. */
    val localErrors: Set<String> = emptySet(),
    val saving: Boolean = false,
    val error: AppError? = null,
    val locating: Boolean = false,
) {
    val canSave: Boolean get() = pin != null && form.line1.isNotBlank() && !saving && !resolving
}

sealed interface AddressEditEvent { data object Saved : AddressEditEvent; data object LocationUnavailable : AddressEditEvent }

/** Map pin + form; `POST/PATCH customers/addresses/` with `latitude`/`longitude`. */
@HiltViewModel
class AddressEditViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val addresses: AddressRepository,
    private val cart: CartRepository,
    private val configRepo: ConfigRepository,
    private val prefs: AppPreferences,
    private val locationClient: LocationClient,
) : ViewModel() {
    private val route = savedState.toRoute<Routes.AddressEdit>()
    private val _state = MutableStateFlow(AddressEditUiState(editing = route.addressId != null))
    val state: StateFlow<AddressEditUiState> = _state.asStateFlow()
    private val _events = MutableSharedFlow<AddressEditEvent>(extraBufferCapacity = 2)
    val events: SharedFlow<AddressEditEvent> = _events.asSharedFlow()
    private var resolveJob: Job? = null

    init {
        viewModelScope.launch { configRepo.config.collect { c -> _state.update { it.copy(config = c) } } }
        viewModelScope.launch {
            val existing = route.addressId?.let { id -> (addresses.addresses.value ?: addresses.load().getOrNull())?.firstOrNull { it.id == id } }
            if (existing != null) {
                _state.update {
                    it.copy(loading = false, pin = LatLng(existing.lat, existing.lng), form = AddressForm(
                        existing.label, existing.contactName, PhoneUtils.localPart(existing.contactPhone), existing.addressLine1, existing.addressLine2, existing.landmark,
                        existing.city, existing.state, existing.postalCode, existing.deliveryInstructions, existing.isDefault,
                    ))
                }
            } else {
                val loc = prefs.deliveryLocation.first()
                _state.update { it.copy(loading = false, pin = loc?.point) }
                loc?.point?.let { onPinMoved(it, prefill = true) }
            }
        }
    }

    fun onPinMoved(point: LatLng, prefill: Boolean = true) {
        if (!point.isValid()) return
        resolveJob?.cancel()
        _state.update { it.copy(pin = point, resolving = prefill) }
        if (!prefill) return
        resolveJob = viewModelScope.launch {
            delay(400)
            configRepo.reverseGeocode(point.latitude, point.longitude).fold(
                onOk = { g ->
                    _state.update { s ->
                        s.copy(resolving = false, form = s.form.copy(
                            line1 = s.form.line1.ifBlank { listOf(g.name, g.road).filter { it.isNotBlank() }.joinToString(", ") },
                            line2 = s.form.line2.ifBlank { g.locality }, city = g.city.ifBlank { s.form.city }, state = g.state.ifBlank { s.form.state }, postalCode = g.postcode.ifBlank { s.form.postalCode },
                        ))
                    }
                },
                onErr = { _state.update { it.copy(resolving = false) } },
            )
        }
    }

    fun useCurrentLocation() {
        _state.update { it.copy(locating = true) }
        viewModelScope.launch {
            val l = locationClient.current()
            _state.update { it.copy(locating = false) }
            if (l == null) _events.tryEmit(AddressEditEvent.LocationUnavailable) else onPinMoved(LatLng(l.latitude, l.longitude))
        }
    }

    fun update(block: AddressForm.() -> AddressForm) = _state.update { it.copy(form = it.form.block(), fieldErrors = emptyMap(), localErrors = emptySet(), error = null) }

    fun save() {
        val s = _state.value
        val pin = s.pin ?: return
        val f = s.form
        val errors = buildSet {
            if (f.line1.isBlank()) add("address_line1")
            if (f.contactPhone.isNotBlank() && !PhoneUtils.isValidIndian(f.contactPhone)) add("contact_phone")
        }
        if (errors.isNotEmpty()) { _state.update { it.copy(localErrors = errors) }; return }
        _state.update { it.copy(saving = true, error = null) }
        val body = AddressWriteDto(
            label = f.label, contactName = f.contactName.trim(), contactPhone = f.contactPhone.takeIf { it.isNotBlank() }?.let { PhoneUtils.normalizeIndian(it) } ?: "",
            addressLine1 = f.line1.trim(), addressLine2 = f.line2.trim(), landmark = f.landmark.trim(), city = f.city.trim(), state = f.state.trim(), postalCode = f.postalCode.trim(),
            latitude = pin.latitude, longitude = pin.longitude, isDefault = f.isDefault, deliveryInstructions = f.instructions.trim(),
        )
        viewModelScope.launch {
            val r = if (route.addressId != null) addresses.update(route.addressId, body) else addresses.create(body)
            r.fold(
                onOk = { a ->
                    if (route.forCheckout) cart.setAddress(a.id)
                    _state.update { it.copy(saving = false) }
                    _events.tryEmit(AddressEditEvent.Saved)
                },
                onErr = { e ->
                    val fields = (e as? AppError.Api)?.fields?.mapValues { it.value.first() } ?: emptyMap()
                    _state.update { it.copy(saving = false, fieldErrors = fields, error = if (fields.isEmpty()) e else null) }
                },
            )
        }
    }
}
