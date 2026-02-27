package de.fosstenbuch.data.repository

import de.fosstenbuch.data.local.TripPurposeDao
import de.fosstenbuch.data.model.TripPurpose
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

class TripPurposeRepositoryTest {

    private lateinit var repository: TripPurposeRepositoryImpl
    private val mockDao: TripPurposeDao = mockk()

    private val testPurpose = TripPurpose(
        id = 1L,
        name = "Beruflich",
        isBusinessRelevant = true,
        color = "#6200EE",
        isDefault = true
    )

    @Before
    fun setup() {
        repository = TripPurposeRepositoryImpl(mockDao)
    }

    @Test
    fun `getAllPurposes delegates to dao`() = runBlocking {
        val purposes = listOf(testPurpose, testPurpose.copy(id = 2L, name = "Privat", isBusinessRelevant = false))
        coEvery { mockDao.getAllPurposes() } returns flowOf(purposes)

        val result = repository.getAllPurposes().first()

        assertEquals(2, result.size)
        assertEquals("Beruflich", result[0].name)
    }

    @Test
    fun `getPurposeById delegates to dao`() = runBlocking {
        coEvery { mockDao.getPurposeById(1L) } returns flowOf(testPurpose)

        val result = repository.getPurposeById(1L).first()

        assertEquals("Beruflich", result?.name)
    }

    @Test
    fun `getPurposeById returns null for non-existent id`() = runBlocking {
        coEvery { mockDao.getPurposeById(999L) } returns flowOf(null)

        val result = repository.getPurposeById(999L).first()

        assertNull(result)
    }

    @Test
    fun `getPurposeByName delegates to dao`() = runBlocking {
        coEvery { mockDao.getPurposeByName("Beruflich") } returns testPurpose

        val result = repository.getPurposeByName("Beruflich")

        assertEquals(testPurpose, result)
    }

    @Test
    fun `getPurposeByName returns null when not found`() = runBlocking {
        coEvery { mockDao.getPurposeByName("Unknown") } returns null

        val result = repository.getPurposeByName("Unknown")

        assertNull(result)
    }

    @Test
    fun `getTripCountForPurpose delegates to dao`() = runBlocking {
        coEvery { mockDao.getTripCountForPurpose(1L) } returns 5

        val result = repository.getTripCountForPurpose(1L)

        assertEquals(5, result)
    }

    @Test
    fun `insertPurpose delegates to dao and returns id`() = runBlocking {
        coEvery { mockDao.insertPurpose(testPurpose) } returns 1L

        val result = repository.insertPurpose(testPurpose)

        assertEquals(1L, result)
        coVerify(exactly = 1) { mockDao.insertPurpose(testPurpose) }
    }

    @Test
    fun `updatePurpose delegates to dao`() = runBlocking {
        coEvery { mockDao.updatePurpose(testPurpose) } returns Unit

        repository.updatePurpose(testPurpose)

        coVerify(exactly = 1) { mockDao.updatePurpose(testPurpose) }
    }

    @Test
    fun `deletePurpose delegates to dao`() = runBlocking {
        coEvery { mockDao.deletePurpose(testPurpose) } returns Unit

        repository.deletePurpose(testPurpose)

        coVerify(exactly = 1) { mockDao.deletePurpose(testPurpose) }
    }

    @Test
    fun `getPurposeCount delegates to dao`() = runBlocking {
        coEvery { mockDao.getPurposeCount() } returns 3

        val result = repository.getPurposeCount()

        assertEquals(3, result)
    }
}
