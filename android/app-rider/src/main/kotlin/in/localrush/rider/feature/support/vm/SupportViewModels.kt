package `in`.localrush.rider.feature.support.vm

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import `in`.localrush.core.common.result.AppError
import `in`.localrush.core.common.util.UiState
import `in`.localrush.core.network.dto.FaqDto
import `in`.localrush.core.network.dto.KeyLabelDto
import `in`.localrush.core.network.dto.SupportContactDto
import `in`.localrush.core.network.dto.SupportTicketDto
import `in`.localrush.rider.data.SupportRepository
import `in`.localrush.rider.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SupportUiState(
    val loading: Boolean = true,
    val error: AppError? = null,
    val faqs: List<FaqDto> = emptyList(),
    val contact: SupportContactDto? = null,
    val tickets: List<SupportTicketDto> = emptyList(),
    val ticketsError: AppError? = null,
)

@HiltViewModel
class SupportViewModel @Inject constructor(private val repo: SupportRepository) : ViewModel() {
    private val _state = MutableStateFlow(SupportUiState())
    val state: StateFlow<SupportUiState> = _state

    init { load() }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val faqs = repo.faqs()
            val contact = repo.contact()
            val tickets = repo.tickets()
            val fatal = faqs.errorOrNull()?.takeIf { contact.isErr && tickets.isErr }
            _state.update {
                it.copy(
                    loading = false, error = fatal,
                    faqs = faqs.getOrNull() ?: it.faqs, contact = contact.getOrNull() ?: it.contact,
                    tickets = tickets.getOrNull()?.results ?: it.tickets, ticketsError = tickets.errorOrNull(),
                )
            }
        }
    }
}

data class NewTicketUiState(
    val categories: List<KeyLabelDto> = emptyList(),
    val category: String = "other",
    val subject: String = "",
    val description: String = "",
    val submitting: Boolean = false,
    val error: AppError? = null,
    val fieldErrors: Map<String, String> = emptyMap(),
) { val canSubmit: Boolean get() = subject.trim().length >= 3 && description.trim().length >= 10 && !submitting }

@HiltViewModel
class NewTicketViewModel @Inject constructor(private val repo: SupportRepository, savedState: SavedStateHandle) : ViewModel() {
    private val orderId: String? = savedState.toRoute<Routes.NewTicket>().orderId
    private val _state = MutableStateFlow(NewTicketUiState())
    val state: StateFlow<NewTicketUiState> = _state
    private val _created = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val created: SharedFlow<String> = _created

    init {
        viewModelScope.launch {
            repo.contact().onSuccess { c -> _state.update { it.copy(categories = c.categories.filter { k -> k.key in RIDER_CATEGORIES }.ifEmpty { c.categories }) } }
        }
    }

    fun setCategory(k: String) = _state.update { it.copy(category = k) }
    fun setSubject(v: String) = _state.update { it.copy(subject = v, fieldErrors = it.fieldErrors - "subject") }
    fun setDescription(v: String) = _state.update { it.copy(description = v, fieldErrors = it.fieldErrors - "description") }

    fun submit() {
        val s = _state.value
        if (!s.canSubmit) return
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            repo.create(orderId, s.category, s.subject.trim(), s.description.trim()).fold(
                onOk = { t -> _state.update { it.copy(submitting = false) }; _created.tryEmit(t.id) },
                onErr = { e -> _state.update { it.copy(submitting = false, error = e, fieldErrors = (e as? AppError.Api)?.fields?.mapValues { f -> f.value.first() } ?: emptyMap()) } },
            )
        }
    }

    private companion object {
        val RIDER_CATEGORIES = setOf("customer_unavailable", "restaurant_delay", "vehicle_issue", "emergency", "payment", "account", "late_delivery", "other")
    }
}

data class TicketDetailUiState(
    val ticket: UiState<SupportTicketDto> = UiState.Loading,
    val reply: String = "",
    val sending: Boolean = false,
    val closing: Boolean = false,
    val actionError: AppError? = null,
)

@HiltViewModel
class TicketDetailViewModel @Inject constructor(private val repo: SupportRepository, savedState: SavedStateHandle) : ViewModel() {
    private val ticketId = savedState.toRoute<Routes.Ticket>().ticketId
    private val _state = MutableStateFlow(TicketDetailUiState())
    val state: StateFlow<TicketDetailUiState> = _state

    init { load() }

    fun load() {
        _state.update { it.copy(ticket = UiState.Loading) }
        viewModelScope.launch { repo.ticket(ticketId).fold(onOk = { t -> _state.update { it.copy(ticket = UiState.Success(t)) } }, onErr = { e -> _state.update { it.copy(ticket = UiState.Error(e)) } }) }
    }

    fun setReply(v: String) = _state.update { it.copy(reply = v, actionError = null) }

    fun sendReply() {
        val text = _state.value.reply.trim()
        if (text.isBlank() || _state.value.sending) return
        _state.update { it.copy(sending = true) }
        viewModelScope.launch {
            repo.reply(ticketId, text).fold(
                onOk = { t -> _state.update { it.copy(sending = false, reply = "", ticket = UiState.Success(t)) } },
                onErr = { e -> _state.update { it.copy(sending = false, actionError = e) } },
            )
        }
    }

    fun close() {
        if (_state.value.closing) return
        _state.update { it.copy(closing = true) }
        viewModelScope.launch {
            repo.close(ticketId).fold(
                onOk = { t -> _state.update { it.copy(closing = false, ticket = UiState.Success(t)) } },
                onErr = { e -> _state.update { it.copy(closing = false, actionError = e) } },
            )
        }
    }
}
