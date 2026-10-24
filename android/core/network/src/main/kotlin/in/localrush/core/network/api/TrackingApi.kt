package `in`.localrush.core.network.api

import `in`.localrush.core.network.dto.ChatMessageDto
import `in`.localrush.core.network.dto.ChatSendDto
import `in`.localrush.core.network.dto.LocationAckDto
import `in`.localrush.core.network.dto.LocationBatchDto
import `in`.localrush.core.network.dto.LocationFixDto
import `in`.localrush.core.network.dto.RouteDto
import `in`.localrush.core.network.dto.TrackingSnapshotDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface TrackingApi {
    @GET("tracking/orders/{id}/")
    suspend fun snapshot(@Path("id") orderId: String): TrackingSnapshotDto

    @GET("tracking/orders/{id}/chat/")
    suspend fun chat(@Path("id") orderId: String): List<ChatMessageDto>

    @POST("tracking/orders/{id}/chat/")
    suspend fun sendChat(@Path("id") orderId: String, @Body body: ChatSendDto): ChatMessageDto

    @GET("tracking/route/")
    suspend fun route(
        @Query("from_lat") fromLat: Double, @Query("from_lng") fromLng: Double,
        @Query("to_lat") toLat: Double, @Query("to_lng") toLng: Double,
        @Query("steps") steps: Int? = null,
    ): RouteDto

    @POST("rider/location/")
    suspend fun postLocation(@Body body: LocationFixDto): LocationAckDto

    @POST("rider/location/")
    suspend fun postLocations(@Body body: LocationBatchDto): LocationAckDto
}
