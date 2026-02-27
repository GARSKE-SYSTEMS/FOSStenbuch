package de.fosstenbuch.domain.usecase.trip

import de.fosstenbuch.data.model.Trip
import de.fosstenbuch.data.repository.TripRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import java.util.Date

class DeleteTripUseCaseTest {

    private lateinit var useCase: DeleteTripUseCase
    private val mockRepository: TripRepository = mockk()

    private val testTrip = Trip(
        id = 1L,
        date = Date(),
        startLocation = "Berlin",
        endLocation = "Hamburg",
        distanceKm = 280.0,
        purpose = "Kundentermin",
        purposeId = 1L
    )

    @Before
    fun setup() {
        useCase = DeleteTripUseCase(mockRepository)
    }

    @Test
    fun `invoke delegates to repository`() = runBlocking {
        coEvery { mockRepository.deleteTrip(testTrip) } returns Unit

        useCase(testTrip)

        coVerify(exactly = 1) { mockRepository.deleteTrip(testTrip) }
    }
}
