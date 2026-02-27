package de.fosstenbuch.domain.validation

import de.fosstenbuch.data.model.Trip
import java.util.Date
import javax.inject.Inject

/**
 * Validates Trip data before insert/update operations.
 * Checks required fields, value ranges, and logical consistency.
 */
class TripValidator @Inject constructor() {

    companion object {
        const val FIELD_START_LOCATION = "startLocation"
        const val FIELD_END_LOCATION = "endLocation"
        const val FIELD_DISTANCE = "distanceKm"
        const val FIELD_PURPOSE = "purpose"
        const val FIELD_PURPOSE_ID = "purposeId"
        const val FIELD_DATE = "date"
        const val FIELD_ODOMETER = "odometer"
        const val FIELD_START_ODOMETER = "startOdometer"
        const val FIELD_VEHICLE = "vehicle"

        private const val MAX_DISTANCE_KM = 99_999.0
        private const val MAX_LOCATION_LENGTH = 200
        private const val MAX_PURPOSE_LENGTH = 200
    }

    /**
     * Validates only the fields required when starting a trip:
     * startLocation, startOdometer, and date.
     */
    fun validateStart(trip: Trip): ValidationResult {
        val errors = mutableMapOf<String, String>()

        // Start location
        if (trip.startLocation.isBlank()) {
            errors[FIELD_START_LOCATION] = "Startort darf nicht leer sein"
        } else if (trip.startLocation.length > MAX_LOCATION_LENGTH) {
            errors[FIELD_START_LOCATION] = "Startort darf maximal $MAX_LOCATION_LENGTH Zeichen lang sein"
        }

        // Start odometer is required for start phase
        if (trip.startOdometer == null) {
            errors[FIELD_START_ODOMETER] = "Kilometerstand ist erforderlich"
        } else if (trip.startOdometer < 0) {
            errors[FIELD_START_ODOMETER] = "Kilometerstand darf nicht negativ sein"
        }

        // Vehicle is required
        if (trip.vehicleId == null) {
            errors[FIELD_VEHICLE] = "Bitte wählen Sie ein Fahrzeug aus"
        }

        return ValidationResult(errors)
    }

    /**
     * Validates all fields for a completed trip (end phase or full edit).
     */

    fun validate(trip: Trip): ValidationResult {
        val errors = mutableMapOf<String, String>()

        // Start location
        if (trip.startLocation.isBlank()) {
            errors[FIELD_START_LOCATION] = "Startort darf nicht leer sein"
        } else if (trip.startLocation.length > MAX_LOCATION_LENGTH) {
            errors[FIELD_START_LOCATION] = "Startort darf maximal $MAX_LOCATION_LENGTH Zeichen lang sein"
        }

        // End location
        if (trip.endLocation.isBlank()) {
            errors[FIELD_END_LOCATION] = "Zielort darf nicht leer sein"
        } else if (trip.endLocation.length > MAX_LOCATION_LENGTH) {
            errors[FIELD_END_LOCATION] = "Zielort darf maximal $MAX_LOCATION_LENGTH Zeichen lang sein"
        }

        // Distance
        if (trip.distanceKm <= 0) {
            errors[FIELD_DISTANCE] = "Distanz muss größer als 0 sein"
        } else if (trip.distanceKm > MAX_DISTANCE_KM) {
            errors[FIELD_DISTANCE] = "Distanz darf maximal $MAX_DISTANCE_KM km betragen"
        }

        // Purpose (freetext description) – optional, but limited in length
        if (trip.purpose.length > MAX_PURPOSE_LENGTH) {
            errors[FIELD_PURPOSE] = "Zweck darf maximal $MAX_PURPOSE_LENGTH Zeichen lang sein"
        }

        // Purpose category – optional
        // (purposeId may be null if user didn't select a category)

        // Date (not in the future)
        if (trip.date.after(Date())) {
            errors[FIELD_DATE] = "Datum darf nicht in der Zukunft liegen"
        }

        // Odometer: both must be set (distance is computed from odometers)
        val start = trip.startOdometer
        val end = trip.endOdometer
        if (start == null || end == null) {
            errors[FIELD_ODOMETER] = "Start- und Endkilometerstand müssen angegeben werden"
        } else if (end <= start) {
            errors[FIELD_ODOMETER] = "Endkilometerstand muss größer als Startkilometerstand sein"
        }

        // Vehicle is required
        if (trip.vehicleId == null) {
            errors[FIELD_VEHICLE] = "Bitte wählen Sie ein Fahrzeug aus"
        }

        return ValidationResult(errors)
    }
}
