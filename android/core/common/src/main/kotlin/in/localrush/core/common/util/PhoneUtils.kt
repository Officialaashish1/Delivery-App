package `in`.localrush.core.common.util

import `in`.localrush.core.common.Constants

object PhoneUtils {
    /** Accepts "9876543210", "+919876543210", "09876543210", "91 98765 43210"; returns E.164 or null. */
    fun normalizeIndian(input: String): String? {
        val digits = input.filter { it.isDigit() }
        val core = when {
            digits.length == 10 -> digits
            digits.length == 11 && digits.startsWith("0") -> digits.drop(1)
            digits.length == 12 && digits.startsWith("91") -> digits.drop(2)
            else -> return null
        }
        if (core.first() !in '6'..'9') return null
        return Constants.INDIA_DIAL_CODE + core
    }

    fun isValidIndian(input: String): Boolean = normalizeIndian(input) != null

    /** "+919876543210" → "+91 98765 43210". */
    fun formatForDisplay(e164: String): String {
        val digits = e164.filter { it.isDigit() }
        return if (digits.length == 12 && digits.startsWith("91")) {
            "+91 ${digits.substring(2, 7)} ${digits.substring(7)}"
        } else e164
    }

    /** "+919876543210" → "98765 43210" */
    fun localPart(e164: String): String = e164.filter { it.isDigit() }.takeLast(10)
}
