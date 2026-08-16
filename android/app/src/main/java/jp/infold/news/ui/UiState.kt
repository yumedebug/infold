package jp.infold.news.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.staticCompositionLocalOf

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Error(val message: String, val debug: String? = null) : UiState<Nothing>
    data class Ready<T>(val data: T) : UiState<T>
}

/** ルート Scaffold の Snackbar を画面から表示するためのローカル */
val LocalSnackbarHostState = staticCompositionLocalOf<SnackbarHostState> {
    error("SnackbarHostState not provided")
}
