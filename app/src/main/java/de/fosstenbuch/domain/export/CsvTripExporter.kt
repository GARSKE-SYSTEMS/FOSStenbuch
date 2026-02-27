package de.fosstenbuch.domain.export

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import de.fosstenbuch.data.model.Trip
import de.fosstenbuch.data.model.TripAuditLog
import de.fosstenbuch.data.model.TripPurpose
import de.fosstenbuch.data.model.Vehicle
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

class CsvTripExporter @Inject constructor(
    @ApplicationContext private val context: Context
) : TripExporter {

    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)

    override suspend fun export(
        config: ExportConfig,
        trips: List<Trip>,
        purposes: Map<Long, TripPurpose>,
        vehicles: Map<Long, Vehicle>,
        auditLogs: Map<Long, List<TripAuditLog>>
    ): File {
        val fromStr = config.dateFrom.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val toStr = config.dateTo.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val fileName = "fahrtenbuch_${fromStr}_${toStr}.csv"
        val file = File(context.cacheDir, fileName)

        FileOutputStream(file).use { fos ->
            OutputStreamWriter(fos, Charsets.UTF_8).use { writer ->
                // UTF-8 BOM for Excel compatibility
                fos.write(0xEF)
                fos.write(0xBB)
                fos.write(0xBF)

                // Driver name if set
                if (config.driverName.isNotBlank()) {
                    writer.write(csvLine("Fahrer", config.driverName))
                    writer.write("\n")
                }

                // List audit-protected vehicles
                val auditProtectedVehicles = vehicles.values.filter { it.auditProtected }
                if (auditProtectedVehicles.isNotEmpty()) {
                    writer.write(csvLine("--- Änderungssicher geführte Fahrzeuge ---"))
                    for (v in auditProtectedVehicles) {
                        writer.write(csvLine("${v.make} ${v.model}", v.licensePlate))
                    }
                    writer.write("\n")
                }

                // Header row
                writer.write(
                    csvLine(
                        "Datum",
                        "Startort",
                        "Zielort",
                        "Distanz (km)",
                        "Zweck",
                        "Kategorie",
                        "Geschäftlich",
                        "Fahrzeug",
                        "Kennzeichen",
                        "Km-Stand Start",
                        "Km-Stand Ende",
                        "Notizen",
                        "Storniert",
                        "Stornogrund"
                    )
                )

                // Data rows
                for (trip in trips) {
                    val purpose = trip.purposeId?.let { purposes[it] }
                    val vehicle = trip.vehicleId?.let { vehicles[it] }

                    writer.write(
                        csvLine(
                            dateFormat.format(trip.date),
                            trip.startLocation,
                            trip.endLocation,
                            "%.2f".format(trip.distanceKm),
                            trip.purpose,
                            purpose?.name ?: "",
                            if (purpose?.isBusinessRelevant == true) "Ja" else "Nein",
                            vehicle?.let { "${it.make} ${it.model}" } ?: "",
                            vehicle?.licensePlate ?: "",
                            trip.startOdometer?.toString() ?: "",
                            trip.endOdometer?.toString() ?: "",
                            trip.notes ?: "",
                            if (trip.isCancelled) "STORNIERT" else "",
                            trip.cancellationReason ?: ""
                        )
                    )
                }

                // Audit log section if requested
                if (config.includeAuditLog && auditLogs.isNotEmpty()) {
                    writer.write("\n")
                    writer.write(csvLine("--- Änderungsprotokoll ---"))
                    writer.write(csvLine("Fahrt-ID", "Feld", "Alter Wert", "Neuer Wert", "Geändert am"))

                    for ((tripId, logs) in auditLogs) {
                        for (log in logs) {
                            writer.write(
                                csvLine(
                                    tripId.toString(),
                                    log.fieldName,
                                    log.oldValue ?: "",
                                    log.newValue ?: "",
                                    dateFormat.format(log.changedAt)
                                )
                            )
                        }
                    }
                }

                writer.flush()
            }
        }

        return file
    }

    private fun csvLine(vararg fields: String): String {
        return fields.joinToString(";") { escapeCsvField(it) } + "\n"
    }

    private fun escapeCsvField(field: String): String {
        return if (field.contains(";") || field.contains("\"") || field.contains("\n")) {
            "\"${field.replace("\"", "\"\"")}\""
        } else {
            field
        }
    }
}
