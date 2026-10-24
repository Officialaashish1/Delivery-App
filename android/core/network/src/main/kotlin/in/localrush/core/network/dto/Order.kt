package `in`.localrush.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class OrderListDto(
    val id: String,
    @SerialName("order_number") val orderNumber: String = "",
    val status: String = "",
    @SerialName("status_display") val statusDisplay: String = "",
    val restaurant: String? = null,
    @SerialName("restaurant_name") val restaurantName: String = "",
    @SerialName("restaurant_logo") val restaurantLogo: String? = null,
    @SerialName("restaurant_slug") val restaurantSlug: String? = null,
    @SerialName("total_amount") val totalAmount: DecimalString = "0",
    @SerialName("payment_method") val paymentMethod: String = "cod",
    @SerialName("is_paid") val isPaid: Boolean = false,
    @SerialName("item_count") val itemCount: Int = 0,
    @SerialName("items_summary") val itemsSummary: String = "",
    @SerialName("placed_at") val placedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("delivered_at") val deliveredAt: String? = null,
    @SerialName("estimated_delivery_at") val estimatedDeliveryAt: String? = null,
    @SerialName("is_reviewed") val isReviewed: Boolean = false,
    @SerialName("can_cancel") val canCancel: Boolean = false,
    @SerialName("is_active") val isActive: Boolean = false,
)

@Serializable
data class OrderDetailDto(
    val id: String,
    @SerialName("order_number") val orderNumber: String = "",
    val status: String = "",
    @SerialName("status_display") val statusDisplay: String = "",
    val restaurant: String? = null,
    @SerialName("restaurant_name") val restaurantName: String = "",
    @SerialName("restaurant_logo") val restaurantLogo: String? = null,
    @SerialName("restaurant_slug") val restaurantSlug: String? = null,
    @SerialName("total_amount") val totalAmount: DecimalString = "0",
    @SerialName("payment_method") val paymentMethod: String = "cod",
    @SerialName("is_paid") val isPaid: Boolean = false,
    @SerialName("item_count") val itemCount: Int = 0,
    @SerialName("items_summary") val itemsSummary: String = "",
    @SerialName("placed_at") val placedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("delivered_at") val deliveredAt: String? = null,
    @SerialName("estimated_delivery_at") val estimatedDeliveryAt: String? = null,
    @SerialName("is_reviewed") val isReviewed: Boolean = false,
    @SerialName("can_cancel") val canCancel: Boolean = false,
    @SerialName("is_active") val isActive: Boolean = false,
    val items: List<OrderItemDto> = emptyList(),
    val timeline: List<TimelineEntryDto> = emptyList(),
    @SerialName("status_history") val statusHistory: List<StatusHistoryDto> = emptyList(),
    val rider: RiderPublicDto? = null,
    @SerialName("delivery_name") val deliveryName: String = "",
    @SerialName("delivery_phone") val deliveryPhone: String = "",
    @SerialName("delivery_address_text") val deliveryAddressText: String = "",
    @SerialName("delivery_landmark") val deliveryLandmark: String = "",
    @SerialName("delivery_location") val deliveryLocation: LatLngDto? = null,
    @SerialName("delivery_instructions") val deliveryInstructions: String = "",
    @SerialName("is_contactless") val isContactless: Boolean = false,
    @SerialName("restaurant_address_text") val restaurantAddressText: String = "",
    @SerialName("restaurant_location") val restaurantLocation: LatLngDto? = null,
    @SerialName("delivery_otp") val deliveryOtp: String? = null,
    val pricing: OrderPricingDto? = null,
    @SerialName("distance_km") val distanceKm: DecimalString? = null,
    @SerialName("preparation_minutes") val preparationMinutes: Int? = null,
    @SerialName("accepted_at") val acceptedAt: String? = null,
    @SerialName("ready_at") val readyAt: String? = null,
    @SerialName("picked_up_at") val pickedUpAt: String? = null,
    @SerialName("cancelled_by") val cancelledBy: String? = null,
    @SerialName("cancellation_reason") val cancellationReason: String = "",
    @SerialName("cancellation_fee") val cancellationFee: DecimalString? = null,
    @SerialName("rejection_reason") val rejectionReason: String = "",
    @SerialName("customer_note") val customerNote: String = "",
    @SerialName("invoice_url") val invoiceUrl: String? = null,
    @SerialName("refund_status") val refundStatus: RefundStatusDto? = null,
)

@Serializable
data class OrderItemDto(
    val id: String,
    @SerialName("menu_item") val menuItem: String? = null,
    val name: String = "",
    val image: String? = null,
    @SerialName("food_type") val foodType: String = "veg",
    @SerialName("variant_name") val variantName: String = "",
    @SerialName("addon_names") val addonNames: List<String> = emptyList(),
    val quantity: Int = 1,
    @SerialName("unit_price") val unitPrice: DecimalString = "0",
    @SerialName("addons_price") val addonsPrice: DecimalString = "0",
    @SerialName("packaging_charge") val packagingCharge: DecimalString = "0",
    @SerialName("line_total") val lineTotal: DecimalString = "0",
    @SerialName("special_instructions") val specialInstructions: String = "",
)

/** `timeline[]` entries: key ∈ TimelineKey wire values. */
@Serializable
data class TimelineEntryDto(
    val key: String,
    val label: String = "",
    val done: Boolean = false,
    val current: Boolean = false,
    val at: String? = null,
)

@Serializable
data class StatusHistoryDto(
    @SerialName("from_status") val fromStatus: String? = null,
    @SerialName("to_status") val toStatus: String = "",
    @SerialName("actor_role") val actorRole: String? = null,
    val note: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class RiderPublicDto(
    val id: String,
    @SerialName("full_name") val fullName: String = "",
    val photo: String? = null,
    @SerialName("rating_avg") val ratingAvg: DecimalString = "0",
    @SerialName("total_deliveries") val totalDeliveries: Int = 0,
    @SerialName("vehicle_type") val vehicleType: String = "",
    @SerialName("vehicle_number") val vehicleNumber: String = "",
)

@Serializable
data class OrderPricingDto(
    @SerialName("item_total") val itemTotal: DecimalString = "0",
    @SerialName("packaging_charge") val packagingCharge: DecimalString = "0",
    @SerialName("delivery_fee") val deliveryFee: DecimalString = "0",
    @SerialName("delivery_discount") val deliveryDiscount: DecimalString = "0",
    @SerialName("platform_fee") val platformFee: DecimalString = "0",
    @SerialName("tax_percent") val taxPercent: DecimalString = "0",
    @SerialName("tax_amount") val taxAmount: DecimalString = "0",
    @SerialName("coupon_code") val couponCode: String? = null,
    @SerialName("coupon_discount") val couponDiscount: DecimalString = "0",
    val total: DecimalString = "0",
    // cart-only extras
    @SerialName("distance_km") val distanceKm: DecimalString? = null,
    @SerialName("min_order_amount") val minOrderAmount: DecimalString? = null,
    @SerialName("meets_min_order") val meetsMinOrder: Boolean? = null,
    val errors: List<String> = emptyList(),
)

@Serializable
data class RefundStatusDto(
    val amount: DecimalString = "0",
    val status: String = "",
    val reason: String? = null,
    @SerialName("processed_at") val processedAt: String? = null,
)

@Serializable
data class PlaceOrderDto(
    @SerialName("address_id") val addressId: String,
    @SerialName("payment_method") val paymentMethod: String,
    @SerialName("idempotency_key") val idempotencyKey: String,
    @SerialName("is_contactless") val isContactless: Boolean = false,
    @SerialName("delivery_instructions") val deliveryInstructions: String = "",
    @SerialName("customer_note") val customerNote: String = "",
)

/** `payment` is null for COD, else the Razorpay Checkout payload (forwarded as-is to the SDK). */
@Serializable
data class PlaceOrderResponseDto(val order: OrderDetailDto, val payment: RazorpayCheckoutDto? = null)

@Serializable
data class RazorpayCheckoutDto(
    @SerialName("key_id") val keyId: String,
    @SerialName("provider_order_id") val providerOrderId: String,
    @SerialName("amount_paise") val amountPaise: Long,
    val currency: String = "INR",
    val name: String = "LocalRush",
    val description: String = "",
    val prefill: JsonObject? = null,
    val notes: JsonObject? = null,
    @SerialName("theme_color") val themeColor: String = "#F26B1D",
)

@Serializable
data class PaymentVerifyDto(
    @SerialName("order_id") val orderId: String,
    @SerialName("razorpay_order_id") val razorpayOrderId: String,
    @SerialName("razorpay_payment_id") val razorpayPaymentId: String,
    @SerialName("razorpay_signature") val razorpaySignature: String,
)

@Serializable
data class RiderContactDto(
    val phone: String? = null,
    @SerialName("masked_phone") val maskedPhone: String? = null,
    val name: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
)
