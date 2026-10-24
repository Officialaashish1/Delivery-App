package `in`.localrush.core.common

import `in`.localrush.core.common.geo.Geo
import `in`.localrush.core.common.geo.LatLng
import `in`.localrush.core.common.geo.Polyline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PolylineTest {
    // Canonical example from Google's polyline docs.
    private val encoded = "_p~iF~ps|U_ulLnnqC_mqNvxq`@"
    private val expected = listOf(LatLng(38.5, -120.2), LatLng(40.7, -120.95), LatLng(43.252, -126.453))

    @Test
    fun `decodes reference polyline`() {
        val pts = Polyline.decode(encoded)
        assertEquals(3, pts.size)
        pts.zip(expected).forEach { (a, b) ->
            assertEquals(b.latitude, a.latitude, 1e-5)
            assertEquals(b.longitude, a.longitude, 1e-5)
        }
    }

    @Test
    fun `encode round trips`() {
        assertEquals(encoded, Polyline.encode(expected))
        val india = listOf(LatLng(12.9716, 77.5946), LatLng(12.9352, 77.6245), LatLng(12.9279, 77.6271))
        val round = Polyline.decode(Polyline.encode(india))
        india.zip(round).forEach { (a, b) ->
            assertEquals(a.latitude, b.latitude, 1e-5)
            assertEquals(a.longitude, b.longitude, 1e-5)
        }
    }

    @Test
    fun `empty and null inputs decode to empty list`() {
        assertTrue(Polyline.decode(null).isEmpty())
        assertTrue(Polyline.decode("").isEmpty())
    }

    @Test
    fun `haversine distance Bengaluru MG Road to Koramangala about 5 km`() {
        val d = Geo.haversineMeters(LatLng(12.9757, 77.6062), LatLng(12.9352, 77.6245))
        assertTrue("was $d", d in 4_500.0..5_500.0)
    }

    @Test
    fun `bearing north is zero and east is ninety`() {
        assertEquals(0f, Geo.bearing(LatLng(0.0, 0.0), LatLng(1.0, 0.0)), 0.01f)
        assertEquals(90f, Geo.bearing(LatLng(0.0, 0.0), LatLng(0.0, 1.0)), 0.01f)
    }

    @Test
    fun `bearing lerp takes shortest arc`() {
        assertEquals(0f, Geo.lerpBearing(350f, 10f, 0.5f), 0.01f)
        assertEquals(355f, Geo.lerpBearing(350f, 0f, 0.5f), 0.01f)
    }
}
