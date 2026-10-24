package `in`.localrush.core.network.api

import `in`.localrush.core.network.dto.AddressDto
import `in`.localrush.core.network.dto.AddressWriteDto
import `in`.localrush.core.network.dto.CustomerProfileDto
import `in`.localrush.core.network.dto.PageDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface AddressApi {
    @GET("customers/addresses/")
    suspend fun list(@Query("page") page: Int? = null, @Query("page_size") pageSize: Int? = null): PageDto<AddressDto>

    @POST("customers/addresses/")
    suspend fun create(@Body body: AddressWriteDto): AddressDto

    @GET("customers/addresses/{id}/")
    suspend fun get(@Path("id") id: String): AddressDto

    @PATCH("customers/addresses/{id}/")
    suspend fun update(@Path("id") id: String, @Body body: AddressWriteDto): AddressDto

    @DELETE("customers/addresses/{id}/")
    suspend fun delete(@Path("id") id: String): Response<Unit>

    @GET("customers/profile/")
    suspend fun profile(): CustomerProfileDto
}
