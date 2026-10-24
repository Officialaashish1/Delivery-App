package `in`.localrush.customer.feature.search.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.localrush.core.common.result.AppError
import `in`.localrush.customer.data.dto.SearchResultDto
import `in`.localrush.customer.data.repo.AppPreferences
import `in`.localrush.customer.data.repo.RestaurantRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SearchTab { RESTAURANTS, DISHES, CUISINES }

data class SearchUiState(
    val query: String = "",
    val tab: SearchTab = SearchTab.RESTAURANTS,
    val searching: Boolean = false,
    val result: SearchResultDto? = null,
    val error: AppError? = null,
    val recent: List<String> = emptyList(),
) {
    val hasQuery: Boolean get() = query.trim().length >= 2
    val isEmptyResult: Boolean get() = result != null && result.restaurants.isEmpty() && result.items.isEmpty() && result.cuisines.isEmpty()
}

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repo: RestaurantRepository,
    private val prefs: AppPreferences,
) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()
    private val queries = MutableStateFlow("")
    private var job: Job? = null

    init {
        viewModelScope.launch { repo.recent.collect { r -> _state.update { it.copy(recent = r) } } }
        viewModelScope.launch {
            queries.debounce(400).distinctUntilChanged().collect { q -> search(q) }
        }
    }

    fun onQueryChange(q: String) {
        _state.update { it.copy(query = q, error = null, result = if (q.trim().length < 2) null else it.result) }
        queries.value = q.trim()
    }

    fun onTab(tab: SearchTab) = _state.update { it.copy(tab = tab) }

    fun submit() { val q = _state.value.query.trim(); if (q.length >= 2) { queries.value = q; viewModelScope.launch { repo.rememberSearch(q) } } }
    fun pickRecent(q: String) { onQueryChange(q); submit() }
    fun removeRecent(q: String) { viewModelScope.launch { repo.forgetSearch(q) } }
    fun clearRecent() { viewModelScope.launch { repo.clearRecent() } }
    fun retry() { search(_state.value.query.trim()) }

    private fun search(q: String) {
        job?.cancel()
        if (q.length < 2) { _state.update { it.copy(searching = false, result = null) }; return }
        _state.update { it.copy(searching = true, error = null) }
        job = viewModelScope.launch {
            val loc = prefs.deliveryLocation.first()
            repo.search(q, loc?.point?.latitude, loc?.point?.longitude).fold(
                onOk = { r -> _state.update { it.copy(searching = false, result = r) } },
                onErr = { e -> _state.update { it.copy(searching = false, error = e) } },
            )
        }
    }
}
