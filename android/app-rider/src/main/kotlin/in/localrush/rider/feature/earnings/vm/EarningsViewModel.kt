package `in`.localrush.rider.feature.earnings.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.localrush.core.common.money.Money
import `in`.localrush.core.common.result.AppError
import `in`.localrush.rider.data.EarningType
import `in`.localrush.rider.data.EarningsPeriod
import `in`.localrush.rider.data.EarningsRepository
import `in`.localrush.rider.data.PeriodTotals
import `in`.localrush.rider.data.RiderEarningDto
import `in`.localrush.rider.data.RiderEarningsDto
import `in`.localrush.rider.data.RiderPayoutDto
import `in`.localrush.rider.data.totalsFor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EarningsUiState(
    val period: EarningsPeriod = EarningsPeriod.TODAY,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: AppError? = null,
    val data: RiderEarningsDto? = null,
    val totals: PeriodTotals? = null,
    val payouts: List<RiderPayoutDto> = emptyList(),
    val payoutsLoading: Boolean = false,
    val payoutsError: AppError? = null,
    val requestingPayout: Boolean = false,
    val payoutRequestError: AppError? = null,
    val showPayoutSheet: Boolean = false,
) {
    val entries: List<RiderEarningDto> get() = data?.entries ?: emptyList()
    /** wallet_balance − cod_cash_in_hand, what the backend will actually let the rider withdraw. */
    val withdrawable: Money get() = data?.summary?.let { (Money.parse(it.walletBalance) - Money.parse(it.codCashInHand)) }?.takeIf { !it.isNegative } ?: Money.ZERO
}

sealed interface EarningsEvent {
    data class PayoutRequested(val amount: String) : EarningsEvent
    data class Error(val error: AppError) : EarningsEvent
}

@HiltViewModel
class EarningsViewModel @Inject constructor(private val repo: EarningsRepository) : ViewModel() {
    private val _state = MutableStateFlow(EarningsUiState())
    val state: StateFlow<EarningsUiState> = _state
    private val _events = MutableSharedFlow<EarningsEvent>(extraBufferCapacity = 2)
    val events: SharedFlow<EarningsEvent> = _events

    init { load(); loadPayouts() }

    fun selectPeriod(p: EarningsPeriod) {
        if (p == _state.value.period) return
        _state.update { it.copy(period = p) }
        load()
    }

    fun load() {
        val period = _state.value.period
        _state.update { it.copy(loading = it.data == null, refreshing = it.data != null, error = null) }
        viewModelScope.launch {
            repo.earnings(period).fold(
                onOk = { d ->
                    if (_state.value.period != period) return@fold
                    _state.update { it.copy(loading = false, refreshing = false, data = d, totals = totals(d, period)) }
                },
                onErr = { e -> _state.update { it.copy(loading = false, refreshing = false, error = if (it.data == null) e else null) }; if (_state.value.data != null) _events.tryEmit(EarningsEvent.Error(e)) },
            )
        }
    }

    fun loadPayouts() {
        _state.update { it.copy(payoutsLoading = true, payoutsError = null) }
        viewModelScope.launch {
            repo.payouts(null).fold(
                onOk = { p -> _state.update { it.copy(payoutsLoading = false, payouts = p.results) } },
                onErr = { e -> _state.update { it.copy(payoutsLoading = false, payoutsError = e) } },
            )
        }
    }

    fun openPayoutSheet() = _state.update { it.copy(showPayoutSheet = true, payoutRequestError = null) }
    fun closePayoutSheet() = _state.update { it.copy(showPayoutSheet = false) }

    /** [amount] null → withdraw everything available. */
    fun requestPayout(amount: Money?) {
        if (_state.value.requestingPayout) return
        _state.update { it.copy(requestingPayout = true, payoutRequestError = null) }
        viewModelScope.launch {
            repo.requestPayout(amount?.toApiString()).fold(
                onOk = { p ->
                    _state.update { it.copy(requestingPayout = false, showPayoutSheet = false) }
                    _events.tryEmit(EarningsEvent.PayoutRequested(p.amount))
                    load(); loadPayouts()
                },
                onErr = { e -> _state.update { it.copy(requestingPayout = false, payoutRequestError = e) } },
            )
        }
    }

    companion object {
        /** Headline totals for the tab: backend summary for today/week/month; `all` is summed from the entries. */
        fun totals(d: RiderEarningsDto, period: EarningsPeriod): PeriodTotals {
            if (period != EarningsPeriod.ALL) return d.summary.totalsFor(period)
            val sum = d.entries.filter { EarningType.fromWire(it.earningType) != EarningType.COD_DEPOSIT }
                .fold(Money.ZERO) { acc, e -> acc + Money.parse(e.amount) }
            return d.summary.totalsFor(period, lifetimeEarnings = sum.toApiString())
        }
    }
}
