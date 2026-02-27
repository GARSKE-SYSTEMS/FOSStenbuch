package de.fosstenbuch.data.repository

import de.fosstenbuch.data.local.SavedLocationDao
import de.fosstenbuch.data.model.SavedLocation
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

class SavedLocationRepositoryTest {

    private lateinit var repository: SavedLocationRepositoryImpl
    private val mockDao: SavedLocationDao = mockk()

    private val testLocation = SavedLocation(
        id = 1L,
        name = "Büro",
        latitude = 52.5200,
        longitude = 13.4050,
        address = "Alexanderplatz 1, Berlin",
        usageCount = 5
    )

    @Before
    fun setup() {
        repository = SavedLocationRepositoryImpl(mockDao)
    }

    @Test
    fun `getAllSavedLocations delegates to dao`() = runBlocking {
        val locations = listOf(testLocation, testLocation.copy(id = 2L, name = "Home"))
        coEvery { mockDao.getAllSavedLocations() } returns flowOf(locations)

        val result = repository.getAllSavedLocations().first()

        assertEquals(2, result.size)
        assertEquals("Büro", result[0].name)
    }

    @Test
    fun `getSavedLocationById delegates to dao`() = runBlocking {
        coEvery { mockDao.getSavedLocationById(1L) } returns flowOf(testLocation)

        val result = repository.getSavedLocationById(1L).first()

        assertEquals("Büro", result?.name)
    }

    @Test
    fun `getSavedLocationById returns null for non-existent id`() = runBlocking {
        coEvery { mockDao.getSavedLocationById(999L) } returns flowOf(null)

        val result = repository.getSavedLocationById(999L).first()

        assertNull(result)
    }

    @Test
    fun `getAllSavedLocationsSync delegates to dao`() = runBlocking {
        val locations = listOf(testLocation)
        coEvery { mockDao.getAllSavedLocationsSync() } returns locations

        val result = repository.getAllSavedLocationsSync()

        assertEquals(1, result.size)
        assertEquals("Büro", result[0].name)
    }

    @Test
    fun `insertSavedLocation delegates to dao and returns id`() = runBlocking {
        coEvery { mockDao.insertSavedLocation(testLocation) } returns 1L

        val result = repository.insertSavedLocation(testLocation)

        assertEquals(1L, result)
        coVerify(exactly = 1) { mockDao.insertSavedLocation(testLocation) }
    }

    @Test
    fun `updateSavedLocation delegates to dao`() = runBlocking {
        coEvery { mockDao.updateSavedLocation(testLocation) } returns Unit

        repository.updateSavedLocation(testLocation)

        coVerify(exactly = 1) { mockDao.updateSavedLocation(testLocation) }
    }

    @Test
    fun `deleteSavedLocation delegates to dao`() = runBlocking {
        coEvery { mockDao.deleteSavedLocation(testLocation) } returns Unit

        repository.deleteSavedLocation(testLocation)

        coVerify(exactly = 1) { mockDao.deleteSavedLocation(testLocation) }
    }

    @Test
    fun `incrementUsageCount delegates to dao`() = runBlocking {
        coEvery { mockDao.incrementUsageCount(1L) } returns Unit

        repository.incrementUsageCount(1L)

        coVerify(exactly = 1) { mockDao.incrementUsageCount(1L) }
    }
}
