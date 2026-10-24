package `in`.localrush.core.common.util

import `in`.localrush.core.common.result.AppError

/**
 * Generic screen state. Feature ViewModels may define richer sealed classes, but every screen
 * must at least represent Loading / Empty / Error / Success (see CONVENTIONS.md).
 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data object Empty : UiState<Nothing>
    data class Error(val error: AppError, val retryable: Boolean = true) : UiState<Nothing>
    data class Success<T>(val data: T, val refreshing: Boolean = false) : UiState<T>
}

/** Single-shot UI events (snackbar, navigation). */
sealed interface UiEvent {
    data class ShowMessage(val message: String) : UiEvent
    data class ShowError(val error: AppError) : UiEvent
    data class Navigate(val route: String) : UiEvent
    data object NavigateBack : UiEvent
}
