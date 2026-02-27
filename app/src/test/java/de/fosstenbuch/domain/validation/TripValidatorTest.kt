package de.fosstenbuch.domain.validation

import de.fosstenbuch.data.model.Trip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.Date

class TripValidatorTest {

    private lateinit var validator: TripValidator

    @Before
    fun setup() {
        validator = TripValidator()
    }

    private fun validTrip() = Trip(
        date = Date(),
        startLocation = "Berlin",
        endLocation = "Hamburg",
        distanceKm = 280.0,
        purpose = "Kundentermin",
        purposeId = 1L
    )

    @Test
    fun `valid trip passes validation`() {
        val result = validator.validate(validTrip())
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `blank start location fails`() {
        val result = validator.validate(validTrip().copy(startLocation = ""))
        assertFalse(result.isValid)
        assertNotNull(result.errorFor(TripValidator.FIELD_START_LOCATION))
    }

    @Test
    fun `blank end location fails`() {
        val result = validator.validate(validTrip().copy(endLocation = "  "))
        assertFalse(result.isValid)
        assertNotNull(result.errorFor(TripValidator.FIELD_END_LOCATION))
    }

    @Test
    fun `zero distance fails`() {
        val result = validator.validate(validTrip().copy(distanceKm = 0.0))
        assertFalse(result.isValid)
        assertNotNull(result.errorFor(TripValidator.FIELD_DISTANCE))
    }

    @Test
    fun `negative distance fails`() {
        val result = validator.validate(validTrip().copy(distanceKm = -5.0))
        assertFalse(result.isValid)
        assertNotNull(result.errorFor(TripValidator.FIELD_DISTANCE))
    }

    @Test
    fun `excessive distance fails`() {
        val result = validator.validate(validTrip().copy(distanceKm = 100_000.0))
        assertFalse(result.isValid)
        assertNotNull(result.errorFor(TripValidator.FIELD_DISTANCE))
    }

    @Test
    fun `blank purpose fails`() {
        val result = validator.validate(validTrip().copy(purpose = ""))
        assertFalse(result.isValid)
        assertNotNull(result.errorFor(TripValidator.FIELD_PURPOSE))
    }

    @Test
    fun `future date fails`() {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_MONTH, 7)
        val result = validator.validate(validTrip().copy(date = cal.time))
        assertFalse(result.isValid)
        assertNotNull(result.errorFor(TripValidator.FIELD_DATE))
    }

    @Test
    fun `odometer end less than start fails`() {
        val result = validator.validate(validTrip().copy(startOdometer = 50000, endOdometer = 49000))
        assertFalse(result.isValid)
        assertNotNull(result.errorFor(TripValidator.FIELD_ODOMETER))
    }

    @Test
    fun `only start odometer set fails`() {
        val result = validator.validate(validTrip().copy(startOdometer = 50000, endOdometer = null))
        assertFalse(result.isValid)
        assertNotNull(result.errorFor(TripValidator.FIELD_ODOMETER))
    }

    @Test
    fun `both odometers null passes`() {
        val result = validator.validate(validTrip().copy(startOdometer = null, endOdometer = null))
        assertNull(result.errorFor(TripValidator.FIELD_ODOMETER))
    }

    @Test
    fun `valid odometer range passes`() {
        val result = validator.validate(validTrip().copy(startOdometer = 50000, endOdometer = 50280))
        assertNull(result.errorFor(TripValidator.FIELD_ODOMETER))
    }

    @Test
    fun `multiple errors returned at once`() {
        val trip = validTrip().copy(
            startLocation = "",
            endLocation = "",
            distanceKm = -1.0,
            purpose = ""
        )
        val result = validator.validate(trip)
        assertFalse(result.isValid)
        assertEquals(4, result.errors.size)
    }

    @Test
    fun `start location exceeding max length fails`() {
        val longLocation = "A".repeat(201)
        val result = validator.validate(validTrip().copy(startLocation = longLocation))
        assertFalse(result.isValid)
        assertNotNull(result.errorFor(TripValidator.FIELD_START_LOCATION))
    }

    @Test
    fun `end location exceeding max length fails`() {
        val longLocation = "A".repeat(201)
        val result = validator.validate(validTrip().copy(endLocation = longLocation))
        assertFalse(result.isValid)
        assertNotNull(result.errorFor(TripValidator.FIELD_END_LOCATION))
    }

    @Test
    fun `purpose exceeding max length fails`() {
        val longPurpose = "A".repeat(201)
        val result = validator.validate(validTrip().copy(purpose = longPurpose))
        assertFalse(result.isValid)
        assertNotNull(result.errorFor(TripValidator.FIELD_PURPOSE))
    }

    @Test
    fun `null purposeId returns validation error`() {
        val result = validator.validate(validTrip().copy(purposeId = null))
        assertFalse(result.isValid)
        assertNotNull(result.errorFor(TripValidator.FIELD_PURPOSE_ID))
    }

    @Test
    fun `start location at exactly max length passes`() {
        val maxLocation = "A".repeat(200)
        val result = validator.validate(validTrip().copy(startLocation = maxLocation))
        assertNull(result.errorFor(TripValidator.FIELD_START_LOCATION))
    }

    @Test
    fun `only end odometer set fails`() {
        val result = validator.validate(validTrip().copy(startOdometer = null, endOdometer = 50000))
        assertFalse(result.isValid)
        assertNotNull(result.errorFor(TripValidator.FIELD_ODOMETER))
    }

    // ===== validateStart tests =====

    private fun validStartTrip() = Trip(
        date = Date(),
        startLocation = "Berlin",
        startOdometer = 50000
    )

    @Test
    fun `validateStart passes for valid start trip`() {
        val result = validator.validateStart(validStartTrip())
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `validateStart blank start location fails`() {
        val result = validator.validateStart(validStartTrip().copy(startLocation = ""))
        assertFalse(result.isValid)
        assertNotNull(result.errorFor(TripValidator.FIELD_START_LOCATION))
    }

    @Test
    fun `validateStart long start location fails`() {
        val result = validator.validateStart(validStartTrip().copy(startLocation = "A".repeat(201)))
        assertFalse(result.isValid)
        assertNotNull(result.errorFor(TripValidator.FIELD_START_LOCATION))
    }

    @Test
    fun `validateStart null odometer fails`() {
        val result = validator.validateStart(validStartTrip().copy(startOdometer = null))
        assertFalse(result.isValid)
        assertNotNull(result.errorFor(TripValidator.FIELD_START_ODOMETER))
    }

    @Test
    fun `validateStart negative odometer fails`() {
        val result = validator.validateStart(validStartTrip().copy(startOdometer = -1))
        assertFalse(result.isValid)
        assertNotNull(result.errorFor(TripValidator.FIELD_START_ODOMETER))
    }

    @Test
    fun `validateStart zero odometer passes`() {
        val result = validator.validateStart(validStartTrip().copy(startOdometer = 0))
        assertTrue(result.isValid)
    }

    @Test
    fun `validateStart multiple errors returned`() {
        val trip = validStartTrip().copy(startLocation = "", startOdometer = null)
        val result = validator.validateStart(trip)
        assertFalse(result.isValid)
        assertEquals(2, result.errors.size)
    }
}
