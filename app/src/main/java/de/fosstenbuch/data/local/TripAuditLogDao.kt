package de.fosstenbuch.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import de.fosstenbuch.data.model.TripAuditLog
import kotlinx.coroutines.flow.Flow

@Dao
interface TripAuditLogDao {
    @Query("SELECT * FROM trip_audit_log WHERE tripId = :tripId ORDER BY changedAt DESC")
    fun getAuditLogForTrip(tripId: Long): Flow<List<TripAuditLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(auditLog: TripAuditLog): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLogs(auditLogs: List<TripAuditLog>)

    @Query("SELECT COUNT(*) > 0 FROM trip_audit_log WHERE tripId = :tripId")
    fun hasAuditLog(tripId: Long): Flow<Boolean>
}
