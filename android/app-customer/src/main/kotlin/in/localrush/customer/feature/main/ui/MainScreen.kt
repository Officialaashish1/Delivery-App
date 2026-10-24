package `in`.localrush.customer.feature.main.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.localrush.core.common.money.toMoney
import `in`.localrush.customer.R
import `in`.localrush.customer.data.dto.CartDto
import `in`.localrush.customer.data.repo.CartRepository
import `in`.localrush.customer.feature.home.ui.HomeActions
import `in`.localrush.customer.feature.home.ui.HomeScreen
import `in`.localrush.customer.feature.orders.ui.OrdersScreen
import `in`.localrush.customer.feature.profile.ui.ProfileActions
import `in`.localrush.customer.feature.profile.ui.ProfileScreen
import `in`.localrush.customer.feature.search.ui.SearchScreen
import `in`.localrush.customer.navigation.MainTabs
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class MainShellViewModel @Inject constructor(cart: CartRepository) : ViewModel() {
    val cart: StateFlow<CartDto?> = cart.cart
}

class MainActions(
    val home: HomeActions,
    val profile: ProfileActions,
    val onRestaurant: (String) -> Unit,
    val onItem: (String, String) -> Unit,
    val onCuisine: (String, String) -> Unit,
    val onSearchAll: (String) -> Unit,
    val onOrder: (String) -> Unit,
    val onTrack: (String) -> Unit,
    val onLogin: () -> Unit,
    val onCart: () -> Unit,
)

/** Bottom nav shell: Home · Search · Orders · Profile, plus the floating cart bar when the cart is non-empty. */
@Composable
fun MainScreen(initialTab: Int, actions: MainActions, viewModel: MainShellViewModel = hiltViewModel()) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    LaunchedEffect(initialTab) { tab = initialTab }
    val cart by viewModel.cart.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            Column {
                CartBar(cart, onClick = actions.onCart)
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavItem(tab == MainTabs.HOME, { tab = MainTabs.HOME }, Icons.Filled.Home, Icons.Outlined.Home, stringResource(R.string.tab_home))
                    NavItem(tab == MainTabs.SEARCH, { tab = MainTabs.SEARCH }, Icons.Filled.Search, Icons.Outlined.Search, stringResource(R.string.tab_search))
                    NavItem(tab == MainTabs.ORDERS, { tab = MainTabs.ORDERS }, Icons.AutoMirrored.Filled.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong, stringResource(R.string.tab_orders))
                    NavItem(tab == MainTabs.PROFILE, { tab = MainTabs.PROFILE }, Icons.Filled.Person, Icons.Outlined.Person, stringResource(R.string.tab_profile))
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
            when (tab) {
                MainTabs.HOME -> HomeScreen(actions.home)
                MainTabs.SEARCH -> SearchScreen(onBack = null, onRestaurant = actions.onRestaurant, onItem = actions.onItem, onCuisine = actions.onCuisine, onSeeAllRestaurants = actions.onSearchAll, autoFocus = false)
                MainTabs.ORDERS -> OrdersScreen(onOrder = actions.onOrder, onTrack = actions.onTrack, onLogin = actions.onLogin, onBrowse = { tab = MainTabs.HOME })
                MainTabs.PROFILE -> ProfileScreen(actions.profile)
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavItem(selected: Boolean, onClick: () -> Unit, filled: androidx.compose.ui.graphics.vector.ImageVector, outlined: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    NavigationBarItem(
        selected = selected, onClick = onClick,
        icon = { Icon(if (selected) filled else outlined, contentDescription = label) },
        label = { Text(label) },
        colors = NavigationBarItemDefaults.colors(selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary, indicatorColor = MaterialTheme.colorScheme.primaryContainer),
    )
}

@Composable
private fun CartBar(cart: CartDto?, onClick: () -> Unit) {
    AnimatedVisibility(cart != null && !cart.isEmpty, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
        Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick), color = MaterialTheme.colorScheme.primary, shadowElevation = 8.dp) {
            CompositionLocalProvider(LocalContentColor provides Color.White) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.ShoppingBag, null, Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(pluralStringResource(R.plurals.cart_items_count, cart?.itemCount ?: 0, cart?.itemCount ?: 0) + (cart?.restaurant?.name?.let { "  ·  $it" } ?: ""), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        Text(cart?.pricing?.itemTotal?.toMoney()?.format() ?: "", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(stringResource(R.string.cart_view), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
