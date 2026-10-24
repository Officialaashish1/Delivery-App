package `in`.localrush.partner.feature.dashboard.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.localrush.core.auth.AuthRepository
import `in`.localrush.core.common.result.Result
import `in`.localrush.core.common.util.UiState
import `in`.localrush.partner.data.ConnectivityObserver
import `in`.localrush.partner.data.DashboardDto
import `in`.localrush.partner.data.MenuItemDto
import `in`.localrush.partner.data.PartnerRestaurantDto
import `in`.localrush.partner.data.RestaurantRepository
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

data class DashboardData(
    val dashboard: DashboardDto,
    val restaurant: PartnerRestaurantDto?,
    val bestSellers: List<MenuItemDto> = emptyList(),
    val toggling: Boolean = false,
    val refreshing: Boolean = false,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repo: RestaurantRepository,
    private val auth: AuthRepository,
    connectivity: ConnectivityObserver,
) : ViewModel() {
    private val _state = MutableStateFlow<UiState<DashboardData>>(UiState.Loading)
    val state: StateFlow<UiState<DashboardData>> = _state.asStateFlow()
    val online: StateFlow<Boolean> = connectivity.isOnline
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        load()
        viewModelScope.launch { repo.restaurant.collect { r -> _state.update { s -> if (s is UiState.Success && r != null) s.copy(data = s.data.copy(restaurant = r)) else s } } }
    }

    fun load() {
        _state.update { s -> if (s is UiState.Success) s.copy(data = s.data.copy(refreshing = true)) else UiState.Loading }
        viewModelScope.launch {
            val dash = repo.dashboard()
            when (dash) {
                is Result.Ok -> {
                    val restaurant = repo.restaurant.value ?: repo.load().getOrNull()
                    val best = repo.bestSellers().getOrNull() ?: emptyList()
                    _state.value = UiState.Success(DashboardData(dash.value, restaurant, best))
                }
                is Result.Err -> _state.update { s ->
                    if (s is UiState.Success) { _messages.tryEmit(dash.error.message); s.copy(data = s.data.copy(refreshing = false)) } else UiState.Error(dash.error)
                }
            }
        }
    }

    fun setOpen(open: Boolean) = toggle(isOpen = open)
    fun setAccepting(accepting: Boolean) = toggle(isAcceptingOrders = accepting)
    /** Temporarily close for [minutes]; null clears the closure. */
    fun temporaryClosure(minutes: Int?) {
        if (minutes == null) toggle(clearClosure = true)
        else toggle(temporarilyClosedUntil = DateTimeFormatter.ISO_INSTANT.format(Instant.now().plusSeconds(minutes * 60L)))
    }

    private fun toggle(isOpen: Boolean? = null, isAcceptingOrders: Boolean? = null, temporarilyClosedUntil: String? = null, clearClosure: Boolean = false) {
        _state.update { s -> if (s is UiState.Success) s.copy(data = s.data.copy(toggling = true)) else s }
        viewModelScope.launch {
            val r = repo.toggle(isOpen, isAcceptingOrders, temporarilyClosedUntil, clearClosure)
            r.onFailure { _messages.tryEmit(it.message) }
            _state.update { s -> if (s is UiState.Success) s.copy(data = s.data.copy(toggling = false, restaurant = r.getOrNull() ?: s.data.restaurant)) else s }
            if (r.isOk) load()
        }
    }

    fun logout() = viewModelScope.launch { auth.logout() }
}
