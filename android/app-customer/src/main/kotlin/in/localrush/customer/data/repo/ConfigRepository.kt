package `in`.localrush.customer.data.repo

import `in`.localrush.core.common.result.AppResult
import `in`.localrush.core.network.api.ConfigApi
import `in`.localrush.core.network.dto.AppConfigDto
import `in`.localrush.core.network.dto.GeocodeResultDto
import `in`.localrush.core.network.util.apiCall
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** `GET config/` cached for the process lifetime (map style, COD rules, Razorpay key, support contact). */
@Singleton
class ConfigRepository @Inject constructor(private val api: ConfigApi) {
    private val _config = MutableStateFlow(AppConfigDto())
    /** Always usable: starts with contract defaults and is replaced once the network answers. */
    val config: StateFlow<AppConfigDto> = _config
    private var loaded = false
    private val mutex = Mutex()

    suspend fun load(force: Boolean = false): AppResult<AppConfigDto> = mutex.withLock {
        if (loaded && !force) return@withLock `in`.localrush.core.common.result.Result.Ok(_config.value)
        apiCall { api.config() }.onSuccess { _config.value = it; loaded = true }
    }

    suspend fun geocodeSearch(query: String, lat: Double?, lng: Double?): AppResult<List<GeocodeResultDto>> =
        apiCall { api.geocodeSearch(query, lat, lng) }

    suspend fun reverseGeocode(lat: Double, lng: Double): AppResult<GeocodeResultDto> = apiCall { api.reverseGeocode(lat, lng) }
}

/** "Flat 12, MG Road, Indiranagar, Bengaluru" from a geocode result. */
fun GeocodeResultDto.shortTitle(): String = listOf(name, road, locality).firstOrNull { it.isNotBlank() } ?: displayName.substringBefore(",")
fun GeocodeResultDto.addressLine(): String = displayName.ifBlank { listOf(road, locality, city, state, postcode).filter { it.isNotBlank() }.joinToString(", ") }
