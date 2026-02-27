package de.fosstenbuch.domain.usecase.mileage

import de.fosstenbuch.data.repository.TripRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Calculates the German Entfernungspauschale (mileage allowance) for tax deductions.
 *
 * 2024 rates:
 * - First 20 km one-way distance: 0.30 €/km per working day
 * - From 21st km: 0.38 €/km per working day
 */
class CalculateMileageAllowanceUseCase @Inject constructor(
    private val tripRepository: TripRepository
) {
    companion object {
        const val STANDARD_RATE = 0.30 // €/km for first 20 km
        const val EXTENDED_RATE = 0.38 // €/km from 21st km
        const val THRESHOLD_KM = 20.0
        const val DEFAULT_WORKING_DAYS = 230
    }

    fun getBusinessDistanceForYear(year: Int): Flow<Double?> =
        tripRepository.getBusinessDistanceForYear(year)

    fun getBusinessTripCountForYear(year: Int): Flow<Int> =
        tripRepository.getBusinessTripCountForYear(year)

    /**
     * Calculate mileage allowance based on one-way distance and working days.
     * This is the manual calculation method.
     *
     * @param oneWayDistanceKm One-way commute distance in km
     * @param workingDays Number of working days in the year
     * @return Deductible amount in euros
     */
    fun calculate(oneWayDistanceKm: Double, workingDays: Int = DEFAULT_WORKING_DAYS): MileageResult {
        if (oneWayDistanceKm <= 0) {
            return MileageResult(0.0, 0.0, 0.0, workingDays, oneWayDistanceKm)
        }

        val standardKm = minOf(oneWayDistanceKm, THRESHOLD_KM)
        val extendedKm = maxOf(0.0, oneWayDistanceKm - THRESHOLD_KM)

        val standardAmount = standardKm * STANDARD_RATE * workingDays
        val extendedAmount = extendedKm * EXTENDED_RATE * workingDays
        val totalAmount = standardAmount + extendedAmount

        return MileageResult(
            totalAmount = totalAmount,
            standardAmount = standardAmount,
            extendedAmount = extendedAmount,
            workingDays = workingDays,
            oneWayDistanceKm = oneWayDistanceKm
        )
    }

    /**
     * Calculate from total annual business distance (round-trip).
     * Divides by 2 to get one-way distance, then by working days to get daily distance.
     */
    fun calculateFromTotalDistance(
        totalBusinessDistanceKm: Double,
        workingDays: Int = DEFAULT_WORKING_DAYS
    ): MileageResult {
        if (totalBusinessDistanceKm <= 0 || workingDays <= 0) {
            return MileageResult(0.0, 0.0, 0.0, workingDays, 0.0)
        }
        // Total distance is round-trip; one-way = total / 2
        // Average daily one-way = (total / 2) / workingDays
        val averageOneWayKm = (totalBusinessDistanceKm / 2.0) / workingDays
        return calculate(averageOneWayKm, workingDays)
    }
}

data class MileageResult(
    val totalAmount: Double,
    val standardAmount: Double,
    val extendedAmount: Double,
    val workingDays: Int,
    val oneWayDistanceKm: Double
)
