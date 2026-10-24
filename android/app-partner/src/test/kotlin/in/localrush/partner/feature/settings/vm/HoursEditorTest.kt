package `in`.localrush.partner.feature.settings.vm

import `in`.localrush.partner.data.RestaurantHoursDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class HoursEditorTest {

    @Test
    fun `defaults are seven open days 9 to 23`() {
        val d = HoursEditor.defaults()
        assertEquals(7, d.size)
        assertEquals((0..6).toList(), d.map { it.weekday })
        assertTrue(d.all { !it.closed && it.open == LocalTime.of(9, 0) && it.close == LocalTime.of(23, 0) })
        assertTrue(HoursEditor.isValid(d))
    }

    @Test
    fun `fromDto fills missing weekdays and parses times`() {
        val days = HoursEditor.fromDto(listOf(RestaurantHoursDto(weekday = 0, openTime = "10:30:00", closeTime = "22:00:00"), RestaurantHoursDto(weekday = 6, isClosed = true)))
        assertEquals(7, days.size)
        assertEquals(LocalTime.of(10, 30), days[0].open)
        assertEquals(LocalTime.of(22, 0), days[0].close)
        assertTrue(days[6].closed)
        assertEquals(LocalTime.of(9, 0), days[3].open)
    }

    @Test
    fun `open after close is invalid unless the day is closed`() {
        val days = HoursEditor.update(HoursEditor.defaults(), 2) { it.copy(open = LocalTime.of(23, 0), close = LocalTime.of(9, 0)) }
        assertFalse(HoursEditor.isValid(days))
        assertEquals(listOf(2), HoursEditor.invalidDays(days))
        val closed = HoursEditor.update(days, 2) { it.copy(closed = true) }
        assertTrue(HoursEditor.isValid(closed))
    }

    @Test
    fun `applyToAll copies times to every weekday`() {
        val days = HoursEditor.update(HoursEditor.defaults(), 4) { it.copy(open = LocalTime.of(11, 0), close = LocalTime.of(23, 30)) }
        val all = HoursEditor.applyToAll(days, 4)
        assertTrue(all.all { it.open == LocalTime.of(11, 0) && it.close == LocalTime.of(23, 30) })
    }

    @Test
    fun `toDto emits HH mm ss wire format sorted by weekday`() {
        val dto = HoursEditor.toDto(HoursEditor.defaults().reversed())
        assertEquals((0..6).toList(), dto.map { it.weekday })
        assertEquals("09:00:00", dto[0].openTime)
        assertEquals("23:00:00", dto[0].closeTime)
        assertFalse(dto[0].isClosed)
    }
}
