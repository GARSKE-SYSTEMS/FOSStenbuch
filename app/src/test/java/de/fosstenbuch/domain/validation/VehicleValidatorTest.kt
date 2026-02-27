package de.fosstenbuch.domain.validation

import de.fosstenbuch.data.model.Vehicle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VehicleValidatorTest {

    private lateinit var validator: VehicleValidator

    @Before
    fun setup() {
        validator = VehicleValidator()
    }

    private fun validVehicle() = Vehicle(
        make = "Volkswagen",
        model = "Golf",
        licensePlate = "B AB 1234",
        fuelType = "Diesel"
    )

    @Test
    fun `valid vehicle passes validation`() {
        val result = validator.validate(validVehicle())
        assertTrue(result.isValid)
    }

    @Test
    fun `blank make fails`() {
        val result = validator.validate(validVehicle().copy(make = ""))
        assertFalse(result.isValid)
        assertNotNull(result.errorFor(VehicleValidator.FIELD_MAKE))
    }

    @Test
    fun `blank model fails`() {
        val result = validator.validate(validVehicle().copy(model = ""))
        assertFalse(result.isValid)
        assertNotNull(result.errorFor(VehicleValidator.FIELD_MODEL))
    }

    @Test
    fun `blank license plate fails`() {
        val result = validator.validate(validVehicle().copy(licensePlate = ""))
        assertFalse(result.isValid)
        assertNotNull(result.errorFor(VehicleValidator.FIELD_LICENSE_PLATE))
    }

    @Test
    fun `invalid license plate format fails`() {
        val result = validator.validate(validVehicle().copy(licensePlate = "12345"))
        assertFalse(result.isValid)
        assertNotNull(result.errorFor(VehicleValidator.FIELD_LICENSE_PLATE))
    }

    @Test
    fun `valid short license plate passes`() {
        val result = validator.validate(validVehicle().copy(licensePlate = "M X 1"))
        assertTrue(result.isValid)
    }

    @Test
    fun `valid license plate with E suffix passes`() {
        val result = validator.validate(validVehicle().copy(licensePlate = "HH AB 123E"))
        assertTrue(result.isValid)
    }

    @Test
    fun `valid license plate with dash separator passes`() {
        val result = validator.validate(validVehicle().copy(licensePlate = "HH-AB-1234"))
        assertTrue(result.isValid)
    }

    @Test
    fun `blank fuel type fails`() {
        val result = validator.validate(validVehicle().copy(fuelType = ""))
        assertFalse(result.isValid)
        assertNotNull(result.errorFor(VehicleValidator.FIELD_FUEL_TYPE))
    }

    @Test
    fun `make exceeding max length fails`() {
        val longMake = "A".repeat(101)
        val result = validator.validate(validVehicle().copy(make = longMake))
        assertFalse(result.isValid)
        assertNotNull(result.errorFor(VehicleValidator.FIELD_MAKE))
    }

    @Test
    fun `model exceeding max length fails`() {
        val longModel = "A".repeat(101)
        val result = validator.validate(validVehicle().copy(model = longModel))
        assertFalse(result.isValid)
        assertNotNull(result.errorFor(VehicleValidator.FIELD_MODEL))
    }

    @Test
    fun `make at exactly max length passes`() {
        val maxMake = "A".repeat(100)
        val result = validator.validate(validVehicle().copy(make = maxMake))
        assertTrue(result.isValid || result.errorFor(VehicleValidator.FIELD_MAKE) == null)
    }

    @Test
    fun `multiple validation errors returned at once`() {
        val result = validator.validate(validVehicle().copy(
            make = "",
            model = "",
            licensePlate = "",
            fuelType = ""
        ))
        assertFalse(result.isValid)
        assertEquals(4, result.errors.size)
    }

    @Test
    fun `license plate with H suffix passes`() {
        val result = validator.validate(validVehicle().copy(licensePlate = "B AB 1234H"))
        assertTrue(result.isValid)
    }
}
