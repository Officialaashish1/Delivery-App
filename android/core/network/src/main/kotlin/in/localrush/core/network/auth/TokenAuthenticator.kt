package `in`.localrush.core.network.auth

import `in`.localrush.core.common.result.ErrorCodes
import `in`.localrush.core.common.result.ErrorEnvelopeParser
import `in`.localrush.core.network.NetworkConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * OkHttp [Authenticator] invoked on 401.
 *
 * - If the 401 carries `token_revoked` / `account_suspended` → clear tokens, notify, give up.
 * - Otherwise refresh via `POST auth/token/refresh/` with single-flight locking: concurrent 401s
 *   wait on the lock; whichever thread wins refreshes, the others see the new token and retry.
 * - Rotating refresh: the response's `refresh` replaces the old one.
 * - Refresh failure (401/400 from refresh endpoint) → clear tokens, notify `refresh_failed`.
 * - Network error during refresh → give up on this request without logging out (transient).
 */
class TokenAuthenticator(
    private val tokens: TokenProvider,
    private val config: NetworkConfig,
    private val sessionInvalidator: SessionInvalidator,
    /** A bare client (no auth interceptor/authenticator) used only for the refresh call. */
    private val refreshClient: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : Authenticator {

    private val lock = ReentrantLock()

    override fun authenticate(route: Route?, response: Response): Request? {
        // Never retry the refresh endpoint itself, or requests that explicitly skip auth.
        val path = response.request.url.encodedPath
        if (path.endsWith(REFRESH_PATH) || response.request.header(AuthInterceptor.NO_AUTH_HEADER) != null) return null
        if (responseCount(response) >= MAX_RETRIES) return null

        val body = response.peekBody(64 * 1024).string()
        val code = ErrorEnvelopeParser.parse(401, body).let { (it as? `in`.localrush.core.common.result.AppError.Api)?.code }
        if (code == ErrorCodes.TOKEN_REVOKED || code == ErrorCodes.ACCOUNT_SUSPENDED) {
            tokens.clear()
            sessionInvalidator.onSessionInvalid(code)
            return null
        }

        // Public (X-No-Auth) requests never carry a bearer; a 401 on them is a real answer, not an expired token.
        val failedWith = response.request.header("Authorization")?.removePrefix("Bearer ") ?: return null

        lock.withLock {
            val current = tokens.accessToken()
            // Another thread already refreshed while we waited: just retry with the fresh token.
            if (current != null && current != failedWith) {
                return response.request.withBearer(current)
            }
            val refresh = tokens.refreshToken() ?: run {
                tokens.clear()
                sessionInvalidator.onSessionInvalid(LogoutReason.REFRESH_FAILED)
                return null
            }
            return when (val result = doRefresh(refresh)) {
                is RefreshResult.Success -> {
                    tokens.saveTokens(result.access, result.refresh ?: refresh)
                    response.request.withBearer(result.access)
                }
                RefreshResult.Rejected -> {
                    tokens.clear()
                    sessionInvalidator.onSessionInvalid(LogoutReason.REFRESH_FAILED)
                    null
                }
                RefreshResult.Transient -> null
            }
        }
    }

    private sealed interface RefreshResult {
        data class Success(val access: String, val refresh: String?) : RefreshResult
        data object Rejected : RefreshResult
        data object Transient : RefreshResult
    }

    private fun doRefresh(refresh: String): RefreshResult {
        val payload = """{"refresh":${JsonPrimitive(refresh)}}"""
        val request = Request.Builder()
            .url(config.apiBaseUrl + REFRESH_PATH.removePrefix("/"))
            .header("User-Agent", config.userAgent)
            .header("X-App", config.role.wire)
            .post(payload.toRequestBody(JSON))
            .build()
        return try {
            refreshClient.newCall(request).execute().use { resp ->
                val text = resp.body?.string()
                if (resp.isSuccessful && text != null) {
                    val obj = json.parseToJsonElement(text).jsonObject
                    val access = (obj["access"] as? JsonPrimitive)?.contentOrNull
                    val newRefresh = (obj["refresh"] as? JsonPrimitive)?.contentOrNull
                    if (access != null) RefreshResult.Success(access, newRefresh) else RefreshResult.Rejected
                } else if (resp.code in 400..499) {
                    RefreshResult.Rejected
                } else {
                    RefreshResult.Transient
                }
            }
        } catch (e: java.io.IOException) {
            RefreshResult.Transient
        } catch (e: Exception) {
            RefreshResult.Rejected
        }
    }

    private fun Request.withBearer(token: String): Request =
        newBuilder().header("Authorization", "Bearer $token").build()

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    companion object {
        const val REFRESH_PATH = "auth/token/refresh/"
        private const val MAX_RETRIES = 2
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
