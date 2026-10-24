package `in`.localrush.partner.feature.gate.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.localrush.core.common.result.Result
import `in`.localrush.core.common.util.UiState
import `in`.localrush.partner.data.ConnectivityObserver
import `in`.localrush.partner.data.RestaurantRepository
import `in`.localrush.partner.data.RestaurantStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Where a logged-in partner lands. */
enum class GateDestination { REGISTRATION, PENDING_APPROVAL, MAIN }

@HiltViewModel
class GateViewModel @Inject constructor(
    private val repo: RestaurantRepository,
    connectivity: ConnectivityObserver,
) : ViewModel() {
    private val _state = MutableStateFlow<UiState<GateDestination>>(UiState.Loading)
    val state: StateFlow<UiState<GateDestination>> = _state.asStateFlow()
    val online: StateFlow<Boolean> = connectivity.isOnline

    init { load() }

    fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            when (val r = repo.load()) {
                is Result.Ok -> {
                    val restaurant = r.value
                    _state.value = UiState.Success(
                        when {
                            restaurant == null -> GateDestination.REGISTRATION
                            RestaurantStatus.fromWire(restaurant.status) == RestaurantStatus.APPROVED -> GateDestination.MAIN
                            else -> GateDestination.PENDING_APPROVAL
                        },
                    )
                }
                is Result.Err -> _state.value = UiState.Error(r.error)
            }
        }
    }
}
