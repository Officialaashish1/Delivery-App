package `in`.localrush.rider.feature.history.vm

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import `in`.localrush.core.common.result.AppError
import `in`.localrush.core.common.util.UiState
import `in`.localrush.rider.data.DeliveryRepository
import `in`.localrush.rider.data.RiderOrderDto
import `in`.localrush.rider.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class HistoryScope(val wire: String?) { ACTIVE("active"), TODAY("today"), COMPLETED("completed"), ALL(null) }

data class HistoryUiState(
    val scope: HistoryScope = HistoryScope.TODAY,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: AppError? = null,
    val items: List<RiderOrderDto> = emptyList(),
    val nextPage: Int? = null,
    val loadingMore: Boolean = false,
) { val hasMore: Boolean get() = nextPage != null }

@HiltViewModel
class HistoryViewModel @Inject constructor(private val repo: DeliveryRepository) : ViewModel() {
    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state

    init { load() }

    fun select(scope: HistoryScope) {
        if (scope == _state.value.scope) return
        _state.update { it.copy(scope = scope, items = emptyList(), nextPage = null) }
        load()
    }

    fun load() {
        val scope = _state.value.scope
        _state.update { it.copy(loading = it.items.isEmpty(), refreshing = it.items.isNotEmpty(), error = null) }
        viewModelScope.launch {
            repo.orders(scope.wire, null).fold(
                onOk = { p -> if (_state.value.scope == scope) _state.update { it.copy(loading = false, refreshing = false, items = p.results, nextPage = p.nextPage) } },
                onErr = { e -> _state.update { it.copy(loading = false, refreshing = false, error = if (it.items.isEmpty()) e else null) } },
            )
        }
    }

    fun loadMore() {
        val s = _state.value
        val page = s.nextPage ?: return
        if (s.loadingMore) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            repo.orders(s.scope.wire, page).fold(
                onOk = { p -> _state.update { it.copy(loadingMore = false, items = it.items + p.results, nextPage = p.nextPage) } },
                onErr = { _state.update { it.copy(loadingMore = false, nextPage = null) } },
            )
        }
    }
}

@HiltViewModel
class OrderDetailViewModel @Inject constructor(private val repo: DeliveryRepository, savedState: SavedStateHandle) : ViewModel() {
    private val orderId = savedState.toRoute<Routes.OrderDetail>().orderId
    private val _state = MutableStateFlow<UiState<RiderOrderDto>>(UiState.Loading)
    val state: StateFlow<UiState<RiderOrderDto>> = _state

    init { load() }

    fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch { repo.order(orderId).fold(onOk = { _state.value = UiState.Success(it) }, onErr = { _state.value = UiState.Error(it) }) }
    }
}
