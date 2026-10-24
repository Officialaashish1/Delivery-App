package `in`.localrush.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Paginated list envelope `{count, next, previous, results}`. */
@Serializable
data class PageDto<T>(
    val count: Int = 0,
    val next: String? = null,
    val previous: String? = null,
    val results: List<T> = emptyList(),
) {
    val hasNext: Boolean get() = next != null

    /** Page number encoded in [next] (`?page=3`), or null. */
    val nextPage: Int? get() = next?.substringAfter("page=", "")?.takeWhile { it.isDigit() }?.toIntOrNull()
}

/** `{latitude, longitude}` as emitted by backend `point_to_latlng`. */
@Serializable
data class LatLngDto(val latitude: Double, val longitude: Double)

@Serializable
data class MessageDto(val message: String? = null, val detail: String? = null)

/** Generic empty body for POST endpoints without parameters. */
@Serializable
class EmptyBody

@Serializable
data class IdsRequest(val ids: List<String>)

@Serializable
data class ReasonRequest(val reason: String)

/** Decimal fields arrive as strings ("249.00"); use `Money.parse`. */
typealias DecimalString = String

/** Raw passthrough for payloads the UI only needs to forward (e.g. Razorpay checkout, notification data). */
typealias RawJson = JsonObject

typealias AnyJson = JsonElement

@Serializable
data class ErrorEnvelopeDto(val error: ErrorBodyDto)

@Serializable
data class ErrorBodyDto(
    val code: String,
    val message: String? = null,
    val fields: Map<String, List<String>>? = null,
    @SerialName("attempts_left") val attemptsLeft: Int? = null,
)
