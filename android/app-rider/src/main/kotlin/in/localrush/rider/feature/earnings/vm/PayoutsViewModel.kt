package `in`.localrush.rider.feature.earnings.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.localrush.core.common.result.AppError
import `in`.localrush.rider.data.EarningsRepository
import `in`.localrush.rider.data.RiderPayoutDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PayoutsUiState(
    val loading: Boolean = true,
    val error: AppError? = null,
    val items: List<RiderPayoutDto> = emptyList(),
    val nextPage: Int? = null,
    val loadingMore: Boolean = false,
) { val hasMore: Boolean get() = nextPage != null }

@HiltViewModel
class PayoutsViewModel @Inject constructor(private val repo: EarningsRepository) : ViewModel() {
    private val _state = MutableStateFlow(PayoutsUiState())
    val state: StateFlow<PayoutsUiState> = _state

    init { load() }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            repo.payouts(null).fold(
                onOk = { p -> _state.update { it.copy(loading = false, items = p.results, nextPage = p.nextPage) } },
                onErr = { e -> _state.update { it.copy(loading = false, error = e) } },
            )
        }
    }

    fun loadMore() {
        val s = _state.value
        val page = s.nextPage ?: return
        if (s.loadingMore) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            repo.payouts(page).fold(
                onOk = { p -> _state.update { it.copy(loadingMore = false, items = it.items + p.results, nextPage = p.nextPage) } },
                onErr = { _state.update { it.copy(loadingMore = false, nextPage = null) } },
            )
        }
    }
}
