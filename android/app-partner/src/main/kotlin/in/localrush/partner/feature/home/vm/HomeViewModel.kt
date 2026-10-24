package `in`.localrush.partner.feature.home.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.localrush.core.auth.AuthRepository
import `in`.localrush.core.auth.SessionManager
import `in`.localrush.core.auth.SessionState
import `in`.localrush.partner.data.PartnerOrderFeed
import `in`.localrush.partner.data.PartnerRestaurantDto
import `in`.localrush.partner.data.RestaurantRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    sessionManager: SessionManager,
    restaurantRepo: RestaurantRepository,
    feed: PartnerOrderFeed,
    private val auth: AuthRepository,
) : ViewModel() {
    val session: StateFlow<SessionState> = sessionManager.state
    val restaurant: StateFlow<PartnerRestaurantDto?> = restaurantRepo.restaurant
    val newOrderCount: StateFlow<Int> = feed.state.map { it.unacknowledged.size }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun logout() = viewModelScope.launch { auth.logout() }
}
