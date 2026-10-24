package `in`.localrush.partner.feature.menu.vm

import `in`.localrush.core.common.money.Money
import `in`.localrush.partner.data.AddonGroupDto
import `in`.localrush.partner.data.AddonOptionDto
import `in`.localrush.partner.data.MenuItemDto
import `in`.localrush.partner.data.MenuItemWriteDto
import `in`.localrush.partner.data.VariantGroupDto
import `in`.localrush.partner.data.VariantOptionDto

/** Editable form model for a menu item. All amounts are raw text so the user can type freely; validation parses them. */
data class ItemForm(
    val categoryId: String? = null,
    val name: String = "",
    val description: String = "",
    val foodType: String = "veg",
    val price: String = "",
    val discountedPrice: String = "",
    val packagingCharge: String = "0",
    val preparationMinutes: String = "15",
    val isAvailable: Boolean = true,
    val stockLimited: Boolean = false,
    val stockQuantity: String = "",
    val isRecommended: Boolean = false,
    val isBestseller: Boolean = false,
    val spiceLevel: Int = 0,
    val tags: String = "",
    val variantGroups: List<VariantGroupForm> = emptyList(),
    val addonGroups: List<AddonGroupForm> = emptyList(),
) {
    companion object {
        fun from(item: MenuItemDto) = ItemForm(
            categoryId = item.category, name = item.name, description = item.description, foodType = item.foodType,
            price = Money.parse(item.price).rupees.stripTrailingZeros().toPlainString(),
            discountedPrice = item.discountedPrice?.let { Money.parse(it).rupees.stripTrailingZeros().toPlainString() } ?: "",
            packagingCharge = Money.parse(item.packagingCharge).rupees.stripTrailingZeros().toPlainString(),
            preparationMinutes = item.preparationMinutes.toString(), isAvailable = item.isAvailable,
            stockLimited = item.stockQuantity != null, stockQuantity = item.stockQuantity?.toString() ?: "",
            isRecommended = item.isRecommended, isBestseller = item.isBestseller, spiceLevel = item.spiceLevel, tags = item.tags.joinToString(", "),
            variantGroups = item.variantGroups.map { g -> VariantGroupForm(g.name, g.isRequired, g.options.map { VariantOptionForm(it.name, Money.parse(it.price).rupees.stripTrailingZeros().toPlainString(), it.isDefault, it.isAvailable) }) },
            addonGroups = item.addonGroups.map { g -> AddonGroupForm(g.name, g.minSelect.toString(), g.maxSelect.toString(), g.options.map { AddonOptionForm(it.name, Money.parse(it.price).rupees.stripTrailingZeros().toPlainString(), it.foodType, it.isAvailable) }) },
        )
    }
}

data class VariantOptionForm(val name: String = "", val price: String = "", val isDefault: Boolean = false, val isAvailable: Boolean = true)
data class VariantGroupForm(val name: String = "", val isRequired: Boolean = true, val options: List<VariantOptionForm> = listOf(VariantOptionForm()))
data class AddonOptionForm(val name: String = "", val price: String = "", val foodType: String = "veg", val isAvailable: Boolean = true)
data class AddonGroupForm(val name: String = "", val minSelect: String = "0", val maxSelect: String = "1", val options: List<AddonOptionForm> = listOf(AddonOptionForm()))

/** Error keys for [ItemEditorValidator]; group/option errors are keyed `variant.<g>`, `variant.<g>.<o>`, `addon.<g>`, `addon.<g>.<o>`. */
object ItemErrors {
    const val CATEGORY = "category"; const val NAME = "name"; const val PRICE = "price"; const val DISCOUNT = "discounted_price"
    const val PACKAGING = "packaging_charge"; const val PREP = "preparation_minutes"; const val STOCK = "stock_quantity"
    const val NAME_REQUIRED = "required"; const val INVALID_AMOUNT = "amount"; const val DISCOUNT_NOT_LOWER = "discount_not_lower"
    const val PREP_RANGE = "prep_range"; const val STOCK_INVALID = "stock"; const val MIN_GT_MAX = "min_gt_max"; const val MAX_GT_OPTIONS = "max_gt_options"
    const val NO_OPTIONS = "no_options"; const val MULTIPLE_DEFAULTS = "multiple_defaults"
}

/** Pure validation + mapping for the item editor. Mirrors the backend rules (discount < price, min_select <= max_select). */
object ItemEditorValidator {
    fun parseAmount(raw: String): Money? = raw.trim().takeIf { it.isNotEmpty() }?.toBigDecimalOrNull()?.takeIf { it.signum() >= 0 }?.let { Money.ofRupees(it) }

    fun validate(form: ItemForm): Map<String, String> = buildMap {
        if (form.categoryId.isNullOrBlank()) put(ItemErrors.CATEGORY, ItemErrors.NAME_REQUIRED)
        if (form.name.trim().length < 2) put(ItemErrors.NAME, ItemErrors.NAME_REQUIRED)
        val price = parseAmount(form.price)
        if (price == null || price.isZero) put(ItemErrors.PRICE, ItemErrors.INVALID_AMOUNT)
        if (form.discountedPrice.isNotBlank()) {
            val d = parseAmount(form.discountedPrice)
            if (d == null) put(ItemErrors.DISCOUNT, ItemErrors.INVALID_AMOUNT)
            else if (price != null && d >= price) put(ItemErrors.DISCOUNT, ItemErrors.DISCOUNT_NOT_LOWER)
        }
        if (parseAmount(form.packagingCharge.ifBlank { "0" }) == null) put(ItemErrors.PACKAGING, ItemErrors.INVALID_AMOUNT)
        val prep = form.preparationMinutes.toIntOrNull()
        if (prep == null || prep < 1 || prep > 240) put(ItemErrors.PREP, ItemErrors.PREP_RANGE)
        if (form.stockLimited) {
            val q = form.stockQuantity.toIntOrNull()
            if (q == null || q < 0) put(ItemErrors.STOCK, ItemErrors.STOCK_INVALID)
        }
        form.variantGroups.forEachIndexed { gi, g ->
            if (g.name.isBlank()) put("variant.$gi", ItemErrors.NAME_REQUIRED)
            val real = g.options.filter { it.name.isNotBlank() || it.price.isNotBlank() }
            if (real.isEmpty()) put("variant.$gi", ItemErrors.NO_OPTIONS)
            if (real.count { it.isDefault } > 1) put("variant.$gi", ItemErrors.MULTIPLE_DEFAULTS)
            g.options.forEachIndexed { oi, o ->
                if (o.name.isBlank() && o.price.isNotBlank()) put("variant.$gi.$oi", ItemErrors.NAME_REQUIRED)
                if (o.name.isNotBlank() && parseAmount(o.price.ifBlank { "0" }) == null) put("variant.$gi.$oi", ItemErrors.INVALID_AMOUNT)
            }
        }
        form.addonGroups.forEachIndexed { gi, g ->
            if (g.name.isBlank()) put("addon.$gi", ItemErrors.NAME_REQUIRED)
            val real = g.options.filter { it.name.isNotBlank() || it.price.isNotBlank() }
            val min = g.minSelect.toIntOrNull(); val max = g.maxSelect.toIntOrNull()
            when {
                real.isEmpty() -> put("addon.$gi", ItemErrors.NO_OPTIONS)
                min == null || max == null || min < 0 || max < 1 -> put("addon.$gi", ItemErrors.INVALID_AMOUNT)
                min > max -> put("addon.$gi", ItemErrors.MIN_GT_MAX)
                max > real.size -> put("addon.$gi", ItemErrors.MAX_GT_OPTIONS)
            }
            g.options.forEachIndexed { oi, o ->
                if (o.name.isBlank() && o.price.isNotBlank()) put("addon.$gi.$oi", ItemErrors.NAME_REQUIRED)
                if (o.name.isNotBlank() && parseAmount(o.price.ifBlank { "0" }) == null) put("addon.$gi.$oi", ItemErrors.INVALID_AMOUNT)
            }
        }
    }

    /** Call only after [validate] returned no errors. */
    fun toWrite(form: ItemForm): MenuItemWriteDto = MenuItemWriteDto(
        category = form.categoryId!!,
        name = form.name.trim(),
        description = form.description.trim(),
        foodType = form.foodType,
        price = parseAmount(form.price)!!.toApiString(),
        discountedPrice = form.discountedPrice.takeIf { it.isNotBlank() }?.let { parseAmount(it)!!.toApiString() },
        packagingCharge = parseAmount(form.packagingCharge.ifBlank { "0" })!!.toApiString(),
        preparationMinutes = form.preparationMinutes.toInt(),
        isAvailable = form.isAvailable,
        stockQuantity = if (form.stockLimited) form.stockQuantity.toInt() else null,
        isRecommended = form.isRecommended,
        isBestseller = form.isBestseller,
        spiceLevel = form.spiceLevel,
        tags = form.tags.split(',').map { it.trim() }.filter { it.isNotEmpty() },
        variantGroups = form.variantGroups.mapIndexed { gi, g ->
            VariantGroupDto(
                name = g.name.trim(), isRequired = g.isRequired, sortOrder = gi,
                options = g.options.filter { it.name.isNotBlank() }.mapIndexed { oi, o -> VariantOptionDto(name = o.name.trim(), price = parseAmount(o.price.ifBlank { "0" })!!.toApiString(), isAvailable = o.isAvailable, isDefault = o.isDefault, sortOrder = oi) },
            )
        },
        addonGroups = form.addonGroups.mapIndexed { gi, g ->
            AddonGroupDto(
                name = g.name.trim(), minSelect = g.minSelect.toInt(), maxSelect = g.maxSelect.toInt(), sortOrder = gi,
                options = g.options.filter { it.name.isNotBlank() }.mapIndexed { oi, o -> AddonOptionDto(name = o.name.trim(), price = parseAmount(o.price.ifBlank { "0" })!!.toApiString(), foodType = o.foodType, isAvailable = o.isAvailable, sortOrder = oi) },
            )
        },
    )
}
