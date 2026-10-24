package `in`.localrush.customer

import `in`.localrush.customer.data.dto.BannerDto
import `in`.localrush.customer.data.dto.CategoryDto
import `in`.localrush.customer.data.dto.CuisineDto
import `in`.localrush.customer.data.dto.HomeDto
import `in`.localrush.customer.data.dto.HomeSectionDto
import `in`.localrush.customer.data.dto.MenuItemDto
import `in`.localrush.customer.data.dto.RestaurantCardDto
import `in`.localrush.customer.feature.home.vm.HomeRow
import `in`.localrush.customer.feature.home.vm.HomeSectionMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSectionMapperTest {
    private fun r(id: String) = RestaurantCardDto(id = id, name = id, slug = id)
    private val home = HomeDto(
        banners = listOf(BannerDto(id = "b1", title = "50% off")),
        categories = listOf(CategoryDto("c1", "Biryani", "biryani")),
        cuisines = listOf(CuisineDto("cu1", "South Indian", "south-indian")),
        sections = listOf(
            HomeSectionDto("featured", "Featured", listOf(r("f1"))),
            HomeSectionDto("top_rated", "Top rated", emptyList()),
            HomeSectionDto("nearby", "Nearby restaurants", listOf(r("n1"), r("n2"))),
            HomeSectionDto("popular", "Popular near you", listOf(r("p1"))),
            HomeSectionDto("custom", "Chef's picks", listOf(r("x1"))),
        ),
        recentlyOrdered = listOf(r("rec1")),
        recommendedItems = listOf(MenuItemDto(id = "i1", name = "Dosa", restaurantSlug = "dosa-hub")),
    )

    @Test fun `guest feed follows the contract order, drops empty sections and hides personalised rows`() {
        val rows = HomeSectionMapper.map(home, loggedIn = false, recentTitle = "Recently ordered", recommendedTitle = "Recommended")
        val keys = rows.map { when (it) { is HomeRow.Banners -> "banners"; is HomeRow.Categories -> "categories"; HomeRow.NearbyPromo -> "promo"; is HomeRow.Restaurants -> it.key; is HomeRow.RecommendedItems -> "items"; is HomeRow.Cuisines -> "cuisines" } }
        assertEquals(listOf("banners", "categories", "promo", "nearby", "popular", "featured", "custom", "cuisines"), keys)
        assertFalse(rows.any { it is HomeRow.RecommendedItems })
    }

    @Test fun `logged-in feed inserts recently ordered after the first section and recommended items before cuisines`() {
        val rows = HomeSectionMapper.map(home, loggedIn = true, recentTitle = "Recently ordered", recommendedTitle = "Recommended")
        val restaurantKeys = rows.filterIsInstance<HomeRow.Restaurants>().map { it.key }
        assertEquals(listOf("nearby", HomeSectionMapper.KEY_RECENT, "popular", "featured", "custom"), restaurantKeys)
        val recent = rows.filterIsInstance<HomeRow.Restaurants>().first { it.key == HomeSectionMapper.KEY_RECENT }
        assertEquals("Recently ordered", recent.title)
        assertEquals(rows.size - 2, rows.indexOfFirst { it is HomeRow.RecommendedItems })
        assertTrue(rows.last() is HomeRow.Cuisines)
    }

    @Test fun `recently ordered still appears when there are no sections`() {
        val rows = HomeSectionMapper.map(home.copy(sections = emptyList()), loggedIn = true, recentTitle = "Recent", recommendedTitle = "Rec")
        assertTrue(rows.filterIsInstance<HomeRow.Restaurants>().single().key == HomeSectionMapper.KEY_RECENT)
    }

    @Test fun `empty payload is detected`() {
        val rows = HomeSectionMapper.map(HomeDto(), loggedIn = false, recentTitle = "", recommendedTitle = "")
        assertEquals(listOf<HomeRow>(HomeRow.NearbyPromo), rows)
        assertTrue(HomeSectionMapper.isEmpty(rows))
        assertFalse(HomeSectionMapper.isEmpty(HomeSectionMapper.map(home, false, "", "")))
    }
}
