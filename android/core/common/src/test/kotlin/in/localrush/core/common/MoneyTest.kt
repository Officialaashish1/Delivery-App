package `in`.localrush.core.common

import `in`.localrush.core.common.money.Money
import `in`.localrush.core.common.money.MoneyFormatter
import `in`.localrush.core.common.money.toMoney
import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTest {
    @Test
    fun `indian grouping`() {
        assertEquals("249", MoneyFormatter.groupIndian("249"))
        assertEquals("1,249", MoneyFormatter.groupIndian("1249"))
        assertEquals("12,345", MoneyFormatter.groupIndian("12345"))
        assertEquals("1,23,456", MoneyFormatter.groupIndian("123456"))
        assertEquals("12,34,56,789", MoneyFormatter.groupIndian("123456789"))
    }

    @Test
    fun `formats with rupee symbol and drops zero paise`() {
        assertEquals("₹249", "249.00".toMoney().format())
        assertEquals("₹249.50", "249.5".toMoney().format())
        assertEquals("₹1,23,456.75", Money(12345675).format())
        assertEquals("₹249.00", Money.ofRupees(249).format(dropZeroPaise = false))
        assertEquals("-₹50", Money.ofRupees(-50).format())
        assertEquals("0", Money.ZERO.format(withSymbol = false))
    }

    @Test
    fun `parses backend decimals and symbols`() {
        assertEquals(24900L, Money.parse("249.00").paise)
        assertEquals(124950L, Money.parse("₹1,249.50").paise)
        assertEquals(0L, Money.parse(null).paise)
        assertEquals(1L, Money.parse("0.01").paise)
        assertEquals("249.50", Money(24950).toApiString())
    }

    @Test
    fun `arithmetic`() {
        val a = Money.ofRupees(100)
        val b = Money.ofRupees(49.5)
        assertEquals(Money(14950), a + b)
        assertEquals(Money(5050), a - b)
        assertEquals(Money(30000), a * 3)
    }

    @Test
    fun `compact`() {
        assertEquals("₹1.2k", MoneyFormatter.compact(Money.ofRupees(1234)))
        assertEquals("₹12.5L", MoneyFormatter.compact(Money.ofRupees(1_250_000)))
        assertEquals("₹2Cr", MoneyFormatter.compact(Money.ofRupees(20_000_000)))
    }
}
