package `in`.localrush.rider.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.localrush.core.auth.AuthRepository
import `in`.localrush.core.auth.SessionManager
import `in`.localrush.core.auth.SessionState
import `in`.localrush.core.common.Constants
import `in`.localrush.core.network.api.ConfigApi
import `in`.localrush.core.network.util.apiCall
import `in`.localrush.core.push.PushTokenManager
import `in`.localrush.rider.data.OfferInbox
import `in`.localrush.rider.data.RiderRepository
import `in`.localrush.rider.data.TrackingController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val authRepository: AuthRepository,
    private val push: PushTokenManager,
    private val configApi: ConfigApi,
    private val riderRepository: RiderRepository,
    private val tracking: TrackingController,
    val deepLinks: DeepLinkHandler,
    val offerInbox: OfferInbox,
) : ViewModel() {
    val session: StateFlow<SessionState> = sessionManager.state
    val forcedLogout: SharedFlow<String> = sessionManager.forcedLogout

    private val _mapStyleUrl = MutableStateFlow(Constants.DEFAULT_MAP_STYLE_URL)
    val mapStyleUrl: StateFlow<String> = _mapStyleUrl

    var splashDone by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch { apiCall { configApi.config() }.onSuccess { c -> c.mapStyleUrl.takeIf { it.isNotBlank() }?.let { _mapStyleUrl.value = it } } }
        if (sessionManager.isLoggedIn) viewModelScope.launch {
            authRepository.refreshMe()
            push.syncToken()
            tracking.ensureRunningIfWanted()
        }
        viewModelScope.launch {
            sessionManager.state.collect { s -> if (s is SessionState.LoggedOut) { tracking.stop(); riderRepository.clearCache() } }
        }
    }

    fun onSplashFinished() { splashDone = true }

    fun logout() { viewModelScope.launch { tracking.stop(); authRepository.logout() } }
}
