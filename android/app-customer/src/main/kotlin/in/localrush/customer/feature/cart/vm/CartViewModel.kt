package `in`.localrush.customer.feature.cart.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.localrush.core.common.money.Money
import `in`.localrush.core.common.money.toMoney
import `in`.localrush.core.common.result.AppError
import `in`.localrush.core.network.dto.AddressDto
import `in`.localrush.core.network.dto.OrderPricingDto
import `in`.localrush.customer.data.dto.AvailableCouponDto
import `in`.localrush.customer.data.dto.CartDto
import `in`.localrush.customer.data.repo.AddressRepository
import `in`.localrush.customer.data.repo.CartRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One display row of the bill, derived from `pricing` by [CartViewModel.billLines]. */
data class BillLine(val key: String, val amount: Money, val isDiscount: Boolean = false, val extra: String? = null)

data class CartUiState(
    val loading: Boolean = true,
    val cart: CartDto? = null,
    val error: AppError? = null,
    /** Line id currently being updated (shows spinner on its stepper). */
    val busyItem: String? = null,
    val couponInput: String = "",
    val couponBusy: Boolean = false,
    val couponError: String? = null,
    val offersOpen: Boolean = false,
    val offers: List<AvailableCouponDto>? = null,
    val offersLoading: Boolean = false,
    val offersError: AppError? = null,
    val addresses: List<AddressDto>? = null,
    val addressSheetOpen: Boolean = false,
    val addressBusy: Boolean = false,
    val message: String? = null,
) {
    val isEmpty: Boolean get() = cart?.isEmpty != false
    val pricing: OrderPricingDto? get() = cart?.pricing
    val meetsMinOrder: Boolean get() = pricing?.meetsMinOrder != false
    val blockingErrors: List<String> get() = pricing?.errors ?: emptyList()
    val canCheckout: Boolean get() = !isEmpty && meetsMinOrder && cart?.deliveryAddress != null && blockingErrors.isEmpty() && (cart?.items?.all { it.isAvailable } == true)
    val needsAddress: Boolean get() = !isEmpty && cart?.deliveryAddress == null
}

sealed interface CartEvent { data object GoToCheckout : CartEvent; data object GoToAddAddress : CartEvent }

@HiltViewModel
class CartViewModel @Inject constructor(
    private val cart: CartRepository,
    private val addresses: AddressRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(CartUiState())
    val state: StateFlow<CartUiState> = _state.asStateFlow()
    private val _events = MutableSharedFlow<CartEvent>(extraBufferCapacity = 2)
    val events: SharedFlow<CartEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch { cart.cart.collect { c -> _state.update { it.copy(cart = c, couponInput = c?.couponCode ?: it.couponInput) } } }
        viewModelScope.launch { addresses.addresses.collect { a -> _state.update { it.copy(addresses = a) } } }
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(loading = it.cart == null, error = null) }
        viewModelScope.launch {
            cart.refresh().fold(
                onOk = { c ->
                    _state.update { it.copy(loading = false, cart = c) }
                    // Pre-select the default address when the cart has none so the bill includes delivery fee.
                    if (c.deliveryAddress == null && !c.isEmpty) {
                        addresses.load().onSuccess { list -> list.firstOrNull { it.isDefault }?.let { d -> cart.setAddress(d.id) } }
                    }
                },
                onErr = { e -> _state.update { it.copy(loading = false, error = e) } },
            )
        }
    }

    fun increment(lineId: String) = changeQty(lineId, +1)
    fun decrement(lineId: String) = changeQty(lineId, -1)
    private fun changeQty(lineId: String, delta: Int) {
        val line = _state.value.cart?.items?.firstOrNull { it.id == lineId } ?: return
        if (_state.value.busyItem != null) return
        _state.update { it.copy(busyItem = lineId) }
        viewModelScope.launch {
            val r = if (line.quantity + delta <= 0) cart.remove(lineId) else cart.updateQuantity(lineId, line.quantity + delta)
            _state.update { it.copy(busyItem = null, message = r.errorOrNull()?.message) }
        }
    }

    fun remove(lineId: String) {
        _state.update { it.copy(busyItem = lineId) }
        viewModelScope.launch { val r = cart.remove(lineId); _state.update { it.copy(busyItem = null, message = r.errorOrNull()?.message) } }
    }

    fun clearCart() { viewModelScope.launch { cart.clear().onFailure { e -> _state.update { it.copy(message = e.message) } } } }

    // ---- coupons ----
    fun onCouponInput(v: String) = _state.update { it.copy(couponInput = v.uppercase(), couponError = null) }
    fun applyCoupon(code: String = _state.value.couponInput) {
        _state.update { it.copy(couponBusy = true, couponError = null, offersOpen = false) }
        viewModelScope.launch {
            cart.applyCoupon(code.trim()).fold(
                onOk = { _state.update { it.copy(couponBusy = false, couponInput = code.trim()) } },
                onErr = { e -> _state.update { it.copy(couponBusy = false, couponError = e.message) } },
            )
        }
    }
    fun removeCoupon() = applyCoupon("")
    fun openOffers() {
        _state.update { it.copy(offersOpen = true, offersLoading = true, offersError = null) }
        viewModelScope.launch {
            val c = _state.value.cart
            cart.coupons(c?.restaurant?.id, c?.pricing?.itemTotal).fold(
                onOk = { list -> _state.update { it.copy(offers = list, offersLoading = false) } },
                onErr = { e -> _state.update { it.copy(offersLoading = false, offersError = e) } },
            )
        }
    }
    fun closeOffers() = _state.update { it.copy(offersOpen = false) }

    // ---- address ----
    fun openAddressSheet() {
        _state.update { it.copy(addressSheetOpen = true) }
        viewModelScope.launch { addresses.load() }
    }
    fun closeAddressSheet() = _state.update { it.copy(addressSheetOpen = false) }
    fun selectAddress(id: String) {
        _state.update { it.copy(addressBusy = true) }
        viewModelScope.launch {
            val r = cart.setAddress(id)
            _state.update { it.copy(addressBusy = false, addressSheetOpen = false, message = r.errorOrNull()?.message) }
        }
    }
    fun addAddress() { _state.update { it.copy(addressSheetOpen = false) }; _events.tryEmit(CartEvent.GoToAddAddress) }

    fun checkout() {
        val s = _state.value
        if (s.needsAddress) { openAddressSheet(); return }
        if (s.canCheckout) _events.tryEmit(CartEvent.GoToCheckout)
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    companion object {
        const val KEY_ITEM_TOTAL = "item_total"; const val KEY_PACKAGING = "packaging"; const val KEY_DELIVERY = "delivery"
        const val KEY_DELIVERY_DISCOUNT = "delivery_discount"; const val KEY_PLATFORM = "platform"; const val KEY_TAX = "tax"; const val KEY_COUPON = "coupon"

        /** Pure mapping of `pricing` → bill rows: zero optional charges are hidden, discounts negative, delivery fee always shown. */
        fun billLines(p: OrderPricingDto): List<BillLine> = buildList {
            add(BillLine(KEY_ITEM_TOTAL, p.itemTotal.toMoney()))
            p.packagingCharge.toMoney().takeIf { !it.isZero }?.let { add(BillLine(KEY_PACKAGING, it)) }
            add(BillLine(KEY_DELIVERY, p.deliveryFee.toMoney()))
            p.deliveryDiscount.toMoney().takeIf { !it.isZero }?.let { add(BillLine(KEY_DELIVERY_DISCOUNT, -it, isDiscount = true)) }
            p.platformFee.toMoney().takeIf { !it.isZero }?.let { add(BillLine(KEY_PLATFORM, it)) }
            p.taxAmount.toMoney().takeIf { !it.isZero }?.let { add(BillLine(KEY_TAX, it, extra = p.taxPercent.trimEnd('0').trimEnd('.'))) }
            p.couponDiscount.toMoney().takeIf { !it.isZero }?.let { add(BillLine(KEY_COUPON, -it, isDiscount = true, extra = p.couponCode)) }
        }

        /** Sum of the rows must equal `pricing.total` (sanity-checked in unit tests). */
        fun totalOf(lines: List<BillLine>): Money = lines.fold(Money.ZERO) { acc, l -> acc + l.amount }

        /** "You save ₹X" when any discount applies. */
        fun savings(p: OrderPricingDto): Money = p.couponDiscount.toMoney() + p.deliveryDiscount.toMoney()
    }
}
