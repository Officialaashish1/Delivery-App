package `in`.localrush.partner.screenshots

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
import `in`.localrush.core.common.money.Money
import `in`.localrush.core.common.util.UiState
import `in`.localrush.core.designsystem.theme.LocalRushTheme
import `in`.localrush.core.network.dto.OrderItemDto
import `in`.localrush.core.network.dto.RiderPublicDto
import `in`.localrush.core.network.ws.WebSocketClient
import `in`.localrush.partner.data.AddonGroupDto
import `in`.localrush.partner.data.AddonOptionDto
import `in`.localrush.partner.data.CuisineDto
import `in`.localrush.partner.data.DashboardDto
import `in`.localrush.partner.data.DashboardPeriodDto
import `in`.localrush.partner.data.DashboardTodayDto
import `in`.localrush.partner.data.LastSettlementDto
import `in`.localrush.partner.data.MenuCategoryDto
import `in`.localrush.partner.data.MenuItemDto
import `in`.localrush.partner.data.PartnerOrderDto
import `in`.localrush.partner.data.PartnerRestaurantDto
import `in`.localrush.partner.data.SettlementDto
import `in`.localrush.partner.data.SettlementLineDto
import `in`.localrush.partner.data.VariantGroupDto
import `in`.localrush.partner.data.VariantOptionDto
import `in`.localrush.partner.feature.dashboard.ui.DashboardScreen
import `in`.localrush.partner.feature.dashboard.vm.DashboardData
import `in`.localrush.partner.feature.dashboard.vm.DashboardViewModel
import `in`.localrush.partner.feature.finance.ui.FinanceScreen
import `in`.localrush.partner.feature.finance.vm.DatePreset
import `in`.localrush.partner.feature.finance.vm.DateRange
import `in`.localrush.partner.feature.finance.vm.FinanceData
import `in`.localrush.partner.feature.finance.vm.FinanceEvent
import `in`.localrush.partner.feature.finance.vm.FinanceSummary
import `in`.localrush.partner.feature.finance.vm.FinanceViewModel
import `in`.localrush.partner.feature.menu.ui.ItemEditorScreen
import `in`.localrush.partner.feature.menu.ui.MenuItemsScreen
import `in`.localrush.partner.feature.menu.vm.AddonGroupForm
import `in`.localrush.partner.feature.menu.vm.AddonOptionForm
import `in`.localrush.partner.feature.menu.vm.ItemEditorData
import `in`.localrush.partner.feature.menu.vm.ItemEditorEvent
import `in`.localrush.partner.feature.menu.vm.ItemEditorViewModel
import `in`.localrush.partner.feature.menu.vm.ItemForm
import `in`.localrush.partner.feature.menu.vm.ItemsData
import `in`.localrush.partner.feature.menu.vm.MenuItemsViewModel
import `in`.localrush.partner.feature.menu.vm.VariantGroupForm
import `in`.localrush.partner.feature.menu.vm.VariantOptionForm
import `in`.localrush.partner.feature.orders.ui.OrderDetailScreen
import `in`.localrush.partner.feature.orders.ui.OrdersBoardScreen
import `in`.localrush.partner.feature.orders.vm.OrderDetailData
import `in`.localrush.partner.feature.orders.vm.OrderDetailViewModel
import `in`.localrush.partner.feature.orders.vm.OrderTab
import `in`.localrush.partner.feature.orders.vm.OrdersBoardState
import `in`.localrush.partner.feature.orders.vm.OrdersEvent
import `in`.localrush.partner.feature.orders.vm.OrdersUiState
import `in`.localrush.partner.feature.orders.vm.OrdersViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Screenshot renders of the partner app's key screens (Paparazzi, JVM/layoutlib, no emulator).
 * ViewModels are MockK mocks holding sample state. Record with `./BUILD.sh :app-partner:recordPaparazziDebug`.
 */
class PartnerScreenshotTest {

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
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides NoopActivityResultRegistryOwner) {
                LocalRushTheme(darkTheme = false) { content() }
            }
        }
    }

    @Test fun ordersBoardNew() = snap("orders_board") {
        val vm = mockk<OrdersViewModel>(relaxed = true)
        every { vm.events } returns MutableSharedFlow<OrdersEvent>()
        every { vm.state } returns MutableStateFlow(
            OrdersUiState(
                board = OrdersBoardState(orders = Sample.orders.associateBy { it.id }, unacknowledged = setOf("o1"), loadedTabs = OrderTab.entries.toSet()),
                selectedTab = OrderTab.NEW, connection = WebSocketClient.State.Connected, online = true, now = Sample.now,
            ),
        )
        OrdersBoardScreen(onOpenOrder = {}, viewModel = vm)
    }

    @Test fun orderDetailPickupOtp() = snap("order_detail") {
        val vm = mockk<OrderDetailViewModel>(relaxed = true)
        every { vm.orderId } returns Sample.readyOrder.id
        every { vm.online } returns MutableStateFlow(true)
        every { vm.events } returns MutableSharedFlow<OrdersEvent>()
        every { vm.state } returns MutableStateFlow(UiState.Success(OrderDetailData(order = Sample.readyOrder, now = Sample.now)))
        OrderDetailScreen(onBack = {}, viewModel = vm)
    }

    @Test fun dashboard() = snap("dashboard") {
        val vm = mockk<DashboardViewModel>(relaxed = true)
        every { vm.online } returns MutableStateFlow(true)
        every { vm.messages } returns MutableSharedFlow<String>()
        every { vm.state } returns MutableStateFlow(
            UiState.Success(DashboardData(dashboard = Sample.dashboard, restaurant = Sample.restaurant, bestSellers = Sample.items.take(4))),
        )
        DashboardScreen(onOpenOrders = {}, onOpenFinance = {}, onOpenNotifications = {}, onOpenSettings = {}, viewModel = vm)
    }

    @Test fun menuItems() = snap("menu_items") {
        val vm = mockk<MenuItemsViewModel>(relaxed = true)
        every { vm.online } returns MutableStateFlow(true)
        every { vm.messages } returns MutableSharedFlow<String>()
        every { vm.state } returns MutableStateFlow(UiState.Success(ItemsData(items = Sample.items, categories = Sample.categories)))
        MenuItemsScreen(categoryId = null, onBack = null, onManageCategories = {}, onEditItem = { _, _ -> }, viewModel = vm)
    }

    @Test fun itemEditor() = snap("item_editor") {
        val vm = mockk<ItemEditorViewModel>(relaxed = true)
        every { vm.itemId } returns "m1"
        every { vm.online } returns MutableStateFlow(true)
        every { vm.events } returns MutableSharedFlow<ItemEditorEvent>()
        every { vm.state } returns MutableStateFlow(
            UiState.Success(
                ItemEditorData(
                    form = ItemForm(
                        categoryId = "mc1", name = "Chicken Biryani", description = "Boneless chicken, long-grain rice, served with raita and salan.", foodType = "non_veg",
                        price = "289", discountedPrice = "", packagingCharge = "20", preparationMinutes = "20", isAvailable = true, isBestseller = true, spiceLevel = 2, tags = "bestseller, spicy",
                        variantGroups = listOf(VariantGroupForm("Portion", isRequired = true, options = listOf(VariantOptionForm("Regular", "289", isDefault = true), VariantOptionForm("Family pack", "549")))),
                        addonGroups = listOf(AddonGroupForm("Extras", minSelect = "0", maxSelect = "3", options = listOf(AddonOptionForm("Extra raita", "30"), AddonOptionForm("Boiled egg", "20", foodType = "egg")))),
                    ),
                    categories = Sample.categories, isNew = false,
                ),
            ),
        )
        ItemEditorScreen(onBack = {}, onSaved = {}, viewModel = vm)
    }

    @Test fun finance() = snap("finance") {
        val vm = mockk<FinanceViewModel>(relaxed = true)
        every { vm.online } returns MutableStateFlow(true)
        every { vm.events } returns MutableSharedFlow<FinanceEvent>()
        every { vm.state } returns MutableStateFlow(
            UiState.Success(
                FinanceData(
                    summary = FinanceSummary(
                        orders = 212, grossSales = Money.ofRupees(168_430), commission = Money.ofRupees(25_264.5), couponDeductions = Money.ofRupees(6_120),
                        refundDeductions = Money.ofRupees(1_240), taxCollected = Money.ofRupees(8_421.5), netEarnings = Money.ofRupees(135_805.5),
                    ),
                    preset = DatePreset.MONTH, range = DateRange.forPreset(DatePreset.MONTH, LocalDate.now()),
                    unsettledLines = Sample.lines, lastSettlement = Sample.settlement,
                ),
            ),
        )
        FinanceScreen(onOpenLines = {}, onOpenSettlements = {}, viewModel = vm)
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

    val restaurant = PartnerRestaurantDto(
        id = "r1", name = "Meghana Foods", slug = "meghana-foods", ownerName = "Ravi Kumar", phone = "08041234567", email = "indiranagar@meghanafoods.in",
        addressLine1 = "544, 1st Floor, CMH Road", addressLine2 = "Indiranagar", city = "Bengaluru", state = "Karnataka",
        cuisines = listOf(CuisineDto(1, "Biryani", "biryani"), CuisineDto(2, "Andhra", "andhra")), minOrderAmount = "99", packagingCharge = "20", avgPreparationMinutes = 20,
        costForTwo = "500", status = "approved", isOpen = true, isAcceptingOrders = true, ratingAvg = "4.5", ratingCount = 2310, totalOrders = 48210,
        effectiveCommissionPercent = "15", isOpenNow = true,
    )

    val dashboard = DashboardDto(
        today = DashboardTodayDto(orders = 46, completed = 39, pending = 4, new = 2, cancelled = 1, revenue = "31240", grossSales = "36750", avgOrderValue = "799"),
        week = DashboardPeriodDto(orders = 288, revenue = "196420"), month = DashboardPeriodDto(orders = 1134, revenue = "781300"),
        ratingAvg = "4.5", ratingCount = 2310, totalOrders = 48210, isOpen = true, isAcceptingOrders = true, isOpenNow = true, status = "approved",
        unsettledAmount = "42180", lastSettlement = LastSettlementDto(amount = "154320", status = "paid", periodEnd = iso(now.minus(6, ChronoUnit.DAYS))), commissionPercent = "15",
    )

    val categories = listOf(
        MenuCategoryDto("mc1", "Biryanis", itemCount = 6), MenuCategoryDto("mc2", "Starters", itemCount = 9), MenuCategoryDto("mc3", "Breads & Sides", itemCount = 5), MenuCategoryDto("mc4", "Desserts", itemCount = 3),
    )

    private fun item(id: String, cat: String, catName: String, name: String, price: String, foodType: String = "veg", available: Boolean = true, bestseller: Boolean = false, orders: Int = 100, discounted: String? = null) = MenuItemDto(
        id = id, category = cat, categoryName = catName, name = name, foodType = foodType, price = price, discountedPrice = discounted, effectivePrice = discounted ?: price,
        isAvailable = available, isBestseller = bestseller, orderCount = orders, ratingAvg = "4.4", ratingCount = orders / 4,
    )

    val items = listOf(
        item("m1", "mc1", "Biryanis", "Chicken Biryani", "289", "non_veg", bestseller = true, orders = 1830).copy(
            variantGroups = listOf(VariantGroupDto("vg1", "Portion", options = listOf(VariantOptionDto("v1", "Regular", "289", isDefault = true), VariantOptionDto("v2", "Family pack", "549")))),
            addonGroups = listOf(AddonGroupDto("ag1", "Extras", maxSelect = 3, options = listOf(AddonOptionDto("a1", "Extra raita", "30"), AddonOptionDto("a2", "Boiled egg", "20", "egg")))),
        ),
        item("m3", "mc1", "Biryanis", "Mutton Biryani", "349", "non_veg", orders = 920),
        item("m4", "mc1", "Biryanis", "Veg Biryani", "219", "veg", orders = 410, discounted = "189"),
        item("m5", "mc2", "Starters", "Chicken 65", "229", "non_veg", bestseller = true, orders = 760),
        item("m6", "mc2", "Starters", "Paneer Majestic", "239", "veg", orders = 330),
        item("m9", "mc2", "Starters", "Apollo Fish", "279", "non_veg", available = false, orders = 190),
        item("m7", "mc3", "Breads & Sides", "Butter Naan", "49", orders = 2200),
        item("m10", "mc4", "Desserts", "Double Ka Meetha", "129", orders = 140),
    )

    private val orderItems = listOf(
        OrderItemDto("oi1", name = "Chicken Biryani", foodType = "non_veg", variantName = "Regular", addonNames = listOf("Extra raita"), quantity = 2, unitPrice = "319", lineTotal = "638"),
        OrderItemDto("oi2", name = "Paneer Majestic", foodType = "veg", quantity = 1, unitPrice = "239", lineTotal = "239"),
        OrderItemDto("oi3", name = "Butter Naan", foodType = "veg", quantity = 2, unitPrice = "49", lineTotal = "98", specialInstructions = "Less butter"),
    )

    private fun order(id: String, number: String, status: String, display: String, customer: String, placedMinAgo: Long, total: String, payment: String = "upi", note: String = "", items: List<OrderItemDto> = orderItems) = PartnerOrderDto(
        id = id, orderNumber = number, status = status, statusDisplay = display, items = items, customerName = customer, customerNote = note,
        itemTotal = "975", packagingCharge = "20", couponDiscount = "100", totalAmount = total, restaurantPayout = "760.75", commissionAmount = "134.25",
        paymentMethod = payment, isPaid = payment != "cod", preparationMinutes = 20, placedAt = minutesAgo(placedMinAgo), createdAt = minutesAgo(placedMinAgo),
        timeSincePlacedS = placedMinAgo * 60,
    )

    val orders = listOf(
        order("o1", "LR-240921-0351", "placed", "New", "Aarav Mehta", 1, "895", note = "Extra spicy please, no onions in the raita"),
        order("o2", "LR-240921-0349", "placed", "New", "Priya Nair", 4, "645", payment = "cod", items = orderItems.take(2)),
        order("o3", "LR-240921-0342", "preparing", "Being prepared", "Rahul S", 18, "818.35"),
        order("o4", "LR-240921-0336", "ready_for_pickup", "Ready for pickup", "Divya R", 27, "412", items = orderItems.drop(1)),
        order("o5", "LR-240921-0311", "delivered", "Delivered", "Karan P", 95, "1240"),
    )

    val readyOrder = order("o4", "LR-240921-0336", "rider_assigned", "Rider assigned", "Divya R", 27, "895", note = "Please pack the raita separately").copy(
        rider = RiderPublicDto(id = "rd1", fullName = "Suresh K", ratingAvg = "4.8", totalDeliveries = 1260, vehicleType = "scooter", vehicleNumber = "KA 03 MX 4521"),
        pickupOtp = "7364", acceptedAt = minutesAgo(25), readyAt = minutesAgo(5), estimatedDeliveryAt = iso(now.plus(18, ChronoUnit.MINUTES)),
    )

    val lines = listOf(
        SettlementLineDto("l1", orderNumber = "LR-240921-0311", gross = "1240", commissionPercent = "15", commission = "186", net = "1054", deliveredAt = minutesAgo(40)),
        SettlementLineDto("l2", orderNumber = "LR-240921-0298", gross = "645", commissionPercent = "15", commission = "96.75", couponDeduction = "50", net = "498.25", isCod = true, deliveredAt = minutesAgo(110)),
        SettlementLineDto("l3", orderNumber = "LR-240921-0287", gross = "818.35", commissionPercent = "15", commission = "122.75", net = "695.60", deliveredAt = minutesAgo(160)),
    )

    val settlement = SettlementDto(
        id = "s1", restaurantName = "Meghana Foods", periodStart = iso(now.minus(13, ChronoUnit.DAYS)), periodEnd = iso(now.minus(6, ChronoUnit.DAYS)), orderCount = 198,
        grossSales = "181560", commissionAmount = "27234", couponDeductions = "5420", refundDeductions = "860", netPayable = "154320", status = "paid", reference = "UTR2409170091",
        processedAt = iso(now.minus(5, ChronoUnit.DAYS)), createdAt = iso(now.minus(6, ChronoUnit.DAYS)),
    )
}
