package de.fosstenbuch.domain.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ExportConfigTest {

    @Test
    fun `default config has CSV format`() {
        val config = ExportConfig(
            dateFrom = LocalDate.of(2024, 1, 1),
            dateTo = LocalDate.of(2024, 12, 31),
            selectedPurposeIds = emptySet(),
            vehicleId = null
        )

        assertEquals(ExportFormat.CSV, config.format)
    }

    @Test
    fun `default config has audit log disabled`() {
        val config = ExportConfig(
            dateFrom = LocalDate.of(2024, 1, 1),
            dateTo = LocalDate.of(2024, 12, 31),
            selectedPurposeIds = emptySet(),
            vehicleId = null
        )

        assertFalse(config.includeAuditLog)
    }

    @Test
    fun `config with all parameters`() {
        val config = ExportConfig(
            dateFrom = LocalDate.of(2024, 1, 1),
            dateTo = LocalDate.of(2024, 6, 30),
            selectedPurposeIds = setOf(1L, 2L),
            vehicleId = 5L,
            includeAuditLog = true,
            format = ExportFormat.PDF
        )

        assertEquals(LocalDate.of(2024, 1, 1), config.dateFrom)
        assertEquals(LocalDate.of(2024, 6, 30), config.dateTo)
        assertEquals(setOf(1L, 2L), config.selectedPurposeIds)
        assertEquals(5L, config.vehicleId)
        assertTrue(config.includeAuditLog)
        assertEquals(ExportFormat.PDF, config.format)
    }

    @Test
    fun `config vehicleId can be null`() {
        val config = ExportConfig(
            dateFrom = LocalDate.of(2024, 1, 1),
            dateTo = LocalDate.of(2024, 12, 31),
            selectedPurposeIds = emptySet(),
            vehicleId = null
        )

        assertNull(config.vehicleId)
    }
}

class ExportFormatTest {

    @Test
    fun `CSV format exists`() {
        assertEquals("CSV", ExportFormat.CSV.name)
    }

    @Test
    fun `PDF format exists`() {
        assertEquals("PDF", ExportFormat.PDF.name)
    }

    @Test
    fun `FINANZAMT_PDF format exists`() {
        assertEquals("FINANZAMT_PDF", ExportFormat.FINANZAMT_PDF.name)
    }

    @Test
    fun `ExportFormat has exactly 3 values`() {
        assertEquals(3, ExportFormat.values().size)
    }
}
