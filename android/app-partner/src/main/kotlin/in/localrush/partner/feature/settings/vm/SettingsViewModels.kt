package `in`.localrush.partner.feature.settings.vm

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.localrush.core.common.money.Money
import `in`.localrush.core.common.result.AppError
import `in`.localrush.core.common.result.Result
import `in`.localrush.core.common.util.PhoneUtils
import `in`.localrush.core.common.util.UiState
import `in`.localrush.partner.data.ConnectivityObserver
import `in`.localrush.partner.data.CuisineDto
import `in`.localrush.partner.data.HolidayDto
import `in`.localrush.partner.data.PartnerRestaurantDto
import `in`.localrush.partner.data.RestaurantCategoryDto
import `in`.localrush.partner.data.RestaurantDocumentDto
import `in`.localrush.partner.data.RestaurantPatchDto
import `in`.localrush.partner.data.RestaurantRepository
import `in`.localrush.partner.data.RestaurantStatus
import `in`.localrush.partner.feature.registration.vm.EMAIL_REGEX
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** Loads the restaurant once and shares it with each settings sub-screen. */
abstract class RestaurantBackedViewModel(protected val repo: RestaurantRepository, connectivity: ConnectivityObserver) : ViewModel() {
    protected val _state = MutableStateFlow<UiState<PartnerRestaurantDto>>(UiState.Loading)
    val state: StateFlow<UiState<PartnerRestaurantDto>> = _state.asStateFlow()
    val online: StateFlow<Boolean> = connectivity.isOnline
    protected val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()
    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    init {
        viewModelScope.launch { repo.restaurant.collect { r -> if (r != null) _state.value = UiState.Success(r) } }
        load()
    }

    fun load() {
        if (_state.value !is UiState.Success) _state.value = UiState.Loading
        viewModelScope.launch {
            when (val r = repo.load()) {
                is Result.Ok -> r.value?.let { _state.value = UiState.Success(it) } ?: run { _state.value = UiState.Error(AppError.Api("not_found", "Restaurant not found", 404)) }
                is Result.Err -> if (_state.value !is UiState.Success) _state.value = UiState.Error(r.error) else _messages.tryEmit(r.error.message)
            }
        }
    }

    protected fun saving(block: suspend () -> Result<*, AppError>, onOk: () -> Unit = {}) = viewModelScope.launch {
        _saving.value = true
        when (val r = block()) {
            is Result.Ok -> onOk()
            is Result.Err -> _messages.tryEmit((r.error as? AppError.Api)?.let { api -> api.fields.values.firstOrNull()?.firstOrNull() ?: api.message } ?: r.error.message)
        }
        _saving.value = false
    }
}

// ------------------------------------------------------------------ settings hub

@HiltViewModel
class RestaurantSettingsViewModel @Inject constructor(repo: RestaurantRepository, connectivity: ConnectivityObserver) : RestaurantBackedViewModel(repo, connectivity) {
    private val _saved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saved: SharedFlow<Unit> = _saved.asSharedFlow()

    fun setOpen(open: Boolean) = saving({ repo.toggle(isOpen = open) })
    fun setAccepting(v: Boolean) = saving({ repo.toggle(isAcceptingOrders = v) })
    fun closeFor(minutes: Int?) = saving({
        if (minutes == null) repo.toggle(clearClosure = true) else repo.toggle(temporarilyClosedUntil = DateTimeFormatter.ISO_INSTANT.format(Instant.now().plusSeconds(minutes * 60L)))
    })

    /** Validates and saves order settings; returns field errors (empty = submitted). */
    fun saveOrderSettings(minOrder: String, packaging: String, radius: String, prep: String, pureVeg: Boolean): Map<String, String> {
        val errors = buildMap {
            if (Money.parseOrNull(minOrder) == null || minOrder.toDoubleOrNull()!! < 0) put("min_order_amount", "amount")
            if (Money.parseOrNull(packaging) == null || packaging.toDoubleOrNull()!! < 0) put("packaging_charge", "amount")
            val r = radius.toDoubleOrNull(); if (r == null || r <= 0 || r > 50) put("delivery_radius_km", "radius")
            val p = prep.toIntOrNull(); if (p == null || p < 5 || p > 120) put("avg_preparation_minutes", "prep")
        }
        if (errors.isNotEmpty()) return errors
        saving({
            repo.patch(RestaurantPatchDto(minOrderAmount = Money.parse(minOrder).toApiString(), packagingCharge = Money.parse(packaging).toApiString(), deliveryRadiusKm = radius.toDouble().toString(), avgPreparationMinutes = prep.toInt(), isPureVeg = pureVeg))
        }) { _saved.tryEmit(Unit) }
        return emptyMap()
    }

    fun updateImages(logo: Uri?, cover: Uri?) = saving({ repo.updateImages(logo, cover) })
}

// ------------------------------------------------------------------ hours

@HiltViewModel
class HoursViewModel @Inject constructor(repo: RestaurantRepository, connectivity: ConnectivityObserver) : RestaurantBackedViewModel(repo, connectivity) {
    private val _draft = MutableStateFlow<List<DayHours>?>(null)
    val draft: StateFlow<List<DayHours>?> = _draft.asStateFlow()
    private val _saved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saved: SharedFlow<Unit> = _saved.asSharedFlow()

    init { viewModelScope.launch { state.collect { s -> if (s is UiState.Success && _draft.value == null) _draft.value = HoursEditor.fromDto(s.data.hours) } } }

    fun edit(days: List<DayHours>) { _draft.value = days }
    val isValid: Boolean get() = _draft.value?.let { HoursEditor.isValid(it) } == true
    fun save() { val d = _draft.value ?: return; if (!HoursEditor.isValid(d)) return; saving({ repo.saveHours(HoursEditor.toDto(d)) }) { _saved.tryEmit(Unit) } }
}

// ------------------------------------------------------------------ holidays

data class HolidaysData(val holidays: List<HolidayDto>, val busy: Boolean = false)

@HiltViewModel
class HolidaysViewModel @Inject constructor(private val repo: RestaurantRepository, connectivity: ConnectivityObserver) : ViewModel() {
    private val _state = MutableStateFlow<UiState<HolidaysData>>(UiState.Loading)
    val state: StateFlow<UiState<HolidaysData>> = _state.asStateFlow()
    val online: StateFlow<Boolean> = connectivity.isOnline
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init { load() }

    fun load() {
        if (_state.value !is UiState.Success) _state.value = UiState.Loading
        viewModelScope.launch {
            when (val r = repo.holidays()) {
                is Result.Ok -> _state.value = if (r.value.isEmpty()) UiState.Empty else UiState.Success(HolidaysData(r.value.sortedBy { it.date }))
                is Result.Err -> if (_state.value !is UiState.Success) _state.value = UiState.Error(r.error) else _messages.tryEmit(r.error.message)
            }
        }
    }

    fun add(date: String, reason: String) = viewModelScope.launch {
        repo.addHoliday(date, reason).onFailure { e -> _messages.tryEmit((e as? AppError.Api)?.fieldError("date") ?: e.message) }
        load()
    }

    fun delete(id: Int) = viewModelScope.launch {
        _state.update { s -> if (s is UiState.Success) s.copy(data = s.data.copy(busy = true)) else s }
        repo.deleteHoliday(id).onFailure { _messages.tryEmit(it.message) }
        load()
    }
}

// ------------------------------------------------------------------ documents

data class DocumentsData(val documents: List<RestaurantDocumentDto>, val uploading: Boolean = false)

@HiltViewModel
class DocumentsViewModel @Inject constructor(private val repo: RestaurantRepository, connectivity: ConnectivityObserver) : ViewModel() {
    private val _state = MutableStateFlow<UiState<DocumentsData>>(UiState.Loading)
    val state: StateFlow<UiState<DocumentsData>> = _state.asStateFlow()
    val online: StateFlow<Boolean> = connectivity.isOnline
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init { load() }

    fun load() {
        if (_state.value !is UiState.Success) _state.value = UiState.Loading
        viewModelScope.launch {
            when (val r = repo.documents()) {
                is Result.Ok -> _state.value = UiState.Success(DocumentsData(r.value))
                is Result.Err -> if (_state.value !is UiState.Success) _state.value = UiState.Error(r.error) else _messages.tryEmit(r.error.message)
            }
        }
    }

    fun upload(docType: String, number: String, file: Uri) {
        _state.update { s -> if (s is UiState.Success) s.copy(data = s.data.copy(uploading = true)) else s }
        viewModelScope.launch {
            repo.uploadDocument(docType, number, file).onFailure { e -> _messages.tryEmit((e as? AppError.Api)?.fieldError("file") ?: e.message) }
            _state.update { s -> if (s is UiState.Success) s.copy(data = s.data.copy(uploading = false)) else s }
            load()
        }
    }

    fun delete(id: String) = viewModelScope.launch { repo.deleteDocument(id).onFailure { _messages.tryEmit(it.message) }; load() }
}

// ------------------------------------------------------------------ profile edit

data class ProfileForm(
    val name: String = "", val restaurantType: String = "restaurant", val description: String = "", val ownerName: String = "", val phone: String = "", val email: String = "",
    val addressLine1: String = "", val addressLine2: String = "", val landmark: String = "", val city: String = "", val state: String = "", val postalCode: String = "",
    val fssaiNumber: String = "", val gstNumber: String = "", val panNumber: String = "", val bankAccountName: String = "", val bankAccountNumber: String = "", val bankIfsc: String = "", val upiId: String = "",
    val cuisineIds: Set<Int> = emptySet(), val categoryIds: Set<Int> = emptySet(), val costForTwo: String = "",
) {
    companion object {
        fun from(r: PartnerRestaurantDto) = ProfileForm(
            r.name, r.restaurantType, r.description, r.ownerName, PhoneUtils.localPart(r.phone), r.email, r.addressLine1, r.addressLine2, r.landmark, r.city, r.state, r.postalCode,
            r.fssaiNumber, r.gstNumber, r.panNumber, r.bankAccountName, r.bankAccountNumber, r.bankIfsc, r.upiId,
            r.cuisines.map { it.id }.toSet(), r.categories.map { it.id }.toSet(), Money.parse(r.costForTwo).rupees.stripTrailingZeros().toPlainString(),
        )
    }
}

data class ProfileEditData(
    val form: ProfileForm,
    val cuisines: List<CuisineDto>,
    val categories: List<RestaurantCategoryDto>,
    /** Compliance/bank fields are locked once approved (backend `locked_fields`). */
    val complianceLocked: Boolean,
    val errors: Map<String, String> = emptyMap(),
    val saving: Boolean = false,
)

@HiltViewModel
class ProfileEditViewModel @Inject constructor(private val repo: RestaurantRepository, connectivity: ConnectivityObserver) : ViewModel() {
    private val _state = MutableStateFlow<UiState<ProfileEditData>>(UiState.Loading)
    val state: StateFlow<UiState<ProfileEditData>> = _state.asStateFlow()
    val online: StateFlow<Boolean> = connectivity.isOnline
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()
    private val _saved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saved: SharedFlow<Unit> = _saved.asSharedFlow()

    init { load() }

    fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            val r = repo.restaurant.value?.let { Result.Ok(it) } ?: repo.load().map { it }
            val restaurant = when (r) {
                is Result.Ok -> r.value ?: run { _state.value = UiState.Error(AppError.Api("not_found", "Restaurant not found", 404)); return@launch }
                is Result.Err -> { _state.value = UiState.Error(r.error); return@launch }
            }
            val cuisines = repo.cuisines().getOrNull() ?: emptyList()
            val categories = repo.restaurantCategories().getOrNull() ?: emptyList()
            _state.value = UiState.Success(ProfileEditData(ProfileForm.from(restaurant), cuisines, categories, complianceLocked = RestaurantStatus.fromWire(restaurant.status) == RestaurantStatus.APPROVED))
        }
    }

    fun edit(block: (ProfileForm) -> ProfileForm) = _state.update { s -> if (s is UiState.Success) s.copy(data = s.data.copy(form = block(s.data.form), errors = emptyMap())) else s }

    fun save() {
        val s = _state.value as? UiState.Success ?: return
        val f = s.data.form
        val errors = buildMap {
            if (f.name.trim().length < 2) put("name", "required")
            if (f.ownerName.trim().length < 2) put("owner_name", "required")
            if (!PhoneUtils.isValidIndian(f.phone)) put("phone", "phone")
            if (f.email.isNotBlank() && !f.email.trim().matches(EMAIL_REGEX)) put("email", "email")
            if (f.addressLine1.isBlank()) put("address_line1", "required")
            if (f.city.isBlank()) put("city", "required")
            if (f.cuisineIds.isEmpty()) put("cuisine_ids", "cuisines")
        }
        if (errors.isNotEmpty()) { _state.value = s.copy(data = s.data.copy(errors = errors)); return }
        _state.value = s.copy(data = s.data.copy(saving = true))
        viewModelScope.launch {
            val locked = s.data.complianceLocked
            val patch = RestaurantPatchDto(
                name = f.name.trim(), restaurantType = f.restaurantType, description = f.description.trim(), ownerName = f.ownerName.trim(),
                phone = PhoneUtils.normalizeIndian(f.phone), email = f.email.trim(),
                addressLine1 = f.addressLine1.trim(), addressLine2 = f.addressLine2.trim(), landmark = f.landmark.trim(), city = f.city.trim(), state = f.state.trim(), postalCode = f.postalCode.trim(),
                fssaiNumber = if (locked) null else f.fssaiNumber.trim(), gstNumber = if (locked) null else f.gstNumber.trim(), panNumber = if (locked) null else f.panNumber.trim(),
                bankAccountName = if (locked) null else f.bankAccountName.trim(), bankAccountNumber = if (locked) null else f.bankAccountNumber.trim(),
                bankIfsc = if (locked) null else f.bankIfsc.trim(), upiId = f.upiId.trim(),
                cuisineIds = f.cuisineIds.toList(), categoryIds = f.categoryIds.toList(), costForTwo = (f.costForTwo.toDoubleOrNull() ?: 0.0).toString(),
            )
            when (val r = repo.patch(patch)) {
                is Result.Ok -> _saved.tryEmit(Unit)
                is Result.Err -> {
                    val api = r.error as? AppError.Api
                    _state.update { cur -> if (cur is UiState.Success) cur.copy(data = cur.data.copy(saving = false, errors = api?.fields?.mapValues { it.value.joinToString() } ?: emptyMap())) else cur }
                    _messages.tryEmit(r.error.message)
                }
            }
        }
    }
}
