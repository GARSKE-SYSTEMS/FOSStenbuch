package de.fosstenbuch.domain.usecase.mileage

import de.fosstenbuch.data.repository.TripRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class CalculateMileageAllowanceUseCaseTest {

    private lateinit var useCase: CalculateMileageAllowanceUseCase
    private val mockRepository: TripRepository = mockk()

    @Before
    fun setup() {
        useCase = CalculateMileageAllowanceUseCase(mockRepository)
    }

    // --- calculate() tests ---

    @Test
    fun `calculate with zero distance returns zero result`() {
        val result = useCase.calculate(0.0, 230)

        assertEquals(0.0, result.totalAmount, 0.001)
        assertEquals(0.0, result.standardAmount, 0.001)
        assertEquals(0.0, result.extendedAmount, 0.001)
    }

    @Test
    fun `calculate with negative distance returns zero result`() {
        val result = useCase.calculate(-10.0, 230)

        assertEquals(0.0, result.totalAmount, 0.001)
    }

    @Test
    fun `calculate within 20km uses standard rate only`() {
        // 15 km * 0.30 €/km * 230 days = 1035.00 €
        val result = useCase.calculate(15.0, 230)

        assertEquals(1035.0, result.totalAmount, 0.001)
        assertEquals(1035.0, result.standardAmount, 0.001)
        assertEquals(0.0, result.extendedAmount, 0.001)
        assertEquals(15.0, result.oneWayDistanceKm, 0.001)
        assertEquals(230, result.workingDays)
    }

    @Test
    fun `calculate exactly 20km uses standard rate only`() {
        // 20 km * 0.30 €/km * 230 days = 1380.00 €
        val result = useCase.calculate(20.0, 230)

        assertEquals(1380.0, result.totalAmount, 0.001)
        assertEquals(1380.0, result.standardAmount, 0.001)
        assertEquals(0.0, result.extendedAmount, 0.001)
    }

    @Test
    fun `calculate above 20km uses both rates`() {
        // 30 km one-way, 230 days:
        // Standard: 20 km * 0.30 €/km * 230 = 1380.00 €
        // Extended: 10 km * 0.38 €/km * 230 = 874.00 €
        // Total: 2254.00 €
        val result = useCase.calculate(30.0, 230)

        assertEquals(2254.0, result.totalAmount, 0.001)
        assertEquals(1380.0, result.standardAmount, 0.001)
        assertEquals(874.0, result.extendedAmount, 0.001)
    }

    @Test
    fun `calculate uses default working days when not specified`() {
        val result = useCase.calculate(10.0)

        assertEquals(CalculateMileageAllowanceUseCase.DEFAULT_WORKING_DAYS, result.workingDays)
        // 10 * 0.30 * 230 = 690
        assertEquals(690.0, result.totalAmount, 0.001)
    }

    @Test
    fun `calculate with custom working days`() {
        // 10 km * 0.30 €/km * 200 days = 600.00 €
        val result = useCase.calculate(10.0, 200)

        assertEquals(600.0, result.totalAmount, 0.001)
        assertEquals(200, result.workingDays)
    }

    @Test
    fun `calculate large distance`() {
        // 50 km one-way, 230 days:
        // Standard: 20 km * 0.30 * 230 = 1380
        // Extended: 30 km * 0.38 * 230 = 2622
        // Total: 4002
        val result = useCase.calculate(50.0, 230)

        assertEquals(4002.0, result.totalAmount, 0.001)
    }

    // --- calculateFromTotalDistance() tests ---

    @Test
    fun `calculateFromTotalDistance with zero returns zero`() {
        val result = useCase.calculateFromTotalDistance(0.0, 230)

        assertEquals(0.0, result.totalAmount, 0.001)
    }

    @Test
    fun `calculateFromTotalDistance with negative returns zero`() {
        val result = useCase.calculateFromTotalDistance(-100.0, 230)

        assertEquals(0.0, result.totalAmount, 0.001)
    }

    @Test
    fun `calculateFromTotalDistance with zero working days returns zero`() {
        val result = useCase.calculateFromTotalDistance(1000.0, 0)

        assertEquals(0.0, result.totalAmount, 0.001)
    }

    @Test
    fun `calculateFromTotalDistance correctly halves round-trip distance`() {
        // Total: 9200 km round-trip, 230 days
        // One-way per day: (9200 / 2) / 230 = 20 km
        // Standard: 20 * 0.30 * 230 = 1380
        val result = useCase.calculateFromTotalDistance(9200.0, 230)

        assertEquals(20.0, result.oneWayDistanceKm, 0.001)
        assertEquals(1380.0, result.totalAmount, 0.001)
    }

    @Test
    fun `calculateFromTotalDistance with long commute`() {
        // Total: 27600 km round-trip, 230 days
        // One-way per day: (27600 / 2) / 230 = 60 km
        // Standard: 20 * 0.30 * 230 = 1380
        // Extended: 40 * 0.38 * 230 = 3496
        // Total: 4876
        val result = useCase.calculateFromTotalDistance(27600.0, 230)

        assertEquals(60.0, result.oneWayDistanceKm, 0.001)
        assertEquals(4876.0, result.totalAmount, 0.001)
    }

    // --- Repository delegation tests ---

    @Test
    fun `getBusinessDistanceForYear delegates to repository`() = runBlocking {
        every { mockRepository.getBusinessDistanceForYear(2024) } returns flowOf(5000.0)

        val result = useCase.getBusinessDistanceForYear(2024).first()

        assertEquals(5000.0, result!!, 0.001)
    }

    @Test
    fun `getBusinessTripCountForYear delegates to repository`() = runBlocking {
        every { mockRepository.getBusinessTripCountForYear(2024) } returns flowOf(150)

        val result = useCase.getBusinessTripCountForYear(2024).first()

        assertEquals(150, result)
    }

    // --- Constants validation tests ---

    @Test
    fun `standard rate is 0,30 Euro per km`() {
        assertEquals(0.30, CalculateMileageAllowanceUseCase.STANDARD_RATE, 0.001)
    }

    @Test
    fun `extended rate is 0,38 Euro per km`() {
        assertEquals(0.38, CalculateMileageAllowanceUseCase.EXTENDED_RATE, 0.001)
    }

    @Test
    fun `threshold is 20 km`() {
        assertEquals(20.0, CalculateMileageAllowanceUseCase.THRESHOLD_KM, 0.001)
    }

    @Test
    fun `default working days is 230`() {
        assertEquals(230, CalculateMileageAllowanceUseCase.DEFAULT_WORKING_DAYS)
    }
}
