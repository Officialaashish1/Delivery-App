package `in`.localrush.core.network.api

import `in`.localrush.core.network.dto.CreateTicketDto
import `in`.localrush.core.network.dto.FaqDto
import `in`.localrush.core.network.dto.PageDto
import `in`.localrush.core.network.dto.SupportContactDto
import `in`.localrush.core.network.dto.SupportTicketDto
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface SupportApi {
    @Headers("X-No-Auth: true") @GET("support/faqs/")
    suspend fun faqs(@Query("audience") audience: String? = null): List<FaqDto>

    @Headers("X-No-Auth: true") @GET("support/contact/")
    suspend fun contact(): SupportContactDto

    @GET("support/tickets/")
    suspend fun tickets(@Query("page") page: Int? = null): PageDto<SupportTicketDto>

    @POST("support/tickets/")
    suspend fun createTicket(@Body body: CreateTicketDto): SupportTicketDto

    @GET("support/tickets/{id}/")
    suspend fun ticket(@Path("id") id: String): SupportTicketDto

    @Multipart @POST("support/tickets/{id}/reply/")
    suspend fun reply(@Path("id") id: String, @Part("text") text: RequestBody, @Part attachment: MultipartBody.Part? = null): SupportTicketDto

    @POST("support/tickets/{id}/close/")
    suspend fun close(@Path("id") id: String): SupportTicketDto
}
