package `in`.localrush.customer.feature.checkout.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.localrush.core.common.money.Money
import `in`.localrush.core.common.money.toMoney
import `in`.localrush.core.common.result.AppError
import `in`.localrush.core.common.result.ErrorCodes
import `in`.localrush.core.network.dto.AddressDto
import `in`.localrush.core.network.dto.AppConfigDto
import `in`.localrush.core.network.dto.PaymentVerifyDto
import `in`.localrush.core.network.dto.PlaceOrderDto
import `in`.localrush.core.network.dto.RazorpayCheckoutDto
import `in`.localrush.customer.data.dto.CartDto
import `in`.localrush.customer.data.repo.AddressRepository
import `in`.localrush.customer.data.repo.CartRepository
import `in`.localrush.customer.data.repo.ConfigRepository
import `in`.localrush.customer.data.repo.OrdersRepository
import `in`.localrush.customer.data.repo.PaymentResult
import `in`.localrush.customer.data.repo.PaymentResultBus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

enum class PaymentMethod(val wire: String, val online: Boolean) {
    COD("cod", false), UPI("upi", true), CARD("card", true), NETBANKING("netbanking", true)
}

/** Checkout state machine (see unit tests): Idle → Placing → (Success | AwaitingPayment → Verifying → Success | PaymentFailed → retry). */
sealed interface CheckoutStep {
    data object Idle : CheckoutStep
    data object Placing : CheckoutStep
    data class AwaitingPayment(val orderId: String, val payload: RazorpayCheckoutDto) : CheckoutStep
    data class Verifying(val orderId: String) : CheckoutStep
    data class Retrying(val orderId: String) : CheckoutStep
    /** [message] is the provider/backend text; null means "show the generic failure copy". */
    data class PaymentFailed(val orderId: String, val message: String?, val canRetry: Boolean = true) : CheckoutStep
    data class Success(val orderId: String, val orderNumber: String) : CheckoutStep
}

data class CheckoutUiState(
    val cart: CartDto? = null,
    val addresses: List<AddressDto>? = null,
    val config: AppConfigDto = AppConfigDto(),
    val selectedAddressId: String? = null,
    val instructions: String = "",
    val contactless: Boolean = false,
    val note: String = "",
    val method: PaymentMethod = PaymentMethod.UPI,
    val step: CheckoutStep = CheckoutStep.Idle,
    val error: AppError? = null,
    val addressBusy: Boolean = false,
) {
    val total: Money get() = cart?.pricing?.total?.toMoney() ?: Money.ZERO
    val codMax: Money get() = config.codMaxOrderValue.toMoney()
    val codAllowed: Boolean get() = config.codEnabled && (codMax.isZero || total <= codMax)
    val availableMethods: List<PaymentMethod> get() = PaymentMethod.entries.filter { it.online || codAllowed }
    val selectedAddress: AddressDto? get() = addresses?.firstOrNull { it.id == selectedAddressId } ?: cart?.deliveryAddress
    val busy: Boolean get() = step is CheckoutStep.Placing || step is CheckoutStep.Verifying || step is CheckoutStep.Retrying || step is CheckoutStep.AwaitingPayment
    val canPlace: Boolean get() = !busy && cart != null && !cart.isEmpty && selectedAddress != null && cart.pricing?.errors.isNullOrEmpty() && cart.pricing?.meetsMinOrder != false
}

sealed interface CheckoutEvent {
    data class OpenRazorpay(val payload: RazorpayCheckoutDto) : CheckoutEvent
    data class OrderPlaced(val orderId: String) : CheckoutEvent
    data class Message(val text: String) : CheckoutEvent
}

@HiltViewModel
class CheckoutViewModel @Inject constructor(
    private val cartRepo: CartRepository,
    private val addressRepo: AddressRepository,
    private val configRepo: ConfigRepository,
    private val orders: OrdersRepository,
    private val payments: PaymentResultBus,
) : ViewModel() {
    private val _state = MutableStateFlow(CheckoutUiState())
    val state: StateFlow<CheckoutUiState> = _state.asStateFlow()
    private val _events = MutableSharedFlow<CheckoutEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<CheckoutEvent> = _events.asSharedFlow()

    /** One key per checkout attempt; reused on transient failures so a retried POST returns the same order. */
    private var idempotencyKey: String = UUID.randomUUID().toString()

    init {
        viewModelScope.launch { cartRepo.cart.collect { c -> _state.update { it.copy(cart = c, selectedAddressId = it.selectedAddressId ?: c?.deliveryAddress?.id) } } }
        viewModelScope.launch { addressRepo.addresses.collect { a -> _state.update { it.copy(addresses = a) } } }
        viewModelScope.launch { configRepo.config.collect { c -> _state.update { s -> s.copy(config = c, method = if (!s.codAllowedWith(c) && s.method == PaymentMethod.COD) PaymentMethod.UPI else s.method) } } }
        viewModelScope.launch { configRepo.load(); addressRepo.load(); cartRepo.refresh() }
        viewModelScope.launch { payments.results.collect { onPaymentResult(it) } }
    }

    private fun CheckoutUiState.codAllowedWith(c: AppConfigDto): Boolean = c.codEnabled && (c.codMaxOrderValue.toMoney().isZero || total <= c.codMaxOrderValue.toMoney())

    fun selectAddress(id: String) {
        if (_state.value.busy) return
        _state.update { it.copy(selectedAddressId = id, addressBusy = true) }
        viewModelScope.launch {
            cartRepo.setAddress(id).onFailure { e -> _events.tryEmit(CheckoutEvent.Message(e.message)) }
            _state.update { it.copy(addressBusy = false) }
        }
    }
    fun setInstructions(v: String) = _state.update { it.copy(instructions = v.take(255)) }
    fun setNote(v: String) = _state.update { it.copy(note = v.take(255)) }
    fun setContactless(v: Boolean) = _state.update { it.copy(contactless = v) }
    fun selectMethod(m: PaymentMethod) { if (m in _state.value.availableMethods) _state.update { it.copy(method = m, error = null) } }
    fun dismissError() = _state.update { it.copy(error = null) }

    fun placeOrder() {
        val s = _state.value
        if (!s.canPlace) return
        val address = s.selectedAddress ?: return
        _state.update { it.copy(step = CheckoutStep.Placing, error = null) }
        viewModelScope.launch {
            val body = PlaceOrderDto(address.id, s.method.wire, idempotencyKey, s.contactless, s.instructions.trim(), s.note.trim())
            orders.place(body).fold(
                onOk = { r ->
                    val payment = r.payment
                    if (payment == null) {
                        // COD (or already paid): done. The server emptied the cart.
                        cartRepo.publish(CartDto())
                        finish(r.order.id, r.order.orderNumber)
                    } else {
                        cartRepo.publish(CartDto())
                        _state.update { it.copy(step = CheckoutStep.AwaitingPayment(r.order.id, payment)) }
                        _events.tryEmit(CheckoutEvent.OpenRazorpay(payment))
                    }
                },
                onErr = { e ->
                    // A new key for the next attempt unless the request may have reached the server.
                    if (!e.isRetryable) idempotencyKey = UUID.randomUUID().toString()
                    _state.update { it.copy(step = CheckoutStep.Idle, error = e) }
                    // Cart may have changed server-side (item unavailable, restaurant closed): refresh to show the reason.
                    if ((e as? AppError.Api)?.code in setOf(ErrorCodes.RESTAURANT_CLOSED, ErrorCodes.VALIDATION_ERROR)) cartRepo.refresh()
                },
            )
        }
    }

    /** Called with the Razorpay Checkout result (via [PaymentResultBus]). */
    fun onPaymentResult(result: PaymentResult) {
        val step = _state.value.step
        val orderId = when (step) {
            is CheckoutStep.AwaitingPayment -> step.orderId
            is CheckoutStep.PaymentFailed -> step.orderId
            else -> { payments.consume(); return }
        }
        payments.consume()
        when (result) {
            is PaymentResult.Success -> {
                _state.update { it.copy(step = CheckoutStep.Verifying(orderId)) }
                viewModelScope.launch {
                    orders.verifyPayment(PaymentVerifyDto(orderId, result.razorpayOrderId, result.paymentId, result.signature)).fold(
                        onOk = { o -> finish(o.id, o.orderNumber) },
                        onErr = { e -> _state.update { it.copy(step = CheckoutStep.PaymentFailed(orderId, e.message, canRetry = true)) } },
                    )
                }
            }
            is PaymentResult.Failure -> _state.update { it.copy(step = CheckoutStep.PaymentFailed(orderId, result.description.ifBlank { null })) }
        }
    }

    /** `POST payments/orders/{id}/retry/` → fresh Razorpay order → open Checkout again. */
    fun retryPayment() {
        val failed = _state.value.step as? CheckoutStep.PaymentFailed ?: return
        _state.update { it.copy(step = CheckoutStep.Retrying(failed.orderId)) }
        viewModelScope.launch {
            orders.retryPayment(failed.orderId).fold(
                onOk = { payload ->
                    if (payload == null) _state.update { it.copy(step = CheckoutStep.PaymentFailed(failed.orderId, null, canRetry = false)) }
                    else { _state.update { it.copy(step = CheckoutStep.AwaitingPayment(failed.orderId, payload)) }; _events.tryEmit(CheckoutEvent.OpenRazorpay(payload)) }
                },
                onErr = { e -> _state.update { it.copy(step = CheckoutStep.PaymentFailed(failed.orderId, e.message, canRetry = e.isRetryable)) } },
            )
        }
    }

    /** User abandons a failed payment: the order stays `pending_payment` and is reachable from Orders. */
    fun abandonPayment(): String? = (_state.value.step as? CheckoutStep.PaymentFailed)?.orderId

    private fun finish(orderId: String, orderNumber: String) {
        _state.update { it.copy(step = CheckoutStep.Success(orderId, orderNumber)) }
        idempotencyKey = UUID.randomUUID().toString()
        _events.tryEmit(CheckoutEvent.OrderPlaced(orderId))
    }
}
