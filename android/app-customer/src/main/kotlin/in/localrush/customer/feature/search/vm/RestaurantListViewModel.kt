package `in`.localrush.customer.feature.search.vm

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import `in`.localrush.core.common.result.AppError
import `in`.localrush.customer.data.dto.RestaurantCardDto
import `in`.localrush.customer.data.repo.AppPreferences
import `in`.localrush.customer.data.repo.RestaurantFilters
import `in`.localrush.customer.data.repo.RestaurantRepository
import `in`.localrush.customer.data.repo.RestaurantSort
import `in`.localrush.customer.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RestaurantListUiState(
    val title: String = "",
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val items: List<RestaurantCardDto> = emptyList(),
    val nextPage: Int? = null,
    val error: AppError? = null,
    val filters: RestaurantFilters = RestaurantFilters(),
)

/** `GET restaurants/` filtered by category / cuisine / query / section, with the filter sheet state. */
@HiltViewModel
class RestaurantListViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: RestaurantRepository,
    private val prefs: AppPreferences,
) : ViewModel() {
    private val route = savedState.toRoute<Routes.RestaurantList>()
    private val _state = MutableStateFlow(RestaurantListUiState(title = route.title, filters = initialFilters(route)))
    val state: StateFlow<RestaurantListUiState> = _state.asStateFlow()
    private var job: Job? = null

    init { load() }

    fun applyFilters(filters: RestaurantFilters) { _state.update { it.copy(filters = filters) }; load() }
    fun retry() = load()

    fun loadMore() {
        val s = _state.value
        val page = s.nextPage ?: return
        if (s.loadingMore) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            fetch(page).fold(
                onOk = { p -> _state.update { it.copy(loadingMore = false, items = it.items + p.results, nextPage = p.nextPage) } },
                onErr = { _state.update { it.copy(loadingMore = false) } },
            )
        }
    }

    private fun load() {
        job?.cancel()
        _state.update { it.copy(loading = true, error = null) }
        job = viewModelScope.launch {
            fetch(null).fold(
                onOk = { p -> _state.update { it.copy(loading = false, items = p.results, nextPage = p.nextPage) } },
                onErr = { e -> _state.update { it.copy(loading = false, error = e) } },
            )
        }
    }

    private suspend fun fetch(page: Int?) = run {
        val loc = prefs.deliveryLocation.first()
        repo.list(loc?.point?.latitude, loc?.point?.longitude, query = route.query, cuisine = route.cuisine, category = route.category, filters = _state.value.filters, page = page)
    }

    private fun initialFilters(r: Routes.RestaurantList): RestaurantFilters = RestaurantFilters(
        openNow = r.openNow,
        sort = RestaurantSort.entries.firstOrNull { it.wire == r.sort } ?: RestaurantSort.RELEVANCE,
    )
}
