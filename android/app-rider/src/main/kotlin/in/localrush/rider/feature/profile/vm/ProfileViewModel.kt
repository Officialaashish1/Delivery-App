package `in`.localrush.rider.feature.profile.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.localrush.core.auth.AuthRepository
import `in`.localrush.core.common.result.AppError
import `in`.localrush.rider.data.ActiveDeliveryStore
import `in`.localrush.rider.data.RiderDocumentDto
import `in`.localrush.rider.data.RiderProfileDto
import `in`.localrush.rider.data.RiderRepository
import `in`.localrush.rider.data.TrackingController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val loading: Boolean = true,
    val error: AppError? = null,
    val profile: RiderProfileDto? = null,
    val documents: List<RiderDocumentDto> = emptyList(),
    val loggingOut: Boolean = false,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repo: RiderRepository,
    private val auth: AuthRepository,
    private val tracking: TrackingController,
    private val store: ActiveDeliveryStore,
) : ViewModel() {
    private val _state = MutableStateFlow(ProfileUiState(profile = repo.profile.value, loading = repo.profile.value == null))
    val state: StateFlow<ProfileUiState> = _state

    init {
        viewModelScope.launch { repo.profile.collect { p -> if (p != null) _state.update { it.copy(profile = p) } } }
        load()
    }

    fun load() {
        _state.update { it.copy(loading = it.profile == null, error = null) }
        viewModelScope.launch {
            repo.refreshProfile().fold(
                onOk = { p -> _state.update { it.copy(loading = false, profile = p) } },
                onErr = { e -> _state.update { it.copy(loading = false, error = if (it.profile == null) e else null) } },
            )
            repo.documents().onSuccess { docs -> _state.update { it.copy(documents = docs) } }
        }
    }

    /** Go offline on the server (best effort), stop tracking, then end the session. */
    fun logout() {
        if (_state.value.loggingOut) return
        _state.update { it.copy(loggingOut = true) }
        viewModelScope.launch {
            if (_state.value.profile?.isOnline == true) repo.setOnline(false, null, null)
            tracking.stop()
            store.setWantsOnline(false)
            store.clear()
            repo.clearCache()
            auth.logout()
        }
    }
}
