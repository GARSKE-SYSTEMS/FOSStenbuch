package de.fosstenbuch.domain.usecase.location

import de.fosstenbuch.data.model.SavedLocation
import de.fosstenbuch.data.repository.SavedLocationRepository
import javax.inject.Inject

class InsertSavedLocationUseCase @Inject constructor(
    private val savedLocationRepository: SavedLocationRepository
) {
    sealed class Result {
        data class Success(val locationId: Long) : Result()
        data class Error(val exception: Exception) : Result()
    }

    suspend operator fun invoke(location: SavedLocation): Result {
        return try {
            if (location.name.isBlank()) {
                return Result.Error(IllegalArgumentException("Name darf nicht leer sein"))
            }
            val id = savedLocationRepository.insertSavedLocation(location)
            Result.Success(id)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}
