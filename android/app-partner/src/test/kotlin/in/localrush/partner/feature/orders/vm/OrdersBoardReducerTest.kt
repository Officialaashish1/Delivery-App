package `in`.localrush.partner.feature.orders.vm

import `in`.localrush.core.common.order.OrderStatus
import `in`.localrush.partner.data.PartnerOrderDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrdersBoardReducerTest {

    private fun order(id: String, status: OrderStatus, placedAt: String = "2025-01-01T10:00:00Z") =
        PartnerOrderDto(id = id, orderNumber = "LR$id", status = status.wire, placedAt = placedAt)

    @Test
    fun `statuses map to the right tab`() {
        assertEquals(OrderTab.NEW, OrdersBoardReducer.tabFor(OrderStatus.PLACED))
        assertEquals(OrderTab.PREPARING, OrdersBoardReducer.tabFor(OrderStatus.RESTAURANT_ACCEPTED))
        assertEquals(OrderTab.PREPARING, OrdersBoardReducer.tabFor(OrderStatus.PREPARING))
        assertEquals(OrderTab.READY, OrdersBoardReducer.tabFor(OrderStatus.READY_FOR_PICKUP))
        assertEquals(OrderTab.READY, OrdersBoardReducer.tabFor(OrderStatus.RIDER_ASSIGNED))
        assertEquals(OrderTab.PAST, OrdersBoardReducer.tabFor(OrderStatus.PICKED_UP))
        assertEquals(OrderTab.PAST, OrdersBoardReducer.tabFor(OrderStatus.DELIVERED))
        assertEquals(OrderTab.PAST, OrdersBoardReducer.tabFor(OrderStatus.REJECTED))
        assertEquals(OrderTab.PAST, OrdersBoardReducer.tabFor(OrderStatus.UNKNOWN))
    }

    @Test
    fun `live status_placed event adds order to New tab and raises the alert`() {
        val s = OrdersBoardReducer.upsert(OrdersBoardState(), order("1", OrderStatus.PLACED), fromLiveEvent = true)
        assertEquals(listOf("1"), s.ordersIn(OrderTab.NEW).map { it.id })
        assertTrue(s.alertActive)
        assertEquals(setOf("1"), s.unacknowledged)
    }

    @Test
    fun `REST snapshot of a placed order does not ring`() {
        val s = OrdersBoardReducer.upsert(OrdersBoardState(), order("1", OrderStatus.PLACED), fromLiveEvent = false)
        assertEquals(1, s.count(OrderTab.NEW))
        assertFalse(s.alertActive)
    }

    @Test
    fun `accept event moves order from New to Preparing and clears its alert`() {
        var s = OrdersBoardReducer.upsert(OrdersBoardState(), order("1", OrderStatus.PLACED), fromLiveEvent = true)
        s = OrdersBoardReducer.upsert(s, order("1", OrderStatus.RESTAURANT_ACCEPTED), fromLiveEvent = true)
        assertEquals(0, s.count(OrderTab.NEW))
        assertEquals(listOf("1"), s.ordersIn(OrderTab.PREPARING).map { it.id })
        assertFalse(s.alertActive)
    }

    @Test
    fun `ready and rider assigned stay in Ready, picked up goes to Past`() {
        var s = OrdersBoardReducer.upsert(OrdersBoardState(), order("1", OrderStatus.PREPARING), fromLiveEvent = false)
        s = OrdersBoardReducer.upsert(s, order("1", OrderStatus.READY_FOR_PICKUP), fromLiveEvent = true)
        assertEquals(1, s.count(OrderTab.READY))
        s = OrdersBoardReducer.upsert(s, order("1", OrderStatus.RIDER_ASSIGNED), fromLiveEvent = true)
        assertEquals(1, s.count(OrderTab.READY))
        s = OrdersBoardReducer.upsert(s, order("1", OrderStatus.PICKED_UP), fromLiveEvent = true)
        assertEquals(0, s.count(OrderTab.READY))
        assertEquals(1, s.count(OrderTab.PAST))
    }

    @Test
    fun `duplicate placed event does not re-ring an acknowledged order`() {
        var s = OrdersBoardReducer.upsert(OrdersBoardState(), order("1", OrderStatus.PLACED), fromLiveEvent = true)
        s = OrdersBoardReducer.acknowledge(s, "1")
        assertFalse(s.alertActive)
        s = OrdersBoardReducer.upsert(s, order("1", OrderStatus.PLACED), fromLiveEvent = true)
        assertFalse("already placed + acknowledged must not ring again", s.alertActive)
    }

    @Test
    fun `alert stays active while any new order is unacknowledged`() {
        var s = OrdersBoardReducer.upsert(OrdersBoardState(), order("1", OrderStatus.PLACED), fromLiveEvent = true)
        s = OrdersBoardReducer.upsert(s, order("2", OrderStatus.PLACED), fromLiveEvent = true)
        s = OrdersBoardReducer.acknowledge(s, "1")
        assertTrue(s.alertActive)
        s = OrdersBoardReducer.acknowledgeAll(s)
        assertFalse(s.alertActive)
    }

    @Test
    fun `snapshot replaces only the given tab and keeps alerts for orders still placed`() {
        var s = OrdersBoardReducer.upsert(OrdersBoardState(), order("1", OrderStatus.PLACED), fromLiveEvent = true)
        s = OrdersBoardReducer.upsert(s, order("2", OrderStatus.PLACED), fromLiveEvent = true)
        s = OrdersBoardReducer.upsert(s, order("3", OrderStatus.PREPARING), fromLiveEvent = false)
        // Server says order 2 is gone from New (someone else rejected it) and a new order 4 exists.
        s = OrdersBoardReducer.applySnapshot(s, OrderTab.NEW, listOf(order("1", OrderStatus.PLACED), order("4", OrderStatus.PLACED)))
        assertEquals(setOf("1", "4"), s.ordersIn(OrderTab.NEW).map { it.id }.toSet())
        assertEquals(setOf("1"), s.unacknowledged)
        assertEquals(1, s.count(OrderTab.PREPARING))
        assertTrue(OrderTab.NEW in s.loadedTabs)
        assertFalse(OrderTab.PAST in s.loadedTabs)
    }

    @Test
    fun `new orders are sorted oldest first, past newest first`() {
        var s = OrdersBoardState()
        s = OrdersBoardReducer.upsert(s, order("late", OrderStatus.PLACED, "2025-01-01T10:05:00Z"), false)
        s = OrdersBoardReducer.upsert(s, order("early", OrderStatus.PLACED, "2025-01-01T10:00:00Z"), false)
        assertEquals(listOf("early", "late"), s.ordersIn(OrderTab.NEW).map { it.id })
        s = OrdersBoardReducer.upsert(s, order("d1", OrderStatus.DELIVERED).copy(deliveredAt = "2025-01-01T11:00:00Z"), false)
        s = OrdersBoardReducer.upsert(s, order("d2", OrderStatus.DELIVERED).copy(deliveredAt = "2025-01-01T12:00:00Z"), false)
        assertEquals(listOf("d2", "d1"), s.ordersIn(OrderTab.PAST).map { it.id })
    }

    @Test
    fun `remove drops the order and its alert`() {
        var s = OrdersBoardReducer.upsert(OrdersBoardState(), order("1", OrderStatus.PLACED), fromLiveEvent = true)
        s = OrdersBoardReducer.remove(s, "1")
        assertTrue(s.orders.isEmpty())
        assertFalse(s.alertActive)
    }

    @Test
    fun `scopes match backend query values`() {
        assertEquals("new", OrdersBoardReducer.scopeFor(OrderTab.NEW))
        assertEquals("preparing", OrdersBoardReducer.scopeFor(OrderTab.PREPARING))
        assertEquals("ready", OrdersBoardReducer.scopeFor(OrderTab.READY))
        assertEquals("past", OrdersBoardReducer.scopeFor(OrderTab.PAST))
    }
}
