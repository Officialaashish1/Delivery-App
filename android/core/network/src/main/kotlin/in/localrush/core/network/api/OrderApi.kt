package `in`.localrush.core.network.api

import `in`.localrush.core.network.dto.OrderDetailDto
import `in`.localrush.core.network.dto.OrderListDto
import `in`.localrush.core.network.dto.PageDto
import `in`.localrush.core.network.dto.PaymentVerifyDto
import `in`.localrush.core.network.dto.PlaceOrderDto
import `in`.localrush.core.network.dto.PlaceOrderResponseDto
import `in`.localrush.core.network.dto.ReasonRequest
import `in`.localrush.core.network.dto.RiderContactDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Customer-side orders & payments. Rider/partner order endpoints live in their feature modules. */
interface OrderApi {
    @GET("orders/")
    suspend fun list(@Query("scope") scope: String? = null, @Query("page") page: Int? = null, @Query("page_size") pageSize: Int? = null): PageDto<OrderListDto>

    @GET("orders/{id}/")
    suspend fun detail(@Path("id") id: String): OrderDetailDto

    @POST("orders/")
    suspend fun place(@Body body: PlaceOrderDto): PlaceOrderResponseDto

    @POST("orders/{id}/cancel/")
    suspend fun cancel(@Path("id") id: String, @Body body: ReasonRequest): OrderDetailDto

    @POST("orders/{id}/reorder/")
    suspend fun reorder(@Path("id") id: String): kotlinx.serialization.json.JsonObject

    @GET("orders/{id}/rider-contact/")
    suspend fun riderContact(@Path("id") id: String): RiderContactDto

    @POST("payments/verify/")
    suspend fun verifyPayment(@Body body: PaymentVerifyDto): OrderDetailDto

    @POST("payments/orders/{id}/retry/")
    suspend fun retryPayment(@Path("id") id: String): PlaceOrderResponseDto
}
