package de.fosstenbuch.domain.validation

import de.fosstenbuch.data.model.Vehicle
import javax.inject.Inject

/**
 * Validates Vehicle data before insert/update operations.
 * Checks required fields and format constraints.
 */
class VehicleValidator @Inject constructor() {

    companion object {
        const val FIELD_MAKE = "make"
        const val FIELD_MODEL = "model"
        const val FIELD_LICENSE_PLATE = "licensePlate"
        const val FIELD_FUEL_TYPE = "fuelType"

        private const val MAX_FIELD_LENGTH = 100
        // German license plate pattern: 1-3 letters, space, 1-2 letters, space, 1-4 digits
        // Also allows common formats like "B AB 1234", "M X 1", "HH AB 123"
        private val LICENSE_PLATE_PATTERN = Regex("^[A-ZÄÖÜ]{1,3}[\\s-][A-Z]{1,2}[\\s-]\\d{1,4}[EH]?$")
    }

    fun validate(vehicle: Vehicle): ValidationResult {
        val errors = mutableMapOf<String, String>()

        // Make
        if (vehicle.make.isBlank()) {
            errors[FIELD_MAKE] = "Hersteller darf nicht leer sein"
        } else if (vehicle.make.length > MAX_FIELD_LENGTH) {
            errors[FIELD_MAKE] = "Hersteller darf maximal $MAX_FIELD_LENGTH Zeichen lang sein"
        }

        // Model
        if (vehicle.model.isBlank()) {
            errors[FIELD_MODEL] = "Modell darf nicht leer sein"
        } else if (vehicle.model.length > MAX_FIELD_LENGTH) {
            errors[FIELD_MODEL] = "Modell darf maximal $MAX_FIELD_LENGTH Zeichen lang sein"
        }

        // License plate
        if (vehicle.licensePlate.isBlank()) {
            errors[FIELD_LICENSE_PLATE] = "Kennzeichen darf nicht leer sein"
        } else {
            val normalized = vehicle.licensePlate.trim().uppercase()
            if (!LICENSE_PLATE_PATTERN.matches(normalized)) {
                errors[FIELD_LICENSE_PLATE] = "Kennzeichen-Format ungültig (z. B. \"B AB 1234\")"
            }
        }

        // Fuel type
        if (vehicle.fuelType.isBlank()) {
            errors[FIELD_FUEL_TYPE] = "Kraftstoffart darf nicht leer sein"
        }

        return ValidationResult(errors)
    }
}
