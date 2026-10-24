package `in`.localrush.customer.data.dto

import `in`.localrush.core.network.dto.DecimalString
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `RestaurantListSerializer` — compact card used in lists, home sections, search and the cart. */
@Serializable
data class RestaurantCardDto(
    val id: String,
    val name: String = "",
    val slug: String = "",
    val logo: String? = null,
    @SerialName("cover_image") val coverImage: String? = null,
    val cuisines: List<String> = emptyList(),
    @SerialName("rating_avg") val ratingAvg: DecimalString = "0",
    @SerialName("rating_count") val ratingCount: Int = 0,
    @SerialName("cost_for_two") val costForTwo: DecimalString = "0",
    @SerialName("is_pure_veg") val isPureVeg: Boolean = false,
    @SerialName("is_promoted") val isPromoted: Boolean = false,
    @SerialName("is_featured") val isFeatured: Boolean = false,
    @SerialName("distance_km") val distanceKm: Double? = null,
    @SerialName("delivery_time_min") val deliveryTimeMin: Int = 0,
    @SerialName("delivery_fee") val deliveryFee: DecimalString = "0",
    @SerialName("min_order_amount") val minOrderAmount: DecimalString = "0",
    @SerialName("is_open_now") val isOpenNow: Boolean = true,
    @SerialName("offer_text") val offerText: String? = null,
    val city: String = "",
    @SerialName("address_line2") val addressLine2: String = "",
)

/** `RestaurantDetailSerializer` — card fields + hours, location and the grouped menu. */
@Serializable
data class RestaurantDetailDto(
    val id: String,
    val name: String = "",
    val slug: String = "",
    val logo: String? = null,
    @SerialName("cover_image") val coverImage: String? = null,
    val cuisines: List<String> = emptyList(),
    @SerialName("rating_avg") val ratingAvg: DecimalString = "0",
    @SerialName("rating_count") val ratingCount: Int = 0,
    @SerialName("cost_for_two") val costForTwo: DecimalString = "0",
    @SerialName("is_pure_veg") val isPureVeg: Boolean = false,
    @SerialName("is_promoted") val isPromoted: Boolean = false,
    @SerialName("is_featured") val isFeatured: Boolean = false,
    @SerialName("distance_km") val distanceKm: Double? = null,
    @SerialName("delivery_time_min") val deliveryTimeMin: Int = 0,
    @SerialName("delivery_fee") val deliveryFee: DecimalString = "0",
    @SerialName("min_order_amount") val minOrderAmount: DecimalString = "0",
    @SerialName("is_open_now") val isOpenNow: Boolean = true,
    @SerialName("offer_text") val offerText: String? = null,
    val city: String = "",
    @SerialName("address_line1") val addressLine1: String = "",
    @SerialName("address_line2") val addressLine2: String = "",
    val landmark: String = "",
    val description: String = "",
    @SerialName("restaurant_type") val restaurantType: String = "",
    val categories: List<CategoryDto> = emptyList(),
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    @SerialName("packaging_charge") val packagingCharge: DecimalString = "0",
    @SerialName("avg_preparation_minutes") val avgPreparationMinutes: Int = 0,
    val hours: List<RestaurantHoursDto> = emptyList(),
    @SerialName("fssai_number") val fssaiNumber: String = "",
    @SerialName("total_orders") val totalOrders: Int = 0,
    val menu: List<MenuCategoryDto> = emptyList(),
)

@Serializable
data class RestaurantHoursDto(
    val id: Int? = null,
    val weekday: Int = 0,
    @SerialName("open_time") val openTime: String? = null,
    @SerialName("close_time") val closeTime: String? = null,
    @SerialName("is_closed") val isClosed: Boolean = false,
)

@Serializable
data class MenuCategoryDto(
    val id: String,
    val name: String = "",
    val description: String = "",
    @SerialName("sort_order") val sortOrder: Int = 0,
    val items: List<MenuItemDto> = emptyList(),
)

/** `MenuItemSerializer` — customer-facing item with its customisation groups. */
@Serializable
data class MenuItemDto(
    val id: String,
    @SerialName("restaurant_id") val restaurantId: String? = null,
    @SerialName("restaurant_name") val restaurantName: String = "",
    @SerialName("restaurant_slug") val restaurantSlug: String = "",
    val category: String? = null,
    @SerialName("category_name") val categoryName: String = "",
    val name: String = "",
    val description: String = "",
    val image: String? = null,
    @SerialName("food_type") val foodType: String = "veg",
    val price: DecimalString = "0",
    @SerialName("discounted_price") val discountedPrice: DecimalString? = null,
    @SerialName("effective_price") val effectivePrice: DecimalString = "0",
    @SerialName("packaging_charge") val packagingCharge: DecimalString = "0",
    @SerialName("preparation_minutes") val preparationMinutes: Int = 0,
    @SerialName("is_available") val isAvailable: Boolean = true,
    @SerialName("in_stock") val inStock: Boolean = true,
    @SerialName("is_recommended") val isRecommended: Boolean = false,
    @SerialName("is_bestseller") val isBestseller: Boolean = false,
    @SerialName("spice_level") val spiceLevel: Int = 0,
    val calories: Int? = null,
    val tags: List<String> = emptyList(),
    @SerialName("rating_avg") val ratingAvg: DecimalString = "0",
    @SerialName("rating_count") val ratingCount: Int = 0,
    @SerialName("order_count") val orderCount: Int = 0,
    @SerialName("variant_groups") val variantGroups: List<VariantGroupDto> = emptyList(),
    @SerialName("addon_groups") val addonGroups: List<AddonGroupDto> = emptyList(),
)

@Serializable
data class VariantGroupDto(
    val id: String,
    val name: String = "",
    @SerialName("is_required") val isRequired: Boolean = true,
    @SerialName("sort_order") val sortOrder: Int = 0,
    val options: List<VariantDto> = emptyList(),
)

@Serializable
data class VariantDto(
    val id: String,
    val name: String = "",
    val price: DecimalString = "0",
    @SerialName("is_available") val isAvailable: Boolean = true,
    @SerialName("is_default") val isDefault: Boolean = false,
    @SerialName("sort_order") val sortOrder: Int = 0,
)

@Serializable
data class AddonGroupDto(
    val id: String,
    val name: String = "",
    @SerialName("min_select") val minSelect: Int = 0,
    @SerialName("max_select") val maxSelect: Int = 5,
    @SerialName("sort_order") val sortOrder: Int = 0,
    val options: List<AddonDto> = emptyList(),
)

@Serializable
data class AddonDto(
    val id: String,
    val name: String = "",
    val price: DecimalString = "0",
    @SerialName("food_type") val foodType: String = "veg",
    @SerialName("is_available") val isAvailable: Boolean = true,
    @SerialName("sort_order") val sortOrder: Int = 0,
)

@Serializable
data class BannerDto(
    val id: String,
    val title: String = "",
    val subtitle: String = "",
    val image: String? = null,
    val restaurant: String? = null,
    @SerialName("coupon_code") val couponCode: String = "",
    @SerialName("deep_link") val deepLink: String = "",
)

@Serializable
data class CategoryDto(val id: String, val name: String = "", val slug: String = "", val image: String? = null)

@Serializable
data class CuisineDto(val id: String, val name: String = "", val slug: String = "", val icon: String? = null)

/** `GET restaurants/home/` */
@Serializable
data class HomeDto(
    val banners: List<BannerDto> = emptyList(),
    val categories: List<CategoryDto> = emptyList(),
    val cuisines: List<CuisineDto> = emptyList(),
    val sections: List<HomeSectionDto> = emptyList(),
    @SerialName("recently_ordered") val recentlyOrdered: List<RestaurantCardDto> = emptyList(),
    @SerialName("recommended_items") val recommendedItems: List<MenuItemDto> = emptyList(),
)

@Serializable
data class HomeSectionDto(val key: String = "", val title: String = "", val restaurants: List<RestaurantCardDto> = emptyList())

/** `GET restaurants/search/` */
@Serializable
data class SearchResultDto(
    val restaurants: List<RestaurantCardDto> = emptyList(),
    val items: List<MenuItemDto> = emptyList(),
    val cuisines: List<CuisineDto> = emptyList(),
)
