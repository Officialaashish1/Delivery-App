package `in`.localrush.core.network.api

import `in`.localrush.core.network.dto.CreateReviewDto
import `in`.localrush.core.network.dto.PageDto
import `in`.localrush.core.network.dto.ReplyDto
import `in`.localrush.core.network.dto.ReviewDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ReviewApi {
    @POST("reviews/")
    suspend fun create(@Body body: CreateReviewDto): ReviewDto

    @GET("reviews/restaurant/")
    suspend fun forRestaurant(@Query("restaurant") restaurantId: String, @Query("page") page: Int? = null): PageDto<ReviewDto>

    @GET("reviews/mine/")
    suspend fun mine(@Query("page") page: Int? = null): PageDto<ReviewDto>

    @GET("reviews/partner/")
    suspend fun partner(@Query("page") page: Int? = null): PageDto<ReviewDto>

    @POST("reviews/partner/{id}/reply/")
    suspend fun partnerReply(@Path("id") id: String, @Body body: ReplyDto): ReviewDto
}
