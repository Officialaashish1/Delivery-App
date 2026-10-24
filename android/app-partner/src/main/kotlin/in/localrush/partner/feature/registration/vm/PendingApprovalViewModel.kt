package `in`.localrush.partner.feature.registration.vm

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.localrush.core.common.result.AppError
import `in`.localrush.core.common.result.Result
import `in`.localrush.core.common.util.UiState
import `in`.localrush.partner.data.ConnectivityObserver
import `in`.localrush.partner.data.PartnerRestaurantDto
import `in`.localrush.partner.data.RestaurantDocumentDto
import `in`.localrush.partner.data.RestaurantRepository
import `in`.localrush.partner.data.RestaurantStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PendingApprovalData(
    val restaurant: PartnerRestaurantDto,
    val documents: List<RestaurantDocumentDto> = emptyList(),
    val uploading: Boolean = false,
    val refreshing: Boolean = false,
)

@HiltViewModel
class PendingApprovalViewModel @Inject constructor(
    private val repo: RestaurantRepository,
    connectivity: ConnectivityObserver,
) : ViewModel() {
    private val _state = MutableStateFlow<UiState<PendingApprovalData>>(UiState.Loading)
    val state: StateFlow<UiState<PendingApprovalData>> = _state.asStateFlow()
    val online: StateFlow<Boolean> = connectivity.isOnline
    private val _approved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val approved: SharedFlow<Unit> = _approved.asSharedFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        refresh()
        // Poll every 30 s so approval is picked up without the owner doing anything.
        viewModelScope.launch { while (isActive) { delay(30_000); refresh(silent = true) } }
    }

    fun refresh(silent: Boolean = false) {
        if (!silent) _state.update { s -> if (s is UiState.Success) s.copy(data = s.data.copy(refreshing = true)) else UiState.Loading }
        viewModelScope.launch {
            when (val r = repo.load()) {
                is Result.Ok -> {
                    val restaurant = r.value
                    if (restaurant == null) { _state.value = UiState.Error(AppError.Api("not_found", "Restaurant not found", 404), retryable = true); return@launch }
                    if (RestaurantStatus.fromWire(restaurant.status) == RestaurantStatus.APPROVED) { _approved.tryEmit(Unit); return@launch }
                    val docs = repo.documents().getOrNull() ?: (_state.value as? UiState.Success)?.data?.documents ?: emptyList()
                    _state.value = UiState.Success(PendingApprovalData(restaurant, docs))
                }
                is Result.Err -> if (_state.value !is UiState.Success) _state.value = UiState.Error(r.error) else {
                    _state.update { s -> if (s is UiState.Success) s.copy(data = s.data.copy(refreshing = false)) else s }
                    if (!silent) _messages.tryEmit(r.error.message)
                }
            }
        }
    }

    fun uploadDocument(docType: String, number: String, file: Uri) {
        _state.update { s -> if (s is UiState.Success) s.copy(data = s.data.copy(uploading = true)) else s }
        viewModelScope.launch {
            when (val r = repo.uploadDocument(docType, number, file)) {
                is Result.Ok -> _state.update { s -> if (s is UiState.Success) s.copy(data = s.data.copy(uploading = false, documents = listOf(r.value) + s.data.documents.filterNot { it.id == r.value.id })) else s }
                is Result.Err -> { _state.update { s -> if (s is UiState.Success) s.copy(data = s.data.copy(uploading = false)) else s }; _messages.tryEmit(r.error.message) }
            }
            // Uploading a document re-opens a rejected application (backend sets status back to pending).
            refresh(silent = true)
        }
    }

    fun deleteDocument(id: String) = viewModelScope.launch {
        repo.deleteDocument(id)
            .onSuccess { _state.update { s -> if (s is UiState.Success) s.copy(data = s.data.copy(documents = s.data.documents.filterNot { it.id == id })) else s } }
            .onFailure { _messages.tryEmit(it.message) }
    }
}
