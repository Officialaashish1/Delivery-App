package `in`.localrush.customer.data.api

import `in`.localrush.core.network.dto.PageDto
import `in`.localrush.customer.data.dto.AddToCartDto
import `in`.localrush.customer.data.dto.AvailableCouponDto
import `in`.localrush.customer.data.dto.CartDto
import `in`.localrush.customer.data.dto.CartSettingsDto
import `in`.localrush.customer.data.dto.CategoryDto
import `in`.localrush.customer.data.dto.CuisineDto
import `in`.localrush.customer.data.dto.HomeDto
import `in`.localrush.customer.data.dto.MenuItemDto
import `in`.localrush.customer.data.dto.ProfileDto
import `in`.localrush.customer.data.dto.ProfilePatchDto
import `in`.localrush.customer.data.dto.ReorderResponseDto
import `in`.localrush.customer.data.dto.RestaurantCardDto
import `in`.localrush.customer.data.dto.RestaurantDetailDto
import `in`.localrush.customer.data.dto.RetryPaymentResponseDto
import `in`.localrush.customer.data.dto.SearchResultDto
import `in`.localrush.customer.data.dto.UpdateCartItemDto
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

private const val NO_AUTH = "X-No-Auth: true"

/** Customer-only endpoints that are not part of core:network (discovery, menu, cart, coupons, profile). */
interface CustomerApi {
    // ---- discovery (public; auth header still sent when logged in so recently_ordered/recommended come back) ----
    @GET("restaurants/home/")
    suspend fun home(@Query("lat") lat: Double?, @Query("lng") lng: Double?, @Query("city") city: String? = null): HomeDto

    @GET("restaurants/")
    suspend fun restaurants(
        @Query("lat") lat: Double?, @Query("lng") lng: Double?,
        @Query("q") q: String? = null, @Query("cuisine") cuisine: String? = null, @Query("category") category: String? = null,
        @Query("veg") veg: Boolean? = null, @Query("min_rating") minRating: Double? = null, @Query("open_only") openOnly: Boolean? = null,
        @Query("sort") sort: String? = null, @Query("page") page: Int? = null, @Query("page_size") pageSize: Int? = null,
    ): PageDto<RestaurantCardDto>

    @GET("restaurants/search/")
    suspend fun search(@Query("q") q: String, @Query("lat") lat: Double?, @Query("lng") lng: Double?): SearchResultDto

    @GET("restaurants/{slug}/")
    suspend fun restaurant(@Path("slug") slug: String, @Query("lat") lat: Double?, @Query("lng") lng: Double?): RestaurantDetailDto

    @GET("menu/items/{id}/")
    suspend fun menuItem(@Path("id") id: String): MenuItemDto

    @Headers(NO_AUTH) @GET("restaurants/categories/")
    suspend fun categories(): List<CategoryDto>

    @Headers(NO_AUTH) @GET("restaurants/cuisines/")
    suspend fun cuisines(): List<CuisineDto>

    // ---- cart ----
    @GET("cart/") suspend fun cart(): CartDto
    @POST("cart/") suspend fun addToCart(@Body body: AddToCartDto): CartDto
    @PATCH("cart/") suspend fun patchCart(@Body body: CartSettingsDto): CartDto
    @DELETE("cart/") suspend fun clearCart(): Response<Unit>
    @PATCH("cart/items/{id}/") suspend fun updateCartItem(@Path("id") id: String, @Body body: UpdateCartItemDto): CartDto
    @DELETE("cart/items/{id}/") suspend fun removeCartItem(@Path("id") id: String): CartDto

    // ---- coupons ----
    @GET("coupons/")
    suspend fun coupons(@Query("restaurant") restaurantId: String?, @Query("subtotal") subtotal: String?): List<AvailableCouponDto>

    // ---- orders extras ----
    @POST("orders/{id}/reorder/") suspend fun reorder(@Path("id") id: String): ReorderResponseDto
    @GET("orders/{id}/invoice/") suspend fun invoice(@Path("id") id: String): ResponseBody
    @POST("payments/orders/{id}/retry/") suspend fun retryPayment(@Path("id") id: String): RetryPaymentResponseDto

    // ---- profile ----
    @GET("customers/profile/") suspend fun profile(): ProfileDto
    @PATCH("customers/profile/") suspend fun patchProfile(@Body body: ProfilePatchDto): ProfileDto
}
