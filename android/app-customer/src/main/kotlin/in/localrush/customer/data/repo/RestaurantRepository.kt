package `in`.localrush.customer.data.repo

import `in`.localrush.core.common.result.AppResult
import `in`.localrush.core.database.RecentSearchDao
import `in`.localrush.core.database.RecentSearchEntity
import `in`.localrush.core.network.api.ReviewApi
import `in`.localrush.core.network.dto.PageDto
import `in`.localrush.core.network.dto.ReviewDto
import `in`.localrush.core.network.util.apiCall
import `in`.localrush.customer.data.api.CustomerApi
import `in`.localrush.customer.data.dto.CategoryDto
import `in`.localrush.customer.data.dto.CuisineDto
import `in`.localrush.customer.data.dto.MenuItemDto
import `in`.localrush.customer.data.dto.RestaurantCardDto
import `in`.localrush.customer.data.dto.RestaurantDetailDto
import `in`.localrush.customer.data.dto.SearchResultDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class RestaurantSort(val wire: String) {
    RELEVANCE("relevance"), RATING("rating"), DELIVERY_TIME("delivery_time"), DISTANCE("distance"), COST_LOW("cost_low"), COST_HIGH("cost_high")
}

data class RestaurantFilters(
    val vegOnly: Boolean = false,
    val openNow: Boolean = false,
    val minRating: Double? = null,
    val sort: RestaurantSort = RestaurantSort.RELEVANCE,
) {
    val activeCount: Int get() = listOf(vegOnly, openNow, minRating != null, sort != RestaurantSort.RELEVANCE).count { it }
}

@Singleton
class RestaurantRepository @Inject constructor(
    private val api: CustomerApi,
    private val reviewApi: ReviewApi,
    private val recentSearches: RecentSearchDao,
) {
    suspend fun list(
        lat: Double?, lng: Double?, query: String? = null, cuisine: String? = null, category: String? = null,
        filters: RestaurantFilters = RestaurantFilters(), page: Int? = null,
    ): AppResult<PageDto<RestaurantCardDto>> = apiCall {
        api.restaurants(
            lat, lng, q = query?.takeIf { it.isNotBlank() }, cuisine = cuisine, category = category,
            veg = filters.vegOnly.takeIf { it }, minRating = filters.minRating, openOnly = filters.openNow.takeIf { it },
            sort = filters.sort.wire.takeIf { filters.sort != RestaurantSort.RELEVANCE }, page = page,
        )
    }

    suspend fun search(query: String, lat: Double?, lng: Double?): AppResult<SearchResultDto> = apiCall { api.search(query, lat, lng) }

    suspend fun detail(slug: String, lat: Double?, lng: Double?): AppResult<RestaurantDetailDto> = apiCall { api.restaurant(slug, lat, lng) }

    suspend fun menuItem(id: String): AppResult<MenuItemDto> = apiCall { api.menuItem(id) }

    suspend fun categories(): AppResult<List<CategoryDto>> = apiCall { api.categories() }
    suspend fun cuisines(): AppResult<List<CuisineDto>> = apiCall { api.cuisines() }

    suspend fun reviews(restaurantId: String, page: Int? = null): AppResult<PageDto<ReviewDto>> = apiCall { reviewApi.forRestaurant(restaurantId, page) }

    val recent: Flow<List<String>> = recentSearches.recent(8).map { list -> list.map { it.query } }
    suspend fun rememberSearch(query: String) { if (query.length >= 2) recentSearches.add(RecentSearchEntity(query.trim(), System.currentTimeMillis())) }
    suspend fun forgetSearch(query: String) = recentSearches.remove(query)
    suspend fun clearRecent() = recentSearches.clear()
}
