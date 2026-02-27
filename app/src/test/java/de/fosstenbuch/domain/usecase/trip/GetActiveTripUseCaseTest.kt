package de.fosstenbuch.domain.usecase.trip

import de.fosstenbuch.data.model.Trip
import de.fosstenbuch.data.repository.TripRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.Date

class GetActiveTripUseCaseTest {

    private lateinit var useCase: GetActiveTripUseCase
    private val mockRepository: TripRepository = mockk()

    @Before
    fun setup() {
        useCase = GetActiveTripUseCase(mockRepository)
    }

    @Test
    fun `returns active trip when one exists`() = runBlocking {
        val activeTrip = Trip(
            id = 1L,
            date = Date(),
            startLocation = "Berlin",
            startOdometer = 50000,
            isActive = true
        )
        every { mockRepository.getActiveTrip() } returns flowOf(activeTrip)

        val result = useCase().first()

        assertEquals(1L, result?.id)
        assertEquals(true, result?.isActive)
    }

    @Test
    fun `returns null when no active trip`() = runBlocking {
        every { mockRepository.getActiveTrip() } returns flowOf(null)

        val result = useCase().first()

        assertNull(result)
    }
}
