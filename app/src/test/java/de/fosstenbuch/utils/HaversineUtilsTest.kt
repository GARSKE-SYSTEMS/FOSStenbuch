package de.fosstenbuch.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class HaversineUtilsTest {

    @Test
    fun `same point returns zero distance`() {
        val distance = HaversineUtils.distanceInMeters(52.52, 13.405, 52.52, 13.405)
        assertEquals(0.0, distance, 0.01)
    }

    @Test
    fun `berlin to hamburg is approximately 255 km`() {
        // Berlin: 52.52, 13.405  Hamburg: 53.5511, 9.9937
        val distance = HaversineUtils.distanceInMeters(52.52, 13.405, 53.5511, 9.9937)
        val distanceKm = distance / 1000.0
        // Should be approximately 255 km (straight line)
        assertEquals(255.0, distanceKm, 10.0)
    }

    @Test
    fun `short distance within city`() {
        // Two points ~1 km apart in Berlin
        val distance = HaversineUtils.distanceInMeters(52.5200, 13.4050, 52.5290, 13.4050)
        // Should be ~1000m
        assertEquals(1000.0, distance, 50.0)
    }

    @Test
    fun `equator to pole is approximately 10000 km`() {
        val distance = HaversineUtils.distanceInMeters(0.0, 0.0, 90.0, 0.0)
        val distanceKm = distance / 1000.0
        assertEquals(10008.0, distanceKm, 10.0)
    }

    @Test
    fun `negative coordinates work correctly`() {
        // South America to Africa
        val distance = HaversineUtils.distanceInMeters(-23.5505, -46.6333, -33.9249, 18.4241)
        val distanceKm = distance / 1000.0
        // São Paulo to Cape Town ~6000-7000 km
        assertEquals(6600.0, distanceKm, 500.0)
    }
}
