package `in`.localrush.customer.feature.location.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.localrush.core.common.geo.LatLng
import `in`.localrush.core.common.result.AppError
import `in`.localrush.core.location.LocationClient
import `in`.localrush.core.network.dto.AppConfigDto
import `in`.localrush.core.network.dto.GeocodeResultDto
import `in`.localrush.customer.data.repo.AppPreferences
import `in`.localrush.customer.data.repo.ConfigRepository
import `in`.localrush.customer.data.repo.DeliveryLocation
import `in`.localrush.customer.data.repo.addressLine
import `in`.localrush.customer.data.repo.shortTitle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PickLocationUiState(
    val config: AppConfigDto = AppConfigDto(),
    val pin: LatLng? = null,
    /** Reverse-geocoded address under the pin. */
    val address: GeocodeResultDto? = null,
    val resolving: Boolean = false,
    val resolveError: AppError? = null,
    val locating: Boolean = false,
    val query: String = "",
    val results: List<GeocodeResultDto> = emptyList(),
    val searching: Boolean = false,
    val searchError: AppError? = null,
    val saving: Boolean = false,
) {
    val canConfirm: Boolean get() = pin != null && address != null && !resolving && !saving
}

sealed interface PickLocationEvent {
    data object Saved : PickLocationEvent
    data class Message(val text: String) : PickLocationEvent
    data object LocationUnavailable : PickLocationEvent
}

@OptIn(FlowPreview::class)
@HiltViewModel
class PickLocationViewModel @Inject constructor(
    private val prefs: AppPreferences,
    private val configRepo: ConfigRepository,
    private val locationClient: LocationClient,
) : ViewModel() {
    private val _state = MutableStateFlow(PickLocationUiState())
    val state: StateFlow<PickLocationUiState> = _state.asStateFlow()
    private val _events = MutableSharedFlow<PickLocationEvent>(extraBufferCapacity = 2)
    val events: SharedFlow<PickLocationEvent> = _events.asSharedFlow()

    private val queries = MutableStateFlow("")
    private var resolveJob: Job? = null

    init {
        viewModelScope.launch { configRepo.config.collect { c -> _state.update { it.copy(config = c) } } }
        viewModelScope.launch { configRepo.load() }
        viewModelScope.launch {
            val existing = prefs.currentDeliveryLocation()
            if (existing != null) onPinMoved(existing.point) else useCurrentLocation(silent = true)
        }
        viewModelScope.launch {
            queries.debounce(350).collect { q ->
                if (q.length < 3) { _state.update { it.copy(results = emptyList(), searching = false, searchError = null) }; return@collect }
                _state.update { it.copy(searching = true, searchError = null) }
                val pin = _state.value.pin
                configRepo.geocodeSearch(q, pin?.latitude, pin?.longitude).fold(
                    onOk = { r -> _state.update { it.copy(results = r, searching = false) } },
                    onErr = { e -> _state.update { it.copy(results = emptyList(), searching = false, searchError = e) } },
                )
            }
        }
    }

    fun onQueryChange(q: String) { _state.update { it.copy(query = q) }; queries.value = q }

    fun pickResult(r: GeocodeResultDto) {
        _state.update { it.copy(pin = LatLng(r.latitude, r.longitude), address = r, query = "", results = emptyList(), resolving = false, resolveError = null) }
    }

    /** Called when the map camera settles on a new centre. */
    fun onPinMoved(point: LatLng) {
        if (!point.isValid()) return
        resolveJob?.cancel()
        _state.update { it.copy(pin = point, resolving = true, resolveError = null) }
        resolveJob = viewModelScope.launch {
            delay(400) // let the camera settle
            configRepo.reverseGeocode(point.latitude, point.longitude).fold(
                onOk = { r -> _state.update { it.copy(address = r, resolving = false) } },
                onErr = { e -> _state.update { it.copy(address = null, resolving = false, resolveError = e) } },
            )
        }
    }

    /** [silent]: don't complain when the fix is unavailable (first launch, before the permission prompt). */
    fun useCurrentLocation(silent: Boolean = false) {
        if (_state.value.locating) return
        _state.update { it.copy(locating = true) }
        viewModelScope.launch {
            val loc = locationClient.current()
            _state.update { it.copy(locating = false) }
            if (loc == null) {
                if (!silent) _events.tryEmit(PickLocationEvent.LocationUnavailable)
            } else {
                onPinMoved(LatLng(loc.latitude, loc.longitude))
            }
        }
    }

    fun confirm() {
        val s = _state.value
        val pin = s.pin ?: return
        val addr = s.address ?: return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            prefs.setDeliveryLocation(DeliveryLocation(point = pin, title = addr.shortTitle(), line = addr.addressLine(), city = addr.city))
            prefs.setOnboardingDone()
            _state.update { it.copy(saving = false) }
            _events.tryEmit(PickLocationEvent.Saved)
        }
    }

    /** Surface a UI-originated message (e.g. permission denied) through the same snackbar channel. */
    fun message(text: String) { _events.tryEmit(PickLocationEvent.Message(text)) }

    fun retryResolve() { _state.value.pin?.let { onPinMoved(it) } }
}
