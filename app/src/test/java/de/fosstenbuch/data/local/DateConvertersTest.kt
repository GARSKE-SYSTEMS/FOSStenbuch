package de.fosstenbuch.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.Date

class DateConvertersTest {

    private lateinit var converter: DateConverters

    @Before
    fun setup() {
        converter = DateConverters()
    }

    @Test
    fun `fromTimestamp converts valid timestamp to Date`() {
        val timestamp = 1704067200000L // 2024-01-01 00:00:00 UTC
        val result = converter.fromTimestamp(timestamp)

        assertEquals(Date(timestamp), result)
    }

    @Test
    fun `fromTimestamp returns null for null input`() {
        val result = converter.fromTimestamp(null)

        assertNull(result)
    }

    @Test
    fun `fromTimestamp handles zero timestamp`() {
        val result = converter.fromTimestamp(0L)

        assertEquals(Date(0L), result)
    }

    @Test
    fun `dateToTimestamp converts valid Date to Long`() {
        val date = Date(1704067200000L)
        val result = converter.dateToTimestamp(date)

        assertEquals(1704067200000L, result)
    }

    @Test
    fun `dateToTimestamp returns null for null input`() {
        val result = converter.dateToTimestamp(null)

        assertNull(result)
    }

    @Test
    fun `dateToTimestamp handles epoch Date`() {
        val result = converter.dateToTimestamp(Date(0L))

        assertEquals(0L, result)
    }

    @Test
    fun `roundtrip Date to timestamp and back`() {
        val original = Date()
        val timestamp = converter.dateToTimestamp(original)
        val restored = converter.fromTimestamp(timestamp)

        assertEquals(original, restored)
    }

    @Test
    fun `roundtrip timestamp to Date and back`() {
        val original = 1704067200000L
        val date = converter.fromTimestamp(original)
        val restored = converter.dateToTimestamp(date)

        assertEquals(original, restored)
    }
}
