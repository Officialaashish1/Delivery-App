package `in`.localrush.customer.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
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
import `in`.localrush.core.designsystem.component.OfflineBanner
import `in`.localrush.customer.R
import `in`.localrush.customer.data.repo.RestaurantSort
import `in`.localrush.customer.feature.cart.ui.CartScreen
import `in`.localrush.customer.feature.checkout.ui.AddressEditScreen
import `in`.localrush.customer.feature.checkout.ui.CheckoutScreen
import `in`.localrush.customer.feature.checkout.ui.OrderSuccessScreen
import `in`.localrush.customer.feature.home.ui.HomeActions
import `in`.localrush.customer.feature.location.ui.PickLocationScreen
import `in`.localrush.customer.feature.main.ui.MainActions
import `in`.localrush.customer.feature.main.ui.MainScreen
import `in`.localrush.customer.feature.main.ui.TermsScreen
import `in`.localrush.customer.feature.onboarding.ui.OnboardingScreen
import `in`.localrush.customer.feature.orders.ui.OrderDetailScreen
import `in`.localrush.customer.feature.orders.ui.RateScreen
import `in`.localrush.customer.feature.profile.ui.AboutScreen
import `in`.localrush.customer.feature.profile.ui.AddressesScreen
import `in`.localrush.customer.feature.profile.ui.EditProfileScreen
import `in`.localrush.customer.feature.profile.ui.HelpScreen
import `in`.localrush.customer.feature.profile.ui.NewTicketScreen
import `in`.localrush.customer.feature.profile.ui.NotificationsScreen
import `in`.localrush.customer.feature.profile.ui.ProfileActions
import `in`.localrush.customer.feature.profile.ui.TicketDetailScreen
import `in`.localrush.customer.feature.restaurant.ui.RestaurantScreen
import `in`.localrush.customer.feature.search.ui.RestaurantListScreen
import `in`.localrush.customer.feature.search.ui.SearchScreen
import `in`.localrush.customer.feature.splash.ui.SplashScreen
import `in`.localrush.customer.feature.tracking.ui.TrackingScreen
import `in`.localrush.feature.auth.ui.AuthRoute

/**
 * Splash → Onboarding → PickLocation → Main (guest browsing allowed). Auth is pushed on demand and popped on login;
 * a forced logout pops everything back to Main. Deep links (`localrush://order/{id}`, `localrush://restaurant/{slug}`)
 * are consumed once Main is on the stack.
 */
@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    viewModel: RootViewModel = hiltViewModel(),
    deepLinks: DeepLinkHandler = hiltViewModel<DeepLinkViewModel>().handler,
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val startup by viewModel.startup.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val pendingLink by deepLinks.pending.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val signedOutMsg = stringResource(R.string.signed_out_message)
    val nearbyTitle = stringResource(R.string.home_find_nearby)

    LaunchedEffect(Unit) { viewModel.forcedLogout.collect { snackbar.showSnackbar(signedOutMsg) } }

    // Forced logout / logout while on a logged-in-only screen: fall back to Main.
    LaunchedEffect(session) {
        if (session is SessionState.LoggedOut) {
            val current = navController.currentBackStackEntry?.destination ?: return@LaunchedEffect
            val onEntry = current.hasRoute<Routes.Splash>() || current.hasRoute<Routes.Onboarding>() || current.hasRoute<Routes.PickLocation>() || current.hasRoute<Routes.Auth>() || current.hasRoute<Routes.Main>()
            if (!onEntry) navController.navigate(Routes.Main()) { popUpTo(navController.graph.id) { inclusive = true } }
        }
    }

    // Deep links: wait until Main exists on the stack.
    LaunchedEffect(pendingLink, startup.ready, session) {
        val link = pendingLink ?: return@LaunchedEffect
        if (!startup.ready || !startup.onboardingDone || !startup.hasLocation) return@LaunchedEffect
        val current = navController.currentBackStackEntry?.destination ?: return@LaunchedEffect
        if (current.hasRoute<Routes.Splash>() || current.hasRoute<Routes.Onboarding>() || current.hasRoute<Routes.PickLocation>()) return@LaunchedEffect
        when (link) {
            is DeepLink.Order -> if (session is SessionState.LoggedIn) navController.navigate(Routes.Tracking(link.orderId)) else navController.navigate(Routes.Auth)
            is DeepLink.Restaurant -> navController.navigate(Routes.Restaurant(link.slug))
            is DeepLink.Ticket -> navController.navigate(Routes.TicketDetail(link.ticketId))
            DeepLink.Promo, DeepLink.Home -> navController.navigate(Routes.Main()) { popUpTo(navController.graph.id) { inclusive = true } }
        }
        deepLinks.consume()
    }

    fun requireLogin(then: () -> Unit) { if (session is SessionState.LoggedIn) then() else navController.navigate(Routes.Auth) }
    fun openRestaurant(slug: String, itemId: String? = null) = navController.navigate(Routes.Restaurant(slug, itemId))
    fun openList(title: String, category: String? = null, cuisine: String? = null, query: String? = null, sort: String? = null, openNow: Boolean = false) =
        navController.navigate(Routes.RestaurantList(title, category, cuisine, query, sort, openNow))
    fun goMain(tab: Int = MainTabs.HOME) = navController.navigate(Routes.Main(tab)) { popUpTo(navController.graph.id) { inclusive = true } }

    Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = navController, startDestination = Routes.Splash,
            enterTransition = { slideInHorizontallyScoped() }, exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(200)) }, popExitTransition = { slideOutHorizontallyScoped() },
        ) {
            composable<Routes.Splash> {
                SplashScreen(onFinished = {
                    when {
                        !startup.onboardingDone -> navController.navigate(Routes.Onboarding) { popUpTo<Routes.Splash> { inclusive = true } }
                        !startup.hasLocation -> navController.navigate(Routes.PickLocation(first = true)) { popUpTo<Routes.Splash> { inclusive = true } }
                        else -> goMain()
                    }
                })
            }
            composable<Routes.Onboarding> {
                OnboardingScreen(onDone = { navController.navigate(Routes.PickLocation(first = true)) { popUpTo<Routes.Onboarding> { inclusive = true } } })
            }
            composable<Routes.PickLocation> { entry ->
                val first = entry.toRoute<Routes.PickLocation>().first
                PickLocationScreen(onSaved = { if (first) goMain() else navController.popBackStack() }, onBack = if (first) null else ({ navController.popBackStack() }))
            }
            composable<Routes.Auth> {
                AuthRoute(headline = stringResource(R.string.auth_headline), allowPasswordLogin = false, onLoggedIn = { _, _ ->
                    navController.popBackStack()
                    if (!startup.termsAccepted) navController.navigate(Routes.Terms)
                })
            }
            composable<Routes.Terms> {
                TermsScreen(onAccept = { viewModel.acceptTerms(); navController.popBackStack() }, onDecline = { viewModel.logout(); navController.popBackStack() })
            }
            composable<Routes.Main> { entry ->
                val tab = entry.toRoute<Routes.Main>().tab
                MainScreen(
                    initialTab = tab,
                    actions = MainActions(
                        home = HomeActions(
                            onPickLocation = { navController.navigate(Routes.PickLocation()) },
                            onNotifications = { requireLogin { navController.navigate(Routes.Notifications) } },
                            onSearch = { navController.navigate(Routes.Search) },
                            onCategory = { slug, name -> openList(name, category = slug) },
                            onCuisine = { slug, name -> openList(name, cuisine = slug) },
                            onNearby = { openList(nearbyTitle, sort = RestaurantSort.DISTANCE.wire, openNow = true) },
                            onSection = { key, title -> openList(title, sort = when (key) { "nearby" -> RestaurantSort.DISTANCE.wire; "fast" -> RestaurantSort.DELIVERY_TIME.wire; "top_rated" -> RestaurantSort.RATING.wire; else -> null }, openNow = key in setOf("nearby", "fast", "popular")) },
                            onRestaurant = { openRestaurant(it) },
                            onItem = { slug, id -> openRestaurant(slug, id) },
                            onBanner = { b ->
                                val link = b.deepLink.takeIf { it.isNotBlank() }?.let { deepLinks.parse(android.net.Uri.parse(it)) }
                                when {
                                    link is DeepLink.Restaurant -> openRestaurant(link.slug)
                                    b.restaurant != null -> openRestaurant(b.restaurant)
                                    b.couponCode.isNotBlank() -> requireLogin { navController.navigate(Routes.Cart) }
                                    else -> navController.navigate(Routes.Search)
                                }
                            },
                        ),
                        profile = ProfileActions(
                            onLogin = { navController.navigate(Routes.Auth) }, onEdit = { navController.navigate(Routes.EditProfile) }, onAddresses = { navController.navigate(Routes.Addresses) },
                            onOrders = { goMain(MainTabs.ORDERS) }, onNotifications = { navController.navigate(Routes.Notifications) }, onHelp = { navController.navigate(Routes.Help) }, onAbout = { navController.navigate(Routes.About) },
                        ),
                        onRestaurant = { openRestaurant(it) }, onItem = { slug, id -> openRestaurant(slug, id) }, onCuisine = { slug, name -> openList(name, cuisine = slug) },
                        onSearchAll = { q -> openList(q, query = q) }, onOrder = { navController.navigate(Routes.OrderDetail(it)) }, onTrack = { navController.navigate(Routes.Tracking(it)) },
                        onLogin = { navController.navigate(Routes.Auth) }, onCart = { requireLogin { navController.navigate(Routes.Cart) } },
                    ),
                )
            }
            composable<Routes.Search> {
                SearchScreen(onBack = { navController.popBackStack() }, onRestaurant = { openRestaurant(it) }, onItem = { slug, id -> openRestaurant(slug, id) }, onCuisine = { slug, name -> openList(name, cuisine = slug) }, onSeeAllRestaurants = { q -> openList(q, query = q) })
            }
            composable<Routes.RestaurantList> { RestaurantListScreen(onBack = { navController.popBackStack() }, onRestaurant = { openRestaurant(it) }) }
            composable<Routes.Restaurant> { RestaurantScreen(onBack = { navController.popBackStack() }, onCart = { navController.navigate(Routes.Cart) }, onLogin = { navController.navigate(Routes.Auth) }) }
            composable<Routes.Cart> {
                CartScreen(onBack = { navController.popBackStack() }, onBrowse = { goMain() }, onRestaurant = { openRestaurant(it) }, onCheckout = { navController.navigate(Routes.Checkout) }, onAddAddress = { navController.navigate(Routes.AddressEdit(forCheckout = true)) })
            }
            composable<Routes.Checkout> {
                CheckoutScreen(
                    onBack = { navController.popBackStack() }, onAddAddress = { navController.navigate(Routes.AddressEdit(forCheckout = true)) },
                    onPlaced = { id -> navController.navigate(Routes.OrderSuccess(id)) { popUpTo<Routes.Main>() } },
                    onViewOrder = { id -> navController.navigate(Routes.OrderDetail(id)) { popUpTo<Routes.Main>() } },
                )
            }
            composable<Routes.AddressEdit> { AddressEditScreen(onBack = { navController.popBackStack() }, onSaved = { navController.popBackStack() }) }
            composable<Routes.OrderSuccess> { OrderSuccessScreen(onTrack = { id -> navController.navigate(Routes.Tracking(id)) { popUpTo<Routes.Main>() } }, onHome = { goMain(MainTabs.ORDERS) }) }
            composable<Routes.OrderDetail> {
                OrderDetailScreen(
                    onBack = { navController.popBackStack() }, onTrack = { navController.navigate(Routes.Tracking(it)) }, onCart = { navController.navigate(Routes.Cart) },
                    onRate = { navController.navigate(Routes.Rate(it)) }, onHelp = { navController.navigate(Routes.NewTicket(it)) }, onRestaurant = { openRestaurant(it) },
                )
            }
            composable<Routes.Tracking> { TrackingScreen(onBack = { navController.popBackStack() }, onOrderDetail = { navController.navigate(Routes.OrderDetail(it)) }) }
            composable<Routes.Rate> { RateScreen(onBack = { navController.popBackStack() }, onDone = { navController.popBackStack() }) }
            composable<Routes.Notifications> { NotificationsScreen(onBack = { navController.popBackStack() }, onOrder = { navController.navigate(Routes.OrderDetail(it)) }) }
            composable<Routes.EditProfile> { EditProfileScreen(onBack = { navController.popBackStack() }) }
            composable<Routes.Addresses> { AddressesScreen(onBack = { navController.popBackStack() }, onAdd = { navController.navigate(Routes.AddressEdit()) }, onEdit = { navController.navigate(Routes.AddressEdit(addressId = it)) }) }
            composable<Routes.Help> { HelpScreen(onBack = { navController.popBackStack() }, onNewTicket = { navController.navigate(Routes.NewTicket()) }, onTicket = { navController.navigate(Routes.TicketDetail(it)) }, onLogin = { navController.navigate(Routes.Auth) }) }
            composable<Routes.NewTicket> { NewTicketScreen(onBack = { navController.popBackStack() }, onCreated = { id -> navController.navigate(Routes.TicketDetail(id)) { popUpTo<Routes.NewTicket> { inclusive = true } } }) }
            composable<Routes.TicketDetail> { TicketDetailScreen(onBack = { navController.popBackStack() }) }
            composable<Routes.About> { AboutScreen(onBack = { navController.popBackStack() }) }
        }
        Column(Modifier.fillMaxWidth().statusBarsPadding()) { OfflineBanner(visible = !isOnline) }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

private fun AnimatedContentTransitionScope<*>.slideInHorizontallyScoped() = slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300)) + fadeIn(tween(300))
private fun AnimatedContentTransitionScope<*>.slideOutHorizontallyScoped() = slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300)) + fadeOut(tween(300))

/** Tiny holder so the singleton DeepLinkHandler is reachable from composition without a Context. */
@dagger.hilt.android.lifecycle.HiltViewModel
class DeepLinkViewModel @javax.inject.Inject constructor(val handler: DeepLinkHandler) : androidx.lifecycle.ViewModel()
