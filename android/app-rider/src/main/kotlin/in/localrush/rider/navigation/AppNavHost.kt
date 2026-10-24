package `in`.localrush.rider.navigation

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
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import `in`.localrush.core.auth.SessionState
import `in`.localrush.feature.auth.ui.AuthRoute
import `in`.localrush.rider.R
import `in`.localrush.rider.feature.delivery.ui.DeliveryScreen
import `in`.localrush.rider.feature.earnings.ui.PayoutsScreen
import `in`.localrush.rider.feature.history.ui.OrderDetailScreen
import `in`.localrush.rider.feature.offer.ui.OfferScreen
import `in`.localrush.rider.feature.onboarding.ui.GateScreen
import `in`.localrush.rider.feature.onboarding.ui.PendingScreen
import `in`.localrush.rider.feature.onboarding.ui.RegistrationScreen
import `in`.localrush.rider.feature.onboarding.vm.Destination
import `in`.localrush.rider.feature.profile.ui.DocumentsScreen
import `in`.localrush.rider.feature.splash.ui.SplashScreen
import `in`.localrush.rider.feature.support.ui.NewTicketScreen
import `in`.localrush.rider.feature.support.ui.SupportScreen
import `in`.localrush.rider.feature.support.ui.TicketDetailScreen
import `in`.localrush.rider.ui.main.MainShell

/**
 * Splash → (Auth | Gate → Registration / Pending / Main). Session changes re-route automatically; pending
 * offers (FCM/WebSocket) and notification deep links open on top of whatever the rider is doing.
 */
@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    viewModel: RootViewModel = hiltViewModel(),
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val mapStyleUrl by viewModel.mapStyleUrl.collectAsStateWithLifecycle()
    val pendingOffer by viewModel.offerInbox.pending.collectAsStateWithLifecycle()
    val deepLink by viewModel.deepLinks.pending.collectAsStateWithLifecycle()
    val backStack by navController.currentBackStackEntryAsState()
    val snackbar = remember { SnackbarHostState() }
    val signedOutMsg = stringResource(R.string.signed_out_message)

    LaunchedEffect(Unit) { viewModel.forcedLogout.collect { snackbar.showSnackbar(signedOutMsg) } }

    val current = backStack?.destination
    val onEntryScreen = current == null || current.hasRoute<Routes.Splash>() || current.hasRoute<Routes.Auth>()
    val inApp = current != null && !onEntryScreen && !current.hasRoute<Routes.Gate>()

    // Route on session changes once the splash has finished.
    LaunchedEffect(session, viewModel.splashDone, onEntryScreen) {
        if (!viewModel.splashDone) return@LaunchedEffect
        when (session) {
            is SessionState.LoggedIn -> if (onEntryScreen) navController.navigate(Routes.Gate) { popUpTo(navController.graph.id) { inclusive = true } }
            SessionState.LoggedOut -> if (current?.hasRoute<Routes.Auth>() != true) navController.navigate(Routes.Auth) { popUpTo(navController.graph.id) { inclusive = true } }
            SessionState.Unknown -> Unit
        }
    }
    // Offers arriving via FCM / WebSocket.
    LaunchedEffect(pendingOffer, inApp) {
        val id = pendingOffer ?: return@LaunchedEffect
        if (!inApp) return@LaunchedEffect
        val onThisOffer = current?.hasRoute<Routes.Offer>() == true && backStack?.toRoute<Routes.Offer>()?.assignmentId == id
        if (!onThisOffer) navController.navigate(Routes.Offer(id)) { launchSingleTop = true }
    }
    // Notification deep links.
    LaunchedEffect(deepLink, inApp) {
        val link = deepLink ?: return@LaunchedEffect
        if (!inApp) return@LaunchedEffect
        when (link) {
            is DeepLink.Offer -> navController.navigate(Routes.Offer(link.assignmentId)) { launchSingleTop = true }
            is DeepLink.Order -> navController.navigate(Routes.Delivery(link.orderId)) { launchSingleTop = true }
            is DeepLink.Ticket -> navController.navigate(Routes.Ticket(link.ticketId))
            DeepLink.Promo, DeepLink.Home -> Unit
        }
        viewModel.deepLinks.consume()
    }

    Box(Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = Routes.Splash) {
            composable<Routes.Splash> { SplashScreen(onFinished = { viewModel.onSplashFinished() }) }
            composable<Routes.Auth> {
                AuthRoute(headline = stringResource(R.string.auth_headline), allowPasswordLogin = false, onLoggedIn = { _, _ -> /* session flow re-routes */ })
            }
            composable<Routes.Gate> {
                GateScreen(onDecided = { d ->
                    val target: Any = when (d) { Destination.REGISTRATION -> Routes.Registration(); Destination.PENDING -> Routes.Pending; Destination.MAIN -> Routes.Main }
                    navController.navigate(target) { popUpTo(navController.graph.id) { inclusive = true } }
                })
            }
            composable<Routes.Registration> {
                RegistrationScreen(
                    onFinished = { navController.navigate(Routes.Gate) { popUpTo(navController.graph.id) { inclusive = true } } },
                    onExit = { if (!navController.popBackStack()) navController.navigate(Routes.Gate) { popUpTo(navController.graph.id) { inclusive = true } } },
                    onLogout = viewModel::logout,
                )
            }
            composable<Routes.Pending> {
                PendingScreen(
                    onApproved = { navController.navigate(Routes.Main) { popUpTo(navController.graph.id) { inclusive = true } } },
                    onEditDetails = { navController.navigate(Routes.Registration(0)) },
                    onFixDocuments = { navController.navigate(Routes.Documents) },
                    onSupport = { navController.navigate(Routes.Support) },
                    onLogout = viewModel::logout,
                )
            }
            composable<Routes.Main> {
                MainShell(
                    onOpenDelivery = { navController.navigate(Routes.Delivery(it)) { launchSingleTop = true } },
                    onOpenOffer = { navController.navigate(Routes.Offer(it)) { launchSingleTop = true } },
                    onOpenOrder = { navController.navigate(Routes.OrderDetail(it)) },
                    onOpenPayouts = { navController.navigate(Routes.Payouts) },
                    onOpenDocuments = { navController.navigate(Routes.Documents) },
                    onOpenSupport = { navController.navigate(Routes.Support) },
                    onEdit = { step -> navController.navigate(Routes.Registration(step.ordinal)) },
                )
            }
            composable<Routes.Offer> { entry ->
                val route = entry.toRoute<Routes.Offer>()
                OfferScreen(
                    assignmentId = route.assignmentId,
                    onAccepted = { orderId -> navController.navigate(Routes.Delivery(orderId)) { popUpTo<Routes.Offer> { inclusive = true }; launchSingleTop = true } },
                    onClose = { if (!navController.popBackStack()) navController.navigate(Routes.Main) },
                )
            }
            composable<Routes.Delivery> {
                DeliveryScreen(
                    onBack = { if (!navController.popBackStack()) navController.navigate(Routes.Main) },
                    onCompleted = { navController.navigate(Routes.Main) { popUpTo(navController.graph.id) { inclusive = true } } },
                    mapStyleUrl = mapStyleUrl,
                )
            }
            composable<Routes.OrderDetail> {
                OrderDetailScreen(onBack = { navController.popBackStack() }, onResume = { navController.navigate(Routes.Delivery(it)) { launchSingleTop = true } })
            }
            composable<Routes.Payouts> { PayoutsScreen(onBack = { navController.popBackStack() }) }
            composable<Routes.Documents> { DocumentsScreen(onBack = { navController.popBackStack() }) }
            composable<Routes.Support> {
                SupportScreen(onBack = { navController.popBackStack() }, onNewTicket = { navController.navigate(Routes.NewTicket()) }, onOpenTicket = { navController.navigate(Routes.Ticket(it)) })
            }
            composable<Routes.NewTicket> {
                NewTicketScreen(onBack = { navController.popBackStack() }, onCreated = { id -> navController.navigate(Routes.Ticket(id)) { popUpTo<Routes.NewTicket> { inclusive = true } } })
            }
            composable<Routes.Ticket> { TicketDetailScreen(onBack = { navController.popBackStack() }) }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}
