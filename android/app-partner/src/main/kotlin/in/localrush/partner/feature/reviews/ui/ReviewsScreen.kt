package `in`.localrush.partner.feature.reviews.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.localrush.core.common.time.DateTimeUtils
import `in`.localrush.core.common.time.toInstantOrNull
import `in`.localrush.core.designsystem.component.EmptyState
import `in`.localrush.core.designsystem.component.LRBottomSheet
import `in`.localrush.core.designsystem.component.LRButton
import `in`.localrush.core.designsystem.component.LRButtonStyle
import `in`.localrush.core.designsystem.component.LRCard
import `in`.localrush.core.designsystem.component.LRTextField
import `in`.localrush.core.designsystem.component.RatingBadge
import `in`.localrush.core.designsystem.component.RatingBar
import `in`.localrush.core.network.dto.ReviewDto
import `in`.localrush.partner.R
import `in`.localrush.partner.feature.common.ui.PartnerTopBar
import `in`.localrush.partner.feature.common.ui.StateContent
import `in`.localrush.partner.feature.reviews.vm.ReviewsViewModel

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ReviewsScreen(onBack: () -> Unit, viewModel: ReviewsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var replyTo by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    Scaffold(topBar = { PartnerTopBar(stringResource(R.string.reviews_title), onBack = onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        StateContent(
            state = state, online = online, onRetry = viewModel::load, modifier = Modifier.padding(padding),
            empty = { EmptyState(stringResource(R.string.reviews_empty), description = stringResource(R.string.reviews_empty_hint), icon = Icons.Outlined.RateReview) },
        ) { d ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    LRCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.reviews_recent_average), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(stringResource(R.string.reviews_unanswered, d.unanswered), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            RatingBadge(d.average, count = d.reviews.size)
                        }
                    }
                }
                itemsIndexed(d.reviews, key = { _, r -> r.id }) { i, review ->
                    if (i >= d.reviews.size - 3) LaunchedEffect(d.reviews.size) { viewModel.loadMore() }
                    ReviewCard(review, replying = review.id in d.replying, onReply = { replyTo = review.id })
                }
                if (d.loadingMore) item { CircularProgressIndicator(Modifier.padding(16.dp)) }
            }
        }
    }
    replyTo?.let { id ->
        val existing = (state as? `in`.localrush.core.common.util.UiState.Success)?.data?.reviews?.firstOrNull { it.id == id }
        var text by rememberSaveable(id) { mutableStateOf(existing?.partnerReply ?: "") }
        LRBottomSheet(onDismiss = { replyTo = null }, title = stringResource(R.string.reviews_reply)) {
            existing?.restaurantComment?.takeIf { it.isNotBlank() }?.let { Text("“$it”", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(8.dp)) }
            LRTextField(text, { text = it.take(1000) }, label = stringResource(R.string.reviews_reply_label), singleLine = false, maxLines = 5, helperText = stringResource(R.string.reviews_reply_hint))
            Spacer(Modifier.height(12.dp))
            LRButton(stringResource(R.string.action_send), onClick = { viewModel.reply(id, text); replyTo = null }, modifier = Modifier.fillMaxWidth(), enabled = text.isNotBlank())
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ReviewCard(r: ReviewDto, replying: Boolean, onReply: () -> Unit) {
    LRCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(r.customerName.ifBlank { stringResource(R.string.order_customer) }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    r.orderNumber?.let { Text(stringResource(R.string.order_number, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(6.dp)) }
                    r.createdAt?.toInstantOrNull()?.let { Text(DateTimeUtils.timeAgo(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            RatingBar(r.restaurantRating.toFloat(), starSize = 18.dp)
        }
        if (r.restaurantComment.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(r.restaurantComment, style = MaterialTheme.typography.bodyMedium) }
        if (r.tags.isNotEmpty()) { Spacer(Modifier.height(6.dp)); Text(r.tags.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (r.itemRatings.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(r.itemRatings.entries.joinToString(" · ") { (k, v) -> "$k ★$v" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        val reply = r.partnerReply
        if (!reply.isNullOrBlank()) {
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(10.dp)).padding(10.dp)) {
                Text(stringResource(R.string.reviews_your_reply), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(reply, style = MaterialTheme.typography.bodyMedium)
                r.partnerRepliedAt?.toInstantOrNull()?.let { Text(DateTimeUtils.timeAgo(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            LRButton(stringResource(R.string.reviews_edit_reply), onClick = onReply, style = LRButtonStyle.Text, loading = replying)
        } else {
            LRButton(stringResource(R.string.reviews_reply), onClick = onReply, modifier = Modifier.fillMaxWidth(), style = LRButtonStyle.Outlined, loading = replying)
        }
    }
}
