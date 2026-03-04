package de.fosstenbuch.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vehicles")
data class Vehicle(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val make: String,
    val model: String,
    val licensePlate: String,
    val fuelType: String,
    val isPrimary: Boolean = false,
    val notes: String? = null,
    val auditProtected: Boolean = false,
    val bluetoothDeviceAddress: String? = null,
    val bluetoothDeviceName: String? = null
)