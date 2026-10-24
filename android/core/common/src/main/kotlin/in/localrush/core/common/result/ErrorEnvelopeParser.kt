package `in`.localrush.core.common.result

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Parses the LocalRush error envelope: `{"error":{"code","message","fields":{"f":["msg"]}, ...extra}}`.
 * Tolerates DRF default shapes (`{"detail": "..."}`, `{"field": ["msg"]}`) as a fallback.
 */
object ErrorEnvelopeParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(httpStatus: Int, body: String?): AppError {
        if (body.isNullOrBlank()) return AppError.Http(httpStatus, defaultMessage(httpStatus))
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject
            ?: return AppError.Http(httpStatus, defaultMessage(httpStatus))

        val error = root["error"] as? JsonObject
        if (error != null) {
            val code = error["code"].str() ?: codeForStatus(httpStatus)
            val fields = parseFields(error["fields"])
            val message = error["message"].str()
                ?: fields.values.firstOrNull()?.firstOrNull()
                ?: defaultMessage(httpStatus)
            val extra = JsonObject(error.filterKeys { it !in setOf("code", "message", "fields") })
            return AppError.Api(code, message, httpStatus, fields, extra.takeIf { it.isNotEmpty() })
        }

        // DRF fallbacks
        val detail = root["detail"].str()
        val code = root["code"].str()
        if (detail != null) {
            return AppError.Api(code ?: codeForStatus(httpStatus), detail, httpStatus)
        }
        val fields = parseFields(root)
        if (fields.isNotEmpty()) {
            return AppError.Api(ErrorCodes.VALIDATION_ERROR, fields.values.first().first(), httpStatus, fields)
        }
        return AppError.Http(httpStatus, defaultMessage(httpStatus))
    }

    private fun parseFields(element: JsonElement?): Map<String, List<String>> {
        val obj = element as? JsonObject ?: return emptyMap()
        return obj.mapNotNull { (k, v) ->
            val msgs = when (v) {
                is JsonArray -> v.mapNotNull { it.str() ?: (it as? JsonObject)?.let { o -> o.values.firstOrNull()?.str() } }
                is JsonPrimitive -> listOfNotNull(v.contentOrNull)
                is JsonObject -> v.values.flatMap { inner -> if (inner is JsonArray) inner.mapNotNull { it.str() } else listOfNotNull(inner.str()) }
                else -> emptyList()
            }
            if (msgs.isEmpty()) null else k to msgs
        }.toMap()
    }

    private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.contentOrNull

    fun codeForStatus(status: Int): String = when (status) {
        400 -> ErrorCodes.VALIDATION_ERROR
        401 -> "unauthenticated"
        403 -> ErrorCodes.PERMISSION_DENIED
        404 -> ErrorCodes.NOT_FOUND
        409 -> "conflict"
        429 -> "throttled"
        in 500..599 -> "server_error"
        else -> "http_$status"
    }

    fun defaultMessage(status: Int): String = when (status) {
        400 -> "Invalid request"
        401 -> "Please log in again"
        403 -> "You don't have permission to do that"
        404 -> "Not found"
        409 -> "This action conflicts with the current state"
        429 -> "Too many requests. Please wait a moment"
        in 500..599 -> "Server error. Please try again"
        else -> "Something went wrong (HTTP $status)"
    }
}

/** Convenience: safely get the object under "error" of a raw body, if any. */
fun String.errorObjectOrNull(): JsonObject? =
    runCatching { Json.parseToJsonElement(this).jsonObject["error"]?.jsonObject }.getOrNull()
