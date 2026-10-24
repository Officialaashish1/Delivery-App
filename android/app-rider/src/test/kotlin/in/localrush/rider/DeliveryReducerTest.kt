package `in`.localrush.rider

import `in`.localrush.core.common.geo.LatLng
import `in`.localrush.core.common.order.OrderStatus
import `in`.localrush.core.network.dto.LatLngDto
import `in`.localrush.rider.data.DeliveryStep
import `in`.localrush.rider.data.RiderOrderDto
import `in`.localrush.rider.data.isRiderActive
import `in`.localrush.rider.feature.delivery.vm.DeliveryCore
import `in`.localrush.rider.feature.delivery.vm.DeliveryEvent
import `in`.localrush.rider.feature.delivery.vm.DeliveryReducer
import `in`.localrush.rider.feature.delivery.vm.PrimaryAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeliveryReducerTest {
    @Test fun `accepted but preparing order is TO_RESTAURANT with Arrived button`() {
        val core = DeliveryCore(OrderStatus.PREPARING)
        assertEquals(DeliveryStep.TO_RESTAURANT, core.step)
        assertEquals(PrimaryAction.ARRIVED_AT_RESTAURANT, core.primaryAction)
        assertEquals("to_restaurant", core.leg)
    }

    @Test fun `arriving while food is still preparing waits, then unlocks OTP when rider_assigned`() {
        var core = DeliveryReducer.reduce(DeliveryCore(OrderStatus.PREPARING), DeliveryEvent.ArrivedAtRestaurant)
        assertEquals(DeliveryStep.WAITING_AT_RESTAURANT, core.step)
        assertEquals(PrimaryAction.WAITING_FOR_FOOD, core.primaryAction)
        core = DeliveryReducer.reduce(core, DeliveryEvent.OrderUpdated(OrderStatus.RIDER_ASSIGNED))
        assertEquals(DeliveryStep.READY_FOR_PICKUP, core.step)
        assertEquals(PrimaryAction.ENTER_PICKUP_OTP, core.primaryAction)
    }

    @Test fun `arrived flag persists across status refreshes but is irrelevant once en route`() {
        var core = DeliveryReducer.reduce(DeliveryCore(OrderStatus.RIDER_ASSIGNED), DeliveryEvent.ArrivedAtRestaurant)
        core = DeliveryReducer.reduce(core, DeliveryEvent.OrderUpdated(OrderStatus.RIDER_ASSIGNED))
        assertEquals(DeliveryStep.READY_FOR_PICKUP, core.step)
        core = DeliveryReducer.reduce(core, DeliveryEvent.OrderUpdated(OrderStatus.OUT_FOR_DELIVERY))
        assertEquals(DeliveryStep.TO_CUSTOMER, core.step)
        assertEquals(PrimaryAction.ARRIVED_AT_CUSTOMER, core.primaryAction)
        assertEquals("to_customer", core.leg)
        assertTrue(core.canReportFailure)
        // Arrived event is a no-op after pickup
        assertEquals(core, DeliveryReducer.reduce(core, DeliveryEvent.ArrivedAtRestaurant))
    }

    @Test fun `pickup transitions picked_up and near_destination all map to TO_CUSTOMER`() {
        listOf(OrderStatus.PICKED_UP, OrderStatus.OUT_FOR_DELIVERY, OrderStatus.NEAR_DESTINATION).forEach {
            assertEquals(DeliveryStep.TO_CUSTOMER, DeliveryStep.from(it, arrivedAtRestaurant = false))
            assertEquals(DeliveryStep.TO_CUSTOMER, DeliveryStep.from(it, arrivedAtRestaurant = true))
        }
    }

    @Test fun `terminal statuses`() {
        assertEquals(DeliveryStep.DELIVERED, DeliveryStep.from(OrderStatus.DELIVERED, true))
        assertEquals(DeliveryStep.FAILED, DeliveryStep.from(OrderStatus.DELIVERY_FAILED, true))
        assertEquals(DeliveryStep.FAILED, DeliveryStep.from(OrderStatus.CANCELLED, false))
        val done = DeliveryCore(OrderStatus.DELIVERED)
        assertEquals(PrimaryAction.NONE, done.primaryAction)
        assertTrue(done.step.isTerminal)
        assertFalse(done.canContactCustomer)
        assertFalse(DeliveryCore(OrderStatus.RIDER_ASSIGNED).canReportFailure)
    }

    @Test fun `destination follows the leg`() {
        val order = RiderOrderDto(id = "o", restaurantLocation = LatLngDto(1.0, 2.0), deliveryLocation = LatLngDto(3.0, 4.0))
        assertEquals(LatLng(1.0, 2.0), DeliveryReducer.destination(order, DeliveryCore(OrderStatus.RIDER_ASSIGNED)))
        assertEquals(LatLng(3.0, 4.0), DeliveryReducer.destination(order, DeliveryCore(OrderStatus.OUT_FOR_DELIVERY)))
    }

    @Test fun `rider-active statuses include accepted-but-preparing orders`() {
        assertTrue(OrderStatus.PREPARING.isRiderActive())
        assertTrue(OrderStatus.NEAR_DESTINATION.isRiderActive())
        assertFalse(OrderStatus.DELIVERED.isRiderActive())
        assertFalse(OrderStatus.PLACED.isRiderActive())
    }
}
