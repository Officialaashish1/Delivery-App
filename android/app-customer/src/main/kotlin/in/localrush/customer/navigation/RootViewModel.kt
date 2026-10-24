package `in`.localrush.customer.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.localrush.core.auth.AuthRepository
import `in`.localrush.core.auth.SessionManager
import `in`.localrush.core.auth.SessionState
import `in`.localrush.core.push.PushTokenManager
import `in`.localrush.customer.data.repo.AppPreferences
import `in`.localrush.customer.data.repo.CartRepository
import `in`.localrush.customer.data.repo.ConfigRepository
import `in`.localrush.customer.data.repo.ConnectivityObserver
import `in`.localrush.customer.data.repo.DeliveryLocation
import `in`.localrush.customer.data.repo.NotificationsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the Splash must decide once it finishes. */
data class StartupState(
    val ready: Boolean = false,
    val onboardingDone: Boolean = false,
    val hasLocation: Boolean = false,
    val termsAccepted: Boolean = false,
)

@HiltViewModel
class RootViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val authRepository: AuthRepository,
    private val push: PushTokenManager,
    private val prefs: AppPreferences,
    private val config: ConfigRepository,
    private val cart: CartRepository,
    private val notifications: NotificationsRepository,
    connectivity: ConnectivityObserver,
) : ViewModel() {
    val session: StateFlow<SessionState> = sessionManager.state
    val forcedLogout: SharedFlow<String> = sessionManager.forcedLogout
    val isOnline: StateFlow<Boolean> = connectivity.isOnline.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val deliveryLocation: StateFlow<DeliveryLocation?> = prefs.deliveryLocation.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val startup: StateFlow<StartupState> = combine(prefs.onboardingDone, prefs.deliveryLocation, prefs.termsAccepted) { ob, loc, terms ->
        StartupState(ready = true, onboardingDone = ob, hasLocation = loc != null, termsAccepted = terms)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, StartupState())

    init {
        viewModelScope.launch { config.load() }
        viewModelScope.launch {
            sessionManager.state.collect { s ->
                if (s is SessionState.LoggedIn) {
                    launch { authRepository.refreshMe() }
                    launch { push.syncToken() }
                    launch { cart.refresh() }
                    launch { notifications.refreshUnread() }
                } else {
                    cart.publish(null)
                    notifications.reset()
                }
            }
        }
    }

    fun acceptTerms() { viewModelScope.launch { prefs.setTermsAccepted() } }
    fun logout() { viewModelScope.launch { authRepository.logout() } }
}
