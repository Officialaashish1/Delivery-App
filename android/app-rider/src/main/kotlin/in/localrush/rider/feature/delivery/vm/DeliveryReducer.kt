package `in`.localrush.rider.feature.delivery.vm

import `in`.localrush.core.common.geo.LatLng
import `in`.localrush.core.common.order.OrderStatus
import `in`.localrush.rider.data.DeliveryStep
import `in`.localrush.rider.data.RiderOrderDto

/** The one big button at the bottom of the navigation screen. */
enum class PrimaryAction { ARRIVED_AT_RESTAURANT, WAITING_FOR_FOOD, ENTER_PICKUP_OTP, ARRIVED_AT_CUSTOMER, NONE }

/** Pure delivery state: backend status + local "arrived" flag. Everything else on screen derives from these two. */
data class DeliveryCore(val status: OrderStatus = OrderStatus.UNKNOWN, val arrivedAtRestaurant: Boolean = false) {
    val step: DeliveryStep get() = DeliveryStep.from(status, arrivedAtRestaurant)

    val primaryAction: PrimaryAction get() = when (step) {
        DeliveryStep.TO_RESTAURANT -> PrimaryAction.ARRIVED_AT_RESTAURANT
        DeliveryStep.WAITING_AT_RESTAURANT -> PrimaryAction.WAITING_FOR_FOOD
        DeliveryStep.READY_FOR_PICKUP -> PrimaryAction.ENTER_PICKUP_OTP
        DeliveryStep.TO_CUSTOMER -> PrimaryAction.ARRIVED_AT_CUSTOMER
        DeliveryStep.DELIVERED, DeliveryStep.FAILED -> PrimaryAction.NONE
    }

    /** `leg` the route endpoint will return for this state (used to detect leg changes → re-route). */
    val leg: String get() = if (step.isToCustomer) "to_customer" else "to_restaurant"

    val canReportFailure: Boolean get() = step == DeliveryStep.TO_CUSTOMER
    val canContactCustomer: Boolean get() = !step.isTerminal
}

sealed interface DeliveryEvent {
    /** Any fresh order payload (REST or WebSocket). */
    data class OrderUpdated(val status: OrderStatus) : DeliveryEvent
    data object ArrivedAtRestaurant : DeliveryEvent
}

object DeliveryReducer {
    fun reduce(core: DeliveryCore, event: DeliveryEvent): DeliveryCore = when (event) {
        is DeliveryEvent.OrderUpdated -> core.copy(status = event.status)
        DeliveryEvent.ArrivedAtRestaurant -> if (core.step.isTerminal || core.step.isToCustomer) core else core.copy(arrivedAtRestaurant = true)
    }

    /** Where the rider is heading for the current step. */
    fun destination(order: RiderOrderDto?, core: DeliveryCore): LatLng? {
        val loc = if (core.step.isToCustomer) order?.deliveryLocation else order?.restaurantLocation
        return loc?.let { LatLng(it.latitude, it.longitude) }
    }
}
