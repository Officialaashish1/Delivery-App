package `in`.localrush.rider.feature.onboarding.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.localrush.core.common.result.AppError
import `in`.localrush.rider.data.DocType
import `in`.localrush.rider.data.RiderProfileDto
import `in`.localrush.rider.data.RiderRepository
import `in`.localrush.rider.data.RiderStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class Destination { REGISTRATION, PENDING, MAIN }

sealed interface GateState {
    data object Loading : GateState
    data class Error(val error: AppError) : GateState
    data class Go(val destination: Destination) : GateState
}

/** Decides where a logged-in rider lands: registration wizard, pending-approval screen or the main shell. */
@HiltViewModel
class GateViewModel @Inject constructor(private val repo: RiderRepository) : ViewModel() {
    private val _state = MutableStateFlow<GateState>(GateState.Loading)
    val state: StateFlow<GateState> = _state

    init { load() }

    fun load() {
        _state.value = GateState.Loading
        viewModelScope.launch {
            repo.refreshProfile().fold(
                onOk = { _state.value = GateState.Go(decide(it)) },
                onErr = { _state.value = GateState.Error(it) },
            )
        }
    }

    companion object {
        fun decide(p: RiderProfileDto): Destination = when (RiderStatus.fromWire(p.status)) {
            RiderStatus.APPROVED -> Destination.MAIN
            RiderStatus.REJECTED, RiderStatus.SUSPENDED, RiderStatus.UNDER_REVIEW -> Destination.PENDING
            else -> if (isRegistrationComplete(p)) Destination.PENDING else Destination.REGISTRATION
        }

        fun isRegistrationComplete(p: RiderProfileDto): Boolean {
            val docs = p.documentStatuses
            val requiredDocs = DocType.entries.filter { it.required }.all { docs.containsKey(it.wire) }
            val bank = p.upiId.isNotBlank() || (p.bankAccountNumber.isNotBlank() && p.bankIfsc.isNotBlank())
            return p.fullName.isNotBlank() && p.vehicleNumber.isNotBlank() && p.drivingLicenceNumber.isNotBlank() && requiredDocs && bank &&
                p.emergencyContactPhone.isNotBlank()
        }
    }
}
