package `in`.localrush.partner.feature.support.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import `in`.localrush.core.common.time.DateTimeUtils
import `in`.localrush.core.common.time.toInstantOrNull
import `in`.localrush.core.designsystem.component.ChipTone
import `in`.localrush.core.designsystem.component.LRButton
import `in`.localrush.core.designsystem.component.LRButtonStyle
import `in`.localrush.core.designsystem.component.LRCard
import `in`.localrush.core.designsystem.component.LRFooter
import `in`.localrush.core.designsystem.component.LRTextField
import `in`.localrush.core.designsystem.component.StatusChip
import `in`.localrush.core.network.dto.SupportTicketDto
import `in`.localrush.partner.R
import `in`.localrush.partner.feature.common.ui.PartnerTopBar
import `in`.localrush.partner.feature.common.ui.SectionTitle
import `in`.localrush.partner.feature.common.ui.StateContent
import `in`.localrush.partner.feature.support.vm.NewTicketViewModel
import `in`.localrush.partner.feature.support.vm.SupportViewModel
import `in`.localrush.partner.feature.support.vm.TicketEvent
import `in`.localrush.partner.feature.support.vm.TicketViewModel

@Composable
fun SupportScreen(onBack: () -> Unit, onOpenTicket: (String) -> Unit, onNewTicket: () -> Unit, viewModel: SupportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    Scaffold(
        topBar = { PartnerTopBar(stringResource(R.string.support_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = { FloatingActionButton(onClick = onNewTicket) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.support_new_ticket)) } },
    ) { padding ->
        StateContent(state = state, online = online, onRetry = viewModel::load, modifier = Modifier.padding(padding)) { d ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                d.contact?.let { c ->
                    item {
                        LRCard {
                            SectionTitle(stringResource(R.string.support_contact))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (c.phone.isNotBlank()) LRButton(c.phone, onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${c.phone}"))) }, modifier = Modifier.weight(1f), style = LRButtonStyle.Outlined, leadingIcon = Icons.Outlined.Call)
                                if (c.email.isNotBlank()) LRButton(stringResource(R.string.support_email), onClick = { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${c.email}"))) }, modifier = Modifier.weight(1f), style = LRButtonStyle.Outlined, leadingIcon = Icons.Outlined.Email)
                            }
                        }
                    }
                }
                item { SectionTitle(stringResource(R.string.support_tickets)) }
                if (d.tickets.isEmpty()) item { Text(stringResource(R.string.support_tickets_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(d.tickets, key = { it.id }) { t -> TicketRow(t) { onOpenTicket(t.id) } }
                item { SectionTitle(stringResource(R.string.support_faqs)) }
                if (d.faqs.isEmpty()) item { Text(stringResource(R.string.support_faqs_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(d.faqs, key = { it.id }) { f ->
                    var open by rememberSaveable(f.id) { mutableStateOf(false) }
                    LRCard(onClick = { open = !open }, contentPadding = PaddingValues(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(f.question, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
                        }
                        AnimatedVisibility(open) { Text(f.answer, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)) }
                    }
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }
}

@Composable
private fun TicketRow(t: SupportTicketDto, onClick: () -> Unit) {
    LRCard(onClick = onClick, contentPadding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t.subject, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("#${t.ticketNumber} · ${t.category.replace('_', ' ')}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                t.updatedAt?.toInstantOrNull()?.let { Text(DateTimeUtils.timeAgo(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            StatusChip(t.status.replace('_', ' ').replaceFirstChar { it.uppercase() }, when (t.status) { "resolved", "closed" -> ChipTone.Success; "in_progress" -> ChipTone.Info; else -> ChipTone.Warning })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewTicketScreen(onBack: () -> Unit, onCreated: (String) -> Unit, viewModel: NewTicketViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var category by rememberSaveable { mutableStateOf("") }
    var subject by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { viewModel.events.collect { e -> when (e) { is TicketEvent.Created -> onCreated(e.ticket.id); is TicketEvent.Message -> snackbar.showSnackbar(e.text) } } }
    val data = (state as? `in`.localrush.core.common.util.UiState.Success)?.data
    Scaffold(
        topBar = { PartnerTopBar(stringResource(R.string.support_new_ticket), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { if (data != null) LRFooter { LRButton(stringResource(R.string.action_submit), onClick = { viewModel.submit(category, subject, description) }, modifier = Modifier.fillMaxWidth(), loading = data.submitting, enabled = online) } },
    ) { padding ->
        StateContent(state = state, online = online, onRetry = viewModel::load, modifier = Modifier.padding(padding)) { d ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle(stringResource(R.string.support_category))
                d.errors["category"]?.let { Text(stringResource(R.string.error_required), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    d.categories.forEach { c -> FilterChip(selected = category == c.key, onClick = { category = c.key }, label = { Text(c.label.ifBlank { c.key }) }) }
                }
                LRTextField(subject, { subject = it }, label = stringResource(R.string.support_subject), errorText = d.errors["subject"]?.let { stringResource(R.string.error_required) })
                LRTextField(description, { description = it }, label = stringResource(R.string.support_description), singleLine = false, maxLines = 8, errorText = d.errors["description"]?.let { stringResource(R.string.error_description_short) })
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun TicketScreen(onBack: () -> Unit, viewModel: TicketViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var text by rememberSaveable { mutableStateOf("") }
    var attachment by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> attachment = uri?.toString() }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    val data = (state as? `in`.localrush.core.common.util.UiState.Success)?.data
    val open = data?.ticket?.status?.let { it != "closed" && it != "resolved" } == true

    Scaffold(
        topBar = {
            PartnerTopBar(data?.ticket?.let { "#${it.ticketNumber}" } ?: stringResource(R.string.support_ticket), onBack = onBack, subtitle = data?.ticket?.subject,
                actions = { if (open) TextButton(onClick = viewModel::close) { Text(stringResource(R.string.support_close_ticket)) } })
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (data != null && open) LRFooter {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(onClick = { picker.launch("*/*") }) { Icon(Icons.Outlined.AttachFile, contentDescription = stringResource(R.string.support_attach), tint = if (attachment != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                    LRTextField(text, { text = it }, placeholder = stringResource(R.string.support_reply_hint), modifier = Modifier.weight(1f), singleLine = false, maxLines = 3)
                    IconButton(onClick = { viewModel.reply(text, attachment?.let(Uri::parse)); text = ""; attachment = null }, enabled = text.isNotBlank() && !data.sending) { Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = stringResource(R.string.action_send)) }
                }
            }
        },
    ) { padding ->
        StateContent(state = state, online = online, onRetry = viewModel::load, modifier = Modifier.padding(padding)) { d ->
            val t = d.ticket
            LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    LRCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(t.category.replace('_', ' ').replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                            StatusChip(t.status.replace('_', ' ').replaceFirstChar { it.uppercase() }, when (t.status) { "resolved", "closed" -> ChipTone.Success; "in_progress" -> ChipTone.Info; else -> ChipTone.Warning })
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(t.description, style = MaterialTheme.typography.bodyMedium)
                        if (t.resolution.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text(stringResource(R.string.support_resolution, t.resolution), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                items(t.messages, key = { it.id }) { m ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.isStaffReply) Arrangement.Start else Arrangement.End) {
                        Column(
                            Modifier.fillMaxWidth(0.82f).background(if (m.isStaffReply) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp)).padding(10.dp),
                        ) {
                            Text(m.senderName.ifBlank { stringResource(if (m.isStaffReply) R.string.support_staff else R.string.support_you) }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(m.text, style = MaterialTheme.typography.bodyMedium)
                            m.attachment?.let { url ->
                                val ctx = LocalContext.current
                                Text(stringResource(R.string.support_attachment), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.clickable { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) })
                            }
                            m.createdAt?.toInstantOrNull()?.let { Text(DateTimeUtils.formatRelativeDay(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
                item { Box(Modifier.width(1.dp).height(8.dp)) }
            }
        }
    }
}
