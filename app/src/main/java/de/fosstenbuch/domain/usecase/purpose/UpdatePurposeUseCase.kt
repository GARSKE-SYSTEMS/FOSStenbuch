package de.fosstenbuch.domain.usecase.purpose

import de.fosstenbuch.data.model.TripPurpose
import de.fosstenbuch.data.repository.TripPurposeRepository
import javax.inject.Inject

class UpdatePurposeUseCase @Inject constructor(
    private val tripPurposeRepository: TripPurposeRepository
) {
    sealed class Result {
        data object Success : Result()
        data class Error(val message: String) : Result()
    }

    suspend operator fun invoke(purpose: TripPurpose): Result {
        if (purpose.name.isBlank()) {
            return Result.Error("Name darf nicht leer sein")
        }

        // Check for duplicate name (excluding current purpose)
        val existing = tripPurposeRepository.getPurposeByName(purpose.name)
        if (existing != null && existing.id != purpose.id) {
            return Result.Error("Ein Zweck mit diesem Namen existiert bereits")
        }

        return try {
            tripPurposeRepository.updatePurpose(purpose)
            Result.Success
        } catch (e: Exception) {
            Result.Error("Zweck konnte nicht aktualisiert werden")
        }
    }
}
