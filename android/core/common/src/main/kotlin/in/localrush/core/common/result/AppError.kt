package `in`.localrush.core.common.result

import kotlinx.serialization.json.JsonObject

/**
 * Every failure in the app is one of these. Network layer parses the backend envelope
 * `{"error":{"code","message","fields", ...extra}}` into [Api]; transport problems become
 * [Network]/[Timeout]; everything else is [Unknown].
 */
sealed class AppError {
    /** Human readable message safe to show the user. */
    abstract val message: String

    /** Backend returned a well-formed error envelope. */
    data class Api(
        val code: String,
        override val message: String,
        val httpStatus: Int,
        /** Field-level validation errors: field -> list of messages. */
        val fields: Map<String, List<String>> = emptyMap(),
        /** Any extra keys the backend attached (e.g. `attempts_left`, `current_restaurant`, `current_status`). */
        val extra: JsonObject? = null,
    ) : AppError() {
        val isValidation get() = code == ErrorCodes.VALIDATION_ERROR
        val isNotFound get() = code == ErrorCodes.NOT_FOUND || httpStatus == 404
        val isUnauthorized get() = httpStatus == 401
        val isForbidden get() = code == ErrorCodes.PERMISSION_DENIED || httpStatus == 403
        val forcesLogout get() = code == ErrorCodes.TOKEN_REVOKED || code == ErrorCodes.ACCOUNT_SUSPENDED
        fun fieldError(name: String): String? = fields[name]?.firstOrNull()
    }

    /** Backend returned non-2xx without a parseable envelope. */
    data class Http(val httpStatus: Int, override val message: String) : AppError()

    /** No connectivity, DNS failure, connection refused, etc. */
    data class Network(override val message: String = "No internet connection") : AppError()

    data class Timeout(override val message: String = "Request timed out") : AppError()

    /** Session ended: token revoked, account suspended or refresh failed. */
    data class Unauthenticated(override val message: String = "Please log in again") : AppError()

    data class Unknown(override val message: String = "Something went wrong", val cause: Throwable? = null) : AppError()

    val isOffline: Boolean get() = this is Network
    val isRetryable: Boolean get() = this is Network || this is Timeout || (this is Http && httpStatus >= 500)
}

/** Error codes from docs/API_CONTRACTS.md. */
object ErrorCodes {
    const val VALIDATION_ERROR = "validation_error"
    const val NOT_FOUND = "not_found"
    const val PERMISSION_DENIED = "permission_denied"
    const val OTP_INVALID = "otp_invalid"
    const val OTP_EXPIRED = "otp_expired"
    const val OTP_COOLDOWN = "otp_cooldown"
    const val WRONG_APP = "wrong_app"
    const val TOKEN_REVOKED = "token_revoked"
    const val ACCOUNT_SUSPENDED = "account_suspended"
    const val TOKEN_NOT_VALID = "token_not_valid"
    const val CART_RESTAURANT_CONFLICT = "cart_restaurant_conflict"
    const val RESTAURANT_CLOSED = "restaurant_closed"
    const val INVALID_TRANSITION = "invalid_transition"
    const val OFFER_EXPIRED = "offer_expired"
    const val OFFER_UNAVAILABLE = "offer_unavailable"
    const val NOT_READY = "not_ready"
    const val NOT_CANCELLABLE = "not_cancellable"
    const val PAYMENT_INVALID_SIGNATURE = "payment_invalid_signature"
    const val PAYMENT_PROVIDER_UNAVAILABLE = "payment_provider_unavailable"
    const val COUPON_PREFIX = "coupon_"
}
