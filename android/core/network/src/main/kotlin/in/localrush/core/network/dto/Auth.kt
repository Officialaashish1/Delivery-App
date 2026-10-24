package `in`.localrush.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OtpRequestDto(
    val phone: String,
    val role: String,
    val purpose: String = "login",
)

@Serializable
data class OtpRequestResponseDto(
    val message: String? = null,
    @SerialName("expires_in") val expiresIn: Int = 300,
    @SerialName("is_existing_user") val isExistingUser: Boolean = false,
    @SerialName("dev_code") val devCode: String? = null,
)

@Serializable
data class OtpVerifyDto(
    val phone: String,
    val code: String,
    val role: String,
    @SerialName("full_name") val fullName: String? = null,
    val email: String? = null,
    @SerialName("referral_code") val referralCode: String? = null,
    @SerialName("accepted_terms") val acceptedTerms: Boolean = true,
)

@Serializable
data class PasswordLoginDto(
    val phone: String,
    val password: String,
    val role: String? = null,
)

@Serializable
data class AuthResponseDto(
    val access: String,
    val refresh: String,
    val user: UserDto,
    @SerialName("is_new_user") val isNewUser: Boolean = false,
    @SerialName("profile_complete") val profileComplete: Boolean = true,
)

@Serializable
data class UserDto(
    val id: String,
    val phone: String = "",
    val email: String? = null,
    @SerialName("full_name") val fullName: String = "",
    val role: String = "",
    @SerialName("is_phone_verified") val isPhoneVerified: Boolean = false,
    @SerialName("is_email_verified") val isEmailVerified: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class UserPatchDto(
    @SerialName("full_name") val fullName: String? = null,
    val email: String? = null,
)

@Serializable
data class RefreshRequestDto(val refresh: String)

@Serializable
data class RefreshResponseDto(val access: String, val refresh: String? = null)

@Serializable
data class LogoutDto(val refresh: String, @SerialName("fcm_token") val fcmToken: String? = null)

@Serializable
data class DeviceRegisterDto(
    @SerialName("fcm_token") val fcmToken: String,
    val platform: String = "android",
    val app: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("app_version") val appVersion: String,
)

@Serializable
data class DeviceDto(
    val id: String,
    @SerialName("fcm_token") val fcmToken: String,
    val platform: String = "android",
    val app: String = "",
    @SerialName("device_id") val deviceId: String? = null,
    @SerialName("app_version") val appVersion: String? = null,
)

@Serializable
data class ChangePasswordDto(@SerialName("old_password") val oldPassword: String, @SerialName("new_password") val newPassword: String)

@Serializable
data class ForgotPasswordDto(val phone: String, val role: String? = null)
