package `in`.localrush.partner.data

import `in`.localrush.core.network.dto.DecimalString
import `in`.localrush.core.network.dto.OrderItemDto
import `in`.localrush.core.network.dto.RiderPublicDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---------------------------------------------------------------- restaurant

@Serializable
data class CuisineDto(val id: Int, val name: String = "", val slug: String = "", val icon: String? = null)

@Serializable
data class RestaurantCategoryDto(val id: Int, val name: String = "", val slug: String = "", val image: String? = null)

@Serializable
data class RestaurantHoursDto(
    val id: Int? = null,
    val weekday: Int,
    @SerialName("open_time") val openTime: String = "09:00:00",
    @SerialName("close_time") val closeTime: String = "23:00:00",
    @SerialName("is_closed") val isClosed: Boolean = false,
)

@Serializable
data class HoursBulkDto(val hours: List<RestaurantHoursDto>)

@Serializable
data class HolidayDto(val id: Int? = null, val date: String, val reason: String = "")

@Serializable
data class RestaurantDocumentDto(
    val id: String,
    @SerialName("doc_type") val docType: String = "other",
    val file: String? = null,
    val number: String = "",
    val status: String = "pending",
    @SerialName("rejection_reason") val rejectionReason: String = "",
    @SerialName("verified_at") val verifiedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

/** `GET/POST/PATCH partner/restaurant/` read model. */
@Serializable
data class PartnerRestaurantDto(
    val id: String,
    val name: String = "",
    val slug: String = "",
    @SerialName("restaurant_type") val restaurantType: String = "restaurant",
    val description: String = "",
    @SerialName("owner_name") val ownerName: String = "",
    val phone: String = "",
    val email: String = "",
    val logo: String? = null,
    @SerialName("cover_image") val coverImage: String? = null,
    @SerialName("address_line1") val addressLine1: String = "",
    @SerialName("address_line2") val addressLine2: String = "",
    val landmark: String = "",
    val city: String = "",
    val state: String = "",
    @SerialName("postal_code") val postalCode: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
    @SerialName("delivery_radius_km") val deliveryRadiusKm: DecimalString = "7.00",
    @SerialName("fssai_number") val fssaiNumber: String = "",
    @SerialName("fssai_expiry") val fssaiExpiry: String? = null,
    @SerialName("gst_number") val gstNumber: String = "",
    @SerialName("pan_number") val panNumber: String = "",
    @SerialName("bank_account_name") val bankAccountName: String = "",
    @SerialName("bank_account_number") val bankAccountNumber: String = "",
    @SerialName("bank_ifsc") val bankIfsc: String = "",
    @SerialName("upi_id") val upiId: String = "",
    val cuisines: List<CuisineDto> = emptyList(),
    val categories: List<RestaurantCategoryDto> = emptyList(),
    @SerialName("is_pure_veg") val isPureVeg: Boolean = false,
    @SerialName("min_order_amount") val minOrderAmount: DecimalString = "0",
    @SerialName("packaging_charge") val packagingCharge: DecimalString = "0",
    @SerialName("avg_preparation_minutes") val avgPreparationMinutes: Int = 20,
    @SerialName("cost_for_two") val costForTwo: DecimalString = "0",
    val status: String = "pending",
    @SerialName("rejection_reason") val rejectionReason: String = "",
    @SerialName("is_open") val isOpen: Boolean = false,
    @SerialName("is_accepting_orders") val isAcceptingOrders: Boolean = true,
    @SerialName("temporarily_closed_until") val temporarilyClosedUntil: String? = null,
    @SerialName("rating_avg") val ratingAvg: DecimalString = "0",
    @SerialName("rating_count") val ratingCount: Int = 0,
    @SerialName("total_orders") val totalOrders: Int = 0,
    @SerialName("effective_commission_percent") val effectiveCommissionPercent: DecimalString = "0",
    val hours: List<RestaurantHoursDto> = emptyList(),
    @SerialName("is_open_now") val isOpenNow: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
)

/** JSON PATCH body for `partner/restaurant/` (nulls omitted). Images go through the multipart variant. */
@Serializable
data class RestaurantPatchDto(
    val name: String? = null,
    @SerialName("restaurant_type") val restaurantType: String? = null,
    val description: String? = null,
    @SerialName("owner_name") val ownerName: String? = null,
    val phone: String? = null,
    val email: String? = null,
    @SerialName("address_line1") val addressLine1: String? = null,
    @SerialName("address_line2") val addressLine2: String? = null,
    val landmark: String? = null,
    val city: String? = null,
    val state: String? = null,
    @SerialName("postal_code") val postalCode: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("delivery_radius_km") val deliveryRadiusKm: String? = null,
    @SerialName("fssai_number") val fssaiNumber: String? = null,
    @SerialName("fssai_expiry") val fssaiExpiry: String? = null,
    @SerialName("gst_number") val gstNumber: String? = null,
    @SerialName("pan_number") val panNumber: String? = null,
    @SerialName("bank_account_name") val bankAccountName: String? = null,
    @SerialName("bank_account_number") val bankAccountNumber: String? = null,
    @SerialName("bank_ifsc") val bankIfsc: String? = null,
    @SerialName("upi_id") val upiId: String? = null,
    @SerialName("cuisine_ids") val cuisineIds: List<Int>? = null,
    @SerialName("category_ids") val categoryIds: List<Int>? = null,
    @SerialName("is_pure_veg") val isPureVeg: Boolean? = null,
    @SerialName("min_order_amount") val minOrderAmount: String? = null,
    @SerialName("packaging_charge") val packagingCharge: String? = null,
    @SerialName("avg_preparation_minutes") val avgPreparationMinutes: Int? = null,
    @SerialName("cost_for_two") val costForTwo: String? = null,
    @SerialName("is_accepting_orders") val isAcceptingOrders: Boolean? = null,
)

@Serializable
data class ToggleDto(
    @SerialName("is_open") val isOpen: Boolean? = null,
    @SerialName("is_accepting_orders") val isAcceptingOrders: Boolean? = null,
    /** ISO-8601; send `""` to clear (the backend treats a falsy value as null; `null` itself would be omitted). */
    @SerialName("temporarily_closed_until") val temporarilyClosedUntil: String? = null,
)

// ---------------------------------------------------------------- dashboard

@Serializable
data class DashboardTodayDto(
    val orders: Int = 0,
    val completed: Int = 0,
    val pending: Int = 0,
    val new: Int = 0,
    val cancelled: Int = 0,
    val revenue: DecimalString = "0",
    @SerialName("gross_sales") val grossSales: DecimalString = "0",
    @SerialName("avg_order_value") val avgOrderValue: DecimalString = "0",
)

@Serializable
data class DashboardPeriodDto(val orders: Int = 0, val revenue: DecimalString = "0")

@Serializable
data class LastSettlementDto(
    val amount: DecimalString = "0",
    val status: String = "pending",
    @SerialName("period_end") val periodEnd: String? = null,
)

/** `GET partner/dashboard/` (shape from `apps/finance/services.partner_dashboard`). */
@Serializable
data class DashboardDto(
    val today: DashboardTodayDto = DashboardTodayDto(),
    val week: DashboardPeriodDto = DashboardPeriodDto(),
    val month: DashboardPeriodDto = DashboardPeriodDto(),
    @SerialName("rating_avg") val ratingAvg: DecimalString = "0",
    @SerialName("rating_count") val ratingCount: Int = 0,
    @SerialName("total_orders") val totalOrders: Int = 0,
    @SerialName("is_open") val isOpen: Boolean = false,
    @SerialName("is_accepting_orders") val isAcceptingOrders: Boolean = true,
    @SerialName("is_open_now") val isOpenNow: Boolean = false,
    val status: String = "pending",
    @SerialName("unsettled_amount") val unsettledAmount: DecimalString = "0",
    @SerialName("last_settlement") val lastSettlement: LastSettlementDto? = null,
    @SerialName("commission_percent") val commissionPercent: DecimalString = "0",
)

// ---------------------------------------------------------------- menu

@Serializable
data class MenuCategoryDto(
    val id: String,
    val name: String = "",
    val description: String = "",
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("item_count") val itemCount: Int = 0,
)

@Serializable
data class MenuCategoryWriteDto(
    val name: String? = null,
    val description: String? = null,
    @SerialName("sort_order") val sortOrder: Int? = null,
    @SerialName("is_active") val isActive: Boolean? = null,
)

@Serializable
data class VariantOptionDto(
    val id: String? = null,
    val name: String,
    val price: DecimalString = "0",
    @SerialName("is_available") val isAvailable: Boolean = true,
    @SerialName("is_default") val isDefault: Boolean = false,
    @SerialName("sort_order") val sortOrder: Int = 0,
)

@Serializable
data class VariantGroupDto(
    val id: String? = null,
    val name: String,
    @SerialName("is_required") val isRequired: Boolean = true,
    @SerialName("sort_order") val sortOrder: Int = 0,
    val options: List<VariantOptionDto> = emptyList(),
)

@Serializable
data class AddonOptionDto(
    val id: String? = null,
    val name: String,
    val price: DecimalString = "0",
    @SerialName("food_type") val foodType: String = "veg",
    @SerialName("is_available") val isAvailable: Boolean = true,
    @SerialName("sort_order") val sortOrder: Int = 0,
)

@Serializable
data class AddonGroupDto(
    val id: String? = null,
    val name: String,
    @SerialName("min_select") val minSelect: Int = 0,
    @SerialName("max_select") val maxSelect: Int = 5,
    @SerialName("sort_order") val sortOrder: Int = 0,
    val options: List<AddonOptionDto> = emptyList(),
)

/** `partner/menu/items/` read model (PartnerMenuItemSerializer). */
@Serializable
data class MenuItemDto(
    val id: String,
    val category: String? = null,
    @SerialName("category_name") val categoryName: String? = null,
    val name: String = "",
    val description: String = "",
    val image: String? = null,
    @SerialName("food_type") val foodType: String = "veg",
    val price: DecimalString = "0",
    @SerialName("discounted_price") val discountedPrice: DecimalString? = null,
    @SerialName("effective_price") val effectivePrice: DecimalString = "0",
    @SerialName("packaging_charge") val packagingCharge: DecimalString = "0",
    @SerialName("preparation_minutes") val preparationMinutes: Int = 15,
    @SerialName("is_available") val isAvailable: Boolean = true,
    @SerialName("stock_quantity") val stockQuantity: Int? = null,
    @SerialName("is_recommended") val isRecommended: Boolean = false,
    @SerialName("is_bestseller") val isBestseller: Boolean = false,
    @SerialName("spice_level") val spiceLevel: Int = 0,
    val calories: Int? = null,
    val tags: List<String> = emptyList(),
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("order_count") val orderCount: Int = 0,
    @SerialName("rating_avg") val ratingAvg: DecimalString = "0",
    @SerialName("rating_count") val ratingCount: Int = 0,
    @SerialName("variant_groups") val variantGroups: List<VariantGroupDto> = emptyList(),
    @SerialName("addon_groups") val addonGroups: List<AddonGroupDto> = emptyList(),
    @SerialName("created_at") val createdAt: String? = null,
)

/** JSON write body for items (image is uploaded separately via multipart PATCH). */
@Serializable
data class MenuItemWriteDto(
    val category: String,
    val name: String,
    val description: String = "",
    @SerialName("food_type") val foodType: String = "veg",
    val price: String,
    /** Null means "no discount"; the repository adds an explicit JSON null so an existing discount is cleared. */
    @SerialName("discounted_price") val discountedPrice: String? = null,
    @SerialName("packaging_charge") val packagingCharge: String = "0.00",
    @SerialName("preparation_minutes") val preparationMinutes: Int = 15,
    @SerialName("is_available") val isAvailable: Boolean = true,
    @SerialName("stock_quantity") val stockQuantity: Int? = null,
    @SerialName("is_recommended") val isRecommended: Boolean = false,
    @SerialName("is_bestseller") val isBestseller: Boolean = false,
    @SerialName("spice_level") val spiceLevel: Int = 0,
    val calories: Int? = null,
    val tags: List<String> = emptyList(),
    @SerialName("variant_groups") val variantGroups: List<VariantGroupDto> = emptyList(),
    @SerialName("addon_groups") val addonGroups: List<AddonGroupDto> = emptyList(),
)

@Serializable
data class AvailabilityDto(@SerialName("is_available") val isAvailable: Boolean, @SerialName("stock_quantity") val stockQuantity: Int? = null)

@Serializable
data class BulkAvailabilityDto(val ids: List<String>, @SerialName("is_available") val isAvailable: Boolean)

@Serializable
data class BulkAvailabilityResultDto(val updated: Int = 0)

// ---------------------------------------------------------------- orders

/** `partner/orders/` list + detail (PartnerOrderSerializer). */
@Serializable
data class PartnerOrderDto(
    val id: String,
    @SerialName("order_number") val orderNumber: String = "",
    val status: String = "",
    @SerialName("status_display") val statusDisplay: String = "",
    val items: List<OrderItemDto> = emptyList(),
    @SerialName("customer_name") val customerName: String = "",
    @SerialName("customer_note") val customerNote: String = "",
    @SerialName("item_total") val itemTotal: DecimalString = "0",
    @SerialName("packaging_charge") val packagingCharge: DecimalString = "0",
    @SerialName("coupon_discount") val couponDiscount: DecimalString = "0",
    @SerialName("total_amount") val totalAmount: DecimalString = "0",
    @SerialName("restaurant_payout") val restaurantPayout: DecimalString = "0",
    @SerialName("commission_amount") val commissionAmount: DecimalString = "0",
    @SerialName("payment_method") val paymentMethod: String = "cod",
    @SerialName("is_paid") val isPaid: Boolean = false,
    val rider: RiderPublicDto? = null,
    @SerialName("pickup_otp") val pickupOtp: String? = null,
    @SerialName("preparation_minutes") val preparationMinutes: Int = 20,
    @SerialName("placed_at") val placedAt: String? = null,
    @SerialName("accepted_at") val acceptedAt: String? = null,
    @SerialName("ready_at") val readyAt: String? = null,
    @SerialName("picked_up_at") val pickedUpAt: String? = null,
    @SerialName("delivered_at") val deliveredAt: String? = null,
    @SerialName("estimated_delivery_at") val estimatedDeliveryAt: String? = null,
    @SerialName("rejection_reason") val rejectionReason: String = "",
    @SerialName("cancellation_reason") val cancellationReason: String = "",
    @SerialName("cancelled_by") val cancelledBy: String = "",
    @SerialName("time_since_placed_s") val timeSincePlacedS: Long = 0,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class AcceptOrderDto(@SerialName("preparation_minutes") val preparationMinutes: Int? = null)

// ---------------------------------------------------------------- finance

@Serializable
data class FinanceSummaryDto(
    val orders: Int = 0,
    @SerialName("gross_sales") val grossSales: DecimalString = "0",
    val commission: DecimalString = "0",
    @SerialName("coupon_deductions") val couponDeductions: DecimalString = "0",
    @SerialName("refund_deductions") val refundDeductions: DecimalString = "0",
    @SerialName("tax_collected") val taxCollected: DecimalString = "0",
    @SerialName("net_earnings") val netEarnings: DecimalString = "0",
)

@Serializable
data class SettlementLineDto(
    val id: String,
    val order: String? = null,
    @SerialName("order_number") val orderNumber: String = "",
    val gross: DecimalString = "0",
    @SerialName("commission_percent") val commissionPercent: DecimalString = "0",
    val commission: DecimalString = "0",
    @SerialName("coupon_deduction") val couponDeduction: DecimalString = "0",
    @SerialName("refund_deduction") val refundDeduction: DecimalString = "0",
    @SerialName("tax_collected") val taxCollected: DecimalString = "0",
    val net: DecimalString = "0",
    @SerialName("is_cod") val isCod: Boolean = false,
    @SerialName("delivered_at") val deliveredAt: String? = null,
)

@Serializable
data class SettlementDto(
    val id: String,
    val restaurant: String? = null,
    @SerialName("restaurant_name") val restaurantName: String = "",
    @SerialName("period_start") val periodStart: String? = null,
    @SerialName("period_end") val periodEnd: String? = null,
    @SerialName("order_count") val orderCount: Int = 0,
    @SerialName("gross_sales") val grossSales: DecimalString = "0",
    @SerialName("commission_amount") val commissionAmount: DecimalString = "0",
    @SerialName("coupon_deductions") val couponDeductions: DecimalString = "0",
    @SerialName("refund_deductions") val refundDeductions: DecimalString = "0",
    val adjustments: DecimalString = "0",
    @SerialName("net_payable") val netPayable: DecimalString = "0",
    val status: String = "pending",
    val reference: String = "",
    val note: String = "",
    @SerialName("processed_at") val processedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)
