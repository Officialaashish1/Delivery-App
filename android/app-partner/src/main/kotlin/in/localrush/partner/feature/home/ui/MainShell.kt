package `in`.localrush.partner.feature.home.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.clickable
import `in`.localrush.core.auth.SessionState
import `in`.localrush.partner.R
import `in`.localrush.partner.feature.common.ui.ConfirmDialog
import `in`.localrush.partner.feature.common.ui.PartnerTopBar
import `in`.localrush.partner.feature.dashboard.ui.DashboardScreen
import `in`.localrush.partner.feature.finance.ui.FinanceScreen
import `in`.localrush.partner.feature.home.vm.HomeViewModel
import `in`.localrush.partner.feature.menu.ui.MenuItemsScreen
import `in`.localrush.partner.feature.orders.ui.NewOrderBanner
import `in`.localrush.partner.feature.orders.ui.OrdersBoardScreen

enum class MainTab(val key: String, val labelRes: Int, val icon: ImageVector) {
    DASHBOARD("dashboard", R.string.tab_dashboard, Icons.Outlined.Dashboard),
    ORDERS("orders", R.string.tab_orders, Icons.AutoMirrored.Outlined.ReceiptLong),
    MENU("menu", R.string.tab_menu, Icons.AutoMirrored.Outlined.MenuBook),
    FINANCE("finance", R.string.tab_finance, Icons.Outlined.AccountBalanceWallet),
    MORE("more", R.string.tab_more, Icons.Outlined.MoreHoriz);

    companion object { fun fromKey(k: String) = entries.firstOrNull { it.key == k } ?: DASHBOARD }
}

/** Navigation targets reachable from the shell (pushed on top of it by the NavHost). */
sealed interface ShellNav {
    data class Order(val id: String) : ShellNav
    data object MenuCategories : ShellNav
    data class ItemEditor(val itemId: String?, val categoryId: String?) : ShellNav
    data object Settings : ShellNav
    data object Hours : ShellNav
    data object Holidays : ShellNav
    data object Documents : ShellNav
    data object Profile : ShellNav
    data object FinanceLines : ShellNav
    data object Settlements : ShellNav
    data object Reviews : ShellNav
    data object Notifications : ShellNav
    data object Support : ShellNav
}

@Composable
fun MainShell(initialTab: MainTab, onNavigate: (ShellNav) -> Unit, viewModel: HomeViewModel = hiltViewModel()) {
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    val alertCount by viewModel.newOrderCount.collectAsStateWithLifecycle()
    Scaffold(
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = {
                            BadgedBox(badge = { if (t == MainTab.ORDERS && alertCount > 0) Badge { Text(alertCount.toString()) } }) { Icon(t.icon, contentDescription = null) }
                        },
                        label = { Text(stringResource(t.labelRes)) },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
            if (alertCount > 0 && tab != MainTab.ORDERS) NewOrderBanner(alertCount, onOpen = { tab = MainTab.ORDERS })
            when (tab) {
                MainTab.DASHBOARD -> DashboardScreen(
                    onOpenOrders = { tab = MainTab.ORDERS },
                    onOpenFinance = { tab = MainTab.FINANCE },
                    onOpenNotifications = { onNavigate(ShellNav.Notifications) },
                    onOpenSettings = { onNavigate(ShellNav.Settings) },
                )
                MainTab.ORDERS -> OrdersBoardScreen(onOpenOrder = { onNavigate(ShellNav.Order(it)) })
                MainTab.MENU -> MenuItemsScreen(
                    categoryId = null,
                    onBack = null,
                    onManageCategories = { onNavigate(ShellNav.MenuCategories) },
                    onEditItem = { itemId, categoryId -> onNavigate(ShellNav.ItemEditor(itemId, categoryId)) },
                )
                MainTab.FINANCE -> FinanceScreen(onOpenLines = { onNavigate(ShellNav.FinanceLines) }, onOpenSettlements = { onNavigate(ShellNav.Settlements) })
                MainTab.MORE -> MoreScreen(onNavigate, viewModel)
            }
        }
    }
}

@Composable
private fun MoreScreen(onNavigate: (ShellNav) -> Unit, viewModel: HomeViewModel) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val restaurant by viewModel.restaurant.collectAsStateWithLifecycle()
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    Scaffold(topBar = { PartnerTopBar(stringResource(R.string.tab_more), subtitle = restaurant?.name) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            val user = (session as? SessionState.LoggedIn)?.user
            if (user != null) {
                ListItem(headlineContent = { Text(user.fullName.ifBlank { user.phone }, fontWeight = FontWeight.SemiBold) }, supportingContent = { Text(user.phone) })
                HorizontalDivider()
            }
            MoreRow(Icons.Outlined.Storefront, stringResource(R.string.settings_title)) { onNavigate(ShellNav.Settings) }
            MoreRow(Icons.Outlined.Schedule, stringResource(R.string.hours_title)) { onNavigate(ShellNav.Hours) }
            MoreRow(Icons.Outlined.EventBusy, stringResource(R.string.holidays_title)) { onNavigate(ShellNav.Holidays) }
            MoreRow(Icons.Outlined.Description, stringResource(R.string.documents_title)) { onNavigate(ShellNav.Documents) }
            MoreRow(Icons.AutoMirrored.Outlined.MenuBook, stringResource(R.string.menu_categories_title)) { onNavigate(ShellNav.MenuCategories) }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            MoreRow(Icons.Outlined.RateReview, stringResource(R.string.reviews_title)) { onNavigate(ShellNav.Reviews) }
            MoreRow(Icons.Outlined.Notifications, stringResource(R.string.notifications_title)) { onNavigate(ShellNav.Notifications) }
            MoreRow(Icons.AutoMirrored.Outlined.HelpOutline, stringResource(R.string.support_title)) { onNavigate(ShellNav.Support) }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            MoreRow(Icons.AutoMirrored.Outlined.Logout, stringResource(R.string.action_logout)) { confirmLogout = true }
        }
    }
    if (confirmLogout) {
        ConfirmDialog(
            title = stringResource(R.string.action_logout), text = stringResource(R.string.logout_confirm), confirmText = stringResource(R.string.action_logout),
            onConfirm = { confirmLogout = false; viewModel.logout() }, onDismiss = { confirmLogout = false }, destructive = true,
        )
    }
}

@Composable
private fun MoreRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
