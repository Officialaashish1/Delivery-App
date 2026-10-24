package `in`.localrush.rider.feature.offer.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.localrush.core.common.result.AppError
import `in`.localrush.core.common.result.ErrorCodes
import `in`.localrush.rider.data.OfferInbox
import `in`.localrush.rider.data.OffersRepository
import `in`.localrush.rider.data.RiderOfferDto
import `in`.localrush.rider.data.RiderOrderDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class GoneReason { TIMED_OUT, UNAVAILABLE, ALREADY_HANDLED }

sealed interface OfferUiState {
    data object Loading : OfferUiState
    data class Error(val error: AppError) : OfferUiState
    data class Ready(
        val offer: RiderOfferDto,
        val secondsLeft: Int,
        val totalSeconds: Int,
        val accepting: Boolean = false,
        val rejecting: Boolean = false,
        val showRejectSheet: Boolean = false,
        val actionError: AppError? = null,
    ) : OfferUiState {
        val progress: Float get() = if (totalSeconds <= 0) 0f else (secondsLeft.toFloat() / totalSeconds).coerceIn(0f, 1f)
        val busy: Boolean get() = accepting || rejecting
    }
    data class Gone(val reason: GoneReason) : OfferUiState
    data class Accepted(val order: RiderOrderDto) : OfferUiState
    data object Rejected : OfferUiState
}

/**
 * Full-screen offer: loads `riders/offers/{id}/`, counts down from `seconds_left`, accepts or rejects.
 * 409 `offer_expired` / `offer_unavailable` and a zero countdown all end in [OfferUiState.Gone].
 */
@HiltViewModel
class OfferViewModel @Inject constructor(
    private val repo: OffersRepository,
    private val inbox: OfferInbox,
) : ViewModel() {
    private val _state = MutableStateFlow<OfferUiState>(OfferUiState.Loading)
    val state: StateFlow<OfferUiState> = _state
    private var assignmentId: String? = null
    private var ticker: Job? = null

    fun load(id: String) {
        if (assignmentId == id && _state.value !is OfferUiState.Error) return
        assignmentId = id
        _state.value = OfferUiState.Loading
        viewModelScope.launch {
            repo.offer(id).fold(
                onOk = { offer ->
                    inbox.consume(id)
                    if (offer.status != "offered" && offer.status.isNotBlank()) { _state.value = OfferUiState.Gone(GoneReason.ALREADY_HANDLED); return@fold }
                    if (offer.secondsLeft <= 0) { _state.value = OfferUiState.Gone(GoneReason.TIMED_OUT); return@fold }
                    _state.value = OfferUiState.Ready(offer, offer.secondsLeft, offer.secondsLeft)
                    startTicker()
                },
                onErr = { e ->
                    inbox.consume(id)
                    _state.value = when {
                        e is AppError.Api && e.isNotFound -> OfferUiState.Gone(GoneReason.ALREADY_HANDLED)
                        e.isOfferGone() -> OfferUiState.Gone(e.goneReason())
                        else -> OfferUiState.Error(e)
                    }
                },
            )
        }
    }

    fun retry() { assignmentId?.let { id -> assignmentId = null; load(id) } }

    private fun startTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (isActive) {
                delay(1_000)
                val s = _state.value as? OfferUiState.Ready ?: return@launch
                val left = s.secondsLeft - 1
                if (left <= 0 && !s.accepting) { _state.value = OfferUiState.Gone(GoneReason.TIMED_OUT); return@launch }
                _state.value = s.copy(secondsLeft = left.coerceAtLeast(0))
            }
        }
    }

    fun accept() {
        val s = _state.value as? OfferUiState.Ready ?: return
        if (s.busy) return
        _state.value = s.copy(accepting = true, actionError = null)
        viewModelScope.launch {
            repo.accept(s.offer.id).fold(
                onOk = { order -> ticker?.cancel(); _state.value = OfferUiState.Accepted(order) },
                onErr = { e ->
                    if (e.isOfferGone()) { ticker?.cancel(); _state.value = OfferUiState.Gone(e.goneReason()) }
                    else _state.update { st -> (st as? OfferUiState.Ready)?.copy(accepting = false, actionError = e) ?: st }
                },
            )
        }
    }

    fun openReject() = _state.update { (it as? OfferUiState.Ready)?.copy(showRejectSheet = true) ?: it }
    fun closeReject() = _state.update { (it as? OfferUiState.Ready)?.copy(showRejectSheet = false) ?: it }

    fun reject(reason: String) {
        val s = _state.value as? OfferUiState.Ready ?: return
        if (s.busy) return
        _state.value = s.copy(rejecting = true, showRejectSheet = false, actionError = null)
        viewModelScope.launch {
            repo.reject(s.offer.id, reason).fold(
                onOk = { ticker?.cancel(); _state.value = OfferUiState.Rejected },
                onErr = { e ->
                    // Backend treats a non-offered assignment as a no-op; anything else: leave the screen too, the offer will expire.
                    if (e.isOfferGone() || (e is AppError.Api && e.isNotFound)) { ticker?.cancel(); _state.value = OfferUiState.Rejected }
                    else _state.update { st -> (st as? OfferUiState.Ready)?.copy(rejecting = false, actionError = e) ?: st }
                },
            )
        }
    }

    fun clearError() = _state.update { (it as? OfferUiState.Ready)?.copy(actionError = null) ?: it }

    private fun AppError.isOfferGone(): Boolean = this is AppError.Api && (code == ErrorCodes.OFFER_EXPIRED || code == ErrorCodes.OFFER_UNAVAILABLE)
    private fun AppError.goneReason(): GoneReason = if ((this as? AppError.Api)?.code == ErrorCodes.OFFER_EXPIRED) GoneReason.TIMED_OUT else GoneReason.UNAVAILABLE
}
