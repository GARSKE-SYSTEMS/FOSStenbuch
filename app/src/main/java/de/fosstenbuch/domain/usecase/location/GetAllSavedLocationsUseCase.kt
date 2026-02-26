package de.fosstenbuch.domain.usecase.location

import de.fosstenbuch.data.model.SavedLocation
import de.fosstenbuch.data.repository.SavedLocationRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetAllSavedLocationsUseCase @Inject constructor(
    private val savedLocationRepository: SavedLocationRepository
) {
    operator fun invoke(): Flow<List<SavedLocation>> =
        savedLocationRepository.getAllSavedLocations()
}
