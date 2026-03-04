package de.fosstenbuch.domain.usecase.trip

import de.fosstenbuch.data.local.TripDao
import de.fosstenbuch.data.model.Trip
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Returns a live [Flow] of all ghost trips, ordered by date descending. */
class GetGhostTripsUseCase @Inject constructor(
    private val tripDao: TripDao
) {
    operator fun invoke(): Flow<List<Trip>> = tripDao.getGhostTrips()
}
