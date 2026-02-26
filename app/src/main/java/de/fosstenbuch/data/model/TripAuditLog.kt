package de.fosstenbuch.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "trip_audit_log",
    foreignKeys = [
        ForeignKey(
            entity = Trip::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tripId")]
)
data class TripAuditLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val fieldName: String,
    val oldValue: String?,
    val newValue: String?,
    val changedAt: Date = Date()
)
