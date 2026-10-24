package `in`.localrush.core.network.auth

/**
 * Implemented by core:auth (TokenStore). core:network only needs to read/write tokens and
 * signal forced logout; keeping this an interface avoids a circular module dependency.
 */
interface TokenProvider {
    fun accessToken(): String?
    fun refreshToken(): String?
    /** Persist a rotated pair atomically. */
    fun saveTokens(access: String, refresh: String)
    fun clear()
}

/** Receives forced-logout signals (token_revoked / account_suspended / refresh failed). */
fun interface SessionInvalidator {
    fun onSessionInvalid(reason: String)
}

/** Reasons passed to [SessionInvalidator]. */
object LogoutReason {
    const val TOKEN_REVOKED = "token_revoked"
    const val ACCOUNT_SUSPENDED = "account_suspended"
    const val REFRESH_FAILED = "refresh_failed"
    const val USER = "user"
}
