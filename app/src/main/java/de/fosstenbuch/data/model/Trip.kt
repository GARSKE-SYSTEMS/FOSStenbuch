package de.fosstenbuch.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "trips")
data class Trip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Date,
    val startLocation: String,
    val endLocation: String,
    val distanceKm: Double,
    val purpose: String,
    val businessTrip: Boolean,
    val notes: String? = null,
    val startOdometer: Int? = null,
    val endOdometer: Int? = null
)