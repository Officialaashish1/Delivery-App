package `in`.localrush.partner.feature.gate.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.localrush.core.common.util.UiState
import `in`.localrush.core.designsystem.component.EmptyState
import `in`.localrush.partner.R
import `in`.localrush.partner.feature.common.ui.StateContent
import `in`.localrush.partner.feature.gate.vm.GateDestination
import `in`.localrush.partner.feature.gate.vm.GateViewModel

/** Loads the owner's restaurant and routes to Registration / Pending approval / Main. */
@Composable
fun GateScreen(onResolved: (GateDestination) -> Unit, viewModel: GateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()
    LaunchedEffect(state) { (state as? UiState.Success)?.let { onResolved(it.data) } }
    StateContent(state = state, online = online, onRetry = viewModel::load, empty = { EmptyState(stringResource(R.string.common_empty)) }) { }
}
