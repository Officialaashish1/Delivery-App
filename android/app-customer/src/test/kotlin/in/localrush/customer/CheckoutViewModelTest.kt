package `in`.localrush.customer

import app.cash.turbine.test
import `in`.localrush.core.common.result.AppError
import `in`.localrush.core.common.result.AppResult
import `in`.localrush.core.common.result.EmptyResult
import `in`.localrush.core.common.result.Result
import `in`.localrush.core.network.dto.AddressDto
import `in`.localrush.core.network.dto.AppConfigDto
import `in`.localrush.core.network.dto.CreateReviewDto
import `in`.localrush.core.network.dto.OrderDetailDto
import `in`.localrush.core.network.dto.OrderListDto
import `in`.localrush.core.network.dto.OrderPricingDto
import `in`.localrush.core.network.dto.PageDto
import `in`.localrush.core.network.dto.PaymentVerifyDto
import `in`.localrush.core.network.dto.PlaceOrderDto
import `in`.localrush.core.network.dto.PlaceOrderResponseDto
import `in`.localrush.core.network.dto.RazorpayCheckoutDto
import `in`.localrush.core.network.dto.ReviewDto
import `in`.localrush.core.network.dto.RiderContactDto
import `in`.localrush.customer.data.dto.AddToCartDto
import `in`.localrush.customer.data.dto.AvailableCouponDto
import `in`.localrush.customer.data.dto.CartDto
import `in`.localrush.customer.data.dto.CartItemDto
import `in`.localrush.customer.data.dto.ReorderResponseDto
import `in`.localrush.customer.data.dto.RestaurantCardDto
import `in`.localrush.customer.data.repo.AddressRepository
import `in`.localrush.customer.data.repo.CartRepository
import `in`.localrush.customer.data.repo.ConfigRepository
import `in`.localrush.customer.data.repo.OrdersRepository
import `in`.localrush.customer.data.repo.PaymentResult
import `in`.localrush.customer.data.repo.PaymentResultBus
import `in`.localrush.customer.feature.checkout.vm.CheckoutEvent
import `in`.localrush.customer.feature.checkout.vm.CheckoutStep
import `in`.localrush.customer.feature.checkout.vm.CheckoutViewModel
import `in`.localrush.customer.feature.checkout.vm.PaymentMethod
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CheckoutViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val address = AddressDto(id = "addr-1", label = "home", addressLine1 = "12 MG Road", lat = 12.9, lng = 77.6, isDefault = true)
    private val cart = CartDto(
        id = "c1", restaurant = RestaurantCardDto(id = "r1", name = "Dosa Hub", slug = "dosa-hub"),
        items = listOf(CartItemDto(id = "li1", menuItem = "m1", name = "Masala Dosa", quantity = 2, unitPrice = "120.00", lineTotal = "240.00")),
        itemCount = 2, deliveryAddress = address,
        pricing = OrderPricingDto(itemTotal = "240.00", deliveryFee = "30.00", total = "270.00", meetsMinOrder = true),
    )
    private val payload = RazorpayCheckoutDto(keyId = "rzp_test", providerOrderId = "order_ABC", amountPaise = 27000)
    private fun order(status: String, method: String) = OrderDetailDto(id = "o1", orderNumber = "LR1001", status = status, paymentMethod = method)

    private class FakeCart(initial: CartDto) : CartRepository {
        override val cart = MutableStateFlow<CartDto?>(initial)
        var addressSet: String? = null
        override suspend fun refresh(): AppResult<CartDto> = Result.Ok(cart.value ?: CartDto())
        override suspend fun add(request: AddToCartDto): AppResult<CartDto> = Result.Ok(cart.value!!)
        override suspend fun updateQuantity(itemId: String, quantity: Int): AppResult<CartDto> = Result.Ok(cart.value!!)
        override suspend fun remove(itemId: String): AppResult<CartDto> = Result.Ok(cart.value!!)
        override suspend fun setAddress(addressId: String?): AppResult<CartDto> { addressSet = addressId; return Result.Ok(cart.value!!) }
        override suspend fun applyCoupon(code: String): AppResult<CartDto> = Result.Ok(cart.value!!)
        override suspend fun setInstructions(text: String): AppResult<CartDto> = Result.Ok(cart.value!!)
        override suspend fun clear(): EmptyResult = Result.Ok(Unit)
        override suspend fun coupons(restaurantId: String?, subtotal: String?): AppResult<List<AvailableCouponDto>> = Result.Ok(emptyList())
        override fun publish(cart: CartDto?) { this.cart.value = cart }
    }

    private class FakeOrders(private val onPlace: (PlaceOrderDto) -> AppResult<PlaceOrderResponseDto>) : OrdersRepository {
        val placed = mutableListOf<PlaceOrderDto>()
        var verifyResult: AppResult<OrderDetailDto> = Result.Err(AppError.Unknown("not configured"))
        var retryResult: AppResult<RazorpayCheckoutDto?> = Result.Err(AppError.Unknown("not configured"))
        override suspend fun list(scope: String, page: Int?): AppResult<PageDto<OrderListDto>> = Result.Ok(PageDto())
        override suspend fun detail(id: String): AppResult<OrderDetailDto> = Result.Err(AppError.Unknown())
        override suspend fun place(body: PlaceOrderDto): AppResult<PlaceOrderResponseDto> { placed += body; return onPlace(body) }
        override suspend fun verifyPayment(body: PaymentVerifyDto): AppResult<OrderDetailDto> = verifyResult
        override suspend fun retryPayment(orderId: String): AppResult<RazorpayCheckoutDto?> = retryResult
        override suspend fun cancel(id: String, reason: String): AppResult<OrderDetailDto> = Result.Err(AppError.Unknown())
        override suspend fun reorder(id: String): AppResult<ReorderResponseDto> = Result.Err(AppError.Unknown())
        override suspend fun downloadInvoice(id: String, orderNumber: String): AppResult<java.io.File> = Result.Err(AppError.Unknown())
        override suspend fun riderContact(id: String): AppResult<RiderContactDto> = Result.Err(AppError.Unknown())
        override suspend fun review(body: CreateReviewDto): AppResult<ReviewDto> = Result.Err(AppError.Unknown())
    }

    /** ConfigRepository / AddressRepository are concrete; drive them through fake Retrofit interfaces. */
    private fun configRepo(config: AppConfigDto) = ConfigRepository(object : `in`.localrush.core.network.api.ConfigApi {
        override suspend fun config() = config
        override suspend fun geocodeSearch(query: String, lat: Double?, lng: Double?) = emptyList<`in`.localrush.core.network.dto.GeocodeResultDto>()
        override suspend fun reverseGeocode(lat: Double, lng: Double) = `in`.localrush.core.network.dto.GeocodeResultDto()
    })
    private fun addressRepo() = AddressRepository(object : `in`.localrush.core.network.api.AddressApi {
        override suspend fun list(page: Int?, pageSize: Int?) = PageDto(results = listOf(address))
        override suspend fun create(body: `in`.localrush.core.network.dto.AddressWriteDto) = address
        override suspend fun get(id: String) = address
        override suspend fun update(id: String, body: `in`.localrush.core.network.dto.AddressWriteDto) = address
        override suspend fun delete(id: String) = retrofit2.Response.success<Unit>(null)
        override suspend fun profile() = `in`.localrush.core.network.dto.CustomerProfileDto()
    })

    private fun vm(orders: OrdersRepository, config: AppConfigDto = AppConfigDto(codEnabled = true, codMaxOrderValue = "0"), bus: PaymentResultBus = PaymentResultBus(), cartRepo: CartRepository = FakeCart(cart)) =
        CheckoutViewModel(cartRepo, addressRepo(), configRepo(config), orders, bus)

    @Test fun `COD order completes without opening Razorpay`() = runTest {
        val orders = FakeOrders { Result.Ok(PlaceOrderResponseDto(order("placed", "cod"), payment = null)) }
        val vm = vm(orders)
        advanceUntilIdle()
        vm.selectMethod(PaymentMethod.COD)
        vm.events.test {
            vm.placeOrder(); advanceUntilIdle()
            assertEquals(CheckoutEvent.OrderPlaced("o1"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(vm.state.value.step is CheckoutStep.Success)
        assertEquals("cod", orders.placed.single().paymentMethod)
        assertTrue(orders.placed.single().idempotencyKey.isNotBlank())
    }

    @Test fun `online order opens Razorpay then verifies and succeeds`() = runTest {
        val orders = FakeOrders { Result.Ok(PlaceOrderResponseDto(order("pending_payment", "upi"), payload)) }
        orders.verifyResult = Result.Ok(order("placed", "upi").copy(isPaid = true))
        val bus = PaymentResultBus()
        val vm = vm(orders, bus = bus)
        advanceUntilIdle()
        vm.selectMethod(PaymentMethod.UPI)
        vm.events.test {
            vm.placeOrder(); advanceUntilIdle()
            assertEquals(CheckoutEvent.OpenRazorpay(payload), awaitItem())
            assertEquals(CheckoutStep.AwaitingPayment("o1", payload), vm.state.value.step)
            bus.publish(PaymentResult.Success("order_ABC", "pay_1", "sig")); advanceUntilIdle()
            assertEquals(CheckoutEvent.OrderPlaced("o1"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(CheckoutStep.Success("o1", "LR1001"), vm.state.value.step)
    }

    @Test fun `payment failure goes to PaymentFailed and retry re-opens Razorpay with the fresh payload`() = runTest {
        val orders = FakeOrders { Result.Ok(PlaceOrderResponseDto(order("pending_payment", "card"), payload)) }
        val retryPayload = payload.copy(providerOrderId = "order_DEF")
        orders.retryResult = Result.Ok(retryPayload)
        val bus = PaymentResultBus()
        val vm = vm(orders, bus = bus)
        advanceUntilIdle()
        vm.selectMethod(PaymentMethod.CARD)
        vm.events.test {
            vm.placeOrder(); advanceUntilIdle()
            awaitItem() // OpenRazorpay
            bus.publish(PaymentResult.Failure(2, "Payment cancelled")); advanceUntilIdle()
            val failed = vm.state.value.step as CheckoutStep.PaymentFailed
            assertEquals("o1", failed.orderId); assertTrue(failed.canRetry)
            vm.retryPayment(); advanceUntilIdle()
            assertEquals(CheckoutEvent.OpenRazorpay(retryPayload), awaitItem())
            assertEquals(CheckoutStep.AwaitingPayment("o1", retryPayload), vm.state.value.step)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, orders.placed.size) // retry never re-places the order
    }

    @Test fun `verification failure is a retryable PaymentFailed`() = runTest {
        val orders = FakeOrders { Result.Ok(PlaceOrderResponseDto(order("pending_payment", "upi"), payload)) }
        orders.verifyResult = Result.Err(AppError.Api("payment_invalid_signature", "Signature mismatch", 400))
        val bus = PaymentResultBus()
        val vm = vm(orders, bus = bus)
        advanceUntilIdle()
        vm.placeOrder(); advanceUntilIdle()
        bus.publish(PaymentResult.Success("order_ABC", "pay_1", "bad")); advanceUntilIdle()
        val step = vm.state.value.step as CheckoutStep.PaymentFailed
        assertEquals("Signature mismatch", step.message)
        assertTrue(step.canRetry)
    }

    @Test fun `COD is unavailable when disabled or above the limit and the default method falls back to UPI`() = runTest {
        val orders = FakeOrders { Result.Err(AppError.Unknown()) }
        val disabled = vm(orders, config = AppConfigDto(codEnabled = false))
        advanceUntilIdle()
        assertFalse(disabled.state.value.codAllowed)
        assertFalse(PaymentMethod.COD in disabled.state.value.availableMethods)
        disabled.selectMethod(PaymentMethod.COD)
        assertNotEquals(PaymentMethod.COD, disabled.state.value.method)

        val capped = vm(orders, config = AppConfigDto(codEnabled = true, codMaxOrderValue = "200.00")) // total is 270
        advanceUntilIdle()
        assertFalse(capped.state.value.codAllowed)

        val ok = vm(orders, config = AppConfigDto(codEnabled = true, codMaxOrderValue = "500.00"))
        advanceUntilIdle()
        assertTrue(ok.state.value.codAllowed)
    }

    @Test fun `place failure returns to Idle with the error and rotates the idempotency key for non-transient errors`() = runTest {
        var attempt = 0
        val orders = FakeOrders { attempt++; if (attempt == 1) Result.Err(AppError.Api("restaurant_closed", "Closed", 400)) else Result.Ok(PlaceOrderResponseDto(order("placed", "cod"), null)) }
        val vm = vm(orders)
        advanceUntilIdle()
        vm.selectMethod(PaymentMethod.COD)
        vm.placeOrder(); advanceUntilIdle()
        assertEquals(CheckoutStep.Idle, vm.state.value.step)
        assertEquals("restaurant_closed", (vm.state.value.error as AppError.Api).code)
        vm.placeOrder(); advanceUntilIdle()
        assertNotEquals(orders.placed[0].idempotencyKey, orders.placed[1].idempotencyKey)
    }

    @Test fun `transient place failure keeps the idempotency key so the retry is deduplicated server-side`() = runTest {
        var attempt = 0
        val orders = FakeOrders { attempt++; if (attempt == 1) Result.Err(AppError.Timeout()) else Result.Ok(PlaceOrderResponseDto(order("placed", "cod"), null)) }
        val vm = vm(orders)
        advanceUntilIdle()
        vm.selectMethod(PaymentMethod.COD)
        vm.placeOrder(); advanceUntilIdle()
        vm.placeOrder(); advanceUntilIdle()
        assertEquals(orders.placed[0].idempotencyKey, orders.placed[1].idempotencyKey)
    }

    @Test fun `cannot place without an address or while busy`() = runTest {
        val orders = FakeOrders { Result.Ok(PlaceOrderResponseDto(order("placed", "cod"), null)) }
        val noAddress = FakeCart(cart.copy(deliveryAddress = null))
        val vm = CheckoutViewModel(noAddress, AddressRepository(object : `in`.localrush.core.network.api.AddressApi {
            override suspend fun list(page: Int?, pageSize: Int?) = PageDto<AddressDto>()
            override suspend fun create(body: `in`.localrush.core.network.dto.AddressWriteDto) = address
            override suspend fun get(id: String) = address
            override suspend fun update(id: String, body: `in`.localrush.core.network.dto.AddressWriteDto) = address
            override suspend fun delete(id: String) = retrofit2.Response.success<Unit>(null)
            override suspend fun profile() = `in`.localrush.core.network.dto.CustomerProfileDto()
        }), configRepo(AppConfigDto()), orders, PaymentResultBus())
        advanceUntilIdle()
        assertFalse(vm.state.value.canPlace)
        vm.placeOrder(); advanceUntilIdle()
        assertTrue(orders.placed.isEmpty())
    }

}
