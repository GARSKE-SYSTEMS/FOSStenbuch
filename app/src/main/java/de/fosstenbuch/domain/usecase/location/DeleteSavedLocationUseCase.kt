package de.fosstenbuch.domain.usecase.location

import de.fosstenbuch.data.model.SavedLocation
import de.fosstenbuch.data.repository.SavedLocationRepository
import javax.inject.Inject

class DeleteSavedLocationUseCase @Inject constructor(
    private val savedLocationRepository: SavedLocationRepository
) {
    suspend operator fun invoke(location: SavedLocation) {
        savedLocationRepository.deleteSavedLocation(location)
    }
}
