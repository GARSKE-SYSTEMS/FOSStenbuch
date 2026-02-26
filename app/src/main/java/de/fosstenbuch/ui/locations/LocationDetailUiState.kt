package de.fosstenbuch.ui.locations

import de.fosstenbuch.data.model.SavedLocation

data class LocationDetailUiState(
    val location: SavedLocation? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val savedSuccessfully: Boolean = false,
    val error: String? = null
)
