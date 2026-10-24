package `in`.localrush.core.network.api

import `in`.localrush.core.network.dto.AppConfigDto
import `in`.localrush.core.network.dto.GeocodeResultDto
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

interface ConfigApi {
    @Headers("X-No-Auth: true") @GET("config/")
    suspend fun config(): AppConfigDto

    @GET("config/geocode/search/")
    suspend fun geocodeSearch(@Query("q") query: String, @Query("lat") lat: Double? = null, @Query("lng") lng: Double? = null): List<GeocodeResultDto>

    @GET("config/geocode/reverse/")
    suspend fun reverseGeocode(@Query("lat") lat: Double, @Query("lng") lng: Double): GeocodeResultDto
}
