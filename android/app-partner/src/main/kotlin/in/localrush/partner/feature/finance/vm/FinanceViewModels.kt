package `in`.localrush.partner.feature.finance.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.localrush.core.common.money.Money
import `in`.localrush.core.common.result.Result
import `in`.localrush.core.common.util.UiState
import `in`.localrush.partner.data.ConnectivityObserver
import `in`.localrush.partner.data.FinanceRepository
import `in`.localrush.partner.data.FinanceSummaryDto
import `in`.localrush.partner.data.SettlementDto
import `in`.localrush.partner.data.SettlementLineDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import javax.inject.Inject

/** Typed finance summary. All amounts parsed from the backend decimal strings. */
data class FinanceSummary(
    val orders: Int,
    val grossSales: Money,
    val commission: Money,
    val couponDeductions: Money,
    val refundDeductions: Money,
    val taxCollected: Money,
    val netEarnings: Money,
) {
    /** Everything taken off gross before payout. */
    val totalDeductions: Money get() = commission + couponDeductions + refundDeductions
    val averageOrderValue: Money get() = if (orders == 0) Money.ZERO else Money(grossSales.paise / orders)
    val commissionPercentOfGross: Double get() = if (grossSales.isZero) 0.0 else commission.paise * 100.0 / grossSales.paise

    companion object {
        fun from(dto: FinanceSummaryDto) = FinanceSummary(
            orders = dto.orders,
            grossSales = Money.parse(dto.grossSales), commission = Money.parse(dto.commission),
            couponDeductions = Money.parse(dto.couponDeductions), refundDeductions = Money.parse(dto.refundDeductions),
            taxCollected = Money.parse(dto.taxCollected), netEarnings = Money.parse(dto.netEarnings),
        )
    }
}

enum class DatePreset { TODAY, WEEK, MONTH, LAST_MONTH, ALL, CUSTOM }

data class DateRange(val from: LocalDate?, val to: LocalDate?) {
    val fromIso: String? get() = from?.toString()
    val toIso: String? get() = to?.toString()

    companion object {
        fun forPreset(p: DatePreset, today: LocalDate = LocalDate.now()): DateRange = when (p) {
            DatePreset.TODAY -> DateRange(today, today)
            DatePreset.WEEK -> DateRange(today.minusDays(today.dayOfWeek.value - 1L), today)
            DatePreset.MONTH -> DateRange(today.withDayOfMonth(1), today)
            DatePreset.LAST_MONTH -> { val first = today.withDayOfMonth(1).minusMonths(1); DateRange(first, first.plusMonths(1).minusDays(1)) }
            DatePreset.ALL -> DateRange(null, null)
            DatePreset.CUSTOM -> DateRange(today.withDayOfMonth(1), today)
        }
    }
}

data class FinanceData(
    val summary: FinanceSummary,
    val preset: DatePreset,
    val range: DateRange,
    val unsettledLines: List<SettlementLineDto> = emptyList(),
    val lastSettlement: SettlementDto? = null,
    val refreshing: Boolean = false,
    val downloading: Boolean = false,
)

sealed interface FinanceEvent {
    data class Message(val text: String) : FinanceEvent
    data class StatementReady(val file: File) : FinanceEvent
}

@HiltViewModel
class FinanceViewModel @Inject constructor(private val repo: FinanceRepository, connectivity: ConnectivityObserver) : ViewModel() {
    private val _state = MutableStateFlow<UiState<FinanceData>>(UiState.Loading)
    val state: StateFlow<UiState<FinanceData>> = _state.asStateFlow()
    val online: StateFlow<Boolean> = connectivity.isOnline
    private val _events = MutableSharedFlow<FinanceEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<FinanceEvent> = _events.asSharedFlow()

    private var preset = DatePreset.MONTH
    private var range = DateRange.forPreset(preset)

    init { load() }

    fun setPreset(p: DatePreset) { preset = p; range = DateRange.forPreset(p); load() }
    fun setCustomRange(from: LocalDate, to: LocalDate) { preset = DatePreset.CUSTOM; range = DateRange(minOf(from, to), maxOf(from, to)); load() }

    fun load() {
        _state.update { s -> if (s is UiState.Success) s.copy(data = s.data.copy(refreshing = true)) else UiState.Loading }
        viewModelScope.launch {
            val summary = repo.summary(range.fromIso, range.toIso)
            when (summary) {
                is Result.Ok -> {
                    val unsettled = repo.lines(unsettledOnly = true).getOrNull()?.results ?: emptyList()
                    val last = repo.settlements().getOrNull()?.results?.firstOrNull()
                    _state.value = UiState.Success(FinanceData(FinanceSummary.from(summary.value), preset, range, unsettled, last))
                }
                is Result.Err -> _state.update { s ->
                    if (s is UiState.Success) { _events.tryEmit(FinanceEvent.Message(summary.error.message)); s.copy(data = s.data.copy(refreshing = false, preset = preset, range = range)) } else UiState.Error(summary.error)
                }
            }
        }
    }

    fun downloadStatement() {
        _state.update { s -> if (s is UiState.Success) s.copy(data = s.data.copy(downloading = true)) else s }
        viewModelScope.launch {
            when (val r = repo.downloadStatement(range.fromIso, range.toIso)) {
                is Result.Ok -> _events.tryEmit(FinanceEvent.StatementReady(r.value))
                is Result.Err -> _events.tryEmit(FinanceEvent.Message(r.error.message))
            }
            _state.update { s -> if (s is UiState.Success) s.copy(data = s.data.copy(downloading = false)) else s }
        }
    }
}

// ------------------------------------------------------------------ paged lists

data class PagedData<T>(val items: List<T>, val nextPage: Int?, val loadingMore: Boolean = false, val refreshing: Boolean = false, val unsettledOnly: Boolean = false)

@HiltViewModel
class FinanceLinesViewModel @Inject constructor(private val repo: FinanceRepository, connectivity: ConnectivityObserver) : ViewModel() {
    private val _state = MutableStateFlow<UiState<PagedData<SettlementLineDto>>>(UiState.Loading)
    val state: StateFlow<UiState<PagedData<SettlementLineDto>>> = _state.asStateFlow()
    val online: StateFlow<Boolean> = connectivity.isOnline
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()
    private var unsettledOnly = false

    init { load() }

    fun setUnsettledOnly(v: Boolean) { unsettledOnly = v; load() }

    fun load() {
        _state.update { s -> if (s is UiState.Success) s.copy(data = s.data.copy(refreshing = true)) else UiState.Loading }
        viewModelScope.launch {
            when (val r = repo.lines(unsettledOnly, page = 1)) {
                is Result.Ok -> _state.value = if (r.value.results.isEmpty()) UiState.Empty else UiState.Success(PagedData(r.value.results, r.value.nextPage, unsettledOnly = unsettledOnly))
                is Result.Err -> if (_state.value !is UiState.Success) _state.value = UiState.Error(r.error) else _messages.tryEmit(r.error.message)
            }
        }
    }

    fun loadMore() {
        val s = _state.value as? UiState.Success ?: return
        val next = s.data.nextPage ?: return
        if (s.data.loadingMore) return
        _state.value = s.copy(data = s.data.copy(loadingMore = true))
        viewModelScope.launch {
            when (val r = repo.lines(unsettledOnly, page = next)) {
                is Result.Ok -> _state.update { cur -> if (cur is UiState.Success) cur.copy(data = cur.data.copy(items = cur.data.items + r.value.results, nextPage = r.value.nextPage, loadingMore = false)) else cur }
                is Result.Err -> { _messages.tryEmit(r.error.message); _state.update { cur -> if (cur is UiState.Success) cur.copy(data = cur.data.copy(loadingMore = false)) else cur } }
            }
        }
    }
}

@HiltViewModel
class SettlementsViewModel @Inject constructor(private val repo: FinanceRepository, connectivity: ConnectivityObserver) : ViewModel() {
    private val _state = MutableStateFlow<UiState<PagedData<SettlementDto>>>(UiState.Loading)
    val state: StateFlow<UiState<PagedData<SettlementDto>>> = _state.asStateFlow()
    val online: StateFlow<Boolean> = connectivity.isOnline
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init { load() }

    fun load() {
        _state.update { s -> if (s is UiState.Success) s.copy(data = s.data.copy(refreshing = true)) else UiState.Loading }
        viewModelScope.launch {
            when (val r = repo.settlements(page = 1)) {
                is Result.Ok -> _state.value = if (r.value.results.isEmpty()) UiState.Empty else UiState.Success(PagedData(r.value.results, r.value.nextPage))
                is Result.Err -> if (_state.value !is UiState.Success) _state.value = UiState.Error(r.error) else _messages.tryEmit(r.error.message)
            }
        }
    }

    fun loadMore() {
        val s = _state.value as? UiState.Success ?: return
        val next = s.data.nextPage ?: return
        if (s.data.loadingMore) return
        _state.value = s.copy(data = s.data.copy(loadingMore = true))
        viewModelScope.launch {
            when (val r = repo.settlements(page = next)) {
                is Result.Ok -> _state.update { cur -> if (cur is UiState.Success) cur.copy(data = cur.data.copy(items = cur.data.items + r.value.results, nextPage = r.value.nextPage, loadingMore = false)) else cur }
                is Result.Err -> { _messages.tryEmit(r.error.message); _state.update { cur -> if (cur is UiState.Success) cur.copy(data = cur.data.copy(loadingMore = false)) else cur } }
            }
        }
    }
}
