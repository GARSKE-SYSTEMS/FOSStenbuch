package de.fosstenbuch.domain.usecase.purpose

import de.fosstenbuch.data.model.TripPurpose
import de.fosstenbuch.data.repository.TripPurposeRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetAllPurposesUseCase @Inject constructor(
    private val tripPurposeRepository: TripPurposeRepository
) {
    operator fun invoke(): Flow<List<TripPurpose>> =
        tripPurposeRepository.getAllPurposes()
}
