package `in`.localrush.partner.feature.registration.vm

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.localrush.core.common.Constants
import `in`.localrush.core.common.geo.LatLng
import `in`.localrush.core.common.result.AppError
import `in`.localrush.core.common.result.Result
import `in`.localrush.core.common.util.PhoneUtils
import `in`.localrush.core.network.dto.GeocodeResultDto
import `in`.localrush.partner.data.ConnectivityObserver
import `in`.localrush.partner.data.CuisineDto
import `in`.localrush.partner.data.RegistrationPayload
import `in`.localrush.partner.data.RestaurantCategoryDto
import `in`.localrush.partner.data.RestaurantDocumentDto
import `in`.localrush.partner.data.RestaurantRepository
import `in`.localrush.partner.feature.settings.vm.DayHours
import `in`.localrush.partner.feature.settings.vm.HoursEditor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class RegStep { BUSINESS, ADDRESS, COMPLIANCE, BANK, HOURS, CATALOG, DOCUMENTS }

val RESTAURANT_TYPES = listOf("restaurant", "hotel", "cafe", "cloud_kitchen", "bakery", "sweets", "other")
val DOCUMENT_TYPES = listOf("fssai", "gst", "pan", "bank_proof", "owner_id", "menu", "other")
val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

data class RegistrationUiState(
    val step: RegStep = RegStep.BUSINESS,
    // business
    val name: String = "", val restaurantType: String = "restaurant", val description: String = "", val ownerName: String = "",
    val phone: String = "", val email: String = "", val isPureVeg: Boolean = false, val costForTwo: String = "",
    // address
    val pin: LatLng? = null, val addressLine1: String = "", val addressLine2: String = "", val landmark: String = "",
    val city: String = "", val state: String = "", val postalCode: String = "", val deliveryRadiusKm: String = "7",
    val geocoding: Boolean = false, val searchQuery: String = "", val searchResults: List<GeocodeResultDto> = emptyList(),
    // compliance
    val fssaiNumber: String = "", val fssaiExpiry: String? = null, val gstNumber: String = "", val panNumber: String = "",
    // bank
    val bankAccountName: String = "", val bankAccountNumber: String = "", val bankIfsc: String = "", val upiId: String = "",
    // hours
    val hours: List<DayHours> = HoursEditor.defaults(),
    // catalog
    val cuisines: List<CuisineDto> = emptyList(), val categories: List<RestaurantCategoryDto> = emptyList(),
    val lookupsError: AppError? = null, val lookupsLoading: Boolean = false,
    val selectedCuisines: Set<Int> = emptySet(), val selectedCategories: Set<Int> = emptySet(),
    val logo: Uri? = null, val cover: Uri? = null,
    val minOrderAmount: String = "0", val packagingCharge: String = "0", val avgPreparationMinutes: String = "20",
    // submission
    val errors: Map<String, String> = emptyMap(),
    val submitting: Boolean = false,
    val submitError: AppError? = null,
    val registered: Boolean = false,
    // documents (after registration)
    val documents: List<RestaurantDocumentDto> = emptyList(),
    val uploadingDoc: Boolean = false,
    val docError: AppError? = null,
) {
    val stepIndex: Int get() = step.ordinal
    val totalSteps: Int get() = RegStep.entries.size
}

sealed interface RegistrationEvent {
    data object Finished : RegistrationEvent
    data class Message(val text: String) : RegistrationEvent
}

/** Field keys used in [RegistrationUiState.errors]; the UI maps them to inline errors. */
object RegFields {
    const val NAME = "name"; const val OWNER = "owner_name"; const val PHONE = "phone"; const val EMAIL = "email"
    const val ADDRESS1 = "address_line1"; const val CITY = "city"; const val POSTAL = "postal_code"; const val PIN = "latitude"; const val RADIUS = "delivery_radius_km"
    const val FSSAI = "fssai_number"; const val GST = "gst_number"; const val PAN = "pan_number"
    const val BANK_NAME = "bank_account_name"; const val BANK_NO = "bank_account_number"; const val IFSC = "bank_ifsc"; const val UPI = "upi_id"
    const val HOURS = "hours"; const val CUISINES = "cuisine_ids"; const val MIN_ORDER = "min_order_amount"; const val PACKAGING = "packaging_charge"; const val PREP = "avg_preparation_minutes"
}

@HiltViewModel
class RegistrationViewModel @Inject constructor(
    private val repo: RestaurantRepository,
    connectivity: ConnectivityObserver,
) : ViewModel() {
    private val _state = MutableStateFlow(RegistrationUiState())
    val state: StateFlow<RegistrationUiState> = _state.asStateFlow()
    val online: StateFlow<Boolean> = connectivity.isOnline
    private val _events = MutableSharedFlow<RegistrationEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<RegistrationEvent> = _events.asSharedFlow()

    private var geocodeJob: Job? = null
    private var searchJob: Job? = null

    init { loadLookups() }

    // ------------------------------------------------------------ field setters
    fun update(transform: RegistrationUiState.() -> RegistrationUiState) = _state.update { it.transform().copy(errors = emptyMap()) }

    fun setPin(latLng: LatLng, reverseGeocode: Boolean = true) {
        // The map starts on the default city centre; an untouched map must not count as "pin placed".
        if (latLng.latitude == Constants.DEFAULT_LAT && latLng.longitude == Constants.DEFAULT_LNG) return
        _state.update { it.copy(pin = latLng, errors = it.errors - RegFields.PIN) }
        if (!reverseGeocode) return
        geocodeJob?.cancel()
        geocodeJob = viewModelScope.launch {
            delay(500)
            _state.update { it.copy(geocoding = true) }
            when (val r = repo.reverseGeocode(latLng.latitude, latLng.longitude)) {
                is Result.Ok -> _state.update {
                    val g = r.value
                    it.copy(
                        geocoding = false,
                        addressLine1 = listOf(g.name, g.road).filter { s -> s.isNotBlank() }.distinct().joinToString(", ").ifBlank { it.addressLine1 },
                        addressLine2 = g.locality.ifBlank { it.addressLine2 },
                        city = g.city.ifBlank { it.city }, state = g.state.ifBlank { it.state }, postalCode = g.postcode.ifBlank { it.postalCode },
                    )
                }
                is Result.Err -> _state.update { it.copy(geocoding = false) }
            }
        }
    }

    fun onSearchQuery(q: String) {
        _state.update { it.copy(searchQuery = q) }
        searchJob?.cancel()
        if (q.length < 3) { _state.update { it.copy(searchResults = emptyList()) }; return }
        searchJob = viewModelScope.launch {
            delay(400)
            val p = _state.value.pin
            repo.geocodeSearch(q, p?.latitude ?: Constants.DEFAULT_LAT, p?.longitude ?: Constants.DEFAULT_LNG).onSuccess { res -> _state.update { it.copy(searchResults = res) } }
        }
    }

    fun pickSearchResult(r: GeocodeResultDto) {
        _state.update { it.copy(searchQuery = "", searchResults = emptyList()) }
        setPin(LatLng(r.latitude, r.longitude))
    }

    fun toggleCuisine(id: Int) = _state.update { it.copy(selectedCuisines = it.selectedCuisines.toggle(id), errors = it.errors - RegFields.CUISINES) }
    fun toggleCategory(id: Int) = _state.update { it.copy(selectedCategories = it.selectedCategories.toggle(id)) }
    private fun Set<Int>.toggle(id: Int) = if (id in this) this - id else this + id

    fun updateHours(days: List<DayHours>) = _state.update { it.copy(hours = days, errors = it.errors - RegFields.HOURS) }

    fun loadLookups() {
        _state.update { it.copy(lookupsLoading = true, lookupsError = null) }
        viewModelScope.launch {
            val c = repo.cuisines(); val k = repo.restaurantCategories()
            _state.update {
                it.copy(
                    lookupsLoading = false,
                    cuisines = c.getOrNull() ?: it.cuisines, categories = k.getOrNull() ?: it.categories,
                    lookupsError = c.errorOrNull() ?: k.errorOrNull(),
                )
            }
        }
    }

    // ------------------------------------------------------------ navigation
    fun back() = _state.update { s -> if (s.stepIndex > 0 && !s.registered) s.copy(step = RegStep.entries[s.stepIndex - 1]) else s }

    fun next() {
        val s = _state.value
        val errors = validate(s.step, s)
        if (errors.isNotEmpty()) { _state.update { it.copy(errors = errors) }; return }
        when (s.step) {
            RegStep.CATALOG -> submit()
            RegStep.DOCUMENTS -> _events.tryEmit(RegistrationEvent.Finished)
            else -> _state.update { it.copy(step = RegStep.entries[it.stepIndex + 1]) }
        }
    }

    fun validate(step: RegStep, s: RegistrationUiState): Map<String, String> = buildMap {
        when (step) {
            RegStep.BUSINESS -> {
                if (s.name.trim().length < 2) put(RegFields.NAME, "required")
                if (s.ownerName.trim().length < 2) put(RegFields.OWNER, "required")
                if (!PhoneUtils.isValidIndian(s.phone)) put(RegFields.PHONE, "phone")
                if (s.email.isNotBlank() && !s.email.trim().matches(EMAIL_REGEX)) put(RegFields.EMAIL, "email")
            }
            RegStep.ADDRESS -> {
                if (s.pin == null || !s.pin.isValid()) put(RegFields.PIN, "pin")
                if (s.addressLine1.isBlank()) put(RegFields.ADDRESS1, "required")
                if (s.city.isBlank()) put(RegFields.CITY, "required")
                if (s.postalCode.isNotBlank() && !s.postalCode.matches(Regex("^[1-9][0-9]{5}$"))) put(RegFields.POSTAL, "postal")
                val radius = s.deliveryRadiusKm.toDoubleOrNull()
                if (radius == null || radius <= 0 || radius > 50) put(RegFields.RADIUS, "radius")
            }
            RegStep.COMPLIANCE -> {
                if (s.fssaiNumber.isNotBlank() && !s.fssaiNumber.matches(Regex("^[0-9]{14}$"))) put(RegFields.FSSAI, "fssai")
                if (s.gstNumber.isNotBlank() && !s.gstNumber.matches(Regex("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$"))) put(RegFields.GST, "gst")
                if (s.panNumber.isNotBlank() && !s.panNumber.matches(Regex("^[A-Z]{5}[0-9]{4}[A-Z]$"))) put(RegFields.PAN, "pan")
            }
            RegStep.BANK -> {
                val hasBank = s.bankAccountNumber.isNotBlank() || s.bankIfsc.isNotBlank() || s.bankAccountName.isNotBlank()
                if (!hasBank && s.upiId.isBlank()) put(RegFields.BANK_NO, "bank_or_upi")
                if (hasBank) {
                    if (s.bankAccountName.isBlank()) put(RegFields.BANK_NAME, "required")
                    if (!s.bankAccountNumber.matches(Regex("^[0-9]{9,18}$"))) put(RegFields.BANK_NO, "account")
                    if (!s.bankIfsc.matches(Regex("^[A-Z]{4}0[A-Z0-9]{6}$"))) put(RegFields.IFSC, "ifsc")
                }
                if (s.upiId.isNotBlank() && !s.upiId.matches(Regex("^[\\w.\\-]{2,}@[A-Za-z]{2,}$"))) put(RegFields.UPI, "upi")
            }
            RegStep.HOURS -> if (!HoursEditor.isValid(s.hours)) put(RegFields.HOURS, "hours")
            RegStep.CATALOG -> {
                if (s.selectedCuisines.isEmpty()) put(RegFields.CUISINES, "cuisines")
                if ((s.minOrderAmount.toDoubleOrNull() ?: -1.0) < 0) put(RegFields.MIN_ORDER, "amount")
                if ((s.packagingCharge.toDoubleOrNull() ?: -1.0) < 0) put(RegFields.PACKAGING, "amount")
                val prep = s.avgPreparationMinutes.toIntOrNull()
                if (prep == null || prep < 5 || prep > 120) put(RegFields.PREP, "prep")
            }
            RegStep.DOCUMENTS -> Unit
        }
    }

    private fun submit() {
        val s = _state.value
        val pin = s.pin ?: return
        _state.update { it.copy(submitting = true, submitError = null) }
        viewModelScope.launch {
            val payload = RegistrationPayload(
                name = s.name.trim(), restaurantType = s.restaurantType, description = s.description.trim(), ownerName = s.ownerName.trim(),
                phone = PhoneUtils.normalizeIndian(s.phone) ?: s.phone, email = s.email.trim(),
                addressLine1 = s.addressLine1.trim(), addressLine2 = s.addressLine2.trim(), landmark = s.landmark.trim(), city = s.city.trim(),
                state = s.state.trim(), postalCode = s.postalCode.trim(), latitude = pin.latitude, longitude = pin.longitude,
                deliveryRadiusKm = s.deliveryRadiusKm.toDouble().toString(),
                fssaiNumber = s.fssaiNumber.trim(), fssaiExpiry = s.fssaiExpiry, gstNumber = s.gstNumber.trim(), panNumber = s.panNumber.trim(),
                bankAccountName = s.bankAccountName.trim(), bankAccountNumber = s.bankAccountNumber.trim(), bankIfsc = s.bankIfsc.trim(), upiId = s.upiId.trim(),
                cuisineIds = s.selectedCuisines.toList(), categoryIds = s.selectedCategories.toList(), isPureVeg = s.isPureVeg,
                minOrderAmount = s.minOrderAmount.toDouble().toString(), packagingCharge = s.packagingCharge.toDouble().toString(),
                avgPreparationMinutes = s.avgPreparationMinutes.toInt(), costForTwo = (s.costForTwo.toDoubleOrNull() ?: 0.0).toString(),
                logo = s.logo, cover = s.cover,
            )
            when (val r = repo.register(payload)) {
                is Result.Ok -> {
                    // Backend seeds 9–23 for every day; apply the owner's schedule right after.
                    repo.saveHours(HoursEditor.toDto(s.hours))
                    _state.update { it.copy(submitting = false, registered = true, step = RegStep.DOCUMENTS) }
                    loadDocuments()
                }
                is Result.Err -> _state.update {
                    val api = r.error as? AppError.Api
                    val fieldErrors = api?.fields?.mapValues { (_, v) -> v.joinToString() } ?: emptyMap()
                    it.copy(submitting = false, submitError = r.error, errors = fieldErrors, step = stepForErrors(fieldErrors.keys) ?: it.step)
                }
            }
        }
    }

    /** Jump back to the first step that has a server-side field error. */
    private fun stepForErrors(keys: Set<String>): RegStep? {
        val map = mapOf(
            RegStep.BUSINESS to setOf(RegFields.NAME, RegFields.OWNER, RegFields.PHONE, RegFields.EMAIL, "restaurant_type", "description", "cost_for_two"),
            RegStep.ADDRESS to setOf(RegFields.ADDRESS1, RegFields.CITY, RegFields.POSTAL, RegFields.PIN, "longitude", RegFields.RADIUS, "state", "landmark"),
            RegStep.COMPLIANCE to setOf(RegFields.FSSAI, "fssai_expiry", RegFields.GST, RegFields.PAN),
            RegStep.BANK to setOf(RegFields.BANK_NAME, RegFields.BANK_NO, RegFields.IFSC, RegFields.UPI),
            RegStep.CATALOG to setOf(RegFields.CUISINES, "category_ids", "logo", "cover_image", RegFields.MIN_ORDER, RegFields.PACKAGING, RegFields.PREP),
        )
        return RegStep.entries.firstOrNull { st -> map[st]?.any { it in keys } == true }
    }

    // ------------------------------------------------------------ documents
    fun loadDocuments() = viewModelScope.launch {
        repo.documents().onSuccess { d -> _state.update { it.copy(documents = d, docError = null) } }.onFailure { e -> _state.update { it.copy(docError = e) } }
    }

    fun uploadDocument(docType: String, number: String, file: Uri) {
        _state.update { it.copy(uploadingDoc = true, docError = null) }
        viewModelScope.launch {
            when (val r = repo.uploadDocument(docType, number, file)) {
                is Result.Ok -> _state.update { it.copy(uploadingDoc = false, documents = listOf(r.value) + it.documents.filterNot { d -> d.id == r.value.id }) }
                is Result.Err -> { _state.update { it.copy(uploadingDoc = false, docError = r.error) }; _events.tryEmit(RegistrationEvent.Message(r.error.message)) }
            }
        }
    }

    fun deleteDocument(id: String) = viewModelScope.launch {
        repo.deleteDocument(id).onSuccess { _state.update { it.copy(documents = it.documents.filterNot { d -> d.id == id }) } }
            .onFailure { e -> _events.tryEmit(RegistrationEvent.Message(e.message)) }
    }
}
