package `in`.localrush.core.common

import `in`.localrush.core.common.order.OrderStatus
import `in`.localrush.core.common.order.TimelineKey
import `in`.localrush.core.common.util.PhoneUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderStatusTest {
    @Test
    fun `wire values round trip`() {
        OrderStatus.entries.filter { it != OrderStatus.UNKNOWN }.forEach {
            assertEquals(it, OrderStatus.fromWire(it.wire))
        }
        assertEquals(OrderStatus.UNKNOWN, OrderStatus.fromWire("nope"))
        assertEquals(OrderStatus.OUT_FOR_DELIVERY, OrderStatus.fromEventType("status.out_for_delivery"))
        assertNull(OrderStatus.fromEventType("rider.location"))
    }

    @Test
    fun `timeline keys follow contract`() {
        assertEquals(
            listOf("placed", "accepted", "preparing", "ready", "rider_assigned", "picked_up", "on_the_way", "near", "delivered"),
            TimelineKey.entries.map { it.wire },
        )
        assertEquals(2, TimelineKey.headerStepIndex(OrderStatus.NEAR_DESTINATION))
        assertEquals(-1, TimelineKey.headerStepIndex(OrderStatus.CANCELLED))
        assertTrue(OrderStatus.DELIVERED.isTerminal)
    }

    @Test
    fun `phone normalisation`() {
        assertEquals("+919876543210", PhoneUtils.normalizeIndian("98765 43210"))
        assertEquals("+919876543210", PhoneUtils.normalizeIndian("+91 9876543210"))
        assertEquals("+919876543210", PhoneUtils.normalizeIndian("09876543210"))
        assertNull(PhoneUtils.normalizeIndian("12345"))
        assertNull(PhoneUtils.normalizeIndian("1234567890"))
        assertEquals("+91 98765 43210", PhoneUtils.formatForDisplay("+919876543210"))
    }
}
