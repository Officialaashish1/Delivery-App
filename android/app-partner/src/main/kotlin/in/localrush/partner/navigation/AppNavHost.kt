package `in`.localrush.partner.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import `in`.localrush.core.auth.SessionState
import `in`.localrush.feature.auth.ui.AuthRoute
import `in`.localrush.partner.R
import `in`.localrush.partner.feature.gate.ui.GateScreen
import `in`.localrush.partner.feature.gate.vm.GateDestination
import `in`.localrush.partner.feature.home.ui.MainShell
import `in`.localrush.partner.feature.home.ui.MainTab
import `in`.localrush.partner.feature.home.ui.ShellNav
import `in`.localrush.partner.feature.menu.ui.CategoriesScreen
import `in`.localrush.partner.feature.menu.ui.ItemEditorScreen
import `in`.localrush.partner.feature.menu.ui.MenuItemsScreen
import `in`.localrush.partner.feature.notifications.NotificationsScreen
import `in`.localrush.partner.feature.orders.ui.OrderDetailScreen
import `in`.localrush.partner.feature.registration.ui.PendingApprovalScreen
import `in`.localrush.partner.feature.registration.ui.RegistrationScreen
import `in`.localrush.partner.feature.reviews.ui.ReviewsScreen
import `in`.localrush.partner.feature.settings.ui.DocumentsScreen
import `in`.localrush.partner.feature.settings.ui.HolidaysScreen
import `in`.localrush.partner.feature.settings.ui.HoursScreen
import `in`.localrush.partner.feature.settings.ui.ProfileEditScreen
import `in`.localrush.partner.feature.settings.ui.RestaurantSettingsScreen
import `in`.localrush.partner.feature.splash.ui.SplashScreen
import `in`.localrush.partner.feature.support.ui.NewTicketScreen
import `in`.localrush.partner.feature.support.ui.SupportScreen
import `in`.localrush.partner.feature.support.ui.TicketScreen

/**
 * Splash -> (Auth | Gate). Gate resolves to Registration / PendingApproval / Main. Session changes (login, logout,
 * forced logout) re-route automatically: a logged-out user never stays on a partner screen.
 */
@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    viewModel: RootViewModel = hiltViewModel(),
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val pendingDeepLink by viewModel.deepLinks.pending.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val signedOutMsg = stringResource(R.string.signed_out_message)

    LaunchedEffect(Unit) { viewModel.forcedLogout.collect { snackbar.showSnackbar(signedOutMsg) } }

    fun resetTo(route: Any) = navController.navigate(route) { popUpTo(navController.graph.id) { inclusive = true } }

    LaunchedEffect(session, viewModel.splashDone) {
        if (!viewModel.splashDone) return@LaunchedEffect
        val current = navController.currentBackStackEntry?.destination
        when (session) {
            is SessionState.LoggedIn -> if (current == null || current.hasRoute<Routes.Auth>() || current.hasRoute<Routes.Splash>()) resetTo(Routes.Gate)
            SessionState.LoggedOut -> if (current?.hasRoute<Routes.Auth>() != true) resetTo(Routes.Auth)
            SessionState.Unknown -> Unit
        }
    }

    // Notification taps: open the order once we are inside the main shell.
    LaunchedEffect(pendingDeepLink, session) {
        val link = pendingDeepLink ?: return@LaunchedEffect
        if (session !is SessionState.LoggedIn) return@LaunchedEffect
        val current = navController.currentBackStackEntry?.destination ?: return@LaunchedEffect
        val inside = current.hasRoute<Routes.Main>() || current.hasRoute<Routes.OrderDetail>() || current.hasRoute<Routes.Notifications>()
        if (!inside) return@LaunchedEffect
        when (link) {
            is DeepLink.Order -> navController.navigate(Routes.OrderDetail(link.orderId)) { launchSingleTop = true }
            is DeepLink.Ticket -> navController.navigate(Routes.Ticket(link.ticketId))
            else -> Unit
        }
        viewModel.deepLinks.consume()
    }

    Box(Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = Routes.Splash) {
            composable<Routes.Splash> { SplashScreen(onFinished = { viewModel.onSplashFinished() }) }
            composable<Routes.Auth> {
                AuthRoute(headline = stringResource(R.string.auth_headline), allowPasswordLogin = true, onLoggedIn = { _, _ -> })
            }
            composable<Routes.Gate> {
                GateScreen(onResolved = { dest ->
                    when (dest) {
                        GateDestination.REGISTRATION -> resetTo(Routes.Registration)
                        GateDestination.PENDING_APPROVAL -> resetTo(Routes.PendingApproval)
                        GateDestination.MAIN -> resetTo(Routes.Main(if (pendingDeepLink is DeepLink.Order) MainTab.ORDERS.key else MainTab.DASHBOARD.key))
                    }
                })
            }
            composable<Routes.Registration> {
                RegistrationScreen(onFinished = { resetTo(Routes.PendingApproval) }, onLogout = viewModel::logout)
            }
            composable<Routes.PendingApproval> {
                PendingApprovalScreen(onApproved = { resetTo(Routes.Main()) }, onEditProfile = { navController.navigate(Routes.ProfileEdit) }, onLogout = viewModel::logout)
            }
            composable<Routes.Main> { entry ->
                val tab = MainTab.fromKey(entry.toRoute<Routes.Main>().tab)
                MainShell(initialTab = tab, onNavigate = { target ->
                    when (target) {
                        is ShellNav.Order -> navController.navigate(Routes.OrderDetail(target.id))
                        ShellNav.MenuCategories -> navController.navigate(Routes.MenuCategories)
                        is ShellNav.ItemEditor -> navController.navigate(Routes.ItemEditor(target.itemId, target.categoryId))
                        ShellNav.Settings -> navController.navigate(Routes.RestaurantSettings)
                        ShellNav.Hours -> navController.navigate(Routes.HoursEditor)
                        ShellNav.Holidays -> navController.navigate(Routes.Holidays)
                        ShellNav.Documents -> navController.navigate(Routes.Documents)
                        ShellNav.Profile -> navController.navigate(Routes.ProfileEdit)
                        ShellNav.FinanceLines -> navController.navigate(Routes.FinanceLines)
                        ShellNav.Settlements -> navController.navigate(Routes.Settlements)
                        ShellNav.Reviews -> navController.navigate(Routes.Reviews)
                        ShellNav.Notifications -> navController.navigate(Routes.Notifications)
                        ShellNav.Support -> navController.navigate(Routes.Support)
                    }
                })
            }
            composable<Routes.OrderDetail> { OrderDetailScreen(onBack = { navController.popBackStack() }) }
            composable<Routes.MenuCategories> {
                CategoriesScreen(onBack = { navController.popBackStack() }, onOpenCategory = { navController.navigate(Routes.MenuItems(it)) })
            }
            composable<Routes.MenuItems> { entry ->
                MenuItemsScreen(
                    categoryId = entry.toRoute<Routes.MenuItems>().categoryId,
                    onBack = { navController.popBackStack() },
                    onManageCategories = { navController.navigate(Routes.MenuCategories) },
                    onEditItem = { itemId, categoryId -> navController.navigate(Routes.ItemEditor(itemId, categoryId)) },
                )
            }
            composable<Routes.ItemEditor> { ItemEditorScreen(onBack = { navController.popBackStack() }, onSaved = { navController.popBackStack() }) }
            composable<Routes.RestaurantSettings> {
                RestaurantSettingsScreen(
                    onBack = { navController.popBackStack() },
                    onHours = { navController.navigate(Routes.HoursEditor) }, onHolidays = { navController.navigate(Routes.Holidays) },
                    onDocuments = { navController.navigate(Routes.Documents) }, onProfile = { navController.navigate(Routes.ProfileEdit) },
                )
            }
            composable<Routes.HoursEditor> { HoursScreen(onBack = { navController.popBackStack() }) }
            composable<Routes.Holidays> { HolidaysScreen(onBack = { navController.popBackStack() }) }
            composable<Routes.Documents> { DocumentsScreen(onBack = { navController.popBackStack() }) }
            composable<Routes.ProfileEdit> { ProfileEditScreen(onBack = { navController.popBackStack() }) }
            composable<Routes.FinanceLines> { `in`.localrush.partner.feature.finance.ui.FinanceLinesScreen(onBack = { navController.popBackStack() }) }
            composable<Routes.Settlements> { `in`.localrush.partner.feature.finance.ui.SettlementsScreen(onBack = { navController.popBackStack() }) }
            composable<Routes.Reviews> { ReviewsScreen(onBack = { navController.popBackStack() }) }
            composable<Routes.Notifications> {
                NotificationsScreen(onBack = { navController.popBackStack() }, onOpenOrder = { navController.navigate(Routes.OrderDetail(it)) })
            }
            composable<Routes.Support> {
                SupportScreen(onBack = { navController.popBackStack() }, onOpenTicket = { navController.navigate(Routes.Ticket(it)) }, onNewTicket = { navController.navigate(Routes.NewTicket) })
            }
            composable<Routes.NewTicket> {
                NewTicketScreen(onBack = { navController.popBackStack() }, onCreated = { id -> navController.navigate(Routes.Ticket(id)) { popUpTo<Routes.Support>() } })
            }
            composable<Routes.Ticket> { TicketScreen(onBack = { navController.popBackStack() }) }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}
