package `in`.localrush.customer.feature.orders.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.localrush.core.auth.SessionManager
import `in`.localrush.core.auth.SessionState
import `in`.localrush.core.common.result.AppError
import `in`.localrush.core.network.dto.OrderListDto
import `in`.localrush.customer.data.repo.OrdersRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class OrdersScope(val wire: String) { ACTIVE("active"), PAST("past") }

data class OrdersTabState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val items: List<OrderListDto> = emptyList(),
    val nextPage: Int? = null,
    val error: AppError? = null,
)

data class OrdersUiState(
    val loggedIn: Boolean = false,
    val scope: OrdersScope = OrdersScope.ACTIVE,
    val active: OrdersTabState = OrdersTabState(),
    val past: OrdersTabState = OrdersTabState(),
) {
    val current: OrdersTabState get() = if (scope == OrdersScope.ACTIVE) active else past
}

@HiltViewModel
class OrdersViewModel @Inject constructor(private val repo: OrdersRepository, session: SessionManager) : ViewModel() {
    private val _state = MutableStateFlow(OrdersUiState())
    val state: StateFlow<OrdersUiState> = _state.asStateFlow()
    private val jobs = mutableMapOf<OrdersScope, Job>()

    init {
        viewModelScope.launch {
            session.state.collect { s ->
                val loggedIn = s is SessionState.LoggedIn
                _state.update { it.copy(loggedIn = loggedIn) }
                if (loggedIn) { load(OrdersScope.ACTIVE); load(OrdersScope.PAST) }
                else _state.update { it.copy(active = OrdersTabState(loading = false), past = OrdersTabState(loading = false)) }
            }
        }
    }

    fun selectScope(s: OrdersScope) = _state.update { it.copy(scope = s) }
    fun refresh() { if (_state.value.loggedIn) load(_state.value.scope, refreshing = true) }
    fun retry() = load(_state.value.scope)

    fun loadMore() {
        val scope = _state.value.scope
        val tab = _state.value.current
        val page = tab.nextPage ?: return
        if (tab.loadingMore) return
        set(scope) { it.copy(loadingMore = true) }
        viewModelScope.launch {
            repo.list(scope.wire, page).fold(
                onOk = { p -> set(scope) { it.copy(loadingMore = false, items = it.items + p.results, nextPage = p.nextPage) } },
                onErr = { set(scope) { it.copy(loadingMore = false) } },
            )
        }
    }

    private fun load(scope: OrdersScope, refreshing: Boolean = false) {
        jobs[scope]?.cancel()
        set(scope) { it.copy(loading = it.items.isEmpty(), refreshing = refreshing, error = null) }
        jobs[scope] = viewModelScope.launch {
            repo.list(scope.wire).fold(
                onOk = { p -> set(scope) { it.copy(loading = false, refreshing = false, items = p.results, nextPage = p.nextPage) } },
                onErr = { e -> set(scope) { it.copy(loading = false, refreshing = false, error = e) } },
            )
        }
    }

    private fun set(scope: OrdersScope, f: (OrdersTabState) -> OrdersTabState) = _state.update { if (scope == OrdersScope.ACTIVE) it.copy(active = f(it.active)) else it.copy(past = f(it.past)) }
}
