package `in`.localrush.rider.feature.earnings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.localrush.core.designsystem.component.EmptyState
import `in`.localrush.core.designsystem.component.ErrorState
import `in`.localrush.core.designsystem.component.ShimmerListPlaceholder
import `in`.localrush.rider.R
import `in`.localrush.rider.feature.earnings.vm.PayoutsViewModel
import `in`.localrush.rider.ui.common.RiderTopBar

@Composable
fun PayoutsScreen(onBack: () -> Unit, viewModel: PayoutsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { RiderTopBar(stringResource(R.string.payouts_title), onBack = onBack) }) { padding ->
        when {
            state.loading -> ShimmerListPlaceholder(count = 6, modifier = Modifier.padding(padding).padding(16.dp))
            state.error != null -> ErrorState(state.error!!, onRetry = viewModel::load, modifier = Modifier.padding(padding))
            state.items.isEmpty() -> EmptyState(title = stringResource(R.string.payouts_empty), description = stringResource(R.string.payouts_empty_hint), modifier = Modifier.padding(padding))
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.items, key = { it.id }) { PayoutRow(it) }
                if (state.hasMore) item { androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.loadMore() }; ShimmerListPlaceholder(count = 1) }
            }
        }
    }
}
