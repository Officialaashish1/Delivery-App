package `in`.localrush.core.common.util

fun String?.orDash(): String = if (this.isNullOrBlank()) "—" else this

fun String.initials(max: Int = 2): String =
    split(" ").filter { it.isNotBlank() }.take(max).joinToString("") { it.first().uppercaseChar().toString() }

fun Int.pluralize(singular: String, plural: String = singular + "s"): String = "$this ${if (this == 1) singular else plural}"

/** "4.3" → "4.3", "4.0" → "4.0"; ratings are always one decimal. */
fun Double.ratingLabel(): String = String.format(java.util.Locale.ROOT, "%.1f", this)
