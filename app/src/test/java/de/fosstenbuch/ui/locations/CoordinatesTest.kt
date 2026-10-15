package de.fosstenbuch.ui.locations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Bereichsprüfung und Anzeige von Koordinaten */
class CoordinatesTest {

    @Test
    fun `Koordinaten im Bereich inklusive Grenzen`() {
        assertEquals(48.1234, coordinate(48.1234, 90.0)!!, 0.0)
        assertEquals(-90.0, coordinate(-90.0, 90.0)!!, 0.0)
        assertEquals(180.0, coordinate(180.0, 180.0)!!, 0.0)
    }

    @Test
    fun `Tippfehler und Nicht-Zahlen werden abgelehnt`() {
        assertNull(coordinate(481.1234, 90.0))
        assertNull(coordinate(-180.5, 180.0))
        assertNull(coordinate(null, 90.0))
        assertNull(coordinate(Double.NaN, 90.0))
        assertNull(coordinate(Double.POSITIVE_INFINITY, 180.0))
    }

    @Test
    fun `Anzeige mit Himmelsrichtung und deutschem Dezimalkomma`() {
        assertEquals("52,5200°N, 13,4050°E", formatCoordinates(52.52, 13.405))
        assertEquals("33,8688°S, 151,2093°E", formatCoordinates(-33.8688, 151.2093))
        assertEquals("40,7128°N, 74,0060°W", formatCoordinates(40.7128, -74.006))
    }
}
