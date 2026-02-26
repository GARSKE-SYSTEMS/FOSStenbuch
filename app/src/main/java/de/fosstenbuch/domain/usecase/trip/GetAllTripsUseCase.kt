package de.fosstenbuch.domain.usecase.trip

import de.fosstenbuch.data.model.Trip
import de.fosstenbuch.data.repository.TripRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetAllTripsUseCase @Inject constructor(
    private val tripRepository: TripRepository
) {
    operator fun invoke(): Flow<List<Trip>> = tripRepository.getAllTrips()
}
