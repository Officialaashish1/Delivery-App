package `in`.localrush.customer

import `in`.localrush.core.common.money.Money
import `in`.localrush.core.common.money.toMoney
import `in`.localrush.core.network.dto.OrderPricingDto
import `in`.localrush.customer.feature.cart.vm.CartViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Bill display derived from `pricing` exactly as the backend computes it. */
class CartPricingTest {
    private val full = OrderPricingDto(
        itemTotal = "480.00", packagingCharge = "20.00", deliveryFee = "35.00", deliveryDiscount = "0.00", platformFee = "5.00",
        taxPercent = "5.00", taxAmount = "25.00", couponCode = "SAVE50", couponDiscount = "50.00", total = "515.00",
        distanceKm = "3.20", minOrderAmount = "99.00", meetsMinOrder = true,
    )

    @Test fun `bill rows sum to the backend total`() {
        val lines = CartViewModel.billLines(full)
        assertEquals(full.total.toMoney(), CartViewModel.totalOf(lines))
    }

    @Test fun `zero optional charges are hidden but delivery fee is always shown`() {
        val p = OrderPricingDto(itemTotal = "200.00", deliveryFee = "0.00", total = "200.00")
        val keys = CartViewModel.billLines(p).map { it.key }
        assertEquals(listOf(CartViewModel.KEY_ITEM_TOTAL, CartViewModel.KEY_DELIVERY), keys)
    }

    @Test fun `discounts are negative rows carrying the coupon code`() {
        val coupon = CartViewModel.billLines(full).first { it.key == CartViewModel.KEY_COUPON }
        assertTrue(coupon.isDiscount)
        assertEquals(Money.ofRupees(-50), coupon.amount)
        assertEquals("SAVE50", coupon.extra)
    }

    @Test fun `free delivery via coupon shows a delivery discount row`() {
        val p = full.copy(deliveryDiscount = "35.00", couponDiscount = "0.00", couponCode = "FREEDEL", total = "530.00")
        val lines = CartViewModel.billLines(p)
        assertTrue(lines.any { it.key == CartViewModel.KEY_DELIVERY_DISCOUNT && it.amount == Money.ofRupees(-35) })
        assertFalse(lines.any { it.key == CartViewModel.KEY_COUPON })
        assertEquals(p.total.toMoney(), CartViewModel.totalOf(lines))
    }

    @Test fun `tax row carries the trimmed percent`() {
        val tax = CartViewModel.billLines(full).first { it.key == CartViewModel.KEY_TAX }
        assertEquals("5", tax.extra)
    }

    @Test fun `savings adds coupon and delivery discounts`() {
        assertEquals(Money.ofRupees(85), CartViewModel.savings(full.copy(deliveryDiscount = "35.00")))
    }
}
