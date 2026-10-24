package `in`.localrush.rider.feature.onboarding.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.localrush.core.common.result.AppError
import `in`.localrush.core.common.util.UiState
import `in`.localrush.rider.data.RiderProfileDto
import `in`.localrush.rider.data.RiderRepository
import `in`.localrush.rider.data.RiderStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Polls `riders/profile/` every 15 s until the account is approved. */
@HiltViewModel
class PendingViewModel @Inject constructor(private val repo: RiderRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState<RiderProfileDto>>(UiState.Loading)
    val state: StateFlow<UiState<RiderProfileDto>> = _state
    private val _approved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val approved: SharedFlow<Unit> = _approved
    private var polling: Job? = null

    init { refresh(); startPolling() }

    fun refresh() {
        if (_state.value !is UiState.Success) _state.value = UiState.Loading
        viewModelScope.launch { fetch() }
    }

    private suspend fun fetch() {
        repo.refreshProfile().fold(
            onOk = { p ->
                _state.value = UiState.Success(p)
                if (RiderStatus.fromWire(p.status) == RiderStatus.APPROVED) _approved.tryEmit(Unit)
            },
            onErr = { e: AppError -> if (_state.value !is UiState.Success) _state.value = UiState.Error(e) },
        )
    }

    private fun startPolling() {
        polling?.cancel()
        polling = viewModelScope.launch {
            while (isActive) { delay(15_000); fetch() }
        }
    }
}
