package `in`.localrush.customer.data.repo

import `in`.localrush.core.common.result.AppError
import `in`.localrush.core.common.result.AppResult
import `in`.localrush.core.common.result.EmptyResult
import `in`.localrush.core.common.result.ErrorCodes
import `in`.localrush.core.common.result.Result
import `in`.localrush.core.network.util.apiCall
import `in`.localrush.core.network.util.apiCallUnit
import `in`.localrush.customer.data.api.CustomerApi
import `in`.localrush.customer.data.dto.AddToCartDto
import `in`.localrush.customer.data.dto.AvailableCouponDto
import `in`.localrush.customer.data.dto.CartDto
import `in`.localrush.customer.data.dto.CartSettingsDto
import `in`.localrush.customer.data.dto.UpdateCartItemDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/** Server-side cart. [cart] is the shared in-memory copy every screen observes (floating cart bar, badges). */
interface CartRepository {
    val cart: StateFlow<CartDto?>
    suspend fun refresh(): AppResult<CartDto>
    suspend fun add(request: AddToCartDto): AppResult<CartDto>
    suspend fun updateQuantity(itemId: String, quantity: Int): AppResult<CartDto>
    suspend fun remove(itemId: String): AppResult<CartDto>
    suspend fun setAddress(addressId: String?): AppResult<CartDto>
    suspend fun applyCoupon(code: String): AppResult<CartDto>
    suspend fun setInstructions(text: String): AppResult<CartDto>
    suspend fun clear(): EmptyResult
    suspend fun coupons(restaurantId: String?, subtotal: String?): AppResult<List<AvailableCouponDto>>
    /** Replace the in-memory cart (e.g. after reorder returned a cart). */
    fun publish(cart: CartDto?)
}

@Singleton
class CartRepositoryImpl @Inject constructor(private val api: CustomerApi) : CartRepository {
    private val _cart = MutableStateFlow<CartDto?>(null)
    override val cart: StateFlow<CartDto?> = _cart

    private suspend fun run(block: suspend () -> CartDto): AppResult<CartDto> = apiCall(block).onSuccess { _cart.value = it }

    override suspend fun refresh() = run { api.cart() }
    override suspend fun add(request: AddToCartDto) = run { api.addToCart(request) }
    override suspend fun updateQuantity(itemId: String, quantity: Int) = run { api.updateCartItem(itemId, UpdateCartItemDto(quantity)) }
    override suspend fun remove(itemId: String) = run { api.removeCartItem(itemId) }
    override suspend fun setAddress(addressId: String?) = run { api.patchCart(CartSettingsDto(deliveryAddressId = addressId)) }
    override suspend fun applyCoupon(code: String) = run { api.patchCart(CartSettingsDto(couponCode = code)) }
    override suspend fun setInstructions(text: String) = run { api.patchCart(CartSettingsDto(specialInstructions = text)) }
    override suspend fun clear(): EmptyResult = apiCallUnit { api.clearCart() }.onSuccess { _cart.value = CartDto() }
    override suspend fun coupons(restaurantId: String?, subtotal: String?) = apiCall { api.coupons(restaurantId, subtotal) }
    override fun publish(cart: CartDto?) { _cart.value = cart }
}

/** `cart_restaurant_conflict` (409) carries `current_restaurant`. */
val AppError.isCartConflict: Boolean get() = (this as? AppError.Api)?.code == ErrorCodes.CART_RESTAURANT_CONFLICT
val AppError.conflictRestaurantName: String? get() = (this as? AppError.Api)?.extra?.get("current_restaurant")?.jsonPrimitive?.contentOrNull
val AppError.isUnauthenticated: Boolean get() = this is AppError.Unauthenticated || (this as? AppError.Api)?.isUnauthorized == true

fun <T> AppResult<T>.errorOr(default: AppError): AppError = (this as? Result.Err)?.error ?: default
