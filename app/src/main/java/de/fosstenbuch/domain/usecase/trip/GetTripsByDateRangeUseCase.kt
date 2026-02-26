package de.fosstenbuch.domain.usecase.trip

import de.fosstenbuch.data.model.Trip
import de.fosstenbuch.data.repository.TripRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetTripsByDateRangeUseCase @Inject constructor(
    private val tripRepository: TripRepository
) {
    operator fun invoke(startDate: Long, endDate: Long): Flow<List<Trip>> =
        tripRepository.getTripsByDateRange(startDate, endDate)
}
