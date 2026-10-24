package `in`.localrush.customer.feature.home.vm

import `in`.localrush.customer.data.dto.HomeDto
import `in`.localrush.customer.data.dto.MenuItemDto
import `in`.localrush.customer.data.dto.RestaurantCardDto

/** One row of the home feed, in display order. Pure data so the mapper is unit-testable. */
sealed interface HomeRow {
    data class Banners(val banners: List<`in`.localrush.customer.data.dto.BannerDto>) : HomeRow
    data class Categories(val categories: List<`in`.localrush.customer.data.dto.CategoryDto>) : HomeRow
    data object NearbyPromo : HomeRow
    data class Restaurants(val key: String, val title: String, val restaurants: List<RestaurantCardDto>) : HomeRow
    data class RecommendedItems(val items: List<MenuItemDto>) : HomeRow
    data class Cuisines(val cuisines: List<`in`.localrush.customer.data.dto.CuisineDto>) : HomeRow
}

object HomeSectionMapper {
    const val KEY_RECENT = "recently_ordered"
    const val KEY_RECOMMENDED = "recommended"
    private val sectionOrder = listOf("nearby", "fast", "popular", "top_rated", "featured")

    /**
     * Builds the feed: banners → categories → nearby promo → sections (contract order, unknown keys last, empty
     * sections dropped) → recently ordered / recommended (logged in only) → cuisines.
     */
    fun map(home: HomeDto, loggedIn: Boolean, recentTitle: String, recommendedTitle: String): List<HomeRow> {
        val rows = mutableListOf<HomeRow>()
        if (home.banners.isNotEmpty()) rows += HomeRow.Banners(home.banners)
        if (home.categories.isNotEmpty()) rows += HomeRow.Categories(home.categories)
        rows += HomeRow.NearbyPromo
        val sections = home.sections.filter { it.restaurants.isNotEmpty() }
            .sortedBy { sectionOrder.indexOf(it.key).let { i -> if (i < 0) Int.MAX_VALUE else i } }
        sections.forEachIndexed { index, s ->
            rows += HomeRow.Restaurants(s.key, s.title, s.restaurants)
            // Recently ordered slots in after the first section so returning users see it early.
            if (index == 0 && loggedIn && home.recentlyOrdered.isNotEmpty()) rows += HomeRow.Restaurants(KEY_RECENT, recentTitle, home.recentlyOrdered)
        }
        if (sections.isEmpty() && loggedIn && home.recentlyOrdered.isNotEmpty()) rows += HomeRow.Restaurants(KEY_RECENT, recentTitle, home.recentlyOrdered)
        if (loggedIn && home.recommendedItems.isNotEmpty()) rows += HomeRow.RecommendedItems(home.recommendedItems)
        if (home.cuisines.isNotEmpty()) rows += HomeRow.Cuisines(home.cuisines)
        return rows
    }

    /** True when there is nothing to show besides the static promo. */
    fun isEmpty(rows: List<HomeRow>): Boolean = rows.all { it is HomeRow.NearbyPromo }
}
