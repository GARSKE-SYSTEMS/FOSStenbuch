package de.fosstenbuch.domain.usecase.purpose

import de.fosstenbuch.data.model.TripPurpose
import de.fosstenbuch.data.repository.TripPurposeRepository
import javax.inject.Inject

class InsertPurposeUseCase @Inject constructor(
    private val tripPurposeRepository: TripPurposeRepository
) {
    sealed class Result {
        data class Success(val purposeId: Long) : Result()
        data class Error(val message: String) : Result()
    }

    suspend operator fun invoke(purpose: TripPurpose): Result {
        if (purpose.name.isBlank()) {
            return Result.Error("Name darf nicht leer sein")
        }

        // Check for duplicate name
        val existing = tripPurposeRepository.getPurposeByName(purpose.name)
        if (existing != null) {
            return Result.Error("Ein Zweck mit diesem Namen existiert bereits")
        }

        return try {
            val id = tripPurposeRepository.insertPurpose(purpose)
            Result.Success(id)
        } catch (e: Exception) {
            Result.Error("Zweck konnte nicht gespeichert werden")
        }
    }
}
