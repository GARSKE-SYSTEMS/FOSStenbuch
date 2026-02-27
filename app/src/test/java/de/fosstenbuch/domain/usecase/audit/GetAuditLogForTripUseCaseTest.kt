package de.fosstenbuch.domain.usecase.audit

import de.fosstenbuch.data.local.TripAuditLogDao
import de.fosstenbuch.data.model.TripAuditLog
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Date

class GetAuditLogForTripUseCaseTest {

    private lateinit var useCase: GetAuditLogForTripUseCase
    private val mockDao: TripAuditLogDao = mockk()

    @Before
    fun setup() {
        useCase = GetAuditLogForTripUseCase(mockDao)
    }

    @Test
    fun `invoke returns audit logs for trip`() = runBlocking {
        val logs = listOf(
            TripAuditLog(1L, 10L, "distanceKm", "100.0", "150.0", Date()),
            TripAuditLog(2L, 10L, "endLocation", "Berlin", "Hamburg", Date())
        )
        every { mockDao.getAuditLogForTrip(10L) } returns flowOf(logs)

        val result = useCase(10L).first()

        assertEquals(2, result.size)
        assertEquals("distanceKm", result[0].fieldName)
        assertEquals("endLocation", result[1].fieldName)
    }

    @Test
    fun `invoke returns empty list when no audit logs`() = runBlocking {
        every { mockDao.getAuditLogForTrip(10L) } returns flowOf(emptyList())

        val result = useCase(10L).first()

        assertEquals(0, result.size)
    }
}
