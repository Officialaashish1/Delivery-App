package `in`.localrush.rider

import `in`.localrush.core.common.geo.LatLng
import `in`.localrush.core.common.geo.Polyline
import `in`.localrush.core.network.dto.RouteDto
import `in`.localrush.core.network.dto.RouteStepDto
import `in`.localrush.rider.data.RouteGeometry
import `in`.localrush.rider.data.RouteSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteGeometryTest {
    // A straight 1 km leg north then 1 km east (approx) with a turn at the corner.
    private val a = LatLng(12.9000, 77.5000)
    private val corner = LatLng(12.9090, 77.5000)
    private val end = LatLng(12.9090, 77.5092)
    private val route = RouteDto(
        geometry = Polyline.encode(listOf(a, corner, end)),
        distanceM = 2000.0, durationS = 400.0, leg = "to_restaurant",
        steps = listOf(
            RouteStepDto("Head north", "", 1000.0, 200.0, "depart", "", listOf(a.longitude, a.latitude)),
            RouteStepDto("Turn right onto MG Road", "MG Road", 1000.0, 200.0, "turn", "right", listOf(corner.longitude, corner.latitude)),
            RouteStepDto("You have arrived", "", 0.0, 0.0, "arrive", "", listOf(end.longitude, end.latitude)),
        ),
    )
    private val snap = RouteSnapshot.from(route, nowMs = 0)

    @Test fun `step maneuver points snap to polyline vertices`() {
        assertEquals(listOf(0, 1, 2), snap.stepVertex)
    }

    @Test fun `progress near the start points at the upcoming turn with remaining distance`() {
        val p = RouteGeometry.progress(snap, LatLng(12.9020, 77.50005))!!
        assertEquals(0, p.segment)
        assertTrue(p.offRouteM < 20)
        assertNotNull(p.next)
        assertEquals("turn", p.next!!.step.maneuverType)
        assertTrue("distance to turn ~780 m, was ${p.next!!.distanceM}", p.next!!.distanceM in 700.0..850.0)
        assertTrue("remaining ~1.8 km, was ${p.remainingM}", p.remainingM in 1700.0..1900.0)
        assertTrue(p.remainingS in 300.0..400.0)
        assertFalse(RouteGeometry.isOffRoute(p))
    }

    @Test fun `after the corner the next maneuver is arrival`() {
        val p = RouteGeometry.progress(snap, LatLng(12.9090, 77.5050))!!
        assertEquals(1, p.segment)
        assertEquals("arrive", p.next!!.step.maneuverType)
        assertTrue(p.remainingM in 400.0..520.0)
    }

    @Test fun `far from the line is off-route`() {
        val p = RouteGeometry.progress(snap, LatLng(12.9040, 77.5030))!!
        assertTrue(p.offRouteM > RouteGeometry.OFF_ROUTE_THRESHOLD_M)
        assertTrue(RouteGeometry.isOffRoute(p))
    }

    @Test fun `routes without geometry yield no progress`() {
        val empty = RouteSnapshot.from(RouteDto(geometry = ""), nowMs = 0)
        assertEquals(null, RouteGeometry.progress(empty, a))
    }
}
