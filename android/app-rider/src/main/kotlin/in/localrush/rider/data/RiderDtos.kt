package `in`.localrush.rider.data

import `in`.localrush.core.network.dto.DecimalString
import `in`.localrush.core.network.dto.LatLngDto
import `in`.localrush.core.network.dto.OrderItemDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** `GET/PATCH riders/profile/` (backend `RiderProfileSerializer`). */
@Serializable
data class RiderProfileDto(
    val id: String = "",
    val phone: String = "",
    @SerialName("full_name") val fullName: String = "",
    val photo: String? = null,
    val email: String? = null,
    @SerialName("date_of_birth") val dateOfBirth: String? = null,
    val address: String = "",
    val city: String = "",
    @SerialName("vehicle_type") val vehicleType: String = "bike",
    @SerialName("vehicle_number") val vehicleNumber: String = "",
    @SerialName("driving_licence_number") val drivingLicenceNumber: String = "",
    @SerialName("driving_licence_expiry") val drivingLicenceExpiry: String? = null,
    @SerialName("aadhaar_last4") val aadhaarLast4: String = "",
    @SerialName("pan_number") val panNumber: String = "",
    @SerialName("bank_account_name") val bankAccountName: String = "",
    @SerialName("bank_account_number") val bankAccountNumber: String = "",
    @SerialName("bank_ifsc") val bankIfsc: String = "",
    @SerialName("upi_id") val upiId: String = "",
    @SerialName("emergency_contact_name") val emergencyContactName: String = "",
    @SerialName("emergency_contact_phone") val emergencyContactPhone: String = "",
    val status: String = "pending",
    @SerialName("rejection_reason") val rejectionReason: String = "",
    @SerialName("is_approved") val isApproved: Boolean = false,
    @SerialName("is_online") val isOnline: Boolean = false,
    @SerialName("is_on_delivery") val isOnDelivery: Boolean = false,
    @SerialName("rating_avg") val ratingAvg: DecimalString = "0",
    @SerialName("rating_count") val ratingCount: Int = 0,
    @SerialName("total_deliveries") val totalDeliveries: Int = 0,
    @SerialName("wallet_balance") val walletBalance: DecimalString = "0",
    @SerialName("cod_cash_in_hand") val codCashInHand: DecimalString = "0",
    /** Backend sends `{doc_type: status}`; the OpenAPI schema says string, so accept anything. */
    @SerialName("documents_summary") val documentsSummary: JsonElement? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    @SerialName("created_at") val createdAt: String? = null,
) {
    /** `{doc_type: status}` when the backend sends an object, else empty. */
    val documentStatuses: Map<String, String>
        get() = (documentsSummary as? JsonObject)?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { k to it } }?.toMap() ?: emptyMap()
}

/** Text fields for `PATCH riders/profile/` (JSON). Nulls are omitted (explicitNulls = false). */
@Serializable
data class RiderProfilePatchDto(
    @SerialName("full_name") val fullName: String? = null,
    val email: String? = null,
    @SerialName("date_of_birth") val dateOfBirth: String? = null,
    val address: String? = null,
    val city: String? = null,
    @SerialName("vehicle_type") val vehicleType: String? = null,
    @SerialName("vehicle_number") val vehicleNumber: String? = null,
    @SerialName("driving_licence_number") val drivingLicenceNumber: String? = null,
    @SerialName("driving_licence_expiry") val drivingLicenceExpiry: String? = null,
    @SerialName("aadhaar_last4") val aadhaarLast4: String? = null,
    @SerialName("pan_number") val panNumber: String? = null,
    @SerialName("bank_account_name") val bankAccountName: String? = null,
    @SerialName("bank_account_number") val bankAccountNumber: String? = null,
    @SerialName("bank_ifsc") val bankIfsc: String? = null,
    @SerialName("upi_id") val upiId: String? = null,
    @SerialName("emergency_contact_name") val emergencyContactName: String? = null,
    @SerialName("emergency_contact_phone") val emergencyContactPhone: String? = null,
)

/** `riders/documents/` */
@Serializable
data class RiderDocumentDto(
    val id: String,
    @SerialName("doc_type") val docType: String = "other",
    val file: String? = null,
    val number: String = "",
    val status: String = "pending",
    @SerialName("rejection_reason") val rejectionReason: String = "",
    @SerialName("verified_at") val verifiedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class OnlineToggleDto(
    @SerialName("is_online") val isOnline: Boolean,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

/** Rider view of an order (backend `RiderOrderSerializer`). */
@Serializable
data class RiderOrderDto(
    val id: String,
    @SerialName("order_number") val orderNumber: String = "",
    val status: String = "",
    @SerialName("status_display") val statusDisplay: String = "",
    val items: List<OrderItemDto> = emptyList(),
    @SerialName("restaurant_name") val restaurantName: String = "",
    @SerialName("restaurant_address_text") val restaurantAddressText: String = "",
    @SerialName("restaurant_location") val restaurantLocation: LatLngDto? = null,
    @SerialName("restaurant_phone") val restaurantPhone: String? = null,
    @SerialName("delivery_name") val deliveryName: String = "",
    @SerialName("delivery_address_text") val deliveryAddressText: String = "",
    @SerialName("delivery_landmark") val deliveryLandmark: String = "",
    @SerialName("delivery_location") val deliveryLocation: LatLngDto? = null,
    @SerialName("delivery_instructions") val deliveryInstructions: String = "",
    @SerialName("is_contactless") val isContactless: Boolean = false,
    @SerialName("payment_method") val paymentMethod: String = "cod",
    @SerialName("is_paid") val isPaid: Boolean = false,
    /** COD amount to collect, "0.00" when prepaid. */
    @SerialName("collect_amount") val collectAmount: DecimalString = "0",
    @SerialName("rider_payout") val riderPayout: DecimalString = "0",
    @SerialName("distance_km") val distanceKm: DecimalString = "0",
    @SerialName("placed_at") val placedAt: String? = null,
    @SerialName("accepted_at") val acceptedAt: String? = null,
    @SerialName("ready_at") val readyAt: String? = null,
    @SerialName("picked_up_at") val pickedUpAt: String? = null,
    @SerialName("delivered_at") val deliveredAt: String? = null,
    @SerialName("estimated_delivery_at") val estimatedDeliveryAt: String? = null,
    @SerialName("preparation_minutes") val preparationMinutes: Int? = null,
)

/** `riders/offers/` (backend `RiderOfferSerializer`). */
@Serializable
data class RiderOfferDto(
    val id: String,
    val order: RiderOrderDto? = null,
    val status: String = "offered",
    @SerialName("distance_to_restaurant_km") val distanceToRestaurantKm: DecimalString? = null,
    @SerialName("offered_at") val offeredAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("seconds_left") val secondsLeft: Int = 0,
)

@Serializable
data class IncentiveDto(
    val name: String = "",
    @SerialName("deliveries_required") val deliveriesRequired: Int = 0,
    @SerialName("bonus_amount") val bonusAmount: DecimalString = "0",
    val progress: Int = 0,
)

/** Shared by `riders/dashboard/` and `riders/earnings/` → `summary` (backend `earnings_summary`). */
@Serializable
data class EarningsSummaryDto(
    @SerialName("today_earnings") val todayEarnings: DecimalString = "0",
    @SerialName("today_deliveries") val todayDeliveries: Int = 0,
    @SerialName("week_earnings") val weekEarnings: DecimalString = "0",
    @SerialName("week_deliveries") val weekDeliveries: Int = 0,
    @SerialName("month_earnings") val monthEarnings: DecimalString = "0",
    @SerialName("month_deliveries") val monthDeliveries: Int = 0,
    @SerialName("wallet_balance") val walletBalance: DecimalString = "0",
    @SerialName("cod_cash_in_hand") val codCashInHand: DecimalString = "0",
    @SerialName("lifetime_deliveries") val lifetimeDeliveries: Int = 0,
    @SerialName("rating_avg") val ratingAvg: DecimalString = "0",
    @SerialName("pending_payout") val pendingPayout: DecimalString = "0",
    val incentives: List<IncentiveDto> = emptyList(),
)

/** `GET riders/dashboard/`: the earnings summary flattened + rider flags + current order + pending offers. */
@Serializable
data class RiderDashboardDto(
    @SerialName("today_earnings") val todayEarnings: DecimalString = "0",
    @SerialName("today_deliveries") val todayDeliveries: Int = 0,
    @SerialName("week_earnings") val weekEarnings: DecimalString = "0",
    @SerialName("week_deliveries") val weekDeliveries: Int = 0,
    @SerialName("month_earnings") val monthEarnings: DecimalString = "0",
    @SerialName("month_deliveries") val monthDeliveries: Int = 0,
    @SerialName("wallet_balance") val walletBalance: DecimalString = "0",
    @SerialName("cod_cash_in_hand") val codCashInHand: DecimalString = "0",
    @SerialName("lifetime_deliveries") val lifetimeDeliveries: Int = 0,
    @SerialName("rating_avg") val ratingAvg: DecimalString = "0",
    @SerialName("pending_payout") val pendingPayout: DecimalString = "0",
    val incentives: List<IncentiveDto> = emptyList(),
    @SerialName("is_online") val isOnline: Boolean = false,
    @SerialName("is_on_delivery") val isOnDelivery: Boolean = false,
    val status: String = "pending",
    @SerialName("current_order") val currentOrder: RiderOrderDto? = null,
    @SerialName("pending_offers") val pendingOffers: List<RiderOfferDto> = emptyList(),
)

@Serializable
data class RiderEarningDto(
    val id: String,
    @SerialName("earning_type") val earningType: String = "delivery",
    val amount: DecimalString = "0",
    val description: String = "",
    @SerialName("distance_km") val distanceKm: DecimalString = "0",
    val order: String? = null,
    @SerialName("order_number") val orderNumber: String? = null,
    @SerialName("is_settled") val isSettled: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class RiderEarningsDto(
    val summary: EarningsSummaryDto = EarningsSummaryDto(),
    val entries: List<RiderEarningDto> = emptyList(),
)

@Serializable
data class RiderPayoutDto(
    val id: String,
    val amount: DecimalString = "0",
    val status: String = "requested",
    @SerialName("period_start") val periodStart: String? = null,
    @SerialName("period_end") val periodEnd: String? = null,
    val reference: String = "",
    val note: String = "",
    @SerialName("processed_at") val processedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class PayoutRequestDto(val amount: DecimalString? = null)

@Serializable
data class OtpActionDto(val otp: String)

/** `GET riders/orders/{id}/customer-contact/` → `{phone, name}`. */
@Serializable
data class CustomerContactDto(val phone: String? = null, val name: String? = null)

/** `POST rider/location/ {locations:[...]}` → `{results:[{accepted, reason?, order_tracked?}]}`. */
@Serializable
data class LocationBatchResultDto(val results: List<LocationResultDto> = emptyList())

@Serializable
data class LocationResultDto(
    val accepted: Boolean = false,
    val reason: String? = null,
    val detail: String? = null,
    @SerialName("order_tracked") val orderTracked: Boolean? = null,
    @SerialName("order_id") val orderId: String? = null,
)
