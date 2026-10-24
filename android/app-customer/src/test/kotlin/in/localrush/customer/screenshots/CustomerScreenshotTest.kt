package `in`.localrush.customer.screenshots

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
import `in`.localrush.core.network.dto.AddressDto
import `in`.localrush.core.network.dto.AppConfigDto
import `in`.localrush.core.network.dto.LatLngDto
import `in`.localrush.core.network.dto.OrderDetailDto
import `in`.localrush.core.network.dto.OrderItemDto
import `in`.localrush.core.network.dto.OrderListDto
import `in`.localrush.core.network.dto.OrderPricingDto
import `in`.localrush.core.network.dto.RiderPublicDto
import `in`.localrush.core.network.dto.RouteDto
import `in`.localrush.core.network.dto.TimelineEntryDto
import `in`.localrush.customer.data.dto.AddonDto
import `in`.localrush.customer.data.dto.AddonGroupDto
import `in`.localrush.customer.data.dto.BannerDto
import `in`.localrush.customer.data.dto.CartDto
import `in`.localrush.customer.data.dto.CartItemDto
import `in`.localrush.customer.data.dto.CategoryDto
import `in`.localrush.customer.data.dto.CuisineDto
import `in`.localrush.customer.data.dto.HomeDto
import `in`.localrush.customer.data.dto.HomeSectionDto
import `in`.localrush.customer.data.dto.MenuCategoryDto
import `in`.localrush.customer.data.dto.MenuItemDto
import `in`.localrush.customer.data.dto.RestaurantCardDto
import `in`.localrush.customer.data.dto.RestaurantDetailDto
import `in`.localrush.customer.data.dto.RestaurantHoursDto
import `in`.localrush.customer.data.dto.VariantDto
import `in`.localrush.customer.data.dto.VariantGroupDto
import `in`.localrush.customer.data.repo.DeliveryLocation
import `in`.localrush.customer.feature.cart.ui.CartScreen
import `in`.localrush.customer.feature.cart.vm.CartEvent
import `in`.localrush.customer.feature.cart.vm.CartUiState
import `in`.localrush.customer.feature.cart.vm.CartViewModel
import `in`.localrush.customer.feature.checkout.ui.CheckoutScreen
import `in`.localrush.customer.feature.checkout.vm.CheckoutEvent
import `in`.localrush.customer.feature.checkout.vm.CheckoutUiState
import `in`.localrush.customer.feature.checkout.vm.CheckoutViewModel
import `in`.localrush.customer.feature.checkout.vm.PaymentMethod
import `in`.localrush.customer.feature.home.ui.HomeActions
import `in`.localrush.customer.feature.home.ui.HomeScreen
import `in`.localrush.customer.feature.home.vm.HomeUiState
import `in`.localrush.customer.feature.home.vm.HomeViewModel
import `in`.localrush.customer.feature.orders.ui.OrderDetailScreen
import `in`.localrush.customer.feature.orders.ui.OrdersScreen
import `in`.localrush.customer.feature.orders.vm.OrderDetailEvent
import `in`.localrush.customer.feature.orders.vm.OrderDetailUiState
import `in`.localrush.customer.feature.orders.vm.OrderDetailViewModel
import `in`.localrush.customer.feature.orders.vm.OrdersScope
import `in`.localrush.customer.feature.orders.vm.OrdersTabState
import `in`.localrush.customer.feature.orders.vm.OrdersUiState
import `in`.localrush.customer.feature.orders.vm.OrdersViewModel
import `in`.localrush.customer.feature.restaurant.ui.RestaurantScreen
import `in`.localrush.customer.feature.restaurant.vm.RestaurantEvent
import `in`.localrush.customer.feature.restaurant.vm.RestaurantUiState
import `in`.localrush.customer.feature.restaurant.vm.RestaurantViewModel
import `in`.localrush.customer.feature.tracking.ui.TrackingScreen
import `in`.localrush.customer.feature.tracking.vm.ConnectionState
import `in`.localrush.customer.feature.tracking.vm.RiderFix
import `in`.localrush.customer.feature.tracking.vm.TrackingEvent
import `in`.localrush.customer.feature.tracking.vm.TrackingUiState
import `in`.localrush.customer.feature.tracking.vm.TrackingViewModel
import `in`.localrush.customer.ui.common.LocalMapPlaceholder
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
 * Screenshot renders of the customer app's key screens (Paparazzi, JVM/layoutlib, no emulator).
 * ViewModels are MockK mocks whose `state` flows hold realistic sample data; the map area is a placeholder
 * because MapLibre needs a GL surface. Record with `./BUILD.sh :app-customer:recordPaparazziDebug`.
 */
class CustomerScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6,
        theme = "android:Theme.Material.Light.NoActionBar",
        renderingMode = SessionParams.RenderingMode.NORMAL,
        showSystemUi = false,
        maxPercentDifference = 0.5,
        useDeviceResolution = true,
    )

    private fun snap(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        paparazzi.snapshot(name) {
            CompositionLocalProvider(
                LocalMapPlaceholder provides true,
                LocalActivityResultRegistryOwner provides NoopActivityResultRegistryOwner,
            ) {
                LocalRushTheme(darkTheme = dark) { content() }
            }
        }
    }

    // ------------------------------------------------------------------ Home

    @Composable
    private fun homeScreen() {
        val vm = mockk<HomeViewModel>(relaxed = true)
        every { vm.state } returns MutableStateFlow(
            HomeUiState(
                loading = false,
                home = Sample.home,
                loggedIn = true,
                location = DeliveryLocation(Sample.homePoint, "Home", "42, 12th Main Rd, Indiranagar, Bengaluru 560038", "Bengaluru"),
                unread = 2,
                userName = "Aarav",
            ),
        )
        HomeScreen(Sample.homeActions, vm)
    }

    @Test fun home() = snap("home") { homeScreen() }

    @Test fun homeDark() = snap("home_dark", dark = true) { homeScreen() }

    // ------------------------------------------------------------ Restaurant

    @Test fun restaurant() = snap("restaurant") {
        val vm = mockk<RestaurantViewModel>(relaxed = true)
        every { vm.state } returns MutableStateFlow(
            RestaurantUiState(loading = false, restaurant = Sample.restaurant, cart = Sample.cart, loggedIn = true),
        )
        every { vm.events } returns MutableSharedFlow<RestaurantEvent>()
        RestaurantScreen(onBack = {}, onCart = {}, onLogin = {}, viewModel = vm)
    }

    // ------------------------------------------------------------------ Cart

    @Test fun cart() = snap("cart") {
        val vm = mockk<CartViewModel>(relaxed = true)
        every { vm.state } returns MutableStateFlow(
            CartUiState(loading = false, cart = Sample.cart, addresses = Sample.addresses),
        )
        every { vm.events } returns MutableSharedFlow<CartEvent>()
        CartScreen(onBack = {}, onBrowse = {}, onRestaurant = {}, onCheckout = {}, onAddAddress = {}, viewModel = vm)
    }

    // -------------------------------------------------------------- Checkout

    @Test fun checkout() = snap("checkout") {
        val vm = mockk<CheckoutViewModel>(relaxed = true)
        every { vm.state } returns MutableStateFlow(
            CheckoutUiState(
                cart = Sample.cart,
                addresses = Sample.addresses,
                config = AppConfigDto(codEnabled = true, codMaxOrderValue = "2000"),
                selectedAddressId = Sample.addresses.first().id,
                instructions = "Ring the bell twice",
                contactless = true,
                method = PaymentMethod.UPI,
            ),
        )
        every { vm.events } returns MutableSharedFlow<CheckoutEvent>()
        CheckoutScreen(onBack = {}, onAddAddress = {}, onPlaced = {}, onViewOrder = {}, viewModel = vm)
    }

    // -------------------------------------------------------------- Tracking

    @Test fun tracking() = snap("tracking") {
        val vm = mockk<TrackingViewModel>(relaxed = true)
        val order = Sample.activeOrder
        every { vm.orderId } returns order.id
        every { vm.config } returns MutableStateFlow(AppConfigDto())
        every { vm.chatSending } returns MutableStateFlow(false)
        every { vm.events } returns MutableSharedFlow<TrackingEvent>()
        every { vm.state } returns MutableStateFlow(
            TrackingUiState(
                loading = false,
                order = order,
                status = OrderStatus.OUT_FOR_DELIVERY,
                statusDisplay = "On the way",
                timeline = order.timeline,
                rider = order.rider,
                riderFix = RiderFix(LatLng(12.9712, 77.6372), bearing = 48f, speedKmph = 22f, recordedAt = Sample.now),
                route = RouteDto(geometry = "", distanceM = 1850.0, durationS = 540.0, durationMin = 9.0, leg = "to_customer", etaAt = Sample.iso(Sample.now.plus(9, ChronoUnit.MINUTES))),
                restaurant = Sample.restaurantPoint,
                destination = Sample.homePoint,
                estimatedDeliveryAt = Sample.now.plus(9, ChronoUnit.MINUTES),
                deliveryOtp = "4821",
                connection = ConnectionState.LIVE,
                now = Sample.now,
            ),
        )
        TrackingScreen(onBack = {}, onOrderDetail = {}, viewModel = vm)
    }

    // ---------------------------------------------------------------- Orders

    @Test fun orders() = snap("orders") {
        val vm = mockk<OrdersViewModel>(relaxed = true)
        every { vm.state } returns MutableStateFlow(
            OrdersUiState(
                loggedIn = true,
                scope = OrdersScope.ACTIVE,
                active = OrdersTabState(loading = false, items = Sample.orderList),
                past = OrdersTabState(loading = false),
            ),
        )
        OrdersScreen(onOrder = {}, onTrack = {}, onLogin = {}, onBrowse = {}, viewModel = vm)
    }

    @Test fun orderDetail() = snap("order_detail") {
        val vm = mockk<OrderDetailViewModel>(relaxed = true)
        every { vm.orderId } returns Sample.deliveredOrder.id
        every { vm.events } returns MutableSharedFlow<OrderDetailEvent>()
        every { vm.state } returns MutableStateFlow(OrderDetailUiState(loading = false, order = Sample.deliveredOrder))
        OrderDetailScreen(onBack = {}, onTrack = {}, onCart = {}, onRate = {}, onHelp = {}, onRestaurant = {}, viewModel = vm)
    }
}

/** `rememberLauncherForActivityResult` needs a registry owner; nothing is ever launched in a screenshot. */
private object NoopActivityResultRegistryOwner : ActivityResultRegistryOwner {
    override val activityResultRegistry: ActivityResultRegistry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) = Unit
    }
}

/** Realistic, deterministic sample data for the renders (no network). */
private object Sample {
    val now: Instant = Instant.now().truncatedTo(ChronoUnit.MINUTES)
    fun iso(i: Instant): String = DateTimeFormatter.ISO_INSTANT.format(i)
    private fun minutesAgo(m: Long) = iso(now.minus(m, ChronoUnit.MINUTES))

    val homePoint = LatLng(12.9784, 77.6408)
    val restaurantPoint = LatLng(12.9698, 77.6350)

    val homeActions = HomeActions(
        onPickLocation = {}, onNotifications = {}, onSearch = {}, onCategory = { _, _ -> }, onCuisine = { _, _ -> },
        onNearby = {}, onSection = { _, _ -> }, onRestaurant = {}, onItem = { _, _ -> }, onBanner = {},
    )

    val restaurants = listOf(
        RestaurantCardDto(
            id = "r1", name = "Meghana Foods", slug = "meghana-foods", cuisines = listOf("Biryani", "Andhra", "North Indian"),
            ratingAvg = "4.5", ratingCount = 2310, costForTwo = "500", distanceKm = 1.8, deliveryTimeMin = 28, deliveryFee = "25",
            minOrderAmount = "99", offerText = "50% OFF up to ₹100", city = "Bengaluru", addressLine2 = "Indiranagar", isPromoted = true,
        ),
        RestaurantCardDto(
            id = "r2", name = "Truffles", slug = "truffles", cuisines = listOf("Burgers", "Continental", "Desserts"),
            ratingAvg = "4.3", ratingCount = 5120, costForTwo = "700", distanceKm = 2.6, deliveryTimeMin = 34, deliveryFee = "30",
            city = "Bengaluru", addressLine2 = "Koramangala", offerText = "Free delivery",
        ),
        RestaurantCardDto(
            id = "r3", name = "MTR 1924", slug = "mtr-1924", cuisines = listOf("South Indian", "Breakfast"),
            ratingAvg = "4.6", ratingCount = 8900, costForTwo = "300", distanceKm = 3.4, deliveryTimeMin = 38, deliveryFee = "35",
            city = "Bengaluru", addressLine2 = "Lalbagh", isPureVeg = true,
        ),
        RestaurantCardDto(
            id = "r4", name = "Chinita Real Mexican", slug = "chinita", cuisines = listOf("Mexican"),
            ratingAvg = "4.4", ratingCount = 1480, costForTwo = "900", distanceKm = 1.1, deliveryTimeMin = 24, deliveryFee = "20",
            city = "Bengaluru", addressLine2 = "Indiranagar",
        ),
    )

    private fun item(id: String, name: String, price: String, foodType: String = "veg", desc: String = "", bestseller: Boolean = false, discounted: String? = null, rating: String = "4.4", count: Int = 120) = MenuItemDto(
        id = id, restaurantId = "r1", restaurantName = "Meghana Foods", restaurantSlug = "meghana-foods", name = name, description = desc,
        foodType = foodType, price = price, discountedPrice = discounted, effectivePrice = discounted ?: price, isBestseller = bestseller,
        ratingAvg = rating, ratingCount = count, preparationMinutes = 20,
    )

    val recommended = listOf(
        item("m1", "Chicken Biryani", "289", "non_veg", bestseller = true, rating = "4.6", count = 1830),
        item("m2", "Paneer Butter Masala", "249", "veg", rating = "4.3", count = 640),
        item("m3", "Mutton Biryani", "349", "non_veg", rating = "4.5", count = 920),
    )

    val home = HomeDto(
        banners = listOf(
            BannerDto("b1", title = "Flat 50% off", subtitle = "On your first 3 orders. T&C apply.", couponCode = "WELCOME50"),
            BannerDto("b2", title = "Free delivery weekend", subtitle = "Orders above ₹199 from partner restaurants"),
        ),
        categories = listOf(
            CategoryDto("c1", "Biryani", "biryani"), CategoryDto("c2", "Pizza", "pizza"), CategoryDto("c3", "South Indian", "south-indian"),
            CategoryDto("c4", "Chinese", "chinese"), CategoryDto("c5", "Desserts", "desserts"), CategoryDto("c6", "Burgers", "burgers"),
        ),
        cuisines = listOf(CuisineDto("k1", "North Indian", "north-indian"), CuisineDto("k2", "Chinese", "chinese"), CuisineDto("k3", "Andhra", "andhra"), CuisineDto("k4", "Italian", "italian")),
        sections = listOf(
            HomeSectionDto("popular", "Popular near you", restaurants),
            HomeSectionDto("top_rated", "Top rated", restaurants.reversed()),
        ),
        recentlyOrdered = listOf(restaurants[0], restaurants[2]),
        recommendedItems = recommended,
    )

    val restaurant = RestaurantDetailDto(
        id = "r1", name = "Meghana Foods", slug = "meghana-foods", cuisines = listOf("Biryani", "Andhra", "North Indian"),
        ratingAvg = "4.5", ratingCount = 2310, costForTwo = "500", distanceKm = 1.8, deliveryTimeMin = 28, deliveryFee = "25", minOrderAmount = "99",
        offerText = "50% OFF up to ₹100", city = "Bengaluru", addressLine1 = "544, 1st Floor, CMH Road", addressLine2 = "Indiranagar", landmark = "Opp. Indiranagar Metro",
        description = "Legendary Andhra-style biryanis served since 2006.", restaurantType = "restaurant", lat = restaurantPoint.latitude, lng = restaurantPoint.longitude,
        packagingCharge = "20", avgPreparationMinutes = 20, fssaiNumber = "11219999000123", totalOrders = 48210,
        hours = (0..6).map { RestaurantHoursDto(weekday = it, openTime = "11:00:00", closeTime = "23:30:00") },
        menu = listOf(
            MenuCategoryDto(
                "mc1", "Biryanis", items = listOf(
                    item("m1", "Chicken Biryani", "289", "non_veg", "Boneless chicken, long-grain rice, served with raita and salan.", bestseller = true, rating = "4.6", count = 1830).copy(
                        variantGroups = listOf(VariantGroupDto("vg1", "Portion", options = listOf(VariantDto("v1", "Regular", "289", isDefault = true), VariantDto("v2", "Family pack", "549")))),
                        addonGroups = listOf(AddonGroupDto("ag1", "Extras", maxSelect = 3, options = listOf(AddonDto("a1", "Extra raita", "30"), AddonDto("a2", "Boiled egg", "20", "egg")))),
                    ),
                    item("m3", "Mutton Biryani", "349", "non_veg", "Slow-cooked mutton on the bone with spiced basmati rice.", rating = "4.5", count = 920),
                    item("m4", "Veg Biryani", "219", "veg", "Seasonal vegetables cooked dum-style.", discounted = "189"),
                ),
            ),
            MenuCategoryDto(
                "mc2", "Starters", items = listOf(
                    item("m5", "Chicken 65", "229", "non_veg", "Crispy fried chicken tossed with curry leaves and chillies.", bestseller = true),
                    item("m6", "Paneer Majestic", "239", "veg", "Spicy paneer strips tossed with onion and coriander."),
                ),
            ),
            MenuCategoryDto("mc3", "Breads & Sides", items = listOf(item("m7", "Butter Naan", "49"), item("m8", "Raita", "39"))),
        ),
    )

    val addresses = listOf(
        AddressDto(
            id = "a1", label = "home", name = "Home", contactName = "Aarav Mehta", contactPhone = "9876543210", addressLine1 = "42, 12th Main Rd",
            addressLine2 = "Indiranagar", landmark = "Near Metro station", city = "Bengaluru", state = "Karnataka", postalCode = "560038",
            lat = homePoint.latitude, lng = homePoint.longitude, isDefault = true, formatted = "42, 12th Main Rd, Indiranagar, Bengaluru 560038",
        ),
        AddressDto(
            id = "a2", label = "work", name = "Work", contactName = "Aarav Mehta", contactPhone = "9876543210", addressLine1 = "Prestige Tech Park",
            addressLine2 = "Marathahalli", city = "Bengaluru", state = "Karnataka", postalCode = "560103", lat = 12.9552, lng = 77.6971,
            formatted = "Prestige Tech Park, Marathahalli, Bengaluru 560103",
        ),
    )

    val pricing = OrderPricingDto(
        itemTotal = "827", packagingCharge = "20", deliveryFee = "25", deliveryDiscount = "0", platformFee = "5", taxPercent = "5", taxAmount = "41.35",
        couponCode = "WELCOME50", couponDiscount = "100", total = "818.35", distanceKm = "1.8", minOrderAmount = "99", meetsMinOrder = true,
    )

    val cart = CartDto(
        id = "cart1", restaurant = restaurants[0],
        items = listOf(
            CartItemDto("ci1", menuItem = "m1", name = "Chicken Biryani", foodType = "non_veg", variant = VariantDto("v1", "Regular", "289"), addons = listOf(AddonDto("a1", "Extra raita", "30")), quantity = 2, unitPrice = "319", lineTotal = "638"),
            CartItemDto("ci2", menuItem = "m6", name = "Paneer Majestic", foodType = "veg", quantity = 1, unitPrice = "239", lineTotal = "239"),
            CartItemDto("ci3", menuItem = "m7", name = "Butter Naan", foodType = "veg", quantity = 2, unitPrice = "49", lineTotal = "98", specialInstructions = "Less butter"),
        ),
        itemCount = 5, couponCode = "WELCOME50", deliveryAddress = addresses[0], pricing = pricing,
    )

    private val orderItems = listOf(
        OrderItemDto("oi1", name = "Chicken Biryani", foodType = "non_veg", variantName = "Regular", addonNames = listOf("Extra raita"), quantity = 2, unitPrice = "319", lineTotal = "638"),
        OrderItemDto("oi2", name = "Paneer Majestic", foodType = "veg", quantity = 1, unitPrice = "239", lineTotal = "239"),
        OrderItemDto("oi3", name = "Butter Naan", foodType = "veg", quantity = 2, unitPrice = "49", lineTotal = "98"),
    )

    val rider = RiderPublicDto(id = "rd1", fullName = "Suresh K", ratingAvg = "4.8", totalDeliveries = 1260, vehicleType = "scooter", vehicleNumber = "KA 03 MX 4521")

    val activeOrder = OrderDetailDto(
        id = "o1", orderNumber = "LR-240921-0342", status = "out_for_delivery", statusDisplay = "On the way", restaurant = "r1", restaurantName = "Meghana Foods",
        restaurantSlug = "meghana-foods", totalAmount = "818.35", paymentMethod = "upi", isPaid = true, itemCount = 5, itemsSummary = "Chicken Biryani x2, Paneer Majestic, Butter Naan x2",
        placedAt = minutesAgo(34), createdAt = minutesAgo(34), estimatedDeliveryAt = iso(now.plus(9, ChronoUnit.MINUTES)), isActive = true,
        items = orderItems,
        timeline = listOf(
            TimelineEntryDto("placed", "Order placed", done = true, at = minutesAgo(34)),
            TimelineEntryDto("accepted", "Accepted by restaurant", done = true, at = minutesAgo(32)),
            TimelineEntryDto("preparing", "Being prepared", done = true, at = minutesAgo(30)),
            TimelineEntryDto("ready", "Ready for pickup", done = true, at = minutesAgo(12)),
            TimelineEntryDto("picked_up", "Picked up", done = true, at = minutesAgo(8)),
            TimelineEntryDto("on_the_way", "On the way", done = true, current = true, at = minutesAgo(7)),
            TimelineEntryDto("delivered", "Delivered"),
        ),
        rider = rider, deliveryName = "Aarav Mehta", deliveryPhone = "9876543210", deliveryAddressText = "42, 12th Main Rd, Indiranagar, Bengaluru 560038",
        deliveryLandmark = "Near Metro station", deliveryLocation = LatLngDto(homePoint.latitude, homePoint.longitude), isContactless = true,
        restaurantAddressText = "544, CMH Road, Indiranagar", restaurantLocation = LatLngDto(restaurantPoint.latitude, restaurantPoint.longitude),
        deliveryOtp = "4821", pricing = pricing, distanceKm = "1.8", preparationMinutes = 20, acceptedAt = minutesAgo(32), readyAt = minutesAgo(12), pickedUpAt = minutesAgo(8),
    )

    val deliveredOrder = activeOrder.copy(
        id = "o2", orderNumber = "LR-240918-0117", status = "delivered", statusDisplay = "Delivered", isActive = false, deliveryOtp = null,
        placedAt = iso(now.minus(3, ChronoUnit.DAYS)), createdAt = iso(now.minus(3, ChronoUnit.DAYS)), deliveredAt = iso(now.minus(3, ChronoUnit.DAYS).plus(41, ChronoUnit.MINUTES)),
        estimatedDeliveryAt = null, isReviewed = false, invoiceUrl = "https://api.localrush.in/invoices/o2.html",
        timeline = activeOrder.timeline.map { it.copy(done = true, current = it.key == "delivered") },
    )

    val orderList = listOf(
        OrderListDto(
            id = "o1", orderNumber = "LR-240921-0342", status = "out_for_delivery", statusDisplay = "On the way", restaurantName = "Meghana Foods", restaurantSlug = "meghana-foods",
            totalAmount = "818.35", paymentMethod = "upi", isPaid = true, itemCount = 5, itemsSummary = "Chicken Biryani x2, Paneer Majestic, Butter Naan x2",
            placedAt = minutesAgo(34), estimatedDeliveryAt = iso(now.plus(9, ChronoUnit.MINUTES)), isActive = true,
        ),
        OrderListDto(
            id = "o3", orderNumber = "LR-240921-0351", status = "preparing", statusDisplay = "Being prepared", restaurantName = "Truffles", restaurantSlug = "truffles",
            totalAmount = "645", paymentMethod = "cod", itemCount = 3, itemsSummary = "Classic Chicken Burger, Fries, Oreo Shake",
            placedAt = minutesAgo(6), estimatedDeliveryAt = iso(now.plus(32, ChronoUnit.MINUTES)), isActive = true, canCancel = true,
        ),
    )
}
