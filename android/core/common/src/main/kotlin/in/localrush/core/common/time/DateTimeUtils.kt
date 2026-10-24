package `in`.localrush.core.common.time

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/** Date/time helpers for ISO-8601 strings from the Django backend. Requires core-library desugaring (enabled). */
object DateTimeUtils {
    val IST: ZoneId = ZoneId.of("Asia/Kolkata")
    private val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val dateFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    private val dateShortFmt = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val dateTimeFmt = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH)
    private val isoDate = DateTimeFormatter.ISO_LOCAL_DATE
    private val hhmm = DateTimeFormatter.ofPattern("HH:mm[:ss]")

    fun zone(): ZoneId = ZoneId.systemDefault()

    /** Parses "2025-01-02T10:11:12.123456+05:30" or "...Z"; returns null on failure. */
    fun parseInstant(iso: String?): Instant? {
        if (iso.isNullOrBlank()) return null
        return try {
            OffsetDateTime.parse(iso).toInstant()
        } catch (e: DateTimeParseException) {
            try { Instant.parse(iso) } catch (e2: DateTimeParseException) { null }
        }
    }

    fun parseDate(iso: String?): LocalDate? = iso?.let { runCatching { LocalDate.parse(it, isoDate) }.getOrNull() }

    /** Parses "09:30:00" or "09:30". */
    fun parseTime(value: String?): LocalTime? = value?.let { runCatching { LocalTime.parse(it, hhmm) }.getOrNull() }

    fun formatIso(instant: Instant): String = DateTimeFormatter.ISO_INSTANT.format(instant)

    fun formatTime(instant: Instant, zone: ZoneId = zone()): String = timeFmt.format(instant.atZone(zone))
    fun formatDate(instant: Instant, zone: ZoneId = zone()): String = dateFmt.format(instant.atZone(zone))
    fun formatDateTime(instant: Instant, zone: ZoneId = zone()): String = dateTimeFmt.format(instant.atZone(zone))
    fun formatTime(time: LocalTime): String = timeFmt.format(time)
    fun formatDate(date: LocalDate): String = dateFmt.format(date)

    /** "Today, 7:45 PM" / "Yesterday, 8:00 AM" / "12 Mar, 1:00 PM". */
    fun formatRelativeDay(instant: Instant, now: Instant = Instant.now(), zone: ZoneId = zone()): String {
        val zdt = instant.atZone(zone)
        val today = now.atZone(zone).toLocalDate()
        val d = zdt.toLocalDate()
        val time = timeFmt.format(zdt)
        return when {
            d == today -> "Today, $time"
            d == today.minusDays(1) -> "Yesterday, $time"
            d == today.plusDays(1) -> "Tomorrow, $time"
            d.year == today.year -> "${dateShortFmt.format(zdt)}, $time"
            else -> dateTimeFmt.format(zdt)
        }
    }

    /** "just now", "5 min ago", "2 h ago", "3 d ago". */
    fun timeAgo(instant: Instant, now: Instant = Instant.now()): String {
        val d = Duration.between(instant, now)
        val s = d.seconds
        return when {
            s < 45 -> "just now"
            s < 3600 -> "${maxOf(1, s / 60)} min ago"
            s < 86_400 -> "${s / 3600} h ago"
            s < 7 * 86_400 -> "${s / 86_400} d ago"
            else -> formatDate(instant)
        }
    }

    /** "in 12 min", "arriving now", "5 min late". ETA strings for tracking. */
    fun etaLabel(eta: Instant, now: Instant = Instant.now()): String {
        val mins = Duration.between(now, eta).toMinutes()
        return when {
            mins <= 0 && mins > -3 -> "Arriving now"
            mins < 0 -> "${-mins} min late"
            mins == 1L -> "1 min"
            else -> "$mins min"
        }
    }

    /** "mm:ss" countdown for OTP resend / rider offers. */
    fun countdown(seconds: Long): String {
        val s = maxOf(0, seconds)
        return String.format(Locale.ROOT, "%02d:%02d", s / 60, s % 60)
    }

    fun secondsUntil(instant: Instant, now: Instant = Instant.now()): Long = Duration.between(now, instant).seconds

    fun nowZoned(): ZonedDateTime = ZonedDateTime.now(zone())
}

fun String?.toInstantOrNull(): Instant? = DateTimeUtils.parseInstant(this)
