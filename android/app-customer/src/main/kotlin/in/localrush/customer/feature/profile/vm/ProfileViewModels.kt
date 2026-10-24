package `in`.localrush.customer.feature.profile.vm

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.localrush.core.auth.AuthRepository
import `in`.localrush.core.auth.SessionManager
import `in`.localrush.core.auth.SessionState
import `in`.localrush.core.common.result.AppError
import `in`.localrush.core.network.dto.AddressDto
import `in`.localrush.core.network.dto.FaqDto
import `in`.localrush.core.network.dto.NotificationDto
import `in`.localrush.core.network.dto.SupportContactDto
import `in`.localrush.core.network.dto.SupportTicketDto
import `in`.localrush.core.network.dto.UserDto
import `in`.localrush.customer.data.dto.ProfileDto
import `in`.localrush.customer.data.repo.AddressRepository
import `in`.localrush.customer.data.repo.ConfigRepository
import `in`.localrush.customer.data.repo.NotificationsRepository
import `in`.localrush.customer.data.repo.ProfileRepository
import `in`.localrush.customer.data.repo.SupportRepository
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

// ---------------------------------------------------------------- profile tab

data class ProfileUiState(
    val user: UserDto? = null,
    val profile: ProfileDto? = null,
    val loading: Boolean = false,
    val error: AppError? = null,
    val unread: Int = 0,
    val supportPhone: String = "",
    val busy: Boolean = false,
    val deleteDialog: Boolean = false,
)

sealed interface ProfileEvent { data class Message(val text: String) : ProfileEvent }

@HiltViewModel
class ProfileViewModel @Inject constructor(
    session: SessionManager,
    private val auth: AuthRepository,
    private val repo: ProfileRepository,
    notifications: NotificationsRepository,
    config: ConfigRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()
    private val _events = MutableSharedFlow<ProfileEvent>(extraBufferCapacity = 2)
    val events: SharedFlow<ProfileEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            session.state.collect { s ->
                val u = (s as? SessionState.LoggedIn)?.user
                _state.update { it.copy(user = u, profile = if (u == null) null else it.profile) }
                if (u != null) load()
            }
        }
        viewModelScope.launch { notifications.unread.collect { n -> _state.update { it.copy(unread = n) } } }
        viewModelScope.launch { config.config.collect { c -> _state.update { it.copy(supportPhone = c.supportPhone) } } }
    }

    fun load() {
        _state.update { it.copy(loading = it.profile == null, error = null) }
        viewModelScope.launch { repo.profile().fold(onOk = { p -> _state.update { it.copy(loading = false, profile = p) } }, onErr = { e -> _state.update { it.copy(loading = false, error = e) } }) }
    }

    fun logout() { _state.update { it.copy(busy = true) }; viewModelScope.launch { auth.logout(); _state.update { it.copy(busy = false) } } }
    fun showDelete(show: Boolean) = _state.update { it.copy(deleteDialog = show) }
    fun deleteAccount() {
        _state.update { it.copy(busy = true, deleteDialog = false) }
        viewModelScope.launch { auth.deleteAccount().onFailure { e -> _events.tryEmit(ProfileEvent.Message(e.message)) }; _state.update { it.copy(busy = false) } }
    }
}

// ---------------------------------------------------------------- edit profile

data class EditProfileUiState(val name: String = "", val email: String = "", val vegOnly: Boolean = false, val loading: Boolean = true, val saving: Boolean = false, val error: AppError? = null, val fieldErrors: Map<String, String> = emptyMap(), val nameMissing: Boolean = false)

@HiltViewModel
class EditProfileViewModel @Inject constructor(private val repo: ProfileRepository, private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(EditProfileUiState())
    val state: StateFlow<EditProfileUiState> = _state.asStateFlow()
    private val _saved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saved: SharedFlow<Unit> = _saved.asSharedFlow()

    init { load() }
    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            repo.profile().fold(
                onOk = { p -> _state.update { it.copy(loading = false, name = p.fullName.ifBlank { p.user?.fullName ?: "" }, email = p.email ?: p.user?.email ?: "", vegOnly = p.isVegOnly) } },
                onErr = { e -> _state.update { it.copy(loading = false, error = e) } },
            )
        }
    }
    fun name(v: String) = _state.update { it.copy(name = v.take(120), fieldErrors = emptyMap(), nameMissing = false) }
    fun email(v: String) = _state.update { it.copy(email = v.trim(), fieldErrors = emptyMap()) }
    fun vegOnly(v: Boolean) = _state.update { it.copy(vegOnly = v) }
    fun save() {
        val s = _state.value
        if (s.name.isBlank()) { _state.update { it.copy(nameMissing = true) }; return }
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            repo.update(s.name.trim(), s.email.ifBlank { "" }, s.vegOnly).fold(
                onOk = { auth.refreshMe(); _state.update { it.copy(saving = false) }; _saved.tryEmit(Unit) },
                onErr = { e ->
                    val fields = (e as? AppError.Api)?.fields?.mapValues { it.value.first() } ?: emptyMap()
                    _state.update { it.copy(saving = false, fieldErrors = fields, error = if (fields.isEmpty()) e else null) }
                },
            )
        }
    }
}

// ---------------------------------------------------------------- addresses

data class AddressesUiState(val loading: Boolean = true, val items: List<AddressDto> = emptyList(), val error: AppError? = null, val busyId: String? = null, val deleteId: String? = null)

@HiltViewModel
class AddressesViewModel @Inject constructor(private val repo: AddressRepository) : ViewModel() {
    private val _state = MutableStateFlow(AddressesUiState())
    val state: StateFlow<AddressesUiState> = _state.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        viewModelScope.launch { repo.addresses.collect { a -> if (a != null) _state.update { it.copy(items = a, loading = false) } } }
        load()
    }
    fun load() { _state.update { it.copy(loading = it.items.isEmpty(), error = null) }; viewModelScope.launch { repo.load().onFailure { e -> _state.update { it.copy(loading = false, error = e) } }.onSuccess { _state.update { it.copy(loading = false) } } } }
    fun askDelete(id: String?) = _state.update { it.copy(deleteId = id) }
    fun delete() {
        val id = _state.value.deleteId ?: return
        _state.update { it.copy(busyId = id, deleteId = null) }
        viewModelScope.launch { repo.delete(id).onFailure { e -> _messages.tryEmit(e.message) }; _state.update { it.copy(busyId = null) } }
    }
    fun setDefault(id: String) {
        _state.update { it.copy(busyId = id) }
        viewModelScope.launch { repo.setDefault(id).onFailure { e -> _messages.tryEmit(e.message) }; _state.update { it.copy(busyId = null) } }
    }
}

// ---------------------------------------------------------------- notifications

data class NotificationsUiState(val loading: Boolean = true, val items: List<NotificationDto> = emptyList(), val error: AppError? = null)

@HiltViewModel
class NotificationsViewModel @Inject constructor(private val repo: NotificationsRepository) : ViewModel() {
    private val _state = MutableStateFlow(NotificationsUiState())
    val state: StateFlow<NotificationsUiState> = _state.asStateFlow()
    init { load() }
    fun load() {
        _state.update { it.copy(loading = it.items.isEmpty(), error = null) }
        viewModelScope.launch {
            repo.list().fold(
                onOk = { p -> _state.update { it.copy(loading = false, items = p.results) }; if (p.results.any { !it.isRead }) repo.markAllRead() },
                onErr = { e -> _state.update { it.copy(loading = false, error = e) } },
            )
        }
    }
}

// ---------------------------------------------------------------- help & support

data class HelpUiState(
    val loading: Boolean = true, val faqs: List<FaqDto> = emptyList(), val contact: SupportContactDto? = null, val tickets: List<SupportTicketDto> = emptyList(),
    val error: AppError? = null, val ticketsError: AppError? = null, val loggedIn: Boolean = false,
)

@HiltViewModel
class HelpViewModel @Inject constructor(private val repo: SupportRepository, session: SessionManager) : ViewModel() {
    private val _state = MutableStateFlow(HelpUiState(loggedIn = session.isLoggedIn))
    val state: StateFlow<HelpUiState> = _state.asStateFlow()
    init { load() }
    fun load() {
        _state.update { it.copy(loading = true, error = null, ticketsError = null) }
        viewModelScope.launch {
            val faqs = repo.faqs(); val contact = repo.contact()
            _state.update { it.copy(loading = false, faqs = faqs.getOrNull() ?: emptyList(), contact = contact.getOrNull(), error = faqs.errorOrNull().takeIf { contact.isErr }) }
            if (_state.value.loggedIn) repo.tickets().fold(onOk = { p -> _state.update { it.copy(tickets = p.results) } }, onErr = { e -> _state.update { it.copy(ticketsError = e) } })
        }
    }
}

data class NewTicketUiState(val orderId: String? = null, val categories: List<Pair<String, String>> = emptyList(), val category: String = "", val subject: String = "", val description: String = "", val submitting: Boolean = false, val error: AppError? = null, val fieldErrors: Map<String, String> = emptyMap()) {
    val canSubmit: Boolean get() = category.isNotBlank() && subject.trim().length >= 3 && description.trim().length >= 10 && !submitting
}

@HiltViewModel
class NewTicketViewModel @Inject constructor(savedState: SavedStateHandle, private val repo: SupportRepository) : ViewModel() {
    private val _state = MutableStateFlow(NewTicketUiState(orderId = savedState.get<String>("orderId")))
    val state: StateFlow<NewTicketUiState> = _state.asStateFlow()
    private val _created = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val created: SharedFlow<String> = _created.asSharedFlow()
    init {
        viewModelScope.launch {
            repo.contact().onSuccess { c ->
                val cats = c.categories.map { it.key to it.label }.filter { it.first !in setOf("customer_unavailable", "restaurant_delay", "vehicle_issue") }
                _state.update { it.copy(categories = cats, category = cats.firstOrNull()?.first ?: "") }
            }
        }
    }
    fun category(k: String) = _state.update { it.copy(category = k) }
    fun subject(v: String) = _state.update { it.copy(subject = v.take(140), fieldErrors = emptyMap()) }
    fun description(v: String) = _state.update { it.copy(description = v.take(2000), fieldErrors = emptyMap()) }
    fun submit() {
        val s = _state.value
        if (!s.canSubmit) return
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            repo.create(s.orderId, s.category, s.subject.trim(), s.description.trim()).fold(
                onOk = { t -> _state.update { it.copy(submitting = false) }; _created.tryEmit(t.id) },
                onErr = { e -> val fields = (e as? AppError.Api)?.fields?.mapValues { it.value.first() } ?: emptyMap(); _state.update { it.copy(submitting = false, fieldErrors = fields, error = if (fields.isEmpty()) e else null) } },
            )
        }
    }
}

data class TicketDetailUiState(val loading: Boolean = true, val ticket: SupportTicketDto? = null, val error: AppError? = null, val reply: String = "", val sending: Boolean = false, val closing: Boolean = false)

@HiltViewModel
class TicketDetailViewModel @Inject constructor(savedState: SavedStateHandle, private val repo: SupportRepository) : ViewModel() {
    private val id: String = savedState.get<String>("ticketId") ?: ""
    private val _state = MutableStateFlow(TicketDetailUiState())
    val state: StateFlow<TicketDetailUiState> = _state.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val messages: SharedFlow<String> = _messages.asSharedFlow()
    init { load() }
    fun load() { _state.update { it.copy(loading = it.ticket == null, error = null) }; viewModelScope.launch { repo.ticket(id).fold(onOk = { t -> _state.update { it.copy(loading = false, ticket = t) } }, onErr = { e -> _state.update { it.copy(loading = false, error = e) } }) } }
    fun reply(v: String) = _state.update { it.copy(reply = v) }
    fun send() {
        val text = _state.value.reply.trim(); if (text.isEmpty()) return
        _state.update { it.copy(sending = true) }
        viewModelScope.launch { repo.reply(id, text).fold(onOk = { t -> _state.update { it.copy(sending = false, reply = "", ticket = t) } }, onErr = { e -> _state.update { it.copy(sending = false) }; _messages.tryEmit(e.message) }) }
    }
    fun close() {
        _state.update { it.copy(closing = true) }
        viewModelScope.launch { repo.close(id).fold(onOk = { t -> _state.update { it.copy(closing = false, ticket = t) } }, onErr = { e -> _state.update { it.copy(closing = false) }; _messages.tryEmit(e.message) }) }
    }
}
