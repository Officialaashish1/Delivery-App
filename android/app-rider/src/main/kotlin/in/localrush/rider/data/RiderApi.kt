package `in`.localrush.rider.data

import `in`.localrush.core.network.dto.LocationAckDto
import `in`.localrush.core.network.dto.LocationBatchDto
import `in`.localrush.core.network.dto.LocationFixDto
import `in`.localrush.core.network.dto.PageDto
import `in`.localrush.core.network.dto.ReasonRequest
import `in`.localrush.core.network.dto.RouteDto
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.PartMap
import retrofit2.http.Path
import retrofit2.http.Query

/** Rider-only endpoints (riders/..., rider/location/). Shared endpoints live in core:network. */
interface RiderApi {
    // ---- profile & documents ----
    @GET("riders/profile/")
    suspend fun profile(): RiderProfileDto

    @PATCH("riders/profile/")
    suspend fun patchProfile(@Body body: RiderProfilePatchDto): RiderProfileDto

    @Multipart @PATCH("riders/profile/")
    suspend fun patchProfileMultipart(@PartMap fields: Map<String, @JvmSuppressWildcards RequestBody>, @Part photo: MultipartBody.Part?): RiderProfileDto

    @GET("riders/documents/")
    suspend fun documents(): List<RiderDocumentDto>

    @Multipart @POST("riders/documents/")
    suspend fun uploadDocument(@Part("doc_type") docType: RequestBody, @Part("number") number: RequestBody, @Part file: MultipartBody.Part): RiderDocumentDto

    @DELETE("riders/documents/{id}/")
    suspend fun deleteDocument(@Path("id") id: String): Response<Unit>

    // ---- availability & dashboard ----
    @POST("riders/online/")
    suspend fun setOnline(@Body body: OnlineToggleDto): RiderProfileDto

    @GET("riders/dashboard/")
    suspend fun dashboard(): RiderDashboardDto

    // ---- offers ----
    @GET("riders/offers/")
    suspend fun offers(@Query("page_size") pageSize: Int = 50): PageDto<RiderOfferDto>

    @GET("riders/offers/{id}/")
    suspend fun offer(@Path("id") id: String): RiderOfferDto

    /** Returns the accepted order (backend), not the offer as the OpenAPI schema claims. */
    @POST("riders/offers/{id}/accept/")
    suspend fun acceptOffer(@Path("id") id: String): RiderOrderDto

    /** 204 No Content on success. */
    @POST("riders/offers/{id}/reject/")
    suspend fun rejectOffer(@Path("id") id: String, @Body body: ReasonRequest): Response<Unit>

    // ---- orders ----
    @GET("riders/orders/")
    suspend fun orders(@Query("scope") scope: String? = null, @Query("page") page: Int? = null, @Query("page_size") pageSize: Int? = null): PageDto<RiderOrderDto>

    @GET("riders/orders/{id}/")
    suspend fun order(@Path("id") id: String): RiderOrderDto

    @POST("riders/orders/{id}/arrived-restaurant/")
    suspend fun arrivedAtRestaurant(@Path("id") id: String): RiderOrderDto

    @POST("riders/orders/{id}/pickup/")
    suspend fun pickup(@Path("id") id: String, @Body body: OtpActionDto): RiderOrderDto

    @Multipart @POST("riders/orders/{id}/deliver/")
    suspend fun deliver(@Path("id") id: String, @Part("otp") otp: RequestBody, @Part proof: MultipartBody.Part?): RiderOrderDto

    @POST("riders/orders/{id}/delivery-failed/")
    suspend fun deliveryFailed(@Path("id") id: String, @Body body: ReasonRequest): RiderOrderDto

    @GET("riders/orders/{id}/customer-contact/")
    suspend fun customerContact(@Path("id") id: String): CustomerContactDto

    @GET("riders/orders/{id}/route/")
    suspend fun route(@Path("id") id: String, @Query("lat") lat: Double, @Query("lng") lng: Double): RouteDto

    // ---- earnings & payouts ----
    @GET("riders/earnings/")
    suspend fun earnings(@Query("period") period: String): RiderEarningsDto

    @GET("riders/payouts/")
    suspend fun payouts(@Query("page") page: Int? = null, @Query("page_size") pageSize: Int? = null): PageDto<RiderPayoutDto>

    @POST("riders/payouts/request/")
    suspend fun requestPayout(@Body body: PayoutRequestDto): RiderPayoutDto

    // ---- location ----
    @POST("rider/location/")
    suspend fun postLocation(@Body body: LocationFixDto): LocationAckDto

    @POST("rider/location/")
    suspend fun postLocationBatch(@Body body: LocationBatchDto): LocationBatchResultDto
}
