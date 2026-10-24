package `in`.localrush.feature.auth.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.localrush.core.auth.AuthRepository
import `in`.localrush.core.auth.otpAttemptsLeft
import `in`.localrush.core.common.Constants
import `in`.localrush.core.common.result.AppError
import `in`.localrush.core.common.result.ErrorCodes
import `in`.localrush.core.common.util.PhoneUtils
import `in`.localrush.core.push.PushTokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val phone: String = "",
    val phoneError: String? = null,
    val sending: Boolean = false,
    /** Non-null once an OTP has been requested successfully. */
    val otpSentTo: String? = null,
    val isExistingUser: Boolean = true,
    val otp: String = "",
    val otpError: String? = null,
    val attemptsLeft: Int? = null,
    val verifying: Boolean = false,
    val resendSeconds: Int = 0,
    val devCode: String? = null,
    val fullName: String = "",
    // password login (partners)
    val password: String = "",
    val passwordError: String? = null,
    val loggingIn: Boolean = false,
    /** Non-field error to surface as a snackbar/inline banner. */
    val generalError: AppError? = null,
    val info: String? = null,
) {
    val phoneValid: Boolean get() = PhoneUtils.isValidIndian(phone)
    val canResend: Boolean get() = resendSeconds == 0 && !sending
    val phoneDisplay: String get() = otpSentTo?.let { PhoneUtils.formatForDisplay(it) } ?: phone
}

sealed interface AuthEvent {
    data object OtpSent : AuthEvent
    data class LoggedIn(val isNewUser: Boolean, val profileComplete: Boolean) : AuthEvent
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repo: AuthRepository,
    private val push: PushTokenManager,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<AuthEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<AuthEvent> = _events.asSharedFlow()

    private var countdown: Job? = null

    fun onPhoneChange(v: String) = _state.update { it.copy(phone = v, phoneError = null, generalError = null) }
    fun onOtpChange(v: String) = _state.update { it.copy(otp = v, otpError = null, generalError = null) }
    fun onNameChange(v: String) = _state.update { it.copy(fullName = v) }
    fun onPasswordChange(v: String) = _state.update { it.copy(password = v, passwordError = null, generalError = null) }
    fun clearError() = _state.update { it.copy(generalError = null, info = null) }

    fun requestOtp() {
        val s = _state.value
        if (!s.phoneValid) { _state.update { it.copy(phoneError = "Enter a valid 10-digit mobile number") }; return }
        if (s.sending) return
        _state.update { it.copy(sending = true, generalError = null, otpError = null) }
        viewModelScope.launch {
            repo.requestOtp(s.phone).fold(
                onOk = { r ->
                    _state.update {
                        it.copy(sending = false, otpSentTo = PhoneUtils.normalizeIndian(s.phone), isExistingUser = r.isExistingUser,
                            otp = "", attemptsLeft = null, devCode = r.devCode, info = r.message)
                    }
                    startCountdown(Constants.OTP_RESEND_SECONDS)
                    _events.tryEmit(AuthEvent.OtpSent)
                },
                onErr = { e ->
                    val fieldMsg = (e as? AppError.Api)?.fieldError("phone")
                    val cooldown = (e as? AppError.Api)?.code == ErrorCodes.OTP_COOLDOWN
                    _state.update { it.copy(sending = false, phoneError = fieldMsg, generalError = if (fieldMsg == null) e else null) }
                    if (cooldown) startCountdown(Constants.OTP_RESEND_SECONDS)
                },
            )
        }
    }

    fun resendOtp() {
        if (!_state.value.canResend) return
        requestOtp()
    }

    fun verifyOtp(code: String = _state.value.otp) {
        val s = _state.value
        val phone = s.otpSentTo ?: return
        if (code.length != Constants.OTP_LENGTH || s.verifying) return
        _state.update { it.copy(verifying = true, otpError = null, generalError = null) }
        viewModelScope.launch {
            repo.verifyOtp(phone, code, fullName = s.fullName.takeIf { !s.isExistingUser }).fold(
                onOk = { outcome ->
                    launch { push.syncToken() }
                    _state.update { it.copy(verifying = false) }
                    _events.tryEmit(AuthEvent.LoggedIn(outcome.isNewUser, outcome.profileComplete))
                },
                onErr = { e ->
                    val api = e as? AppError.Api
                    val otpError = when (api?.code) {
                        ErrorCodes.OTP_INVALID -> api.message
                        ErrorCodes.OTP_EXPIRED -> "This OTP has expired. Please request a new one."
                        else -> api?.fieldError("code")
                    }
                    _state.update { it.copy(verifying = false, otp = if (otpError != null) "" else it.otp, otpError = otpError, attemptsLeft = e.otpAttemptsLeft, generalError = if (otpError == null) e else null) }
                },
            )
        }
    }

    fun loginWithPassword() {
        val s = _state.value
        if (!s.phoneValid) { _state.update { it.copy(phoneError = "Enter a valid 10-digit mobile number") }; return }
        if (s.password.isBlank()) { _state.update { it.copy(passwordError = "Enter your password") }; return }
        if (s.loggingIn) return
        _state.update { it.copy(loggingIn = true, generalError = null) }
        viewModelScope.launch {
            repo.loginWithPassword(s.phone, s.password).fold(
                onOk = { outcome ->
                    launch { push.syncToken() }
                    _state.update { it.copy(loggingIn = false) }
                    _events.tryEmit(AuthEvent.LoggedIn(outcome.isNewUser, outcome.profileComplete))
                },
                onErr = { e ->
                    val api = e as? AppError.Api
                    _state.update { it.copy(loggingIn = false, passwordError = api?.fieldError("password"), phoneError = api?.fieldError("phone"), generalError = if (api?.fields.isNullOrEmpty()) e else null) }
                },
            )
        }
    }

    fun backToPhone() {
        countdown?.cancel()
        _state.update { it.copy(otpSentTo = null, otp = "", otpError = null, attemptsLeft = null, resendSeconds = 0, generalError = null, info = null) }
    }

    private fun startCountdown(seconds: Int) {
        countdown?.cancel()
        countdown = viewModelScope.launch {
            var left = seconds
            while (left > 0) {
                _state.update { it.copy(resendSeconds = left) }
                delay(1000)
                left--
            }
            _state.update { it.copy(resendSeconds = 0) }
        }
    }
}
