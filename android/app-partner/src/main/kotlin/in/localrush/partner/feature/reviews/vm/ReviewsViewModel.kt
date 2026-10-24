package `in`.localrush.partner.feature.reviews.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.localrush.core.common.result.Result
import `in`.localrush.core.common.util.UiState
import `in`.localrush.core.network.api.ReviewApi
import `in`.localrush.core.network.dto.ReplyDto
import `in`.localrush.core.network.dto.ReviewDto
import `in`.localrush.core.network.util.apiCall
import `in`.localrush.partner.data.ConnectivityObserver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReviewsData(val reviews: List<ReviewDto>, val nextPage: Int?, val replying: Set<String> = emptySet(), val loadingMore: Boolean = false, val refreshing: Boolean = false) {
    val average: Double get() = if (reviews.isEmpty()) 0.0 else reviews.sumOf { it.restaurantRating }.toDouble() / reviews.size
    val unanswered: Int get() = reviews.count { it.partnerReply.isNullOrBlank() }
}

@HiltViewModel
class ReviewsViewModel @Inject constructor(private val api: ReviewApi, connectivity: ConnectivityObserver) : ViewModel() {
    private val _state = MutableStateFlow<UiState<ReviewsData>>(UiState.Loading)
    val state: StateFlow<UiState<ReviewsData>> = _state.asStateFlow()
    val online: StateFlow<Boolean> = connectivity.isOnline
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init { load() }

    fun load() {
        _state.update { s -> if (s is UiState.Success) s.copy(data = s.data.copy(refreshing = true)) else UiState.Loading }
        viewModelScope.launch {
            when (val r = apiCall { api.partner(page = 1) }) {
                is Result.Ok -> _state.value = if (r.value.results.isEmpty()) UiState.Empty else UiState.Success(ReviewsData(r.value.results, r.value.nextPage))
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
            when (val r = apiCall { api.partner(page = next) }) {
                is Result.Ok -> _state.update { c -> if (c is UiState.Success) c.copy(data = c.data.copy(reviews = c.data.reviews + r.value.results, nextPage = r.value.nextPage, loadingMore = false)) else c }
                is Result.Err -> { _messages.tryEmit(r.error.message); _state.update { c -> if (c is UiState.Success) c.copy(data = c.data.copy(loadingMore = false)) else c } }
            }
        }
    }

    fun reply(reviewId: String, text: String) {
        if (text.isBlank()) return
        _state.update { c -> if (c is UiState.Success) c.copy(data = c.data.copy(replying = c.data.replying + reviewId)) else c }
        viewModelScope.launch {
            when (val r = apiCall { api.partnerReply(reviewId, ReplyDto(text.trim().take(1000))) }) {
                is Result.Ok -> _state.update { c -> if (c is UiState.Success) c.copy(data = c.data.copy(reviews = c.data.reviews.map { if (it.id == reviewId) r.value else it }, replying = c.data.replying - reviewId)) else c }
                is Result.Err -> { _messages.tryEmit(r.error.message); _state.update { c -> if (c is UiState.Success) c.copy(data = c.data.copy(replying = c.data.replying - reviewId)) else c } }
            }
        }
    }
}
