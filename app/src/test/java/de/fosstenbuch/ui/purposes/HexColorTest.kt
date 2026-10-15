package de.fosstenbuch.ui.purposes

import de.fosstenbuch.ui.common.hexColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Farbwerte der Kategorien (ui.common.hexColor) */
class HexColorTest {

    @Test
    fun `gültige Farbe ist voll deckend`() {
        assertEquals(0xFF6200EE.toInt(), hexColor("#6200EE"))
        assertEquals(0xFFFF5722.toInt(), hexColor("#ff5722"))
    }

    @Test
    fun `ungültige Farben werden abgelehnt statt still gespeichert`() {
        for (text in listOf(null, "", "#12AB", "6200EE", "#GGGGGG", "#6200EE0", "red", " #6200EE")) {
            assertNull(text, hexColor(text))
        }
    }
}
