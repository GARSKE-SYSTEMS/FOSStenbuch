package de.fosstenbuch.domain.usecase.trip

import de.fosstenbuch.data.model.Trip
import de.fosstenbuch.data.repository.TripRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.Date

class GetTripByIdUseCaseTest {

    private lateinit var useCase: GetTripByIdUseCase
    private val mockRepository: TripRepository = mockk()

    @Before
    fun setup() {
        useCase = GetTripByIdUseCase(mockRepository)
    }

    @Test
    fun `invoke returns trip by id`() = runBlocking {
        val trip = Trip(1L, Date(), "Berlin", "Hamburg", 280.0, "Business", purposeId = 1L)
        every { mockRepository.getTripById(1L) } returns flowOf(trip)

        val result = useCase(1L).first()

        assertEquals("Berlin", result?.startLocation)
        assertEquals(1L, result?.id)
    }

    @Test
    fun `invoke returns null for non-existent id`() = runBlocking {
        every { mockRepository.getTripById(999L) } returns flowOf(null)

        val result = useCase(999L).first()

        assertNull(result)
    }
}
