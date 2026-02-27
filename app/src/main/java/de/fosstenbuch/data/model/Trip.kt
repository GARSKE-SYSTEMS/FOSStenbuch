package de.fosstenbuch.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "trips",
    foreignKeys = [
        ForeignKey(
            entity = TripPurpose::class,
            parentColumns = ["id"],
            childColumns = ["purposeId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = Vehicle::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("purposeId"), Index("vehicleId"), Index("date")]
)
data class Trip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Date,
    val startLocation: String,
    val endLocation: String = "",
    val distanceKm: Double = 0.0,
    val purpose: String = "",
    val purposeId: Long? = null,
    val notes: String? = null,
    val startOdometer: Int? = null,
    val endOdometer: Int? = null,
    val vehicleId: Long? = null,
    val isCancelled: Boolean = false,
    val cancellationReason: String? = null,
    val isActive: Boolean = false,
    val endTime: Date? = null,
    val gpsDistanceKm: Double? = null
)