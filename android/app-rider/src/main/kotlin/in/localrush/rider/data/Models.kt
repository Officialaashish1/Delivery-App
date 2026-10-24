package `in`.localrush.rider.data

import `in`.localrush.core.common.order.OrderStatus

/** `riders/profile/.status` */
enum class RiderStatus(val wire: String) {
    PENDING("pending"), UNDER_REVIEW("under_review"), APPROVED("approved"), REJECTED("rejected"), SUSPENDED("suspended"), UNKNOWN("unknown");

    companion object { fun fromWire(v: String?): RiderStatus = entries.firstOrNull { it.wire == v } ?: UNKNOWN }
}

enum class VehicleType(val wire: String) {
    BIKE("bike"), SCOOTER("scooter"), BICYCLE("bicycle"), EV("ev");

    companion object { fun fromWire(v: String?): VehicleType = entries.firstOrNull { it.wire == v } ?: BIKE }
}

/** `riders/documents/.doc_type`. [required] documents gate the registration wizard. */
enum class DocType(val wire: String, val required: Boolean) {
    DRIVING_LICENCE("driving_licence", true),
    AADHAAR("aadhaar", true),
    PAN("pan", true),
    VEHICLE_RC("vehicle_rc", true),
    INSURANCE("insurance", false),
    BANK_PROOF("bank_proof", false),
    PHOTO("photo", false),
    OTHER("other", false);

    companion object {
        fun fromWire(v: String?): DocType = entries.firstOrNull { it.wire == v } ?: OTHER
        val uploadable: List<DocType> get() = entries.filter { it != PHOTO && it != OTHER }
    }
}

enum class DocStatus(val wire: String) {
    PENDING("pending"), VERIFIED("verified"), REJECTED("rejected"), MISSING("missing");

    companion object { fun fromWire(v: String?): DocStatus = entries.firstOrNull { it.wire == v } ?: MISSING }
}

enum class PayoutStatus(val wire: String) {
    REQUESTED("requested"), PROCESSING("processing"), PAID("paid"), FAILED("failed"), REJECTED("rejected"), UNKNOWN("unknown");

    companion object { fun fromWire(v: String?): PayoutStatus = entries.firstOrNull { it.wire == v } ?: UNKNOWN }
}

enum class EarningType(val wire: String) {
    DELIVERY("delivery"), DISTANCE("distance"), INCENTIVE("incentive"), BONUS("bonus"), TIP("tip"), PENALTY("penalty"), ADJUSTMENT("adjustment"), COD_DEPOSIT("cod_deposit"), UNKNOWN("unknown");

    val isDeduction: Boolean get() = this == PENALTY || this == COD_DEPOSIT

    companion object { fun fromWire(v: String?): EarningType = entries.firstOrNull { it.wire == v } ?: UNKNOWN }
}

/** `GET riders/earnings/?period=` */
enum class EarningsPeriod(val wire: String) {
    TODAY("today"), WEEK("week"), MONTH("month"), ALL("all");

    companion object { fun fromWire(v: String?): EarningsPeriod = entries.firstOrNull { it.wire == v } ?: WEEK }
}

/** Headline numbers for one period picked out of the backend `summary` block. */
data class PeriodTotals(val earnings: String, val deliveries: Int)

fun EarningsSummaryDto.totalsFor(period: EarningsPeriod, lifetimeEarnings: String? = null): PeriodTotals = when (period) {
    EarningsPeriod.TODAY -> PeriodTotals(todayEarnings, todayDeliveries)
    EarningsPeriod.WEEK -> PeriodTotals(weekEarnings, weekDeliveries)
    EarningsPeriod.MONTH -> PeriodTotals(monthEarnings, monthDeliveries)
    // The backend summary has no lifetime earnings figure; callers sum the entries list for `all`.
    EarningsPeriod.ALL -> PeriodTotals(lifetimeEarnings ?: monthEarnings, lifetimeDeliveries)
}

/** Reasons offered when rejecting an offer. Wire value is free text (max 255). */
enum class RejectReason(val wire: String) {
    TOO_FAR("too_far"), LOW_PAYOUT("low_payout"), WRONG_DIRECTION("wrong_direction"), TAKING_BREAK("taking_break"), VEHICLE_ISSUE("vehicle_issue"), OTHER("other");
}

/** Support ticket categories relevant to riders (backend `TicketCategory`). */
enum class ProblemCategory(val wire: String) {
    CUSTOMER_UNAVAILABLE("customer_unavailable"), RESTAURANT_DELAY("restaurant_delay"), VEHICLE_ISSUE("vehicle_issue"), EMERGENCY("emergency"), PAYMENT("payment"), OTHER("other");
}

/**
 * Rider-side delivery state machine. Derived purely from the order status plus the local
 * "I tapped Arrived at restaurant" flag (the backend records arrival as a history note, not a status).
 */
enum class DeliveryStep {
    /** Heading to the restaurant (accepted; food may still be preparing). */
    TO_RESTAURANT,
    /** At the restaurant, waiting for the food to be marked ready (pickup OTP not yet accepted by backend). */
    WAITING_AT_RESTAURANT,
    /** At the restaurant and the order is `rider_assigned` (ready): enter the pickup OTP. */
    READY_FOR_PICKUP,
    /** Picked up / out for delivery / near destination: heading to the customer. */
    TO_CUSTOMER,
    DELIVERED,
    FAILED;

    val isTerminal: Boolean get() = this == DELIVERED || this == FAILED
    val isToCustomer: Boolean get() = this == TO_CUSTOMER
    val atRestaurant: Boolean get() = this == WAITING_AT_RESTAURANT || this == READY_FOR_PICKUP

    companion object {
        fun from(status: OrderStatus, arrivedAtRestaurant: Boolean): DeliveryStep = when (status) {
            OrderStatus.DELIVERED -> DELIVERED
            OrderStatus.REJECTED, OrderStatus.CANCELLED, OrderStatus.PAYMENT_FAILED, OrderStatus.DELIVERY_FAILED -> FAILED
            OrderStatus.PICKED_UP, OrderStatus.OUT_FOR_DELIVERY, OrderStatus.NEAR_DESTINATION -> TO_CUSTOMER
            OrderStatus.RIDER_ASSIGNED -> if (arrivedAtRestaurant) READY_FOR_PICKUP else TO_RESTAURANT
            // placed / restaurant_accepted / preparing / ready_for_pickup(not yet assigned) / unknown
            else -> if (arrivedAtRestaurant) WAITING_AT_RESTAURANT else TO_RESTAURANT
        }
    }
}

/** Which statuses count as "rider still has this order" on the rider side (accepted-but-preparing included). */
fun OrderStatus.isRiderActive(): Boolean = when (this) {
    OrderStatus.RESTAURANT_ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP,
    OrderStatus.RIDER_ASSIGNED, OrderStatus.PICKED_UP, OrderStatus.OUT_FOR_DELIVERY, OrderStatus.NEAR_DESTINATION -> true
    else -> false
}
