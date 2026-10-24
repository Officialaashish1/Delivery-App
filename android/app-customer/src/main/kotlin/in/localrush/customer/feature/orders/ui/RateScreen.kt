package `in`.localrush.customer.feature.orders.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.localrush.core.designsystem.component.ErrorState
import `in`.localrush.core.designsystem.component.InlineError
import `in`.localrush.core.designsystem.component.LRButton
import `in`.localrush.core.designsystem.component.LRCard
import `in`.localrush.core.designsystem.component.LRFooter
import `in`.localrush.core.designsystem.component.LRImage
import `in`.localrush.core.designsystem.component.LRTextField
import `in`.localrush.core.designsystem.component.LoadingState
import `in`.localrush.core.designsystem.component.RatingBar
import `in`.localrush.core.designsystem.component.VegNonVegDot
import `in`.localrush.customer.R
import `in`.localrush.customer.feature.orders.vm.RateEvent
import `in`.localrush.customer.feature.orders.vm.RateViewModel
import `in`.localrush.customer.ui.common.LRTopBar

private val TAGS = listOf("tasty", "fresh", "well_packed", "on_time", "polite_rider", "value_for_money", "late", "cold_food", "missing_items")

@Composable
fun RateScreen(onBack: () -> Unit, onDone: () -> Unit, viewModel: RateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.events.collect { if (it == RateEvent.Done) onDone() } }
    Scaffold(
        topBar = { LRTopBar(stringResource(R.string.rate_title), onBack) },
        bottomBar = { if (state.order != null) LRFooter { Column { state.submitError?.let { InlineError(it, Modifier.padding(bottom = 8.dp)) }; LRButton(stringResource(R.string.rate_submit), onClick = viewModel::submit, modifier = Modifier.fillMaxWidth(), enabled = state.canSubmit, loading = state.submitting) } } },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val o = state.order
            when {
                state.loading -> LoadingState()
                state.error != null -> ErrorState(state.error!!, onRetry = viewModel::load)
                o != null -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        LRCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LRImage(o.restaurantLogo, o.restaurantName, Modifier.size(48.dp), radius = 12.dp); Spacer(Modifier.width(12.dp))
                                Column { Text(o.restaurantName, style = MaterialTheme.typography.titleMedium); Text(stringResource(R.string.rate_restaurant_q), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                            Spacer(Modifier.height(10.dp))
                            RatingBar(state.restaurantRating.toFloat(), starSize = 40.dp, onRate = viewModel::rateRestaurant)
                            Spacer(Modifier.height(10.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Column { TAGS.chunked(3).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { row.forEach { t -> FilterChip(selected = t in state.tags, onClick = { viewModel.toggleTag(t) }, label = { Text(t.replace('_', ' ')) }) } } } }
                            }
                            Spacer(Modifier.height(10.dp))
                            LRTextField(state.restaurantComment, viewModel::restaurantComment, placeholder = stringResource(R.string.rate_comment_hint), singleLine = false, maxLines = 4)
                        }
                    }
                    o.rider?.let { r ->
                        item {
                            LRCard {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    LRImage(r.photo, r.fullName, Modifier.size(48.dp), radius = 24.dp); Spacer(Modifier.width(12.dp))
                                    Column { Text(r.fullName, style = MaterialTheme.typography.titleMedium); Text(stringResource(R.string.rate_rider_q), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                }
                                Spacer(Modifier.height(10.dp))
                                RatingBar(state.riderRating.toFloat(), starSize = 36.dp, onRate = viewModel::rateRider)
                                Spacer(Modifier.height(10.dp))
                                LRTextField(state.riderComment, viewModel::riderComment, placeholder = stringResource(R.string.rate_rider_comment_hint), singleLine = false, maxLines = 3)
                            }
                        }
                    }
                    item { Text(stringResource(R.string.rate_items_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp)) }
                    items(o.items.filter { it.menuItem != null }, key = { it.id }) { item ->
                        LRCard(contentPadding = PaddingValues(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                VegNonVegDot(item.foodType); Spacer(Modifier.width(8.dp))
                                Text(item.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                RatingBar((state.itemRatings[item.menuItem!!] ?: 0).toFloat(), starSize = 22.dp, onRate = { viewModel.rateItem(item.menuItem!!, it) })
                            }
                        }
                    }
                }
            }
        }
    }
}
