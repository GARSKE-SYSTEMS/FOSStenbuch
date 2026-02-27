package de.fosstenbuch.domain.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationResultTest {

    @Test
    fun `valid result has no errors`() {
        val result = ValidationResult.valid()

        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `invalid result with single error`() {
        val result = ValidationResult.invalid("field1" to "Error message")

        assertFalse(result.isValid)
        assertEquals(1, result.errors.size)
        assertEquals("Error message", result.errorFor("field1"))
    }

    @Test
    fun `invalid result with multiple errors`() {
        val result = ValidationResult.invalid(
            "field1" to "Error 1",
            "field2" to "Error 2",
            "field3" to "Error 3"
        )

        assertFalse(result.isValid)
        assertEquals(3, result.errors.size)
        assertEquals("Error 1", result.errorFor("field1"))
        assertEquals("Error 2", result.errorFor("field2"))
        assertEquals("Error 3", result.errorFor("field3"))
    }

    @Test
    fun `errorFor returns null for non-existent field`() {
        val result = ValidationResult.invalid("field1" to "Error")

        assertNull(result.errorFor("nonExistent"))
    }

    @Test
    fun `empty errors map is valid`() {
        val result = ValidationResult(emptyMap())

        assertTrue(result.isValid)
    }

    @Test
    fun `default constructor creates valid result`() {
        val result = ValidationResult()

        assertTrue(result.isValid)
    }

    @Test
    fun `non-empty errors map is invalid`() {
        val result = ValidationResult(mapOf("field1" to "Error"))

        assertFalse(result.isValid)
    }
}
