package `in`.localrush.customer

import `in`.localrush.core.common.geo.LatLng
import `in`.localrush.core.common.order.OrderStatus
import `in`.localrush.core.network.dto.ChatMessageDto
import `in`.localrush.core.network.dto.LatLngDto
import `in`.localrush.core.network.dto.OrderDetailDto
import `in`.localrush.core.network.dto.OrderEventDto
import `in`.localrush.core.network.dto.RiderLocationDto
import `in`.localrush.core.network.dto.RiderLocationEventDto
import `in`.localrush.core.network.dto.RiderPublicDto
import `in`.localrush.core.network.dto.RouteDto
import `in`.localrush.core.network.dto.TimelineEntryDto
import `in`.localrush.core.network.dto.TrackingSnapshotDto
import `in`.localrush.customer.feature.tracking.vm.ConnectionState
import `in`.localrush.customer.feature.tracking.vm.TrackingReducer
import `in`.localrush.customer.feature.tracking.vm.TrackingUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class TrackingReducerTest {
    private val me = "user-1"
    private val rider = RiderPublicDto(id = "rider-1", fullName = "Ravi", ratingAvg = "4.80", totalDeliveries = 312)
    private val order = OrderDetailDto(
        id = "o1", orderNumber = "LR1", status = "preparing", statusDisplay = "Preparing", estimatedDeliveryAt = "2026-10-24T12:40:00+05:30",
        timeline = listOf(TimelineEntryDto("placed", done = true), TimelineEntryDto("accepted", done = true), TimelineEntryDto("preparing", done = true, current = true)),
        restaurantLocation = LatLngDto(12.97, 77.59), deliveryLocation = LatLngDto(12.99, 77.61),
    )
    private val snapshot = TrackingSnapshotDto(order = order, route = RouteDto(geometry = "abc", distanceM = 3200.0, durationS = 600.0, leg = "restaurant_to_customer"), restaurantLocation = LatLngDto(12.97, 77.59), deliveryLocation = LatLngDto(12.99, 77.61))

    @Test fun `snapshot populates order, route, pins and step`() {
        val s = TrackingReducer.applySnapshot(TrackingUiState(), snapshot, me)
        assertFalse(s.loading)
        assertEquals(OrderStatus.PREPARING, s.status)
        assertEquals(1, s.headerStep)
        assertEquals(LatLng(12.97, 77.59), s.restaurant)
        assertEquals(LatLng(12.99, 77.61), s.destination)
        assertEquals("abc", s.route?.geometry)
        assertNull(s.riderFix)
        assertNull(s.deliveryOtp)
        assertFalse(s.showOtp)
    }

    @Test fun `status event moves the step and attaches the rider`() {
        var s = TrackingReducer.applySnapshot(TrackingUiState(), snapshot, me)
        s = TrackingReducer.applyOrderEvent(s, OrderEventDto(type = "status.rider_assigned", orderId = "o1", status = "rider_assigned", statusDisplay = "Rider assigned", rider = rider, timeline = order.timeline + TimelineEntryDto("rider_assigned", done = true, current = true)))
        assertEquals(OrderStatus.RIDER_ASSIGNED, s.status)
        assertEquals(1, s.headerStep)
        assertEquals("Ravi", s.rider?.fullName)
        assertTrue(s.canContactRider)
        s = TrackingReducer.applyOrderEvent(s, OrderEventDto(type = "status.out_for_delivery", status = "out_for_delivery", statusDisplay = "On the way"))
        assertEquals(2, s.headerStep)
        assertTrue(s.riderOnTheWay)
        assertEquals("Ravi", s.rider?.fullName) // rider kept when the event omits it
    }

    @Test fun `location events update the fix and replace the route only when one is sent`() {
        var s = TrackingReducer.applySnapshot(TrackingUiState(), snapshot, me)
        s = TrackingReducer.applyOrderEvent(s, OrderEventDto(type = "status.out_for_delivery", status = "out_for_delivery", rider = rider))
        s = TrackingReducer.applyLocation(s, RiderLocationEventDto(latitude = 12.975, longitude = 77.595, bearing = 45f, speed = 20f, recordedAt = "2026-10-24T12:20:00Z"))
        assertEquals(LatLng(12.975, 77.595), s.riderFix?.point)
        assertEquals(45f, s.riderFix?.bearing)
        assertEquals("abc", s.route?.geometry)
        val newRoute = RouteDto(geometry = "xyz", distanceM = 1500.0, durationS = 300.0, etaAt = "2026-10-24T12:35:00Z", leg = "rider_to_customer")
        s = TrackingReducer.applyLocation(s, RiderLocationEventDto(latitude = 12.98, longitude = 77.60, bearing = 50f, recordedAt = "2026-10-24T12:21:00Z", route = newRoute))
        assertEquals("xyz", s.route?.geometry)
        assertEquals(Instant.parse("2026-10-24T12:35:00Z"), s.etaAt) // route ETA wins while on the way
        assertEquals(listOf(LatLng(12.98, 77.60), LatLng(12.99, 77.61)), s.focusPoints) // restaurant dropped once en route
    }

    @Test fun `stale location fixes are ignored`() {
        var s = TrackingReducer.applySnapshot(TrackingUiState(), snapshot, me)
        s = TrackingReducer.applyLocation(s, RiderLocationEventDto(latitude = 1.0, longitude = 1.0, recordedAt = "2026-10-24T12:21:00Z"))
        s = TrackingReducer.applyLocation(s, RiderLocationEventDto(latitude = 2.0, longitude = 2.0, recordedAt = "2026-10-24T12:20:00Z"))
        assertEquals(LatLng(1.0, 1.0), s.riderFix?.point)
    }

    @Test fun `OTP shows while the rider is en route and disappears on delivery`() {
        val snap = snapshot.copy(order = order.copy(status = "out_for_delivery", deliveryOtp = "4821", rider = rider), riderLocation = RiderLocationDto(12.98, 77.60, bearing = 90f))
        var s = TrackingReducer.applySnapshot(TrackingUiState(), snap, me)
        assertTrue(s.showOtp)
        assertEquals("4821", s.deliveryOtp)
        assertNotNull(s.riderFix)
        s = TrackingReducer.applyOrderEvent(s, OrderEventDto(type = "status.delivered", status = "delivered", statusDisplay = "Delivered"))
        assertFalse(s.showOtp)
        assertNull(s.deliveryOtp)
        assertNull(s.riderFix)
        assertTrue(s.isTerminal)
        assertNull(s.etaLabel)
        assertEquals(3, s.headerStep)
    }

    @Test fun `ETA label uses the ticked now`() {
        val eta = Instant.parse("2026-10-24T12:40:00Z")
        val s = TrackingReducer.tick(TrackingUiState(status = OrderStatus.PREPARING, estimatedDeliveryAt = eta), Instant.parse("2026-10-24T12:28:00Z"))
        assertEquals("12 min", s.etaLabel)
        assertEquals("Arriving now", TrackingReducer.tick(s, Instant.parse("2026-10-24T12:40:30Z")).etaLabel)
        assertEquals("5 min late", TrackingReducer.tick(s, Instant.parse("2026-10-24T12:45:00Z")).etaLabel)
    }

    @Test fun `chat messages dedupe, count unread from the rider only and reset when read`() {
        var s = TrackingReducer.applySnapshot(TrackingUiState(), snapshot, me)
        val m1 = ChatMessageDto(id = "m1", senderId = "rider-u", senderRole = "rider", text = "On my way", createdAt = "2026-10-24T12:30:00Z")
        s = TrackingReducer.applyChat(s, m1, me, chatOpen = false)
        s = TrackingReducer.applyChat(s, m1, me, chatOpen = false)
        s = TrackingReducer.applyChat(s, ChatMessageDto(id = "m2", senderId = me, senderRole = "customer", text = "ok", createdAt = "2026-10-24T12:31:00Z"), me, chatOpen = false)
        assertEquals(2, s.chat.size)
        assertEquals(1, s.unreadChat)
        s = TrackingReducer.applyChat(s, ChatMessageDto(id = "m3", senderId = "rider-u", senderRole = "rider", text = "here", createdAt = "2026-10-24T12:32:00Z"), me, chatOpen = true)
        assertEquals(1, s.unreadChat)
        assertEquals(0, TrackingReducer.markChatRead(s).unreadChat)
    }

    @Test fun `snapshot merges chat history in time order without duplicates`() {
        val s0 = TrackingUiState(chat = listOf(ChatMessageDto(id = "b", senderRole = "rider", text = "2", createdAt = "2026-10-24T12:02:00Z")))
        val s = TrackingReducer.applySnapshot(s0, snapshot.copy(chat = listOf(
            ChatMessageDto(id = "a", senderRole = "rider", text = "1", createdAt = "2026-10-24T12:01:00Z"),
            ChatMessageDto(id = "b", senderRole = "rider", text = "2", createdAt = "2026-10-24T12:02:00Z"),
        )), me)
        assertEquals(listOf("a", "b"), s.chat.map { it.id })
    }

    @Test fun `unassignment clears the rider and connection state is tracked`() {
        var s = TrackingReducer.applySnapshot(TrackingUiState(), snapshot.copy(order = order.copy(rider = rider, status = "rider_assigned")), me)
        s = TrackingReducer.applyOrderEvent(s, OrderEventDto(type = "order.unassigned", status = "ready_for_pickup"))
        assertNull(s.rider)
        assertEquals(OrderStatus.READY_FOR_PICKUP, s.status)
        assertEquals(ConnectionState.POLLING, TrackingReducer.connection(s, ConnectionState.POLLING).connection)
    }
}
