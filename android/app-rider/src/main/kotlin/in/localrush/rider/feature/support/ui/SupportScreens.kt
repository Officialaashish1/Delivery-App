package `in`.localrush.rider.feature.support.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.localrush.core.common.util.UiState
import `in`.localrush.core.designsystem.component.ChipTone
import `in`.localrush.core.designsystem.component.EmptyState
import `in`.localrush.core.designsystem.component.ErrorState
import `in`.localrush.core.designsystem.component.InlineError
import `in`.localrush.core.designsystem.component.LRButton
import `in`.localrush.core.designsystem.component.LRButtonStyle
import `in`.localrush.core.designsystem.component.LRCard
import `in`.localrush.core.designsystem.component.LRTextField
import `in`.localrush.core.designsystem.component.LoadingState
import `in`.localrush.core.designsystem.component.StatusChip
import `in`.localrush.core.network.dto.FaqDto
import `in`.localrush.core.network.dto.SupportTicketDto
import `in`.localrush.rider.R
import `in`.localrush.rider.feature.support.vm.NewTicketViewModel
import `in`.localrush.rider.feature.support.vm.SupportViewModel
import `in`.localrush.rider.feature.support.vm.TicketDetailViewModel
import `in`.localrush.rider.ui.common.RiderTopBar
import `in`.localrush.rider.ui.common.SectionTitle
import `in`.localrush.rider.ui.common.dial
import `in`.localrush.rider.ui.common.email
import `in`.localrush.rider.ui.common.relativeDay

@Composable
fun SupportScreen(onBack: () -> Unit, onNewTicket: () -> Unit, onOpenTicket: (String) -> Unit, viewModel: SupportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Scaffold(topBar = { RiderTopBar(stringResource(R.string.support_title), onBack = onBack) }) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            state.error != null -> ErrorState(state.error!!, onRetry = viewModel::load, modifier = Modifier.padding(padding))
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    LRCard(Modifier.fillMaxWidth(), large = true) {
                        Text(stringResource(R.string.support_need_help), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val phone = state.contact?.phone
                            LRButton(stringResource(R.string.action_call), onClick = { phone?.let { dial(context, it) } }, modifier = Modifier.weight(1f), style = LRButtonStyle.Outlined, leadingIcon = Icons.Filled.Call, enabled = !phone.isNullOrBlank())
                            val mail = state.contact?.email
                            LRButton(stringResource(R.string.action_email), onClick = { mail?.let { email(context, it) } }, modifier = Modifier.weight(1f), style = LRButtonStyle.Outlined, leadingIcon = Icons.Outlined.Email, enabled = !mail.isNullOrBlank())
                        }
                        Spacer(Modifier.height(8.dp))
                        LRButton(stringResource(R.string.support_new_ticket), onClick = onNewTicket, modifier = Modifier.fillMaxWidth())
                    }
                }
                item { SectionTitle(stringResource(R.string.support_my_tickets)) }
                if (state.ticketsError != null) item { InlineError(state.ticketsError!!.message) }
                else if (state.tickets.isEmpty()) item { Text(stringResource(R.string.support_no_tickets), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(state.tickets, key = { it.id }) { t -> TicketRow(t) { onOpenTicket(t.id) } }
                item { SectionTitle(stringResource(R.string.support_faqs)) }
                if (state.faqs.isEmpty()) item { Text(stringResource(R.string.support_no_faqs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(state.faqs, key = { it.id }) { FaqRow(it) }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun FaqRow(faq: FaqDto) {
    var open by rememberSaveable(faq.id) { mutableStateOf(false) }
    LRCard(Modifier.fillMaxWidth(), onClick = { open = !open }, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(faq.question, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
        }
        AnimatedVisibility(open) { Text(faq.answer, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)) }
    }
}

@Composable
private fun TicketRow(t: SupportTicketDto, onClick: () -> Unit) {
    LRCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t.subject, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(listOfNotNull(t.ticketNumber.takeIf { it.isNotBlank() }, t.orderNumber?.let { stringResource(R.string.order_number, it) }, t.updatedAt.relativeDay()).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TicketStatusChip(t.status)
        }
    }
}

@Composable
private fun TicketStatusChip(status: String) {
    val (label, tone) = when (status) {
        "resolved" -> R.string.ticket_resolved to ChipTone.Success
        "closed" -> R.string.ticket_closed to ChipTone.Neutral
        "in_progress" -> R.string.ticket_in_progress to ChipTone.Info
        "waiting" -> R.string.ticket_waiting to ChipTone.Warning
        else -> R.string.ticket_open to ChipTone.Primary
    }
    StatusChip(stringResource(label), tone)
}

@Composable
fun NewTicketScreen(onBack: () -> Unit, onCreated: (String) -> Unit, viewModel: NewTicketViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.created.collect { onCreated(it) } }
    Scaffold(topBar = { RiderTopBar(stringResource(R.string.support_new_ticket), onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp).imePadding()) {
            Text(stringResource(R.string.ticket_category), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                state.categories.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { c -> FilterChip(selected = state.category == c.key, onClick = { viewModel.setCategory(c.key) }, label = { Text(c.label.ifBlank { c.key }) }) }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            LRTextField(state.subject, viewModel::setSubject, label = stringResource(R.string.ticket_subject), errorText = state.fieldErrors["subject"])
            Spacer(Modifier.height(12.dp))
            LRTextField(state.description, viewModel::setDescription, label = stringResource(R.string.ticket_description), errorText = state.fieldErrors["description"], helperText = stringResource(R.string.ticket_description_hint), singleLine = false, maxLines = 6)
            state.error?.takeIf { state.fieldErrors.isEmpty() }?.let { Spacer(Modifier.height(12.dp)); InlineError(it.message) }
            Spacer(Modifier.height(20.dp))
            LRButton(stringResource(R.string.ticket_submit), onClick = viewModel::submit, modifier = Modifier.fillMaxWidth(), enabled = state.canSubmit, loading = state.submitting)
        }
    }
}

@Composable
fun TicketDetailScreen(onBack: () -> Unit, viewModel: TicketDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { RiderTopBar(stringResource(R.string.ticket_detail_title), onBack = onBack) }) { padding ->
        when (val t = state.ticket) {
            UiState.Loading -> LoadingState(Modifier.padding(padding))
            is UiState.Error -> ErrorState(t.error, onRetry = viewModel::load, modifier = Modifier.padding(padding))
            UiState.Empty -> EmptyState(title = stringResource(R.string.support_no_tickets), modifier = Modifier.padding(padding))
            is UiState.Success -> {
                val ticket = t.data
                val closed = ticket.status == "closed" || ticket.status == "resolved"
                Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(ticket.subject, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    Text(listOfNotNull(ticket.ticketNumber.takeIf { it.isNotBlank() }, ticket.orderNumber?.let { stringResource(R.string.order_number, it) }, ticket.createdAt.relativeDay()).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TicketStatusChip(ticket.status)
                            }
                        }
                        item { LRCard(Modifier.fillMaxWidth()) { Text(ticket.description, style = MaterialTheme.typography.bodyMedium) } }
                        if (ticket.resolution.isNotBlank()) item { LRCard(Modifier.fillMaxWidth()) { Text(stringResource(R.string.ticket_resolution), style = MaterialTheme.typography.labelLarge); Text(ticket.resolution) } }
                        items(ticket.messages, key = { it.id }) { m ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.isStaffReply) Arrangement.Start else Arrangement.End) {
                                Column(
                                    Modifier.background(if (m.isStaffReply) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp)).padding(12.dp),
                                ) {
                                    Text(m.senderName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(m.text, style = MaterialTheme.typography.bodyMedium)
                                    Text(m.createdAt.relativeDay(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        state.actionError?.let { item { InlineError(it.message) } }
                    }
                    if (!closed) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            LRTextField(state.reply, viewModel::setReply, modifier = Modifier.weight(1f), placeholder = stringResource(R.string.chat_placeholder))
                            Spacer(Modifier.width(8.dp))
                            IconButton(onClick = viewModel::sendReply, enabled = state.reply.isNotBlank() && !state.sending) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.chat_send), tint = MaterialTheme.colorScheme.primary) }
                        }
                        LRButton(stringResource(R.string.ticket_close), onClick = viewModel::close, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), style = LRButtonStyle.Text, loading = state.closing)
                    }
                }
            }
        }
    }
}
