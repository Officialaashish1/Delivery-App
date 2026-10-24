package `in`.localrush.rider.feature.onboarding.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.localrush.core.designsystem.component.ErrorState
import `in`.localrush.rider.feature.onboarding.vm.Destination
import `in`.localrush.rider.feature.onboarding.vm.GateState
import `in`.localrush.rider.feature.onboarding.vm.GateViewModel

/** Post-login router: loads the rider profile and sends the user to registration / pending / main. */
@Composable
fun GateScreen(onDecided: (Destination) -> Unit, viewModel: GateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state) { (state as? GateState.Go)?.let { onDecided(it.destination) } }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (val s = state) {
            is GateState.Error -> ErrorState(s.error, onRetry = viewModel::load)
            else -> CircularProgressIndicator()
        }
    }
}
