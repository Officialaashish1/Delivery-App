package `in`.localrush.core.network.auth

import `in`.localrush.core.network.NetworkConfig
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds `Authorization: Bearer <access>` (unless the request opted out with the
 * [NO_AUTH_HEADER] marker header) plus User-Agent / X-App headers.
 */
class AuthInterceptor(
    private val tokens: TokenProvider,
    private val config: NetworkConfig,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()
            .header("User-Agent", config.userAgent)
            .header("X-App", config.role.wire)
            .header("X-App-Version", config.appVersion)
            .header("Accept", "application/json")

        if (original.header(NO_AUTH_HEADER) != null) {
            builder.removeHeader(NO_AUTH_HEADER)
        } else if (original.header("Authorization") == null) {
            tokens.accessToken()?.let { builder.header("Authorization", "Bearer $it") }
        }
        return chain.proceed(builder.build())
    }

    companion object {
        /** Add `@Headers("X-No-Auth: true")` to public endpoints (OTP, login, config). */
        const val NO_AUTH_HEADER = "X-No-Auth"
    }
}
