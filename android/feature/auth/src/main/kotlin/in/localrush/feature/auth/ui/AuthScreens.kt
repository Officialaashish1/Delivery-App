package `in`.localrush.feature.auth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.localrush.core.common.time.DateTimeUtils
import `in`.localrush.core.designsystem.component.InlineError
import `in`.localrush.core.designsystem.component.LRButton
import `in`.localrush.core.designsystem.component.LRButtonStyle
import `in`.localrush.core.designsystem.component.LRPhoneField
import `in`.localrush.core.designsystem.component.LRTextField
import `in`.localrush.core.designsystem.component.OtpInput
import `in`.localrush.core.designsystem.component.SuccessNote
import `in`.localrush.feature.auth.R
import `in`.localrush.feature.auth.vm.AuthEvent
import `in`.localrush.feature.auth.vm.AuthUiState
import `in`.localrush.feature.auth.vm.AuthViewModel

/**
 * Phone → OTP flow. Hosted inside one nav destination; the ViewModel decides which step is shown
 * so the OTP state survives rotation and back-press returns to the phone step.
 *
 * @param headline app-specific title shown above the form (e.g. "Deliver with LocalRush").
 * @param allowPasswordLogin partner app: shows a "Log in with password" switch.
 */
@Composable
fun AuthRoute(
    onLoggedIn: (isNewUser: Boolean, profileComplete: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    headline: String? = null,
    allowPasswordLogin: Boolean = false,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var passwordMode by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                is AuthEvent.LoggedIn -> onLoggedIn(e.isNewUser, e.profileComplete)
                AuthEvent.OtpSent -> Unit
            }
        }
    }
    LaunchedEffect(state.generalError) {
        state.generalError?.let { snackbar.showSnackbar(it.message); viewModel.clearError() }
    }

    Scaffold(modifier = modifier, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).statusBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        ) {
            when {
                state.otpSentTo != null -> OtpStep(state, viewModel)
                passwordMode -> PasswordStep(state, viewModel, headline, onUseOtp = { passwordMode = false })
                else -> PhoneStep(state, viewModel, headline, if (allowPasswordLogin) ({ passwordMode = true }) else null)
            }
        }
    }
}

@Composable
private fun PhoneStep(state: AuthUiState, vm: AuthViewModel, headline: String?, onUsePassword: (() -> Unit)?) {
    Spacer(Modifier.height(48.dp))
    if (headline != null) {
        Text(headline, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
    }
    Text(stringResource(R.string.auth_phone_title), style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(6.dp))
    Text(stringResource(R.string.auth_phone_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(28.dp))
    LRPhoneField(value = state.phone, onValueChange = vm::onPhoneChange, errorText = state.phoneError, enabled = !state.sending, onDone = vm::requestOtp, label = stringResource(R.string.auth_phone_label))
    Spacer(Modifier.height(20.dp))
    LRButton(stringResource(R.string.auth_continue), onClick = vm::requestOtp, modifier = Modifier.fillMaxWidth(), enabled = state.phoneValid, loading = state.sending)
    if (onUsePassword != null) {
        Spacer(Modifier.height(8.dp))
        LRButton(stringResource(R.string.auth_use_password), onClick = onUsePassword, modifier = Modifier.fillMaxWidth(), style = LRButtonStyle.Text)
    }
    Spacer(Modifier.height(24.dp))
    Text(stringResource(R.string.auth_terms), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun OtpStep(state: AuthUiState, vm: AuthViewModel) {
    Spacer(Modifier.height(12.dp))
    IconButton(onClick = vm::backToPhone, modifier = Modifier.padding(start = (-12).dp)) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.auth_otp_change_number))
    }
    Spacer(Modifier.height(16.dp))
    Text(stringResource(R.string.auth_otp_title), style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(6.dp))
    Text(stringResource(R.string.auth_otp_subtitle, state.phoneDisplay), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(28.dp))
    OtpInput(value = state.otp, onValueChange = vm::onOtpChange, isError = state.otpError != null, enabled = !state.verifying, onComplete = { vm.verifyOtp(it) })
    Spacer(Modifier.height(8.dp))
    if (state.otpError != null) {
        Text(state.otpError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        state.attemptsLeft?.let { Text(stringResource(R.string.auth_otp_attempts_left, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    } else if (state.devCode != null) {
        Text(stringResource(R.string.auth_otp_dev_hint, state.devCode), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (!state.isExistingUser) {
        Spacer(Modifier.height(20.dp))
        LRTextField(value = state.fullName, onValueChange = vm::onNameChange, label = stringResource(R.string.auth_name_label), helperText = stringResource(R.string.auth_name_hint),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { vm.verifyOtp() }))
    }
    Spacer(Modifier.height(24.dp))
    LRButton(stringResource(R.string.auth_otp_verify), onClick = { vm.verifyOtp() }, modifier = Modifier.fillMaxWidth(), enabled = state.otp.length == 6, loading = state.verifying)
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        if (state.resendSeconds > 0) {
            Text(stringResource(R.string.auth_otp_resend_in, DateTimeUtils.countdown(state.resendSeconds.toLong())), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LRButton(stringResource(R.string.auth_otp_resend), onClick = vm::resendOtp, style = LRButtonStyle.Text, loading = state.sending)
        }
    }
}

@Composable
private fun PasswordStep(state: AuthUiState, vm: AuthViewModel, headline: String?, onUseOtp: () -> Unit) {
    var showPassword by remember { mutableStateOf(false) }
    Spacer(Modifier.height(48.dp))
    if (headline != null) {
        Text(headline, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
    }
    Text(stringResource(R.string.auth_password_title), style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(6.dp))
    Text(stringResource(R.string.auth_password_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(28.dp))
    LRPhoneField(value = state.phone, onValueChange = vm::onPhoneChange, errorText = state.phoneError, enabled = !state.loggingIn, label = stringResource(R.string.auth_phone_label))
    Spacer(Modifier.height(12.dp))
    LRTextField(
        value = state.password, onValueChange = vm::onPasswordChange, label = stringResource(R.string.auth_password_label), errorText = state.passwordError, enabled = !state.loggingIn,
        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { vm.loginWithPassword() }),
        trailingIcon = {
            IconButton(onClick = { showPassword = !showPassword }) {
                Icon(if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = stringResource(if (showPassword) R.string.auth_hide_password else R.string.auth_show_password))
            }
        },
    )
    Spacer(Modifier.height(20.dp))
    LRButton(stringResource(R.string.auth_login), onClick = vm::loginWithPassword, modifier = Modifier.fillMaxWidth(), enabled = state.phoneValid && state.password.isNotBlank(), loading = state.loggingIn)
    Spacer(Modifier.height(8.dp))
    LRButton(stringResource(R.string.auth_use_otp), onClick = onUseOtp, modifier = Modifier.fillMaxWidth(), style = LRButtonStyle.Text)
    state.info?.let { Spacer(Modifier.height(12.dp)); SuccessNote(it) }
    state.generalError?.let { Spacer(Modifier.height(12.dp)); InlineError(it.message) }
}
