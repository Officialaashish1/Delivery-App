package `in`.localrush.rider.feature.delivery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import `in`.localrush.core.designsystem.component.EmptyState
import `in`.localrush.core.designsystem.component.InlineError
import `in`.localrush.core.designsystem.component.LRBottomSheet
import `in`.localrush.core.designsystem.component.LRButton
import `in`.localrush.core.designsystem.component.LRButtonStyle
import `in`.localrush.core.designsystem.component.LRImage
import `in`.localrush.core.designsystem.component.LRTextField
import `in`.localrush.core.designsystem.component.SuccessNote
import `in`.localrush.core.designsystem.theme.LRTheme
import `in`.localrush.rider.R
import `in`.localrush.rider.data.ProblemCategory
import `in`.localrush.rider.feature.delivery.vm.DeliverySheet
import `in`.localrush.rider.feature.delivery.vm.DeliveryUiState
import `in`.localrush.rider.feature.delivery.vm.DeliveryViewModel
import `in`.localrush.rider.ui.common.rememberImagePicker
import `in`.localrush.rider.ui.common.timeLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OtpSheet(state: DeliveryUiState, vm: DeliveryViewModel, pickup: Boolean) {
    val picker = rememberImagePicker { vm.setProof(it) }
    LRBottomSheet(onDismiss = vm::closeSheet, title = stringResource(if (pickup) R.string.sheet_pickup_title else R.string.sheet_deliver_title)) {
        Column(Modifier.imePadding()) {
            Text(
                stringResource(if (pickup) R.string.sheet_pickup_body else R.string.sheet_deliver_body),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            LRTextField(
                value = state.otp, onValueChange = vm::onOtpChange, label = stringResource(R.string.sheet_otp_label),
                errorText = state.otpError ?: if (state.otpTooShort) stringResource(R.string.sheet_otp_short) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            )
            if (!pickup) {
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.sheet_proof_title), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.proofUri != null) {
                        Box {
                            LRImage(state.proofUri.toString(), contentDescription = null, modifier = Modifier.size(72.dp))
                            IconButton(onClick = { vm.setProof(null) }, modifier = Modifier.align(Alignment.TopEnd).size(24.dp)) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_delete)) }
                        }
                        Spacer(Modifier.width(12.dp))
                    }
                    LRButton(stringResource(if (state.proofUri == null) R.string.sheet_take_photo else R.string.sheet_retake_photo), onClick = picker.fromCamera, style = LRButtonStyle.Outlined, leadingIcon = Icons.Filled.CameraAlt)
                }
            }
            state.actionError?.let { Spacer(Modifier.height(12.dp)); InlineError(it.message) }
            Spacer(Modifier.height(20.dp))
            LRButton(
                stringResource(if (pickup) R.string.sheet_confirm_pickup else R.string.sheet_confirm_delivery),
                onClick = if (pickup) vm::submitPickup else vm::submitDeliver, modifier = Modifier.fillMaxWidth().height(56.dp),
                loading = state.actionInProgress, enabled = state.otp.length >= 4,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProblemSheet(state: DeliveryUiState, vm: DeliveryViewModel) {
    var category by rememberSaveable { mutableStateOf(ProblemCategory.CUSTOMER_UNAVAILABLE) }
    var description by rememberSaveable { mutableStateOf("") }
    LRBottomSheet(onDismiss = vm::closeSheet, title = stringResource(R.string.problem_title)) {
        Column(Modifier.imePadding()) {
            if (state.problemSubmitted) {
                SuccessNote(stringResource(R.string.problem_submitted))
                Spacer(Modifier.height(12.dp))
                LRButton(stringResource(R.string.action_close), onClick = vm::closeSheet, modifier = Modifier.fillMaxWidth(), style = LRButtonStyle.Outlined)
                Spacer(Modifier.height(8.dp))
                return@Column
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ProblemCategory.entries.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pair.forEach { c -> FilterChip(selected = category == c, onClick = { category = c }, label = { Text(problemLabel(c)) }) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            LRTextField(description, { description = it }, label = stringResource(R.string.problem_description), singleLine = false, maxLines = 4)
            if (category == ProblemCategory.EMERGENCY) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.problem_emergency_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            state.actionError?.let { Spacer(Modifier.height(12.dp)); InlineError(it.message) }
            Spacer(Modifier.height(16.dp))
            LRButton(stringResource(R.string.problem_submit), onClick = { vm.reportProblem(category, description) }, modifier = Modifier.fillMaxWidth(), loading = state.actionInProgress)
            if (state.core.canReportFailure && category == ProblemCategory.CUSTOMER_UNAVAILABLE) {
                Spacer(Modifier.height(8.dp))
                LRButton(stringResource(R.string.problem_mark_failed), onClick = { vm.openSheet(DeliverySheet.FAIL_REASON) }, modifier = Modifier.fillMaxWidth(), style = LRButtonStyle.Text)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FailReasonSheet(state: DeliveryUiState, vm: DeliveryViewModel) {
    var reason by rememberSaveable { mutableStateOf("") }
    LRBottomSheet(onDismiss = vm::closeSheet, title = stringResource(R.string.fail_title)) {
        Column(Modifier.imePadding()) {
            Text(stringResource(R.string.fail_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            listOf(R.string.fail_reason_unreachable, R.string.fail_reason_wrong_address, R.string.fail_reason_refused).forEach { res ->
                val label = stringResource(res)
                FilterChip(selected = reason == label, onClick = { reason = label }, label = { Text(label) }, modifier = Modifier.padding(vertical = 2.dp))
            }
            Spacer(Modifier.height(8.dp))
            LRTextField(reason, { reason = it }, label = stringResource(R.string.fail_reason_label), singleLine = false, maxLines = 3)
            state.actionError?.let { Spacer(Modifier.height(12.dp)); InlineError(it.message) }
            Spacer(Modifier.height(16.dp))
            LRButton(stringResource(R.string.fail_confirm), onClick = { vm.markDeliveryFailed(reason) }, modifier = Modifier.fillMaxWidth(), enabled = reason.isNotBlank(), loading = state.actionInProgress, style = LRButtonStyle.Secondary)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatSheet(state: DeliveryUiState, vm: DeliveryViewModel) {
    var text by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(state.chat.size) { if (state.chat.isNotEmpty()) listState.animateScrollToItem(state.chat.lastIndex) }
    LRBottomSheet(onDismiss = vm::closeSheet, title = stringResource(R.string.chat_title, state.order?.deliveryName?.ifBlank { null } ?: stringResource(R.string.offer_customer))) {
        Column(Modifier.imePadding()) {
            if (state.chat.isEmpty()) {
                EmptyState(title = stringResource(R.string.chat_empty), description = stringResource(R.string.chat_empty_hint), modifier = Modifier.height(220.dp))
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 360.dp), state = listState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(state.chat, key = { it.id }) { m ->
                        val mine = m.senderRole == "rider"
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                            Column(
                                Modifier.widthIn(max = 280.dp).background(
                                    if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    RoundedCornerShape(14.dp),
                                ).padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Text(m.text, style = MaterialTheme.typography.bodyMedium)
                                Text(m.createdAt.timeLabel(), style = MaterialTheme.typography.labelSmall, color = LRTheme.colors.textTertiary, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LRTextField(text, { text = it }, modifier = Modifier.weight(1f), placeholder = stringResource(R.string.chat_placeholder), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send))
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { vm.sendChat(text); text = "" }, enabled = text.isNotBlank() && !state.chatSending) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.chat_send), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun problemLabel(c: ProblemCategory): String = stringResource(
    when (c) {
        ProblemCategory.CUSTOMER_UNAVAILABLE -> R.string.problem_customer_unavailable
        ProblemCategory.RESTAURANT_DELAY -> R.string.problem_restaurant_delay
        ProblemCategory.VEHICLE_ISSUE -> R.string.problem_vehicle_issue
        ProblemCategory.EMERGENCY -> R.string.problem_emergency
        ProblemCategory.PAYMENT -> R.string.problem_payment
        ProblemCategory.OTHER -> R.string.problem_other
    },
)

