package de.fosstenbuch.logic

import de.fosstenbuch.data.SavedLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeoTest {

    @Test
    fun `Entfernung Berlin – München ca 504 km`() {
        val m = Geo.distanceMeters(52.5200, 13.4050, 48.1351, 11.5820)
        assertEquals(504_000.0, m, 2_000.0)
    }

    @Test
    fun `gleicher Punkt hat Entfernung 0`() {
        assertEquals(0.0, Geo.distanceMeters(50.0, 8.0, 50.0, 8.0), 0.0)
    }

    @Test
    fun `nächster Ort nur innerhalb des Radius`() {
        val home = SavedLocation(id = 1, name = "Zuhause", latitude = 52.5200, longitude = 13.4050)
        val office = SavedLocation(id = 2, name = "Büro", latitude = 52.5210, longitude = 13.4050) // ca. 111 m entfernt
        assertEquals("Zuhause", Geo.nearest(listOf(office, home), 52.5200, 13.4051)?.name)
        assertEquals("Büro", Geo.nearest(listOf(home, office), 52.5211, 13.4050)?.name)
        assertNull(Geo.nearest(listOf(home), 52.6, 13.4, radiusMeters = 500.0))
    }
}
