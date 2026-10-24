package `in`.localrush.rider.ui.common

import `in`.localrush.core.common.money.Money
import `in`.localrush.core.common.time.DateTimeUtils
import `in`.localrush.core.common.time.toInstantOrNull

fun String?.rupees(): String = Money.parse(this).format()

fun String?.relativeDay(): String = this.toInstantOrNull()?.let { DateTimeUtils.formatRelativeDay(it) } ?: "—"

fun String?.dateLabel(): String = this.toInstantOrNull()?.let { DateTimeUtils.formatDate(it) } ?: (DateTimeUtils.parseDate(this)?.let { DateTimeUtils.formatDate(it) } ?: "—")

fun String?.timeLabel(): String = this.toInstantOrNull()?.let { DateTimeUtils.formatTime(it) } ?: "—"

/** "12 min" / "1 h 05 min" from seconds. */
fun durationLabel(seconds: Double): String {
    val m = (seconds / 60).toInt()
    return if (m < 60) "$m min" else "${m / 60} h ${(m % 60).toString().padStart(2, '0')} min"
}
