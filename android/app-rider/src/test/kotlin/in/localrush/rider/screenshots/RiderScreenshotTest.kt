package `in`.localrush.rider.screenshots

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.app.ActivityOptionsCompat
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
import `in`.localrush.core.common.geo.LatLng
import `in`.localrush.core.common.order.OrderStatus
import `in`.localrush.core.designsystem.theme.LocalRushTheme
import `in`.localrush.core.network.dto.LatLngDto
import `in`.localrush.core.network.dto.OrderItemDto
import `in`.localrush.core.network.dto.RouteStepDto
import `in`.localrush.rider.data.EarningsPeriod
import `in`.localrush.rider.data.EarningsSummaryDto
import `in`.localrush.rider.data.IncentiveDto
import `in`.localrush.rider.data.NextManeuver
import `in`.localrush.rider.data.PeriodTotals
import `in`.localrush.rider.data.Progress
import `in`.localrush.rider.data.RiderDashboardDto
import `in`.localrush.rider.data.RiderEarningDto
import `in`.localrush.rider.data.RiderEarningsDto
import `in`.localrush.rider.data.RiderOfferDto
import `in`.localrush.rider.data.RiderOrderDto
import `in`.localrush.rider.data.RiderPayoutDto
import `in`.localrush.rider.data.RiderStatus
import `in`.localrush.rider.data.RouteSnapshot
import `in`.localrush.rider.data.VehicleType
import `in`.localrush.rider.feature.dashboard.ui.DashboardScreen
import `in`.localrush.rider.feature.dashboard.vm.DashboardEvent
import `in`.localrush.rider.feature.dashboard.vm.DashboardUiState
import `in`.localrush.rider.feature.dashboard.vm.DashboardViewModel
import `in`.localrush.rider.feature.delivery.ui.DeliveryScreen
import `in`.localrush.rider.feature.delivery.vm.DeliveryCore
import `in`.localrush.rider.feature.delivery.vm.DeliveryUiEvent
import `in`.localrush.rider.feature.delivery.vm.DeliveryUiState
import `in`.localrush.rider.feature.delivery.vm.DeliveryViewModel
import `in`.localrush.rider.feature.earnings.ui.EarningsScreen
import `in`.localrush.rider.feature.earnings.vm.EarningsEvent
import `in`.localrush.rider.feature.earnings.vm.EarningsUiState
import `in`.localrush.rider.feature.earnings.vm.EarningsViewModel
import `in`.localrush.rider.feature.offer.ui.OfferScreen
import `in`.localrush.rider.feature.offer.vm.OfferUiState
import `in`.localrush.rider.feature.offer.vm.OfferViewModel
import `in`.localrush.rider.feature.onboarding.ui.RegistrationScreen
import `in`.localrush.rider.feature.onboarding.vm.RegForm
import `in`.localrush.rider.feature.onboarding.vm.RegStep
import `in`.localrush.rider.feature.onboarding.vm.RegistrationEvent
import `in`.localrush.rider.feature.onboarding.vm.RegistrationUiState
import `in`.localrush.rider.feature.onboarding.vm.RegistrationViewModel
import `in`.localrush.rider.ui.common.LocalMapPlaceholder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Screenshot renders of the rider app's key screens (Paparazzi, JVM/layoutlib, no emulator).
 * ViewModels are MockK mocks holding sample state; the navigation map is a placeholder (MapLibre needs GL).
 * Record with `./BUILD.sh :app-rider:recordPaparazziDebug`.
 */
class RiderScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6,
        theme = "android:Theme.Material.Light.NoActionBar",
        renderingMode = SessionParams.RenderingMode.NORMAL,
        showSystemUi = false,
        maxPercentDifference = 0.5,
        useDeviceResolution = true,
    )

    private fun snap(name: String, content: @Composable () -> Unit) {
        paparazzi.snapshot(name) {
            CompositionLocalProvider(
                LocalMapPlaceholder provides true,
                LocalActivityResultRegistryOwner provides NoopActivityResultRegistryOwner,
            ) {
                LocalRushTheme(darkTheme = false) { content() }
            }
        }
    }

    @Test fun dashboardOnline() = snap("dashboard") {
        val vm = mockk<DashboardViewModel>(relaxed = true)
        every { vm.events } returns MutableSharedFlow<DashboardEvent>()
        every { vm.state } returns MutableStateFlow(
            DashboardUiState(
                loading = false, data = Sample.dashboard, currentOrder = null, isOnline = true, riderStatus = RiderStatus.APPROVED,
                riderName = "Suresh Kumar", networkOnline = true, trackingRunning = true, backgroundPermission = true,
            ),
        )
        DashboardScreen(onOpenDelivery = {}, onOpenOffer = {}, onOpenEarnings = {}, viewModel = vm)
    }

    @Test fun offer() = snap("offer") {
        val vm = mockk<OfferViewModel>(relaxed = true)
        every { vm.state } returns MutableStateFlow(OfferUiState.Ready(offer = Sample.offer, secondsLeft = 24, totalSeconds = 45))
        OfferScreen(assignmentId = Sample.offer.id, onAccepted = {}, onClose = {}, viewModel = vm)
    }

    @Test fun activeDelivery() = snap("active_delivery") {
        val vm = mockk<DeliveryViewModel>(relaxed = true)
        every { vm.events } returns MutableSharedFlow<DeliveryUiEvent>()
        every { vm.state } returns MutableStateFlow(Sample.delivery)
        DeliveryScreen(onBack = {}, onCompleted = {}, mapStyleUrl = "https://tiles.openfreemap.org/styles/liberty", viewModel = vm)
    }

    @Test fun earnings() = snap("earnings") {
        val vm = mockk<EarningsViewModel>(relaxed = true)
        every { vm.events } returns MutableSharedFlow<EarningsEvent>()
        every { vm.state } returns MutableStateFlow(
            EarningsUiState(
                period = EarningsPeriod.WEEK, loading = false, data = Sample.earnings,
                totals = PeriodTotals(Sample.summary.weekEarnings, Sample.summary.weekDeliveries), payouts = Sample.payouts,
            ),
        )
        EarningsScreen(onOpenPayouts = {}, onBack = {}, viewModel = vm)
    }

    @Test fun registrationStep1() = snap("registration_step1") {
        val vm = mockk<RegistrationViewModel>(relaxed = true)
        every { vm.events } returns MutableSharedFlow<RegistrationEvent>()
        every { vm.state } returns MutableStateFlow(
            RegistrationUiState(
                loading = false, step = RegStep.PERSONAL,
                form = RegForm(fullName = "Suresh Kumar", email = "suresh.k@example.com", dateOfBirth = "1994-06-12", address = "18, 4th Cross, BTM Layout 2nd Stage", city = "Bengaluru", vehicleType = VehicleType.SCOOTER),
            ),
        )
        RegistrationScreen(onFinished = {}, onExit = {}, onLogout = {}, viewModel = vm)
    }
}

/** `rememberLauncherForActivityResult` needs a registry owner; nothing is ever launched in a screenshot. */
private object NoopActivityResultRegistryOwner : ActivityResultRegistryOwner {
    override val activityResultRegistry: ActivityResultRegistry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) = Unit
    }
}

private object Sample {
    val now: Instant = Instant.now().truncatedTo(ChronoUnit.MINUTES)
    fun iso(i: Instant): String = DateTimeFormatter.ISO_INSTANT.format(i)
    private fun minutesAgo(m: Long) = iso(now.minus(m, ChronoUnit.MINUTES))

    val restaurantPoint = LatLng(12.9698, 77.6350)
    val customerPoint = LatLng(12.9784, 77.6408)
    val riderPoint = LatLng(12.9731, 77.6379)

    val order = RiderOrderDto(
        id = "o1", orderNumber = "LR-240921-0342", status = "picked_up", statusDisplay = "Picked up",
        items = listOf(
            OrderItemDto("oi1", name = "Chicken Biryani", foodType = "non_veg", variantName = "Regular", quantity = 2, unitPrice = "319", lineTotal = "638"),
            OrderItemDto("oi2", name = "Paneer Majestic", foodType = "veg", quantity = 1, unitPrice = "239", lineTotal = "239"),
            OrderItemDto("oi3", name = "Butter Naan", foodType = "veg", quantity = 2, unitPrice = "49", lineTotal = "98"),
        ),
        restaurantName = "Meghana Foods", restaurantAddressText = "544, CMH Road, Indiranagar", restaurantLocation = LatLngDto(restaurantPoint.latitude, restaurantPoint.longitude),
        restaurantPhone = "08041234567", deliveryName = "Aarav Mehta", deliveryAddressText = "42, 12th Main Rd, Indiranagar, Bengaluru 560038", deliveryLandmark = "Near Metro station",
        deliveryLocation = LatLngDto(customerPoint.latitude, customerPoint.longitude), deliveryInstructions = "Ring the bell twice", isContactless = false,
        paymentMethod = "cod", isPaid = false, collectAmount = "818", riderPayout = "64", distanceKm = "3.2",
        placedAt = minutesAgo(34), acceptedAt = minutesAgo(32), readyAt = minutesAgo(12), pickedUpAt = minutesAgo(8), estimatedDeliveryAt = iso(now.plus(9, ChronoUnit.MINUTES)), preparationMinutes = 20,
    )

    val offer = RiderOfferDto(
        id = "as1", order = order.copy(status = "ready_for_pickup", statusDisplay = "Ready for pickup", pickedUpAt = null), status = "offered",
        distanceToRestaurantKm = "1.4", offeredAt = iso(now), expiresAt = iso(now.plus(45, ChronoUnit.SECONDS)), secondsLeft = 24,
    )

    val dashboard = RiderDashboardDto(
        todayEarnings = "640", todayDeliveries = 7, weekEarnings = "4320", weekDeliveries = 38, monthEarnings = "15870", monthDeliveries = 142,
        walletBalance = "2140", codCashInHand = "818", lifetimeDeliveries = 1260, ratingAvg = "4.8", pendingPayout = "1322",
        incentives = listOf(IncentiveDto("Weekend rush", deliveriesRequired = 12, bonusAmount = "300", progress = 7)),
        isOnline = true, status = "approved",
        pendingOffers = listOf(offer),
    )

    val summary = EarningsSummaryDto(
        todayEarnings = "640", todayDeliveries = 7, weekEarnings = "4320", weekDeliveries = 38, monthEarnings = "15870", monthDeliveries = 142,
        walletBalance = "2140", codCashInHand = "818", lifetimeDeliveries = 1260, ratingAvg = "4.8", pendingPayout = "1322",
    )

    val earnings = RiderEarningsDto(
        summary = summary,
        entries = listOf(
            RiderEarningDto("e1", earningType = "delivery", amount = "64", description = "Delivery payout", distanceKm = "3.2", orderNumber = "LR-240921-0342", createdAt = minutesAgo(5)),
            RiderEarningDto("e2", earningType = "distance", amount = "18", description = "Long distance bonus", distanceKm = "3.2", orderNumber = "LR-240921-0342", createdAt = minutesAgo(5)),
            RiderEarningDto("e3", earningType = "tip", amount = "30", description = "Customer tip", orderNumber = "LR-240921-0298", createdAt = minutesAgo(95)),
            RiderEarningDto("e4", earningType = "delivery", amount = "58", description = "Delivery payout", distanceKm = "2.1", orderNumber = "LR-240921-0298", createdAt = minutesAgo(96)),
            RiderEarningDto("e5", earningType = "incentive", amount = "300", description = "Weekend rush incentive", isSettled = true, createdAt = iso(now.minus(2, ChronoUnit.DAYS))),
            RiderEarningDto("e6", earningType = "cod_deposit", amount = "1200", description = "COD cash deposited", isSettled = true, createdAt = iso(now.minus(2, ChronoUnit.DAYS))),
        ),
    )

    val payouts = listOf(
        RiderPayoutDto("p1", amount = "3200", status = "paid", reference = "UTR2409180042", processedAt = iso(now.minus(3, ChronoUnit.DAYS)), createdAt = iso(now.minus(4, ChronoUnit.DAYS))),
        RiderPayoutDto("p2", amount = "1322", status = "requested", createdAt = iso(now.minus(1, ChronoUnit.HOURS))),
    )

    private val steps = listOf(
        RouteStepDto(instruction = "Head north on CMH Road", name = "CMH Road", distanceM = 420.0, durationS = 80.0, maneuverType = "depart", location = listOf(restaurantPoint.longitude, restaurantPoint.latitude)),
        RouteStepDto(instruction = "Turn right onto 100 Feet Road", name = "100 Feet Road", distanceM = 900.0, durationS = 180.0, maneuverType = "turn", modifier = "right", location = listOf(77.6379, 12.9731)),
        RouteStepDto(instruction = "Turn left onto 12th Main Road", name = "12th Main Road", distanceM = 530.0, durationS = 110.0, maneuverType = "turn", modifier = "left", location = listOf(77.6401, 12.9776)),
        RouteStepDto(instruction = "Arrive at destination", name = "", distanceM = 0.0, durationS = 0.0, maneuverType = "arrive", location = listOf(customerPoint.longitude, customerPoint.latitude)),
    )

    val delivery = DeliveryUiState(
        orderId = order.id, loading = false, order = order, core = DeliveryCore(status = OrderStatus.PICKED_UP),
        position = riderPoint, heading = 48f, speedKmph = 24f, gpsAccuracyM = 6f,
        route = RouteSnapshot(
            geometry = "", points = listOf(restaurantPoint, riderPoint, LatLng(12.9776, 77.6401), customerPoint), steps = steps, stepVertex = listOf(0, 1, 2, 3),
            totalDistanceM = 1850.0, totalDurationS = 370.0, leg = "to_customer", fetchedAtMs = now.toEpochMilli(),
        ),
        progress = Progress(
            segment = 1, onRoute = riderPoint, offRouteM = 4.0, remainingM = 1430.0, remainingS = 290.0,
            next = NextManeuver(step = steps[1], distanceM = 220.0, index = 1),
        ),
        following = true, voiceEnabled = true, networkOnline = true,
    )
}
