package de.fosstenbuch.domain.usecase.trip

import de.fosstenbuch.data.model.Trip
import de.fosstenbuch.data.repository.TripRepository
import de.fosstenbuch.domain.usecase.location.ResolveLocationNameUseCase
import timber.log.Timber
import java.util.Date
import javax.inject.Inject

/**
 * Creates a ghost trip entry after an automatic Bluetooth-triggered tracking session ends.
 * Resolves start and end location names via [ResolveLocationNameUseCase].
 */
class CreateGhostTripUseCase @Inject constructor(
    private val tripRepository: TripRepository,
    private val resolveLocationNameUseCase: ResolveLocationNameUseCase
) {
    data class GhostTripInput(
        val vehicleId: Long,
        val startTime: Date,
        val endTime: Date,
        val startLat: Double,
        val startLng: Double,
        val endLat: Double,
        val endLng: Double,
        val gpsDistanceKm: Double
    )

    sealed class Result {
        data class Success(val tripId: Long) : Result()
        data class Error(val exception: Throwable) : Result()
    }

    suspend operator fun invoke(input: GhostTripInput): Result {
        return try {
            val startName = resolveLocationNameUseCase(input.startLat, input.startLng)
            val endName = resolveLocationNameUseCase(input.endLat, input.endLng)

            val ghost = Trip(
                date = input.startTime,
                endTime = input.endTime,
                startLocation = startName,
                endLocation = endName,
                startLatitude = input.startLat,
                startLongitude = input.startLng,
                endLatitude = input.endLat,
                endLongitude = input.endLng,
                gpsDistanceKm = input.gpsDistanceKm,
                distanceKm = 0.0,
                vehicleId = input.vehicleId,
                isActive = false,
                isGhost = true
            )

            val id = tripRepository.insertTrip(ghost)
            Timber.i("Ghost trip created: id=%d, %s → %s (%.1f km)", id, startName, endName, input.gpsDistanceKm)
            Result.Success(id)
        } catch (e: Exception) {
            Timber.e(e, "Failed to create ghost trip")
            Result.Error(e)
        }
    }
}
