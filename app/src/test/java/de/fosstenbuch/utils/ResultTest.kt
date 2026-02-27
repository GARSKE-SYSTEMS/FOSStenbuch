package de.fosstenbuch.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultTest {

    @Test
    fun `success contains data`() {
        val result = Result.success("hello")
        assertTrue(result.isSuccess)
        assertFalse(result.isError)
        assertFalse(result.isLoading)
        assertEquals("hello", result.getOrNull())
    }

    @Test
    fun `error contains message`() {
        val result = Result.error("something failed")
        assertTrue(result.isError)
        assertFalse(result.isSuccess)
        assertEquals("something failed", result.errorMessageOrNull())
    }

    @Test
    fun `error from exception`() {
        val exception = RuntimeException("DB failed")
        val result = Result.error(exception)
        assertTrue(result.isError)
        assertNull(result.getOrNull())
    }

    @Test
    fun `loading state`() {
        val result = Result.loading()
        assertTrue(result.isLoading)
        assertFalse(result.isSuccess)
        assertFalse(result.isError)
    }

    @Test
    fun `getOrDefault returns data on success`() {
        val result = Result.success(42)
        assertEquals(42, result.getOrDefault(0))
    }

    @Test
    fun `getOrDefault returns default on error`() {
        val result: Result<Int> = Result.error("failed")
        assertEquals(0, result.getOrDefault(0))
    }

    @Test
    fun `getOrDefault returns default on loading`() {
        val result: Result<Int> = Result.loading()
        assertEquals(-1, result.getOrDefault(-1))
    }

    @Test
    fun `map transforms success data`() {
        val result = Result.success(5).map { it * 2 }
        assertEquals(10, result.getOrNull())
    }

    @Test
    fun `map passes through error`() {
        val result: Result<Int> = Result.error("failed")
        val mapped = result.map { it * 2 }
        assertTrue(mapped.isError)
    }

    @Test
    fun `flatMap chains success`() {
        val result = Result.success(5).flatMap { Result.success(it * 2) }
        assertEquals(10, result.getOrNull())
    }

    @Test
    fun `flatMap chains to error`() {
        val result = Result.success(5).flatMap<Int> { Result.error("conversion failed") }
        assertTrue(result.isError)
    }

    @Test
    fun `errorMessageOrNull returns null on success`() {
        val result = Result.success("data")
        assertNull(result.errorMessageOrNull())
    }

    @Test
    fun `getOrNull returns null on error`() {
        val result: Result<String> = Result.error("fail")
        assertNull(result.getOrNull())
    }

    @Test
    fun `map passes through loading`() {
        val result: Result<Int> = Result.loading()
        val mapped = result.map { it * 2 }
        assertTrue(mapped.isLoading)
    }

    @Test
    fun `flatMap passes through loading`() {
        val result: Result<Int> = Result.loading()
        val mapped = result.flatMap { Result.success(it * 2) }
        assertTrue(mapped.isLoading)
    }

    @Test
    fun `flatMap passes through error`() {
        val result: Result<Int> = Result.error("failed")
        val mapped = result.flatMap { Result.success(it * 2) }
        assertTrue(mapped.isError)
    }

    @Test
    fun `error with exception and message`() {
        val ex = RuntimeException("boom")
        val result = Result.Error(exception = ex, message = "custom")
        assertEquals("custom", result.message)
        assertEquals(ex, result.exception)
    }

    @Test
    fun `error with null exception uses default message`() {
        val result = Result.Error()
        assertEquals("Unbekannter Fehler", result.message)
    }

    @Test
    fun `errorMessageOrNull returns null on loading`() {
        val result: Result<String> = Result.loading()
        assertNull(result.errorMessageOrNull())
    }

    @Test
    fun `getOrNull returns null on loading`() {
        val result: Result<String> = Result.loading()
        assertNull(result.getOrNull())
    }
}
