package `in`.localrush.partner.feature.notifications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import `in`.localrush.core.common.result.Result
import `in`.localrush.core.common.time.DateTimeUtils
import `in`.localrush.core.common.time.toInstantOrNull
import `in`.localrush.core.common.util.UiState
import `in`.localrush.core.designsystem.component.EmptyState
import `in`.localrush.core.designsystem.component.LRCard
import `in`.localrush.core.network.api.NotificationApi
import `in`.localrush.core.network.dto.MarkReadDto
import `in`.localrush.core.network.dto.NotificationDto
import `in`.localrush.core.network.util.apiCall
import `in`.localrush.core.network.util.apiCallUnit
import `in`.localrush.partner.R
import `in`.localrush.partner.data.ConnectivityObserver
import `in`.localrush.partner.feature.common.ui.PartnerTopBar
import `in`.localrush.partner.feature.common.ui.StateContent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import javax.inject.Inject

data class NotificationsData(val items: List<NotificationDto>, val nextPage: Int?, val loadingMore: Boolean = false, val refreshing: Boolean = false) {
    val unread: Int get() = items.count { !it.isRead }
}

@HiltViewModel
class NotificationsViewModel @Inject constructor(private val api: NotificationApi, connectivity: ConnectivityObserver) : ViewModel() {
    private val _state = MutableStateFlow<UiState<NotificationsData>>(UiState.Loading)
    val state: StateFlow<UiState<NotificationsData>> = _state.asStateFlow()
    val online: StateFlow<Boolean> = connectivity.isOnline
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init { load() }

    fun load() {
        _state.update { s -> if (s is UiState.Success) s.copy(data = s.data.copy(refreshing = true)) else UiState.Loading }
        viewModelScope.launch {
            when (val r = apiCall { api.list(page = 1) }) {
                is Result.Ok -> _state.value = if (r.value.results.isEmpty()) UiState.Empty else UiState.Success(NotificationsData(r.value.results, r.value.nextPage))
                is Result.Err -> if (_state.value !is UiState.Success) _state.value = UiState.Error(r.error) else _messages.tryEmit(r.error.message)
            }
        }
    }

    fun loadMore() {
        val s = _state.value as? UiState.Success ?: return
        val next = s.data.nextPage ?: return
        if (s.data.loadingMore) return
        _state.value = s.copy(data = s.data.copy(loadingMore = true))
        viewModelScope.launch {
            when (val r = apiCall { api.list(page = next) }) {
                is Result.Ok -> _state.update { c -> if (c is UiState.Success) c.copy(data = c.data.copy(items = c.data.items + r.value.results, nextPage = r.value.nextPage, loadingMore = false)) else c }
                is Result.Err -> { _messages.tryEmit(r.error.message); _state.update { c -> if (c is UiState.Success) c.copy(data = c.data.copy(loadingMore = false)) else c } }
            }
        }
    }

    fun markRead(id: String) = viewModelScope.launch {
        apiCallUnit { api.markRead(MarkReadDto(listOf(id))) }.onSuccess { _state.update { c -> if (c is UiState.Success) c.copy(data = c.data.copy(items = c.data.items.map { if (it.id == id) it.copy(isRead = true) else it })) else c } }
    }

    fun markAllRead() = viewModelScope.launch {
        apiCallUnit { api.markRead(MarkReadDto()) }
            .onSuccess { _state.update { c -> if (c is UiState.Success) c.copy(data = c.data.copy(items = c.data.items.map { it.copy(isRead = true) })) else c } }
            .onFailure { _messages.tryEmit(it.message) }
    }
}

/** `order_id` from the notification's data block, if any. */
fun NotificationDto.orderId(): String? = (data?.get("order_id") as? JsonPrimitive)?.contentOrNull

@Composable
fun NotificationsScreen(onBack: () -> Unit, onOpenOrder: (String) -> Unit, viewModel: NotificationsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    Scaffold(
        topBar = {
            PartnerTopBar(stringResource(R.string.notifications_title), onBack = onBack, actions = {
                IconButton(onClick = viewModel::markAllRead) { Icon(Icons.Outlined.DoneAll, contentDescription = stringResource(R.string.notifications_mark_all_read)) }
            })
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        StateContent(
            state = state, online = online, onRetry = viewModel::load, modifier = Modifier.padding(padding),
            empty = { EmptyState(stringResource(R.string.notifications_empty), description = stringResource(R.string.notifications_empty_hint), icon = Icons.Outlined.NotificationsNone) },
        ) { d ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(d.items, key = { _, n -> n.id }) { i, n ->
                    if (i >= d.items.size - 3) LaunchedEffect(d.items.size) { viewModel.loadMore() }
                    LRCard(contentPadding = PaddingValues(12.dp), modifier = Modifier.clickable {
                        if (!n.isRead) viewModel.markRead(n.id)
                        n.orderId()?.let(onOpenOrder)
                    }) {
                        Row(verticalAlignment = Alignment.Top) {
                            if (!n.isRead) { androidx.compose.foundation.layout.Box(Modifier.padding(top = 6.dp).size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape)); androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp)) }
                            Column(Modifier.weight(1f)) {
                                Text(n.title, style = MaterialTheme.typography.titleSmall, fontWeight = if (n.isRead) FontWeight.Normal else FontWeight.SemiBold)
                                Text(n.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                n.createdAt?.toInstantOrNull()?.let { Text(DateTimeUtils.timeAgo(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                    }
                }
                if (d.loadingMore) item { CircularProgressIndicator(Modifier.padding(16.dp)) }
            }
        }
    }
}
