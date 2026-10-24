package `in`.localrush.customer.feature.orders.vm

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.localrush.core.common.result.AppError
import `in`.localrush.core.network.dto.CreateReviewDto
import `in`.localrush.core.network.dto.OrderDetailDto
import `in`.localrush.customer.data.repo.OrdersRepository
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

data class RateUiState(
    val loading: Boolean = true,
    val order: OrderDetailDto? = null,
    val error: AppError? = null,
    val restaurantRating: Int = 0,
    val restaurantComment: String = "",
    val riderRating: Int = 0,
    val riderComment: String = "",
    val itemRatings: Map<String, Int> = emptyMap(),
    val tags: Set<String> = emptySet(),
    val submitting: Boolean = false,
    val submitError: String? = null,
) {
    val canSubmit: Boolean get() = restaurantRating in 1..5 && !submitting
}

sealed interface RateEvent { data object Done : RateEvent }

@HiltViewModel
class RateViewModel @Inject constructor(savedState: SavedStateHandle, private val repo: OrdersRepository) : ViewModel() {
    private val orderId: String = savedState.get<String>("orderId") ?: ""
    private val _state = MutableStateFlow(RateUiState())
    val state: StateFlow<RateUiState> = _state.asStateFlow()
    private val _events = MutableSharedFlow<RateEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<RateEvent> = _events.asSharedFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            repo.detail(orderId).fold(onOk = { o -> _state.update { it.copy(loading = false, order = o) } }, onErr = { e -> _state.update { it.copy(loading = false, error = e) } })
        }
    }

    fun rateRestaurant(r: Int) = _state.update { it.copy(restaurantRating = r, submitError = null) }
    fun rateRider(r: Int) = _state.update { it.copy(riderRating = r) }
    fun rateItem(menuItemId: String, r: Int) = _state.update { it.copy(itemRatings = it.itemRatings + (menuItemId to r)) }
    fun restaurantComment(v: String) = _state.update { it.copy(restaurantComment = v.take(500)) }
    fun riderComment(v: String) = _state.update { it.copy(riderComment = v.take(500)) }
    fun toggleTag(t: String) = _state.update { it.copy(tags = if (t in it.tags) it.tags - t else it.tags + t) }

    fun submit() {
        val s = _state.value
        if (!s.canSubmit) return
        _state.update { it.copy(submitting = true, submitError = null) }
        viewModelScope.launch {
            repo.review(CreateReviewDto(
                orderId = orderId, restaurantRating = s.restaurantRating, restaurantComment = s.restaurantComment.trim(),
                riderRating = s.riderRating.takeIf { it > 0 && s.order?.rider != null }, riderComment = s.riderComment.trim(),
                itemRatings = s.itemRatings.filterValues { it > 0 }, tags = s.tags.toList(),
            )).fold(
                onOk = { _state.update { it.copy(submitting = false) }; _events.tryEmit(RateEvent.Done) },
                onErr = { e -> _state.update { it.copy(submitting = false, submitError = e.message) } },
            )
        }
    }
}
