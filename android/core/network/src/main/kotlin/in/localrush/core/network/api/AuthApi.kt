package `in`.localrush.core.network.api

import `in`.localrush.core.network.dto.AuthResponseDto
import `in`.localrush.core.network.dto.ChangePasswordDto
import `in`.localrush.core.network.dto.DeviceDto
import `in`.localrush.core.network.dto.DeviceRegisterDto
import `in`.localrush.core.network.dto.ForgotPasswordDto
import `in`.localrush.core.network.dto.LogoutDto
import `in`.localrush.core.network.dto.MessageDto
import `in`.localrush.core.network.dto.OtpRequestDto
import `in`.localrush.core.network.dto.OtpRequestResponseDto
import `in`.localrush.core.network.dto.OtpVerifyDto
import `in`.localrush.core.network.dto.PasswordLoginDto
import `in`.localrush.core.network.dto.RefreshRequestDto
import `in`.localrush.core.network.dto.RefreshResponseDto
import `in`.localrush.core.network.dto.UserDto
import `in`.localrush.core.network.dto.UserPatchDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST

private const val NO_AUTH = "X-No-Auth: true"

interface AuthApi {
    @Headers(NO_AUTH) @POST("auth/otp/request/")
    suspend fun requestOtp(@Body body: OtpRequestDto): OtpRequestResponseDto

    @Headers(NO_AUTH) @POST("auth/otp/verify/")
    suspend fun verifyOtp(@Body body: OtpVerifyDto): AuthResponseDto

    @Headers(NO_AUTH) @POST("auth/login/")
    suspend fun login(@Body body: PasswordLoginDto): AuthResponseDto

    @Headers(NO_AUTH) @POST("auth/token/refresh/")
    suspend fun refresh(@Body body: RefreshRequestDto): RefreshResponseDto

    @Headers(NO_AUTH) @POST("auth/password/forgot/")
    suspend fun forgotPassword(@Body body: ForgotPasswordDto): MessageDto

    @POST("auth/password/change/")
    suspend fun changePassword(@Body body: ChangePasswordDto): MessageDto

    @POST("auth/logout/")
    suspend fun logout(@Body body: LogoutDto): Response<Unit>

    @POST("auth/logout-all/")
    suspend fun logoutAll(): Response<Unit>

    @GET("auth/me/")
    suspend fun me(): UserDto

    @PATCH("auth/me/")
    suspend fun updateMe(@Body body: UserPatchDto): UserDto

    @POST("auth/devices/")
    suspend fun registerDevice(@Body body: DeviceRegisterDto): DeviceDto

    @DELETE("auth/account/")
    suspend fun deleteAccount(): Response<Unit>
}
