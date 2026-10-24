package `in`.localrush.partner.feature.menu.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import `in`.localrush.core.common.money.Money
import `in`.localrush.core.designsystem.component.ChipTone
import `in`.localrush.core.designsystem.component.EmptyState
import `in`.localrush.core.designsystem.component.LRButton
import `in`.localrush.core.designsystem.component.LRButtonStyle
import `in`.localrush.core.designsystem.component.LRCard
import `in`.localrush.core.designsystem.component.LRFooter
import `in`.localrush.core.designsystem.component.LRImage
import `in`.localrush.core.designsystem.component.LRTextField
import `in`.localrush.core.designsystem.component.PriceText
import `in`.localrush.core.designsystem.component.StatusChip
import `in`.localrush.core.designsystem.component.VegNonVegDot
import `in`.localrush.core.designsystem.icon.LRIcons
import `in`.localrush.partner.R
import `in`.localrush.partner.data.MenuItemDto
import `in`.localrush.partner.feature.common.ui.ConfirmDialog
import `in`.localrush.partner.feature.common.ui.PartnerTopBar
import `in`.localrush.partner.feature.common.ui.StateContent
import `in`.localrush.partner.feature.menu.vm.AvailabilityFilter
import `in`.localrush.partner.feature.menu.vm.MenuItemsViewModel

/** Items list with search, category + availability filters and multi-select bulk availability. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MenuItemsScreen(
    categoryId: String?,
    onBack: (() -> Unit)?,
    onManageCategories: () -> Unit,
    onEditItem: (itemId: String?, categoryId: String?) -> Unit,
    viewModel: MenuItemsViewModel = hiltViewModel(),
) {
    LaunchedEffect(categoryId) { viewModel.start(categoryId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    val data = (state as? `in`.localrush.core.common.util.UiState.Success)?.data

    Scaffold(
        topBar = {
            if (data?.selectionMode == true) {
                PartnerTopBar(
                    title = stringResource(R.string.menu_selected_count, data.selected.size), onBack = viewModel::clearSelection,
                    actions = { IconButton(onClick = viewModel::selectAllVisible) { Icon(Icons.Outlined.SelectAll, contentDescription = stringResource(R.string.action_select_all)) } },
                )
            } else {
                PartnerTopBar(
                    title = stringResource(R.string.menu_title), onBack = onBack,
                    actions = { IconButton(onClick = onManageCategories) { Icon(Icons.Outlined.Category, contentDescription = stringResource(R.string.menu_categories_title)) } },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (data?.selectionMode != true) FloatingActionButton(onClick = { onEditItem(null, data?.categoryId) }) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.item_add)) }
        },
        bottomBar = {
            if (data?.selectionMode == true) LRFooter {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LRButton(stringResource(R.string.menu_bulk_unavailable), onClick = { viewModel.bulkAvailability(false) }, modifier = Modifier.weight(1f), style = LRButtonStyle.Outlined, loading = data.bulkBusy)
                    LRButton(stringResource(R.string.menu_bulk_available), onClick = { viewModel.bulkAvailability(true) }, modifier = Modifier.weight(1f), loading = data.bulkBusy)
                }
            }
        },
    ) { padding ->
        StateContent(state = state, online = online, onRetry = viewModel::load, modifier = Modifier.padding(padding)) { d ->
            Column(Modifier.fillMaxSize()) {
                LRTextField(
                    d.query, viewModel::setQuery, placeholder = stringResource(R.string.menu_search), leadingIcon = LRIcons.Search, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    trailingIcon = if (d.query.isNotEmpty()) ({ IconButton(onClick = { viewModel.setQuery("") }) { Icon(Icons.Outlined.Close, contentDescription = null) } }) else null,
                )
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(selected = d.categoryId == null, onClick = { viewModel.setCategory(null) }, label = { Text(stringResource(R.string.menu_all_categories)) }) }
                    items(d.categories, key = { it.id }) { c -> FilterChip(selected = d.categoryId == c.id, onClick = { viewModel.setCategory(c.id) }, label = { Text(c.name) }) }
                }
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AvailabilityFilter.entries.forEach { f ->
                        FilterChip(selected = d.filter == f, onClick = { viewModel.setFilter(f) }, label = {
                            Text(stringResource(when (f) { AvailabilityFilter.ALL -> R.string.menu_filter_all; AvailabilityFilter.AVAILABLE -> R.string.menu_filter_available; AvailabilityFilter.UNAVAILABLE -> R.string.menu_filter_unavailable }))
                        })
                    }
                }
                PullToRefreshBox(isRefreshing = d.refreshing, onRefresh = viewModel::load, modifier = Modifier.fillMaxSize()) {
                    val visible = d.visible
                    if (visible.isEmpty()) {
                        EmptyState(
                            title = stringResource(if (d.items.isEmpty()) R.string.menu_empty else R.string.menu_no_match),
                            description = stringResource(if (d.items.isEmpty()) R.string.menu_empty_hint else R.string.menu_no_match_hint),
                            actionText = if (d.items.isEmpty()) stringResource(R.string.item_add) else null,
                            onAction = if (d.items.isEmpty()) ({ onEditItem(null, d.categoryId) }) else null,
                        )
                    } else {
                        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(visible, key = { it.id }) { item ->
                                MenuItemRow(
                                    item, selected = item.id in d.selected, selectionMode = d.selectionMode, busy = item.id in d.busyIds,
                                    onClick = { if (d.selectionMode) viewModel.toggleSelect(item.id) else onEditItem(item.id, item.category) },
                                    onLongClick = { viewModel.toggleSelect(item.id) },
                                    onToggle = { viewModel.setAvailability(item.id, it) },
                                    onDelete = { deleting = item.id },
                                )
                            }
                            item { Spacer(Modifier.height(80.dp)) }
                        }
                    }
                }
            }
        }
    }
    deleting?.let { id ->
        ConfirmDialog(stringResource(R.string.item_delete), stringResource(R.string.item_delete_confirm), stringResource(R.string.action_delete), onConfirm = { viewModel.delete(id); deleting = null }, onDismiss = { deleting = null }, destructive = true)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MenuItemRow(item: MenuItemDto, selected: Boolean, selectionMode: Boolean, busy: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    LRCard(modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick), contentPadding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selectionMode) { Checkbox(checked = selected, onCheckedChange = { onClick() }); Spacer(Modifier.width(4.dp)) }
            LRImage(item.image, contentDescription = null, modifier = Modifier.size(64.dp), radius = 12.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VegNonVegDot(item.foodType)
                    Spacer(Modifier.width(6.dp))
                    Text(item.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
                }
                item.categoryName?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                PriceText(Money.parse(item.effectivePrice.ifBlank { item.price }), originalPrice = if (item.discountedPrice != null) Money.parse(item.price) else null, style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (item.stockQuantity != null) StatusChip(stringResource(R.string.item_stock_left, item.stockQuantity), if (item.stockQuantity == 0) ChipTone.Error else ChipTone.Neutral)
                    if (item.variantGroups.isNotEmpty()) StatusChip(stringResource(R.string.item_variants_count, item.variantGroups.size), ChipTone.Info)
                    if (item.addonGroups.isNotEmpty()) StatusChip(stringResource(R.string.item_addons_count, item.addonGroups.size), ChipTone.Info)
                }
            }
            if (!selectionMode) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Switch(checked = item.isAvailable, onCheckedChange = onToggle, enabled = !busy)
                    IconButton(onClick = onDelete, enabled = !busy) { Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}
