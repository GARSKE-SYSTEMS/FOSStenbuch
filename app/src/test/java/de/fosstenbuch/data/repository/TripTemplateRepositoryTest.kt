package de.fosstenbuch.data.repository

import de.fosstenbuch.data.local.TripTemplateDao
import de.fosstenbuch.data.model.TripTemplate
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class TripTemplateRepositoryTest {

    private lateinit var repository: TripTemplateRepositoryImpl
    private val mockDao: TripTemplateDao = mockk()

    private val testTemplate = TripTemplate(
        id = 1L,
        name = "Büro-Pendeln",
        startLocation = "Zuhause",
        endLocation = "Büro",
        distanceKm = 25.0,
        purpose = "Arbeitsweg",
        purposeId = 1L,
        notes = "Täglicher Arbeitsweg"
    )

    @Before
    fun setup() {
        repository = TripTemplateRepositoryImpl(mockDao)
    }

    @Test
    fun `getAllTemplates delegates to dao`() = runBlocking {
        val templates = listOf(testTemplate, testTemplate.copy(id = 2L, name = "Kundentermin"))
        coEvery { mockDao.getAllTemplates() } returns flowOf(templates)

        val result = repository.getAllTemplates().first()

        assertEquals(2, result.size)
        assertEquals("Büro-Pendeln", result[0].name)
    }

    @Test
    fun `getTemplateById delegates to dao`() = runBlocking {
        coEvery { mockDao.getTemplateById(1L) } returns flowOf(testTemplate)

        val result = repository.getTemplateById(1L).first()

        assertEquals("Büro-Pendeln", result?.name)
    }

    @Test
    fun `getTemplateById returns null for non-existent id`() = runBlocking {
        coEvery { mockDao.getTemplateById(999L) } returns flowOf(null)

        val result = repository.getTemplateById(999L).first()

        assertNull(result)
    }

    @Test
    fun `insertTemplate delegates to dao and returns id`() = runBlocking {
        coEvery { mockDao.insertTemplate(testTemplate) } returns 1L

        val result = repository.insertTemplate(testTemplate)

        assertEquals(1L, result)
        coVerify(exactly = 1) { mockDao.insertTemplate(testTemplate) }
    }

    @Test
    fun `deleteTemplate delegates to dao`() = runBlocking {
        coEvery { mockDao.deleteTemplate(testTemplate) } returns Unit

        repository.deleteTemplate(testTemplate)

        coVerify(exactly = 1) { mockDao.deleteTemplate(testTemplate) }
    }
}
