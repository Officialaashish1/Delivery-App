package `in`.localrush.partner.feature.finance.vm

import `in`.localrush.core.common.money.Money
import `in`.localrush.partner.data.FinanceSummaryDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class FinanceSummaryTest {

    @Test
    fun `maps decimal strings into Money and derives totals`() {
        val s = FinanceSummary.from(
            FinanceSummaryDto(orders = 4, grossSales = "1000.00", commission = "150.00", couponDeductions = "50.00", refundDeductions = "25.50", taxCollected = "45.00", netEarnings = "774.50"),
        )
        assertEquals(Money.ofRupees(1000), s.grossSales)
        assertEquals(Money.ofRupees(774.5), s.netEarnings)
        assertEquals(Money.ofRupees(225.5), s.totalDeductions)
        assertEquals(Money.ofRupees(250), s.averageOrderValue)
        assertEquals(15.0, s.commissionPercentOfGross, 0.001)
    }

    @Test
    fun `empty summary is safe`() {
        val s = FinanceSummary.from(FinanceSummaryDto())
        assertEquals(Money.ZERO, s.grossSales)
        assertEquals(Money.ZERO, s.averageOrderValue)
        assertEquals(0.0, s.commissionPercentOfGross, 0.0)
    }

    @Test
    fun `date presets`() {
        val wed = LocalDate.of(2025, 1, 15) // Wednesday
        assertEquals(DateRange(wed, wed), DateRange.forPreset(DatePreset.TODAY, wed))
        assertEquals(DateRange(LocalDate.of(2025, 1, 13), wed), DateRange.forPreset(DatePreset.WEEK, wed))
        assertEquals(DateRange(LocalDate.of(2025, 1, 1), wed), DateRange.forPreset(DatePreset.MONTH, wed))
        assertEquals(DateRange(LocalDate.of(2024, 12, 1), LocalDate.of(2024, 12, 31)), DateRange.forPreset(DatePreset.LAST_MONTH, wed))
        val all = DateRange.forPreset(DatePreset.ALL, wed)
        assertNull(all.fromIso); assertNull(all.toIso)
        assertEquals("2025-01-15", DateRange.forPreset(DatePreset.TODAY, wed).toIso)
    }
}
