package de.fosstenbuch.ui.common

/**
 * Generic sealed interface for representing UI state across all screens.
 * Type parameter [T] represents the data type for the success state.
 *
 * Usage example:
 * ```
 * val uiState: StateFlow<UiState<List<Trip>>> = ...
 * ```
 */
sealed interface UiState<out T> {
    /** Initial loading state, no data available yet. */
    object Loading : UiState<Nothing>

    /** Data loaded successfully. */
    data class Success<T>(val data: T) : UiState<T>

    /** No data available (e.g. empty list). */
    object Empty : UiState<Nothing>

    /** An error occurred. */
    data class Error(val message: String) : UiState<Nothing>
}
