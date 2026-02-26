package de.fosstenbuch.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trip_purposes")
data class TripPurpose(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isBusinessRelevant: Boolean,
    val color: String = "#6200EE",
    val isDefault: Boolean = false
)
