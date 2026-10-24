package `in`.localrush.core.common.money

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Indian Rupee amounts. Backend sends decimals as strings ("249.00"). Stored as paise (Long) for exactness.
 */
@JvmInline
value class Money(val paise: Long) : Comparable<Money> {
    val rupees: BigDecimal get() = BigDecimal.valueOf(paise).movePointLeft(2)
    val isZero: Boolean get() = paise == 0L
    val isNegative: Boolean get() = paise < 0

    operator fun plus(other: Money) = Money(paise + other.paise)
    operator fun minus(other: Money) = Money(paise - other.paise)
    operator fun times(qty: Int) = Money(paise * qty)
    operator fun unaryMinus() = Money(-paise)
    override fun compareTo(other: Money): Int = paise.compareTo(other.paise)

    /** "₹1,23,456.50" / "₹249" (drops .00 when [dropZeroPaise]). */
    fun format(dropZeroPaise: Boolean = true, withSymbol: Boolean = true): String =
        MoneyFormatter.format(this, dropZeroPaise, withSymbol)

    /** Backend wire format: "249.00". */
    fun toApiString(): String = rupees.setScale(2, RoundingMode.HALF_UP).toPlainString()

    override fun toString(): String = format()

    companion object {
        val ZERO = Money(0)
        fun ofRupees(rupees: Double): Money = Money(BigDecimal.valueOf(rupees).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong())
        fun ofRupees(rupees: Int): Money = Money(rupees * 100L)
        fun ofRupees(rupees: BigDecimal): Money = Money(rupees.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong())

        /** Parses "249.00", "1,249.5", "₹ 249", null → ZERO when [default] not given. */
        fun parse(raw: String?, default: Money = ZERO): Money {
            if (raw.isNullOrBlank()) return default
            val cleaned = raw.replace("₹", "").replace(",", "").replace("Rs.", "").trim()
            return cleaned.toBigDecimalOrNull()?.let { ofRupees(it) } ?: default
        }

        fun parseOrNull(raw: String?): Money? {
            if (raw.isNullOrBlank()) return null
            val cleaned = raw.replace("₹", "").replace(",", "").trim()
            return cleaned.toBigDecimalOrNull()?.let { ofRupees(it) }
        }
    }
}

object MoneyFormatter {
    const val RUPEE = "₹"

    /**
     * Indian grouping: last three digits, then groups of two: 12,34,567.
     */
    fun groupIndian(integerPart: String): String {
        val digits = integerPart.trimStart('-')
        if (digits.length <= 3) return digits
        val last3 = digits.takeLast(3)
        val rest = digits.dropLast(3)
        val sb = StringBuilder()
        var i = rest.length
        while (i > 0) {
            val start = maxOf(0, i - 2)
            sb.insert(0, rest.substring(start, i))
            if (start > 0) sb.insert(0, ',')
            i = start
        }
        return "$sb,$last3"
    }

    fun format(money: Money, dropZeroPaise: Boolean = true, withSymbol: Boolean = true): String {
        val negative = money.paise < 0
        val abs = kotlin.math.abs(money.paise)
        val rupees = abs / 100
        val paise = (abs % 100).toInt()
        val grouped = groupIndian(rupees.toString())
        val body = if (paise == 0 && dropZeroPaise) grouped else "$grouped.${paise.toString().padStart(2, '0')}"
        val sign = if (negative) "-" else ""
        return if (withSymbol) "$sign$RUPEE$body" else "$sign$body"
    }

    /** "₹1.2k", "₹12.5L" style compact strings for dashboards. */
    fun compact(money: Money): String {
        val r = money.paise / 100.0
        return when {
            r >= 1_00_00_000 -> "$RUPEE${trim1(r / 1_00_00_000)}Cr"
            r >= 1_00_000 -> "$RUPEE${trim1(r / 1_00_000)}L"
            r >= 1_000 -> "$RUPEE${trim1(r / 1_000)}k"
            else -> format(money)
        }
    }

    private fun trim1(v: Double): String {
        val s = String.format(java.util.Locale.ROOT, "%.1f", v)
        return if (s.endsWith(".0")) s.dropLast(2) else s
    }
}

fun String?.toMoney(): Money = Money.parse(this)
fun String?.toMoneyOrNull(): Money? = Money.parseOrNull(this)
