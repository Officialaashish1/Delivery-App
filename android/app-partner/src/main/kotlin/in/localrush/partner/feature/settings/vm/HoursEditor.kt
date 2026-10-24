package `in`.localrush.partner.feature.settings.vm

import `in`.localrush.partner.data.RestaurantHoursDto
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** One weekday row of the hours editor. [weekday] 0 = Monday … 6 = Sunday (backend convention). */
data class DayHours(val weekday: Int, val open: LocalTime, val close: LocalTime, val closed: Boolean) {
    /** Open time later than (or equal to) close time is invalid unless the day is closed. */
    val isValid: Boolean get() = closed || open < close
}

/** Pure helpers for the weekly hours editor, shared by registration and settings. */
object HoursEditor {
    private val wire = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val parse = DateTimeFormatter.ofPattern("HH:mm[:ss]")
    val DEFAULT_OPEN: LocalTime = LocalTime.of(9, 0)
    val DEFAULT_CLOSE: LocalTime = LocalTime.of(23, 0)

    fun defaults(): List<DayHours> = (0..6).map { DayHours(it, DEFAULT_OPEN, DEFAULT_CLOSE, closed = false) }

    /** Builds 7 rows from the backend list; missing weekdays default to open 9–23 (what the backend creates). */
    fun fromDto(hours: List<RestaurantHoursDto>): List<DayHours> {
        val byDay = hours.groupBy { it.weekday }
        return (0..6).map { d ->
            val h = byDay[d]?.minByOrNull { it.openTime }
            if (h == null) DayHours(d, DEFAULT_OPEN, DEFAULT_CLOSE, false)
            else DayHours(d, parseTime(h.openTime) ?: DEFAULT_OPEN, parseTime(h.closeTime) ?: DEFAULT_CLOSE, h.isClosed)
        }
    }

    fun toDto(days: List<DayHours>): List<RestaurantHoursDto> = days.sortedBy { it.weekday }.map {
        RestaurantHoursDto(weekday = it.weekday, openTime = it.open.format(wire), closeTime = it.close.format(wire), isClosed = it.closed)
    }

    fun update(days: List<DayHours>, weekday: Int, transform: (DayHours) -> DayHours): List<DayHours> =
        days.map { if (it.weekday == weekday) transform(it) else it }

    /** Copies [source]'s times (and closed flag) to every other day. */
    fun applyToAll(days: List<DayHours>, source: Int): List<DayHours> {
        val src = days.firstOrNull { it.weekday == source } ?: return days
        return days.map { it.copy(open = src.open, close = src.close, closed = src.closed) }
    }

    fun invalidDays(days: List<DayHours>): List<Int> = days.filterNot { it.isValid }.map { it.weekday }
    fun isValid(days: List<DayHours>): Boolean = days.size == 7 && days.all { it.isValid }

    fun parseTime(v: String?): LocalTime? = v?.let { runCatching { LocalTime.parse(it, parse) }.getOrNull() }
}
