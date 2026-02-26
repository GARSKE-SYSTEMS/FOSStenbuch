package de.fosstenbuch.domain.usecase.stats

import de.fosstenbuch.data.repository.TripRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/**
 * Returns the total distance broken down by trip type (business vs. private).
 */
class GetDistanceByTypeUseCase @Inject constructor(
    private val tripRepository: TripRepository
) {
    data class DistanceByType(
        val businessDistanceKm: Double,
        val privateDistanceKm: Double
    ) {
        val totalDistanceKm: Double get() = businessDistanceKm + privateDistanceKm
    }

    operator fun invoke(): Flow<DistanceByType> =
        combine(
            tripRepository.getTotalBusinessDistance(),
            tripRepository.getTotalPrivateDistance()
        ) { business, private_ ->
            DistanceByType(
                businessDistanceKm = business ?: 0.0,
                privateDistanceKm = private_ ?: 0.0
            )
        }
}
