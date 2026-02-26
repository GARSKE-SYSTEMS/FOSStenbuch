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
        businessTrip = true
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
}
