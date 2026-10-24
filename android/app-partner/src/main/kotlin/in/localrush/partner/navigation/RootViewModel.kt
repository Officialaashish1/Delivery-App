package `in`.localrush.partner.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.localrush.core.auth.AuthRepository
import `in`.localrush.core.auth.SessionManager
import `in`.localrush.core.auth.SessionState
import `in`.localrush.core.push.PushTokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val authRepository: AuthRepository,
    private val push: PushTokenManager,
    val deepLinks: DeepLinkHandler,
) : ViewModel() {
    val session: StateFlow<SessionState> = sessionManager.state
    val forcedLogout: SharedFlow<String> = sessionManager.forcedLogout

    var splashDone by mutableStateOf(false)
        private set

    init {
        // Refresh the cached user + FCM registration in the background when a session exists.
        if (sessionManager.isLoggedIn) viewModelScope.launch {
            authRepository.refreshMe()
            push.syncToken()
        }
    }

    fun onSplashFinished() { splashDone = true }

    fun logout() { viewModelScope.launch { authRepository.logout() } }
}
