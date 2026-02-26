package de.fosstenbuch.ui.purposes

import de.fosstenbuch.data.model.TripPurpose

data class PurposesUiState(
    val purposes: List<TripPurpose> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
) {
    val isEmpty: Boolean get() = !isLoading && error == null && purposes.isEmpty()
}
