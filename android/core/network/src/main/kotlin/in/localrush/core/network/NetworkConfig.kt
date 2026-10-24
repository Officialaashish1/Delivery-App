package `in`.localrush.core.network

import `in`.localrush.core.common.AppRole

/**
 * Provided by each app module (from its BuildConfig) via Hilt so core:network stays app-agnostic.
 *
 * ```
 * @Provides fun config() = NetworkConfig(BuildConfig.API_BASE_URL, BuildConfig.WS_BASE_URL, BuildConfig.DEBUG, BuildConfig.VERSION_NAME, AppRole.CUSTOMER)
 * ```
 */
data class NetworkConfig(
    /** e.g. `http://10.0.2.2:8001/api/v1/` — must end with `/`. */
    val apiBaseUrl: String,
    /** e.g. `ws://10.0.2.2:8001/ws/` — must end with `/`. */
    val wsBaseUrl: String,
    val isDebug: Boolean,
    val appVersion: String,
    val role: AppRole,
    val connectTimeoutSeconds: Long = 15,
    val readTimeoutSeconds: Long = 30,
    val writeTimeoutSeconds: Long = 30,
) {
    init {
        require(apiBaseUrl.endsWith("/")) { "apiBaseUrl must end with '/'" }
        require(wsBaseUrl.endsWith("/")) { "wsBaseUrl must end with '/'" }
    }

    val userAgent: String get() = "LocalRush-${role.wire}/$appVersion (Android)"
}
