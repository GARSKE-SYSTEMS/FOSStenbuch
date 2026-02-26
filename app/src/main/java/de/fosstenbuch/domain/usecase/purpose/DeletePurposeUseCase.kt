package de.fosstenbuch.domain.usecase.purpose

import de.fosstenbuch.data.model.TripPurpose
import de.fosstenbuch.data.repository.TripPurposeRepository
import javax.inject.Inject

class DeletePurposeUseCase @Inject constructor(
    private val tripPurposeRepository: TripPurposeRepository
) {
    sealed class Result {
        data object Success : Result()
        data class Error(val message: String) : Result()
    }

    suspend operator fun invoke(purpose: TripPurpose): Result {
        // Cannot delete default purposes
        if (purpose.isDefault) {
            return Result.Error("Standard-Zwecke können nicht gelöscht werden")
        }

        // Check if any trips use this purpose
        val tripCount = tripPurposeRepository.getTripCountForPurpose(purpose.id)
        if (tripCount > 0) {
            return Result.Error(
                "Dieser Zweck wird von $tripCount Fahrt(en) verwendet und kann nicht gelöscht werden"
            )
        }

        return try {
            tripPurposeRepository.deletePurpose(purpose)
            Result.Success
        } catch (e: Exception) {
            Result.Error("Zweck konnte nicht gelöscht werden")
        }
    }
}
