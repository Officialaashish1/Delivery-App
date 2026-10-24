package `in`.localrush.customer.feature.search.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.localrush.core.designsystem.component.EmptyState
import `in`.localrush.core.designsystem.component.ErrorState
import `in`.localrush.core.designsystem.component.LRBottomSheet
import `in`.localrush.core.designsystem.component.LRButton
import `in`.localrush.core.designsystem.component.LRButtonStyle
import `in`.localrush.core.designsystem.component.ShimmerListPlaceholder
import `in`.localrush.customer.R
import `in`.localrush.customer.data.repo.RestaurantFilters
import `in`.localrush.customer.data.repo.RestaurantSort
import `in`.localrush.customer.feature.search.vm.RestaurantListViewModel
import `in`.localrush.customer.ui.common.LRTopBar
import `in`.localrush.customer.ui.common.RestaurantRow

@Composable
fun RestaurantListScreen(onBack: () -> Unit, onRestaurant: (slug: String) -> Unit, viewModel: RestaurantListViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var filtersOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val nearEnd by remember { derivedStateOf { val info = listState.layoutInfo; info.visibleItemsInfo.lastOrNull()?.index ?: 0 >= info.totalItemsCount - 3 } }
    LaunchedEffect(nearEnd) { if (nearEnd) viewModel.loadMore() }

    Scaffold(topBar = {
        LRTopBar(state.title, onBack, actions = {
            IconButton(onClick = { filtersOpen = true }) {
                BadgedBox(badge = { if (state.filters.activeCount > 0) Badge { Text(state.filters.activeCount.toString()) } }) { Icon(Icons.Outlined.Tune, stringResource(R.string.filters_title)) }
            }
        })
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> ShimmerListPlaceholder(count = 6, modifier = Modifier.padding(top = 16.dp), thumbnail = 96.dp)
                state.error != null -> ErrorState(state.error!!, onRetry = viewModel::retry)
                state.items.isEmpty() -> EmptyState(stringResource(R.string.list_empty_title), description = stringResource(R.string.list_empty_body), icon = Icons.Outlined.Storefront,
                    actionText = if (state.filters.activeCount > 0) stringResource(R.string.filters_clear) else null, onAction = { viewModel.applyFilters(RestaurantFilters()) })
                else -> LazyColumn(state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.items, key = { it.id }) { c -> RestaurantRow(c, onClick = { onRestaurant(c.slug) }) }
                    if (state.loadingMore) item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                }
            }
        }
    }
    if (filtersOpen) FilterSheet(state.filters, onDismiss = { filtersOpen = false }, onApply = { viewModel.applyFilters(it); filtersOpen = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(initial: RestaurantFilters, onDismiss: () -> Unit, onApply: (RestaurantFilters) -> Unit) {
    var f by remember { mutableStateOf(initial) }
    LRBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.filters_title)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.filters_veg_only), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(f.vegOnly, { f = f.copy(vegOnly = it) })
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.filters_open_now), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(f.openNow, { f = f.copy(openNow = it) })
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.filters_rating), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(null, 3.5, 4.0, 4.5).forEach { r ->
                FilterChip(selected = f.minRating == r, onClick = { f = f.copy(minRating = r) }, label = { Text(if (r == null) stringResource(R.string.filters_any) else "$r+ ★") })
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.filters_sort), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        Column {
            RestaurantSort.entries.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    pair.forEach { s -> FilterChip(selected = f.sort == s, onClick = { f = f.copy(sort = s) }, label = { Text(sortLabel(s)) }) }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LRButton(stringResource(R.string.filters_clear), onClick = { f = RestaurantFilters() }, style = LRButtonStyle.Outlined, modifier = Modifier.weight(1f))
            LRButton(stringResource(R.string.filters_apply), onClick = { onApply(f) }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun sortLabel(s: RestaurantSort): String = stringResource(
    when (s) {
        RestaurantSort.RELEVANCE -> R.string.sort_relevance
        RestaurantSort.RATING -> R.string.sort_rating
        RestaurantSort.DELIVERY_TIME -> R.string.sort_delivery_time
        RestaurantSort.DISTANCE -> R.string.sort_distance
        RestaurantSort.COST_LOW -> R.string.sort_cost_low
        RestaurantSort.COST_HIGH -> R.string.sort_cost_high
    },
)
