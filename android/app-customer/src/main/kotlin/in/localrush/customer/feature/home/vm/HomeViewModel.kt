package `in`.localrush.customer.feature.home.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.localrush.core.auth.SessionManager
import `in`.localrush.core.auth.SessionState
import `in`.localrush.core.common.result.AppError
import `in`.localrush.customer.data.dto.HomeDto
import `in`.localrush.customer.data.repo.AppPreferences
import `in`.localrush.customer.data.repo.DeliveryLocation
import `in`.localrush.customer.data.repo.HomeRepository
import `in`.localrush.customer.data.repo.NotificationsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val home: HomeDto? = null,
    val error: AppError? = null,
    val loggedIn: Boolean = false,
    val location: DeliveryLocation? = null,
    val unread: Int = 0,
    /** Greeting name when logged in. */
    val userName: String? = null,
) {
    val showError: Boolean get() = error != null && home == null
    val isStale: Boolean get() = error != null && home != null
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: HomeRepository,
    private val prefs: AppPreferences,
    private val notifications: NotificationsRepository,
    sessionManager: SessionManager,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            combine(prefs.deliveryLocation, sessionManager.state) { loc, s -> loc to s }
                .distinctUntilChanged { a, b -> a.first?.point == b.first?.point && (a.second is SessionState.LoggedIn) == (b.second is SessionState.LoggedIn) }
                .collect { (loc, session) ->
                    val user = (session as? SessionState.LoggedIn)?.user
                    _state.update { it.copy(location = loc, loggedIn = user != null, userName = user?.fullName?.takeIf { n -> n.isNotBlank() }) }
                    load(loc, showCached = true)
                }
        }
        viewModelScope.launch { notifications.unread.collect { n -> _state.update { it.copy(unread = n) } } }
    }

    fun refresh() { load(_state.value.location, showCached = false, refreshing = true) }

    private fun load(loc: DeliveryLocation?, showCached: Boolean, refreshing: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val lat = loc?.point?.latitude; val lng = loc?.point?.longitude
            if (showCached) {
                val cached = repo.cached(lat, lng)
                _state.update { it.copy(loading = cached == null, home = cached ?: it.home, error = null) }
            } else {
                _state.update { it.copy(refreshing = refreshing, loading = it.home == null, error = null) }
            }
            repo.fetch(lat, lng, loc?.city).fold(
                onOk = { h -> _state.update { it.copy(loading = false, refreshing = false, home = h, error = null) } },
                onErr = { e -> _state.update { it.copy(loading = false, refreshing = false, error = e) } },
            )
            if (_state.value.loggedIn) notifications.refreshUnread()
        }
    }
}
