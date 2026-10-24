package `in`.localrush.partner.feature.menu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.localrush.core.designsystem.component.EmptyState
import `in`.localrush.core.designsystem.component.LRBottomSheet
import `in`.localrush.core.designsystem.component.LRButton
import `in`.localrush.core.designsystem.component.LRCard
import `in`.localrush.core.designsystem.component.LRTextField
import `in`.localrush.partner.R
import `in`.localrush.partner.data.MenuCategoryDto
import `in`.localrush.partner.feature.common.ui.ConfirmDialog
import `in`.localrush.partner.feature.common.ui.PartnerTopBar
import `in`.localrush.partner.feature.common.ui.StateContent
import `in`.localrush.partner.feature.menu.vm.CategoriesViewModel

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(onBack: () -> Unit, onOpenCategory: (String) -> Unit, viewModel: CategoriesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var editing by rememberSaveable { mutableStateOf<String?>(null) } // "" = new
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        topBar = { PartnerTopBar(stringResource(R.string.menu_categories_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = { FloatingActionButton(onClick = { editing = "" }) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.category_add)) } },
    ) { padding ->
        StateContent(
            state = state, online = online, onRetry = viewModel::load, modifier = Modifier.padding(padding),
            empty = { EmptyState(stringResource(R.string.categories_empty), description = stringResource(R.string.categories_empty_hint), actionText = stringResource(R.string.category_add), onAction = { editing = "" }) },
        ) { data ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(data.categories, key = { it.id }) { c ->
                    CategoryRow(
                        c, busy = data.busy, isFirst = c == data.categories.first(), isLast = c == data.categories.last(),
                        onOpen = { onOpenCategory(c.id) }, onEdit = { editing = c.id }, onDelete = { deleting = c.id },
                        onToggle = { viewModel.setActive(c.id, it) }, onUp = { viewModel.move(c.id, -1) }, onDown = { viewModel.move(c.id, 1) },
                    )
                }
                item { Spacer(Modifier.height(72.dp)) }
            }
        }
    }

    editing?.let { id ->
        val existing = (state as? `in`.localrush.core.common.util.UiState.Success)?.data?.categories?.firstOrNull { it.id == id }
        var name by rememberSaveable(id) { mutableStateOf(existing?.name ?: "") }
        var description by rememberSaveable(id) { mutableStateOf(existing?.description ?: "") }
        LRBottomSheet(onDismiss = { editing = null }, title = stringResource(if (existing == null) R.string.category_add else R.string.category_edit)) {
            LRTextField(name, { name = it }, label = stringResource(R.string.category_name))
            Spacer(Modifier.height(8.dp))
            LRTextField(description, { description = it }, label = stringResource(R.string.category_description), singleLine = false, maxLines = 3)
            Spacer(Modifier.height(12.dp))
            LRButton(
                stringResource(R.string.action_save), modifier = Modifier.fillMaxWidth(), enabled = name.trim().length >= 2,
                onClick = { if (existing == null) viewModel.create(name, description) else viewModel.rename(existing.id, name, description); editing = null },
            )
            Spacer(Modifier.height(8.dp))
        }
    }
    deleting?.let { id ->
        ConfirmDialog(
            title = stringResource(R.string.category_delete), text = stringResource(R.string.category_delete_confirm), confirmText = stringResource(R.string.action_delete),
            onConfirm = { viewModel.delete(id); deleting = null }, onDismiss = { deleting = null }, destructive = true,
        )
    }
}

@Composable
private fun CategoryRow(
    c: MenuCategoryDto, busy: Boolean, isFirst: Boolean, isLast: Boolean,
    onOpen: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onToggle: (Boolean) -> Unit, onUp: () -> Unit, onDown: () -> Unit,
) {
    LRCard(onClick = onOpen, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(c.name, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.category_item_count, c.itemCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (c.description.isNotBlank()) Text(c.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Switch(checked = c.isActive, onCheckedChange = onToggle, enabled = !busy)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onUp, enabled = !busy && !isFirst) { Icon(Icons.Outlined.ArrowUpward, contentDescription = stringResource(R.string.action_move_up)) }
            IconButton(onClick = onDown, enabled = !busy && !isLast) { Icon(Icons.Outlined.ArrowDownward, contentDescription = stringResource(R.string.action_move_down)) }
            IconButton(onClick = onEdit, enabled = !busy) { Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.action_edit)) }
            IconButton(onClick = onDelete, enabled = !busy && c.itemCount == 0) { Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_delete)) }
        }
    }
}
