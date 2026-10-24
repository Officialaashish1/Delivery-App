package `in`.localrush.customer.feature.search.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.localrush.core.common.money.toMoney
import `in`.localrush.core.designsystem.component.EmptyState
import `in`.localrush.core.designsystem.component.ErrorState
import `in`.localrush.core.designsystem.component.LRCard
import `in`.localrush.core.designsystem.component.LRImage
import `in`.localrush.core.designsystem.component.LRTextField
import `in`.localrush.core.designsystem.component.PriceText
import `in`.localrush.core.designsystem.component.VegNonVegDot
import `in`.localrush.customer.R
import `in`.localrush.customer.data.dto.MenuItemDto
import `in`.localrush.customer.feature.search.vm.SearchTab
import `in`.localrush.customer.feature.search.vm.SearchViewModel
import `in`.localrush.customer.ui.common.RestaurantRow

@Composable
fun SearchScreen(
    onBack: (() -> Unit)?,
    onRestaurant: (slug: String) -> Unit,
    onItem: (restaurantSlug: String, itemId: String) -> Unit,
    onCuisine: (slug: String, name: String) -> Unit,
    onSeeAllRestaurants: (query: String) -> Unit,
    autoFocus: Boolean = true,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (autoFocus) runCatching { focus.requestFocus() } }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
            LRTextField(
                value = state.query, onValueChange = viewModel::onQueryChange, modifier = Modifier.weight(1f).focusRequester(focus),
                placeholder = stringResource(R.string.search_hint), leadingIcon = Icons.Default.Search,
                trailingIcon = if (state.query.isNotEmpty()) ({ IconButton(onClick = { viewModel.onQueryChange("") }) { Icon(Icons.Default.Close, stringResource(R.string.action_clear)) } }) else null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { viewModel.submit() }),
            )
            Spacer(Modifier.width(8.dp))
        }
        if (state.searching) LinearProgressIndicator(Modifier.fillMaxWidth())

        when {
            !state.hasQuery -> RecentSearches(state.recent, viewModel::pickRecent, viewModel::removeRecent, viewModel::clearRecent)
            state.error != null && state.result == null -> ErrorState(state.error!!, onRetry = viewModel::retry)
            state.result == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.isEmptyResult -> EmptyState(stringResource(R.string.search_no_results_title), description = stringResource(R.string.search_no_results_body, state.query), icon = Icons.Outlined.SearchOff)
            else -> {
                val r = state.result!!
                TabRow(selectedTabIndex = state.tab.ordinal, containerColor = MaterialTheme.colorScheme.background) {
                    Tab(state.tab == SearchTab.RESTAURANTS, { viewModel.onTab(SearchTab.RESTAURANTS) }, text = { Text(stringResource(R.string.search_tab_restaurants, r.restaurants.size)) })
                    Tab(state.tab == SearchTab.DISHES, { viewModel.onTab(SearchTab.DISHES) }, text = { Text(stringResource(R.string.search_tab_dishes, r.items.size)) })
                    Tab(state.tab == SearchTab.CUISINES, { viewModel.onTab(SearchTab.CUISINES) }, text = { Text(stringResource(R.string.search_tab_cuisines, r.cuisines.size)) })
                }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (state.tab) {
                        SearchTab.RESTAURANTS -> {
                            if (r.restaurants.isEmpty()) item { TabEmpty(stringResource(R.string.search_tab_empty)) }
                            items(r.restaurants, key = { it.id }) { c -> RestaurantRow(c, onClick = { onRestaurant(c.slug) }) }
                            if (r.restaurants.isNotEmpty()) item { TextButton(onClick = { onSeeAllRestaurants(state.query) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.search_see_all_with_filters)) } }
                        }
                        SearchTab.DISHES -> {
                            if (r.items.isEmpty()) item { TabEmpty(stringResource(R.string.search_tab_empty)) }
                            items(r.items, key = { it.id }) { item -> DishRow(item) { onItem(item.restaurantSlug, item.id) } }
                        }
                        SearchTab.CUISINES -> {
                            if (r.cuisines.isEmpty()) item { TabEmpty(stringResource(R.string.search_tab_empty)) }
                            items(r.cuisines, key = { it.id }) { c ->
                                LRCard(onClick = { onCuisine(c.slug, c.name) }) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (!c.icon.isNullOrBlank()) { LRImage(c.icon, null, Modifier.size(40.dp), radius = 20.dp); Spacer(Modifier.width(12.dp)) }
                                        Text(c.name, style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabEmpty(text: String) {
    Text(text, Modifier.fillMaxWidth().padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun DishRow(item: MenuItemDto, onClick: () -> Unit) {
    LRCard(onClick = onClick, contentPadding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LRImage(item.image, item.name, Modifier.size(72.dp), radius = 12.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) { VegNonVegDot(item.foodType, size = 12.dp); Spacer(Modifier.width(6.dp)); Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Text(item.restaurantName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                PriceText(item.effectivePrice.toMoney(), originalPrice = item.discountedPrice?.let { item.price.toMoney() }, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun RecentSearches(recent: List<String>, onPick: (String) -> Unit, onRemove: (String) -> Unit, onClear: () -> Unit) {
    if (recent.isEmpty()) {
        EmptyState(stringResource(R.string.search_start_title), description = stringResource(R.string.search_start_body), icon = Icons.Default.Search)
        return
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.search_recent), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onClear) { Text(stringResource(R.string.action_clear)) }
        }
        recent.forEach { q ->
            Row(Modifier.fillMaxWidth().clickable { onPick(q) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.History, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                Spacer(Modifier.width(12.dp))
                Text(q, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { onRemove(q) }) { Icon(Icons.Default.Close, stringResource(R.string.action_remove), Modifier.size(18.dp)) }
            }
        }
    }
}
