package `in`.localrush.partner.data

import `in`.localrush.core.network.dto.PageDto
import kotlinx.serialization.json.JsonObject
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.PartMap
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/** Partner-only endpoints (`partner/...`, `reviews/partner/...`, `restaurants/cuisines|categories`). */
interface PartnerApi {

    // ---- lookups
    @Headers("X-No-Auth: true") @GET("restaurants/cuisines/")
    suspend fun cuisines(): List<CuisineDto>

    @Headers("X-No-Auth: true") @GET("restaurants/categories/")
    suspend fun restaurantCategories(): List<RestaurantCategoryDto>

    // ---- restaurant
    @GET("partner/restaurant/")
    suspend fun restaurant(): PartnerRestaurantDto

    /** Register. Multipart so logo/cover can be sent in the same request; list fields repeat the key. */
    @Multipart @POST("partner/restaurant/")
    suspend fun register(
        @PartMap fields: Map<String, @JvmSuppressWildcards RequestBody>,
        @Part listParts: List<MultipartBody.Part>,
        @Part logo: MultipartBody.Part? = null,
        @Part cover: MultipartBody.Part? = null,
    ): PartnerRestaurantDto

    @PATCH("partner/restaurant/")
    suspend fun patchRestaurant(@Body body: RestaurantPatchDto): PartnerRestaurantDto

    /** Image-only PATCH (logo and/or cover). */
    @Multipart @PATCH("partner/restaurant/")
    suspend fun patchRestaurantImages(@Part logo: MultipartBody.Part? = null, @Part cover: MultipartBody.Part? = null): PartnerRestaurantDto

    @POST("partner/restaurant/toggle/")
    suspend fun toggle(@Body body: ToggleDto): PartnerRestaurantDto

    @PUT("partner/restaurant/hours/")
    suspend fun putHours(@Body body: HoursBulkDto): List<RestaurantHoursDto>

    @GET("partner/holidays/")
    suspend fun holidays(): List<HolidayDto>

    @POST("partner/holidays/")
    suspend fun addHoliday(@Body body: HolidayDto): HolidayDto

    @DELETE("partner/holidays/{id}/")
    suspend fun deleteHoliday(@Path("id") id: Int): Response<Unit>

    @GET("partner/documents/")
    suspend fun documents(): List<RestaurantDocumentDto>

    @Multipart @POST("partner/documents/")
    suspend fun uploadDocument(
        @Part("doc_type") docType: RequestBody,
        @Part("number") number: RequestBody,
        @Part file: MultipartBody.Part,
    ): RestaurantDocumentDto

    @DELETE("partner/documents/{id}/")
    suspend fun deleteDocument(@Path("id") id: String): Response<Unit>

    @GET("partner/dashboard/")
    suspend fun dashboard(): DashboardDto

    @GET("partner/best-sellers/")
    suspend fun bestSellers(): List<MenuItemDto>

    // ---- menu
    @GET("partner/menu/categories/")
    suspend fun menuCategories(): List<MenuCategoryDto>

    @POST("partner/menu/categories/")
    suspend fun createCategory(@Body body: MenuCategoryWriteDto): MenuCategoryDto

    @PATCH("partner/menu/categories/{id}/")
    suspend fun updateCategory(@Path("id") id: String, @Body body: MenuCategoryWriteDto): MenuCategoryDto

    @DELETE("partner/menu/categories/{id}/")
    suspend fun deleteCategory(@Path("id") id: String): Response<Unit>

    @POST("partner/menu/categories/reorder/")
    suspend fun reorderCategories(@Body body: `in`.localrush.core.network.dto.IdsRequest): List<MenuCategoryDto>

    @GET("partner/menu/items/")
    suspend fun menuItems(
        @Query("q") query: String? = null,
        @Query("category") category: String? = null,
        @Query("is_available") isAvailable: Boolean? = null,
        @Query("food_type") foodType: String? = null,
        @Query("page") page: Int? = null,
        @Query("page_size") pageSize: Int? = 100,
    ): PageDto<MenuItemDto>

    @GET("partner/menu/items/{id}/")
    suspend fun menuItem(@Path("id") id: String): MenuItemDto

    @POST("partner/menu/items/")
    suspend fun createItem(@Body body: JsonObject): MenuItemDto

    @PATCH("partner/menu/items/{id}/")
    suspend fun updateItem(@Path("id") id: String, @Body body: JsonObject): MenuItemDto

    @Multipart @PATCH("partner/menu/items/{id}/")
    suspend fun updateItemImage(@Path("id") id: String, @Part image: MultipartBody.Part): MenuItemDto

    @DELETE("partner/menu/items/{id}/")
    suspend fun deleteItem(@Path("id") id: String): Response<Unit>

    @POST("partner/menu/items/{id}/availability/")
    suspend fun setAvailability(@Path("id") id: String, @Body body: AvailabilityDto): MenuItemDto

    @POST("partner/menu/items/bulk_availability/")
    suspend fun bulkAvailability(@Body body: BulkAvailabilityDto): BulkAvailabilityResultDto

    // ---- orders
    @GET("partner/orders/")
    suspend fun orders(
        @Query("scope") scope: String? = null,
        @Query("status") status: String? = null,
        @Query("page") page: Int? = null,
        @Query("page_size") pageSize: Int? = null,
    ): PageDto<PartnerOrderDto>

    @GET("partner/orders/{id}/")
    suspend fun order(@Path("id") id: String): PartnerOrderDto

    @POST("partner/orders/{id}/accept/")
    suspend fun accept(@Path("id") id: String, @Body body: AcceptOrderDto): PartnerOrderDto

    @POST("partner/orders/{id}/reject/")
    suspend fun reject(@Path("id") id: String, @Body body: `in`.localrush.core.network.dto.ReasonRequest): PartnerOrderDto

    @POST("partner/orders/{id}/preparing/")
    suspend fun preparing(@Path("id") id: String): PartnerOrderDto

    @POST("partner/orders/{id}/ready/")
    suspend fun ready(@Path("id") id: String): PartnerOrderDto

    @POST("partner/orders/{id}/cancel/")
    suspend fun cancel(@Path("id") id: String, @Body body: `in`.localrush.core.network.dto.ReasonRequest): PartnerOrderDto

    // ---- finance
    @GET("partner/finance/summary/")
    suspend fun financeSummary(@Query("date_from") dateFrom: String? = null, @Query("date_to") dateTo: String? = null): FinanceSummaryDto

    @GET("partner/finance/lines/")
    suspend fun financeLines(
        @Query("unsettled") unsettled: Int? = null,
        @Query("settlement") settlement: String? = null,
        @Query("page") page: Int? = null,
        @Query("page_size") pageSize: Int? = null,
    ): PageDto<SettlementLineDto>

    @GET("partner/finance/settlements/")
    suspend fun settlements(@Query("page") page: Int? = null): PageDto<SettlementDto>

    @Streaming @GET("partner/finance/statement.csv")
    suspend fun statementCsv(@Query("date_from") dateFrom: String? = null, @Query("date_to") dateTo: String? = null): ResponseBody
}
