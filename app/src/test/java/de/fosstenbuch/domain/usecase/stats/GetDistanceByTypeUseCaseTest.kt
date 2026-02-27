package de.fosstenbuch.domain.usecase.stats

import de.fosstenbuch.data.repository.TripRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GetDistanceByTypeUseCaseTest {

    private lateinit var useCase: GetDistanceByTypeUseCase
    private val mockRepository: TripRepository = mockk()

    @Before
    fun setup() {
        useCase = GetDistanceByTypeUseCase(mockRepository)
    }

    @Test
    fun `invoke combines business and private distance`() = runBlocking {
        every { mockRepository.getTotalBusinessDistance() } returns flowOf(3000.0)
        every { mockRepository.getTotalPrivateDistance() } returns flowOf(2000.0)

        val result = useCase().first()

        assertEquals(3000.0, result.businessDistanceKm, 0.001)
        assertEquals(2000.0, result.privateDistanceKm, 0.001)
        assertEquals(5000.0, result.totalDistanceKm, 0.001)
    }

    @Test
    fun `invoke maps null business distance to 0,0`() = runBlocking {
        every { mockRepository.getTotalBusinessDistance() } returns flowOf(null)
        every { mockRepository.getTotalPrivateDistance() } returns flowOf(1500.0)

        val result = useCase().first()

        assertEquals(0.0, result.businessDistanceKm, 0.001)
        assertEquals(1500.0, result.privateDistanceKm, 0.001)
        assertEquals(1500.0, result.totalDistanceKm, 0.001)
    }

    @Test
    fun `invoke maps null private distance to 0,0`() = runBlocking {
        every { mockRepository.getTotalBusinessDistance() } returns flowOf(2500.0)
        every { mockRepository.getTotalPrivateDistance() } returns flowOf(null)

        val result = useCase().first()

        assertEquals(2500.0, result.businessDistanceKm, 0.001)
        assertEquals(0.0, result.privateDistanceKm, 0.001)
        assertEquals(2500.0, result.totalDistanceKm, 0.001)
    }

    @Test
    fun `invoke maps both null to 0,0`() = runBlocking {
        every { mockRepository.getTotalBusinessDistance() } returns flowOf(null)
        every { mockRepository.getTotalPrivateDistance() } returns flowOf(null)

        val result = useCase().first()

        assertEquals(0.0, result.businessDistanceKm, 0.001)
        assertEquals(0.0, result.privateDistanceKm, 0.001)
        assertEquals(0.0, result.totalDistanceKm, 0.001)
    }

    @Test
    fun `DistanceByType totalDistanceKm sums correctly`() {
        val distanceByType = GetDistanceByTypeUseCase.DistanceByType(
            businessDistanceKm = 1234.5,
            privateDistanceKm = 678.9
        )

        assertEquals(1913.4, distanceByType.totalDistanceKm, 0.001)
    }
}
