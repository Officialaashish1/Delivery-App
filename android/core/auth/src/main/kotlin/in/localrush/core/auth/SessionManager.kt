package `in`.localrush.core.auth

import `in`.localrush.core.network.auth.LogoutReason
import `in`.localrush.core.network.auth.SessionInvalidator
import `in`.localrush.core.network.dto.UserDto
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SessionState {
    /** Initial state before TokenStore has been read (very brief). */
    data object Unknown : SessionState
    data object LoggedOut : SessionState
    data class LoggedIn(val user: UserDto, val profileComplete: Boolean = true) : SessionState
}

/**
 * Single source of truth for "who is logged in". Observed by the NavHost to route between
 * Auth and Home. Receives forced-logout signals from the network layer ([SessionInvalidator]).
 */
@Singleton
class SessionManager @Inject constructor(private val tokenStore: TokenStore) : SessionInvalidator {

    private val _state = MutableStateFlow<SessionState>(
        if (tokenStore.hasSession()) tokenStore.user()?.let { SessionState.LoggedIn(it) } ?: SessionState.LoggedOut
        else SessionState.LoggedOut
    )
    val state: StateFlow<SessionState> = _state.asStateFlow()

    /** Emits the reason when the session ends involuntarily (show a snackbar: "You were signed out"). */
    private val _forcedLogout = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val forcedLogout: SharedFlow<String> = _forcedLogout.asSharedFlow()

    val isLoggedIn: Boolean get() = _state.value is SessionState.LoggedIn
    val currentUser: UserDto? get() = (_state.value as? SessionState.LoggedIn)?.user

    fun onLoggedIn(access: String, refresh: String, user: UserDto, profileComplete: Boolean) {
        tokenStore.saveTokens(access, refresh)
        tokenStore.saveUser(user)
        _state.value = SessionState.LoggedIn(user, profileComplete)
    }

    fun updateUser(user: UserDto) {
        tokenStore.saveUser(user)
        _state.update { if (it is SessionState.LoggedIn) it.copy(user = user) else it }
    }

    fun onLoggedOut() {
        tokenStore.clear()
        _state.value = SessionState.LoggedOut
    }

    /** Called from OkHttp threads by TokenAuthenticator. */
    override fun onSessionInvalid(reason: String) {
        if (_state.value is SessionState.LoggedOut) return
        tokenStore.clear()
        _state.value = SessionState.LoggedOut
        if (reason != LogoutReason.USER) _forcedLogout.tryEmit(reason)
    }
}
