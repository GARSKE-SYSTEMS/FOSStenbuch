package de.fosstenbuch.ui.purposes

import de.fosstenbuch.data.model.TripPurpose

data class PurposeDetailUiState(
    val purpose: TripPurpose? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val savedSuccessfully: Boolean = false,
    val error: String? = null
)
