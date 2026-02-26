package de.fosstenbuch.ui.vehicles

import de.fosstenbuch.data.model.Vehicle

/**
 * UI state for the Vehicles list screen.
 */
data class VehiclesUiState(
    val vehicles: List<Vehicle> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
) {
    val isEmpty: Boolean get() = !isLoading && error == null && vehicles.isEmpty()
}
