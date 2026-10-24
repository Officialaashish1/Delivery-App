package `in`.localrush.rider.feature.profile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.localrush.core.designsystem.component.ErrorState
import `in`.localrush.core.designsystem.component.LoadingState
import `in`.localrush.rider.R
import `in`.localrush.rider.feature.onboarding.ui.DocumentCard
import `in`.localrush.rider.feature.onboarding.vm.RegistrationViewModel
import `in`.localrush.rider.ui.common.RiderTopBar

/** Standalone document manager (re-upload rejected/expired documents). Reuses the wizard's ViewModel. */
@Composable
fun DocumentsScreen(onBack: () -> Unit, viewModel: RegistrationViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { RiderTopBar(stringResource(R.string.profile_documents), onBack = onBack) }) { padding ->
        when {
            state.loading || state.docsLoading -> LoadingState(Modifier.padding(padding))
            state.loadError != null -> ErrorState(state.loadError!!, onRetry = viewModel::load, modifier = Modifier.padding(padding))
            else -> LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text(stringResource(R.string.documents_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(state.docs, key = { it.type.wire }) { row ->
                    DocumentCard(row, onUpload = { number, uri -> viewModel.uploadDocument(row.type, number, uri) }, onDelete = { viewModel.deleteDocument(row.type) })
                }
            }
        }
    }
}
