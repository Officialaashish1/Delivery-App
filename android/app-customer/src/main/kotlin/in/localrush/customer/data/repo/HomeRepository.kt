package `in`.localrush.customer.data.repo

import `in`.localrush.core.common.result.AppResult
import `in`.localrush.core.common.result.Result
import `in`.localrush.core.database.CachedJsonDao
import `in`.localrush.core.database.CachedJsonEntity
import `in`.localrush.core.network.util.apiCall
import `in`.localrush.customer.data.api.CustomerApi
import `in`.localrush.customer.data.dto.HomeDto
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Home feed with a Room JSON cache so the screen renders instantly (and offline) from the last payload. */
@Singleton
class HomeRepository @Inject constructor(
    private val api: CustomerApi,
    private val cache: CachedJsonDao,
    private val json: Json,
) {
    suspend fun cached(lat: Double?, lng: Double?): HomeDto? =
        cache.get(key(lat, lng))?.let { runCatching { json.decodeFromString(HomeDto.serializer(), it.payload) }.getOrNull() }

    suspend fun fetch(lat: Double?, lng: Double?, city: String?): AppResult<HomeDto> {
        val result = apiCall { api.home(lat, lng, city?.takeIf { it.isNotBlank() }) }
        if (result is Result.Ok) {
            runCatching { cache.put(CachedJsonEntity(key(lat, lng), json.encodeToString(HomeDto.serializer(), result.value), System.currentTimeMillis())) }
        }
        return result
    }

    private fun key(lat: Double?, lng: Double?) = "home:" + if (lat != null && lng != null) "%.2f,%.2f".format(java.util.Locale.ROOT, lat, lng) else "none"
}
