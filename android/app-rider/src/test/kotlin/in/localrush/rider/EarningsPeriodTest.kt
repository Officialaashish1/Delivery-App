package `in`.localrush.rider

import `in`.localrush.rider.data.EarningsPeriod
import `in`.localrush.rider.data.EarningsSummaryDto
import `in`.localrush.rider.data.PeriodTotals
import `in`.localrush.rider.data.RiderEarningDto
import `in`.localrush.rider.data.RiderEarningsDto
import `in`.localrush.rider.data.totalsFor
import `in`.localrush.rider.feature.earnings.vm.EarningsViewModel
import org.junit.Assert.assertEquals
import org.junit.Test

class EarningsPeriodTest {
    private val summary = EarningsSummaryDto(
        todayEarnings = "120.00", todayDeliveries = 3,
        weekEarnings = "860.50", weekDeliveries = 21,
        monthEarnings = "3200.00", monthDeliveries = 80,
        lifetimeDeliveries = 412, walletBalance = "500.00", codCashInHand = "200.00",
    )

    @Test fun `period wire values match the API`() {
        assertEquals(listOf("today", "week", "month", "all"), EarningsPeriod.entries.map { it.wire })
        assertEquals(EarningsPeriod.MONTH, EarningsPeriod.fromWire("month"))
        assertEquals(EarningsPeriod.WEEK, EarningsPeriod.fromWire("garbage"))
    }

    @Test fun `summary maps to the selected period`() {
        assertEquals(PeriodTotals("120.00", 3), summary.totalsFor(EarningsPeriod.TODAY))
        assertEquals(PeriodTotals("860.50", 21), summary.totalsFor(EarningsPeriod.WEEK))
        assertEquals(PeriodTotals("3200.00", 80), summary.totalsFor(EarningsPeriod.MONTH))
    }

    @Test fun `all-time totals sum the entries excluding COD deposits and use lifetime deliveries`() {
        val entries = listOf(
            RiderEarningDto("1", "delivery", "40.00"), RiderEarningDto("2", "distance", "12.50"),
            RiderEarningDto("3", "cod_deposit", "-300.00"), RiderEarningDto("4", "penalty", "-10.00"), RiderEarningDto("5", "tip", "20.00"),
        )
        val totals = EarningsViewModel.totals(RiderEarningsDto(summary, entries), EarningsPeriod.ALL)
        assertEquals("62.50", totals.earnings)
        assertEquals(412, totals.deliveries)
        // non-ALL periods ignore the entries list
        assertEquals(PeriodTotals("120.00", 3), EarningsViewModel.totals(RiderEarningsDto(summary, entries), EarningsPeriod.TODAY))
    }
}
