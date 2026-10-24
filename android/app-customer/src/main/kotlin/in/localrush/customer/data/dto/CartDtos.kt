package `in`.localrush.customer.data.dto

import `in`.localrush.core.network.dto.AddressDto
import `in`.localrush.core.network.dto.DecimalString
import `in`.localrush.core.network.dto.OrderPricingDto
import `in`.localrush.core.network.dto.RazorpayCheckoutDto
import `in`.localrush.core.network.dto.UserDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `GET cart/` — server-side cart, one restaurant at a time. */
@Serializable
data class CartDto(
    val id: String? = null,
    val restaurant: RestaurantCardDto? = null,
    val items: List<CartItemDto> = emptyList(),
    @SerialName("item_count") val itemCount: Int = 0,
    @SerialName("coupon_code") val couponCode: String = "",
    @SerialName("delivery_address") val deliveryAddress: AddressDto? = null,
    @SerialName("special_instructions") val specialInstructions: String = "",
    val pricing: OrderPricingDto? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
) {
    val isEmpty: Boolean get() = items.isEmpty()
}

@Serializable
data class CartItemDto(
    val id: String,
    @SerialName("menu_item") val menuItem: String? = null,
    val name: String = "",
    val image: String? = null,
    @SerialName("food_type") val foodType: String = "veg",
    val variant: VariantDto? = null,
    val addons: List<AddonDto> = emptyList(),
    val quantity: Int = 1,
    @SerialName("special_instructions") val specialInstructions: String = "",
    @SerialName("unit_price") val unitPrice: DecimalString = "0",
    @SerialName("line_total") val lineTotal: DecimalString = "0",
    @SerialName("is_available") val isAvailable: Boolean = true,
)

@Serializable
data class AddToCartDto(
    @SerialName("menu_item_id") val menuItemId: String,
    val quantity: Int = 1,
    @SerialName("variant_id") val variantId: String? = null,
    @SerialName("addon_ids") val addonIds: List<String> = emptyList(),
    @SerialName("special_instructions") val specialInstructions: String = "",
    @SerialName("replace_cart") val replaceCart: Boolean = false,
)

/** `PATCH cart/` — null fields are omitted (explicitNulls=false), so only pass what changes. */
@Serializable
data class CartSettingsDto(
    @SerialName("delivery_address_id") val deliveryAddressId: String? = null,
    @SerialName("coupon_code") val couponCode: String? = null,
    @SerialName("special_instructions") val specialInstructions: String? = null,
)

@Serializable
data class UpdateCartItemDto(val quantity: Int)

@Serializable
data class CouponDto(
    val id: String,
    val code: String = "",
    val title: String = "",
    val description: String = "",
    @SerialName("discount_type") val discountType: String = "",
    val value: DecimalString = "0",
    @SerialName("min_order_amount") val minOrderAmount: DecimalString = "0",
    @SerialName("max_discount") val maxDiscount: DecimalString? = null,
    @SerialName("first_order_only") val firstOrderOnly: Boolean = false,
    val restaurant: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("short_text") val shortText: String = "",
)

/** `GET coupons/?restaurant=&subtotal=` */
@Serializable
data class AvailableCouponDto(
    val coupon: CouponDto,
    val applicable: Boolean = true,
    val discount: DecimalString = "0",
    val reason: String = "",
)

/** `POST orders/{id}/reorder/` → `{cart, skipped_items}` */
@Serializable
data class ReorderResponseDto(val cart: CartDto? = null, @SerialName("skipped_items") val skippedItems: List<String> = emptyList())

/** `POST payments/orders/{id}/retry/` → `{payment}` (the backend returns only the fresh checkout payload). */
@Serializable
data class RetryPaymentResponseDto(val payment: RazorpayCheckoutDto? = null)

/** `GET/PATCH customers/profile/` as the backend actually serialises it. */
@Serializable
data class ProfileDto(
    val user: UserDto? = null,
    @SerialName("full_name") val fullName: String = "",
    val email: String? = null,
    val avatar: String? = null,
    @SerialName("date_of_birth") val dateOfBirth: String? = null,
    @SerialName("referral_code") val referralCode: String? = null,
    @SerialName("is_veg_only") val isVegOnly: Boolean = false,
    @SerialName("total_orders") val totalOrders: Int = 0,
)

@Serializable
data class ProfilePatchDto(
    @SerialName("full_name") val fullName: String? = null,
    val email: String? = null,
    @SerialName("is_veg_only") val isVegOnly: Boolean? = null,
)
