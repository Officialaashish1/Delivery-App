package `in`.localrush.core.auth

import `in`.localrush.core.common.result.AppError
import `in`.localrush.core.common.result.AppResult
import `in`.localrush.core.common.result.EmptyResult
import `in`.localrush.core.common.result.Result
import `in`.localrush.core.common.util.PhoneUtils
import `in`.localrush.core.network.NetworkConfig
import `in`.localrush.core.network.api.AuthApi
import `in`.localrush.core.network.auth.LogoutReason
import `in`.localrush.core.network.dto.AuthResponseDto
import `in`.localrush.core.network.dto.DeviceRegisterDto
import `in`.localrush.core.network.dto.LogoutDto
import `in`.localrush.core.network.dto.OtpRequestDto
import `in`.localrush.core.network.dto.OtpVerifyDto
import `in`.localrush.core.network.dto.PasswordLoginDto
import `in`.localrush.core.network.dto.UserDto
import `in`.localrush.core.network.dto.UserPatchDto
import `in`.localrush.core.network.util.apiCall
import `in`.localrush.core.network.util.apiCallUnit
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

data class OtpRequested(val expiresInSeconds: Int, val isExistingUser: Boolean, val message: String?, val devCode: String?)

data class LoginOutcome(val user: UserDto, val isNewUser: Boolean, val profileComplete: Boolean)

/**
 * Role-aware auth. The role comes from [NetworkConfig.role] so each app automatically sends
 * `customer`/`rider`/`partner` and gets `wrong_app` (409) when a user of another role tries to log in.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val api: AuthApi,
    private val session: SessionManager,
    private val tokenStore: TokenStore,
    private val config: NetworkConfig,
) {
    val role get() = config.role

    /** Accepts 10-digit Indian numbers or E.164; returns a validation error locally when malformed. */
    suspend fun requestOtp(phoneInput: String): AppResult<OtpRequested> {
        val phone = PhoneUtils.normalizeIndian(phoneInput)
            ?: return Result.Err(AppError.Api("validation_error", "Enter a valid 10-digit mobile number", 400, mapOf("phone" to listOf("Invalid phone"))))
        return apiCall { api.requestOtp(OtpRequestDto(phone = phone, role = role.wire)) }
            .map { OtpRequested(it.expiresIn, it.isExistingUser, it.message, it.devCode) }
    }

    suspend fun verifyOtp(phoneInput: String, code: String, fullName: String? = null, email: String? = null, referralCode: String? = null): AppResult<LoginOutcome> {
        val phone = PhoneUtils.normalizeIndian(phoneInput) ?: phoneInput
        return apiCall {
            api.verifyOtp(OtpVerifyDto(phone = phone, code = code, role = role.wire, fullName = fullName?.takeIf { it.isNotBlank() }, email = email?.takeIf { it.isNotBlank() }, referralCode = referralCode?.takeIf { it.isNotBlank() }))
        }.map { onAuthenticated(it) }
    }

    suspend fun loginWithPassword(phoneInput: String, password: String): AppResult<LoginOutcome> {
        val phone = PhoneUtils.normalizeIndian(phoneInput) ?: phoneInput
        return apiCall { api.login(PasswordLoginDto(phone = phone, password = password, role = role.wire)) }
            .map { onAuthenticated(it) }
    }

    private fun onAuthenticated(resp: AuthResponseDto): LoginOutcome {
        session.onLoggedIn(resp.access, resp.refresh, resp.user, resp.profileComplete)
        return LoginOutcome(resp.user, resp.isNewUser, resp.profileComplete)
    }

    suspend fun refreshMe(): AppResult<UserDto> = apiCall { api.me() }.onSuccess { session.updateUser(it) }

    suspend fun updateProfile(fullName: String? = null, email: String? = null): AppResult<UserDto> =
        apiCall { api.updateMe(UserPatchDto(fullName, email)) }.onSuccess { session.updateUser(it) }

    /** Best-effort server logout (blacklists refresh, unregisters FCM), then local logout regardless. */
    suspend fun logout(): EmptyResult {
        val refresh = tokenStore.refreshToken()
        val result: EmptyResult = if (refresh != null) apiCallUnit { api.logout(LogoutDto(refresh, tokenStore.fcmToken())) } else Result.Ok(Unit)
        session.onLoggedOut()
        return result
    }

    suspend fun logoutAllDevices(): EmptyResult {
        val r = apiCallUnit { api.logoutAll() }
        session.onLoggedOut()
        return r
    }

    suspend fun deleteAccount(): EmptyResult = apiCallUnit { api.deleteAccount() }.onSuccess { session.onSessionInvalid(LogoutReason.USER) }

    /** Called from FirebaseMessagingService.onNewToken and after login. No-op when logged out. */
    suspend fun registerDevice(fcmToken: String): EmptyResult {
        tokenStore.saveFcmToken(fcmToken)
        if (!session.isLoggedIn) return Result.Ok(Unit)
        return apiCall {
            api.registerDevice(DeviceRegisterDto(fcmToken = fcmToken, app = role.wire, deviceId = tokenStore.deviceId(), appVersion = config.appVersion))
        }.map { }
    }

    /** Re-sends the cached FCM token after login (the token may have arrived while logged out). */
    suspend fun syncDeviceIfNeeded(): EmptyResult = tokenStore.fcmToken()?.let { registerDevice(it) } ?: Result.Ok(Unit)
}

/** `attempts_left` from an `otp_invalid` error, if present. */
val AppError.otpAttemptsLeft: Int? get() = (this as? AppError.Api)?.extra?.get("attempts_left")?.jsonPrimitive?.let { runCatching { it.int }.getOrNull() }
