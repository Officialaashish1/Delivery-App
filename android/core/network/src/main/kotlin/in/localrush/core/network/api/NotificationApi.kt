package `in`.localrush.core.network.api

import `in`.localrush.core.network.dto.MarkReadDto
import `in`.localrush.core.network.dto.NotificationDto
import `in`.localrush.core.network.dto.PageDto
import `in`.localrush.core.network.dto.UnreadCountDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface NotificationApi {
    @GET("notifications/")
    suspend fun list(@Query("page") page: Int? = null, @Query("page_size") pageSize: Int? = null): PageDto<NotificationDto>

    @GET("notifications/unread-count/")
    suspend fun unreadCount(): UnreadCountDto

    @POST("notifications/mark-read/")
    suspend fun markRead(@Body body: MarkReadDto = MarkReadDto()): Response<Unit>

    @POST("notifications/clear/")
    suspend fun clear(): Response<Unit>
}
