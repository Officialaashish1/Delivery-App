package `in`.localrush.partner.feature.support.vm

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import `in`.localrush.core.common.result.AppError
import `in`.localrush.core.common.result.Result
import `in`.localrush.core.common.util.UiState
import `in`.localrush.core.network.api.SupportApi
import `in`.localrush.core.network.dto.CreateTicketDto
import `in`.localrush.core.network.dto.FaqDto
import `in`.localrush.core.network.dto.KeyLabelDto
import `in`.localrush.core.network.dto.SupportContactDto
import `in`.localrush.core.network.dto.SupportTicketDto
import `in`.localrush.core.network.util.Multipart
import `in`.localrush.core.network.util.apiCall
import `in`.localrush.partner.data.ConnectivityObserver
import `in`.localrush.partner.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SupportData(val faqs: List<FaqDto>, val contact: SupportContactDto?, val tickets: List<SupportTicketDto>, val refreshing: Boolean = false)

@HiltViewModel
class SupportViewModel @Inject constructor(private val api: SupportApi, connectivity: ConnectivityObserver) : ViewModel() {
    private val _state = MutableStateFlow<UiState<SupportData>>(UiState.Loading)
    val state: StateFlow<UiState<SupportData>> = _state.asStateFlow()
    val online: StateFlow<Boolean> = connectivity.isOnline
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init { load() }

    fun load() {
        _state.update { s -> if (s is UiState.Success) s.copy(data = s.data.copy(refreshing = true)) else UiState.Loading }
        viewModelScope.launch {
            val faqs = apiCall { api.faqs(audience = "partner") }
            val contact = apiCall { api.contact() }
            val tickets = apiCall { api.tickets() }
            if (faqs is Result.Err && tickets is Result.Err) { _state.value = UiState.Error(faqs.error); return@launch }
            (tickets as? Result.Err)?.let { _messages.tryEmit(it.error.message) }
            _state.value = UiState.Success(SupportData(faqs.getOrNull() ?: emptyList(), contact.getOrNull(), tickets.getOrNull()?.results ?: emptyList()))
        }
    }
}

data class NewTicketData(val categories: List<KeyLabelDto>, val submitting: Boolean = false, val errors: Map<String, String> = emptyMap())

sealed interface TicketEvent {
    data class Created(val ticket: SupportTicketDto) : TicketEvent
    data class Message(val text: String) : TicketEvent
}

@HiltViewModel
class NewTicketViewModel @Inject constructor(private val api: SupportApi, connectivity: ConnectivityObserver) : ViewModel() {
    private val _state = MutableStateFlow<UiState<NewTicketData>>(UiState.Loading)
    val state: StateFlow<UiState<NewTicketData>> = _state.asStateFlow()
    val online: StateFlow<Boolean> = connectivity.isOnline
    private val _events = MutableSharedFlow<TicketEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<TicketEvent> = _events.asSharedFlow()

    init { load() }

    fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            when (val r = apiCall { api.contact() }) {
                is Result.Ok -> _state.value = UiState.Success(NewTicketData(r.value.categories))
                is Result.Err -> _state.value = UiState.Error(r.error)
            }
        }
    }

    fun submit(category: String, subject: String, description: String) {
        val s = _state.value as? UiState.Success ?: return
        val errors = buildMap {
            if (category.isBlank()) put("category", "required")
            if (subject.trim().length < 3) put("subject", "required")
            if (description.trim().length < 10) put("description", "short")
        }
        if (errors.isNotEmpty()) { _state.value = s.copy(data = s.data.copy(errors = errors)); return }
        _state.value = s.copy(data = s.data.copy(submitting = true, errors = emptyMap()))
        viewModelScope.launch {
            when (val r = apiCall { api.createTicket(CreateTicketDto(order = null, category = category, subject = subject.trim(), description = description.trim())) }) {
                is Result.Ok -> _events.tryEmit(TicketEvent.Created(r.value))
                is Result.Err -> {
                    _state.update { c -> if (c is UiState.Success) c.copy(data = c.data.copy(submitting = false, errors = (r.error as? AppError.Api)?.fields?.mapValues { it.value.joinToString() } ?: emptyMap())) else c }
                    _events.tryEmit(TicketEvent.Message(r.error.message))
                }
            }
        }
    }
}

data class TicketData(val ticket: SupportTicketDto, val sending: Boolean = false)

@HiltViewModel
class TicketViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val api: SupportApi,
    connectivity: ConnectivityObserver,
    @ApplicationContext private val context: android.content.Context,
) : ViewModel() {
    private val ticketId: String = savedState.toRoute<Routes.Ticket>().ticketId
    private val _state = MutableStateFlow<UiState<TicketData>>(UiState.Loading)
    val state: StateFlow<UiState<TicketData>> = _state.asStateFlow()
    val online: StateFlow<Boolean> = connectivity.isOnline
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init { load() }

    fun load() {
        if (_state.value !is UiState.Success) _state.value = UiState.Loading
        viewModelScope.launch {
            when (val r = apiCall { api.ticket(ticketId) }) {
                is Result.Ok -> _state.value = UiState.Success(TicketData(r.value))
                is Result.Err -> if (_state.value !is UiState.Success) _state.value = UiState.Error(r.error) else _messages.tryEmit(r.error.message)
            }
        }
    }

    fun reply(text: String, attachment: Uri?) {
        if (text.isBlank()) return
        _state.update { s -> if (s is UiState.Success) s.copy(data = s.data.copy(sending = true)) else s }
        viewModelScope.launch {
            when (val r = apiCall { api.reply(ticketId, Multipart.text(text.trim()), attachment?.let { Multipart.uriPart(context, "attachment", it) }) }) {
                is Result.Ok -> _state.value = UiState.Success(TicketData(r.value))
                is Result.Err -> { _messages.tryEmit(r.error.message); _state.update { s -> if (s is UiState.Success) s.copy(data = s.data.copy(sending = false)) else s } }
            }
        }
    }

    fun close() = viewModelScope.launch {
        when (val r = apiCall { api.close(ticketId) }) {
            is Result.Ok -> _state.value = UiState.Success(TicketData(r.value))
            is Result.Err -> _messages.tryEmit(r.error.message)
        }
    }
}
