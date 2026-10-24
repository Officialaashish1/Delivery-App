package `in`.localrush.partner.feature.menu.vm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemEditorValidatorTest {

    private val valid = ItemForm(categoryId = "cat", name = "Paneer Tikka", price = "249", packagingCharge = "10", preparationMinutes = "15")

    @Test
    fun `valid form has no errors`() {
        assertTrue(ItemEditorValidator.validate(valid).isEmpty())
    }

    @Test
    fun `required fields`() {
        val e = ItemEditorValidator.validate(ItemForm())
        assertEquals(ItemErrors.NAME_REQUIRED, e[ItemErrors.CATEGORY])
        assertEquals(ItemErrors.NAME_REQUIRED, e[ItemErrors.NAME])
        assertEquals(ItemErrors.INVALID_AMOUNT, e[ItemErrors.PRICE])
    }

    @Test
    fun `discount must be lower than price`() {
        assertEquals(ItemErrors.DISCOUNT_NOT_LOWER, ItemEditorValidator.validate(valid.copy(discountedPrice = "249"))[ItemErrors.DISCOUNT])
        assertEquals(ItemErrors.DISCOUNT_NOT_LOWER, ItemEditorValidator.validate(valid.copy(discountedPrice = "300"))[ItemErrors.DISCOUNT])
        assertNull(ItemEditorValidator.validate(valid.copy(discountedPrice = "199"))[ItemErrors.DISCOUNT])
        assertEquals(ItemErrors.INVALID_AMOUNT, ItemEditorValidator.validate(valid.copy(discountedPrice = "abc"))[ItemErrors.DISCOUNT])
    }

    @Test
    fun `price zero or negative is rejected`() {
        assertEquals(ItemErrors.INVALID_AMOUNT, ItemEditorValidator.validate(valid.copy(price = "0"))[ItemErrors.PRICE])
        assertEquals(ItemErrors.INVALID_AMOUNT, ItemEditorValidator.validate(valid.copy(price = "-5"))[ItemErrors.PRICE])
    }

    @Test
    fun `prep time and stock ranges`() {
        assertEquals(ItemErrors.PREP_RANGE, ItemEditorValidator.validate(valid.copy(preparationMinutes = "0"))[ItemErrors.PREP])
        assertEquals(ItemErrors.PREP_RANGE, ItemEditorValidator.validate(valid.copy(preparationMinutes = "500"))[ItemErrors.PREP])
        assertEquals(ItemErrors.STOCK_INVALID, ItemEditorValidator.validate(valid.copy(stockLimited = true, stockQuantity = ""))[ItemErrors.STOCK])
        assertNull(ItemEditorValidator.validate(valid.copy(stockLimited = true, stockQuantity = "12"))[ItemErrors.STOCK])
        assertNull(ItemEditorValidator.validate(valid.copy(stockLimited = false, stockQuantity = ""))[ItemErrors.STOCK])
    }

    @Test
    fun `add-on group min cannot exceed max and max cannot exceed option count`() {
        val group = AddonGroupForm(name = "Extras", minSelect = "2", maxSelect = "1", options = listOf(AddonOptionForm("Cheese", "20"), AddonOptionForm("Butter", "10")))
        assertEquals(ItemErrors.MIN_GT_MAX, ItemEditorValidator.validate(valid.copy(addonGroups = listOf(group)))["addon.0"])
        val tooMany = group.copy(minSelect = "0", maxSelect = "5")
        assertEquals(ItemErrors.MAX_GT_OPTIONS, ItemEditorValidator.validate(valid.copy(addonGroups = listOf(tooMany)))["addon.0"])
        val ok = group.copy(minSelect = "0", maxSelect = "2")
        assertNull(ItemEditorValidator.validate(valid.copy(addonGroups = listOf(ok)))["addon.0"])
        val empty = AddonGroupForm(name = "Empty")
        assertEquals(ItemErrors.NO_OPTIONS, ItemEditorValidator.validate(valid.copy(addonGroups = listOf(empty)))["addon.0"])
        val unnamed = ok.copy(name = "")
        assertEquals(ItemErrors.NAME_REQUIRED, ItemEditorValidator.validate(valid.copy(addonGroups = listOf(unnamed)))["addon.0"])
    }

    @Test
    fun `variant group needs options and at most one default`() {
        val twoDefaults = VariantGroupForm(name = "Size", options = listOf(VariantOptionForm("S", "0", isDefault = true), VariantOptionForm("L", "50", isDefault = true)))
        assertEquals(ItemErrors.MULTIPLE_DEFAULTS, ItemEditorValidator.validate(valid.copy(variantGroups = listOf(twoDefaults)))["variant.0"])
        val priceWithoutName = VariantGroupForm(name = "Size", options = listOf(VariantOptionForm("", "20")))
        val e = ItemEditorValidator.validate(valid.copy(variantGroups = listOf(priceWithoutName)))
        assertEquals(ItemErrors.NAME_REQUIRED, e["variant.0.0"])
    }

    @Test
    fun `write mapping produces backend wire values`() {
        val form = valid.copy(
            discountedPrice = "199.5", tags = "spicy, bestseller ,",
            variantGroups = listOf(VariantGroupForm("Size", true, listOf(VariantOptionForm("Half", "0", true), VariantOptionForm("Full", "80"), VariantOptionForm("", "")))),
            addonGroups = listOf(AddonGroupForm("Extras", "0", "2", listOf(AddonOptionForm("Cheese", "20", "veg"), AddonOptionForm("Egg", "15", "egg")))),
        )
        val w = ItemEditorValidator.toWrite(form)
        assertEquals("249.00", w.price)
        assertEquals("199.50", w.discountedPrice)
        assertEquals("10.00", w.packagingCharge)
        assertEquals(listOf("spicy", "bestseller"), w.tags)
        assertEquals(2, w.variantGroups.single().options.size) // blank option dropped
        assertEquals("80.00", w.variantGroups.single().options[1].price)
        assertEquals(1, w.variantGroups.single().options[1].sortOrder)
        assertEquals(2, w.addonGroups.single().maxSelect)
        assertEquals("egg", w.addonGroups.single().options[1].foodType)
        assertNull(w.stockQuantity)
    }

    @Test
    fun `form from dto round-trips amounts`() {
        val dto = `in`.localrush.partner.data.MenuItemDto(id = "i", category = "c", name = "X", price = "120.00", discountedPrice = "99.50", stockQuantity = 5)
        val f = ItemForm.from(dto)
        assertEquals("120", f.price)
        assertEquals("99.5", f.discountedPrice)
        assertTrue(f.stockLimited)
        assertEquals("5", f.stockQuantity)
    }
}
