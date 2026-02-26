package de.fosstenbuch.ui.locations

import de.fosstenbuch.data.model.SavedLocation

data class SavedLocationsUiState(
    val locations: List<SavedLocation> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
) {
    val isEmpty: Boolean get() = !isLoading && error == null && locations.isEmpty()
}
