package de.fosstenbuch.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trip_templates")
data class TripTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startLocation: String,
    val endLocation: String,
    val distanceKm: Double,
    val purpose: String,
    val purposeId: Long? = null,
    val notes: String? = null,
    val vehicleId: Long? = null,
    val businessPartner: String? = null,
    val route: String? = null
)
