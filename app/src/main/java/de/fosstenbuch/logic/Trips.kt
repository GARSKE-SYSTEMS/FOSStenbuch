package de.fosstenbuch.logic

import android.content.Context
import androidx.room.withTransaction
import de.fosstenbuch.data.AppDao
import de.fosstenbuch.data.Trip
import de.fosstenbuch.data.TripAuditLog
import de.fosstenbuch.data.dao
import de.fosstenbuch.data.db
import de.fosstenbuch.tracking.TrackingService
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Alle Schreibzugriffe auf Fahrten mit ihren Geschäftsregeln:
 * Validierung, Sperre änderungssicherer Fahrten, Änderungsprotokoll und Hash-Kette.
 * Jede Operation läuft in einer Transaktion – entweder alles oder nichts.
 *
 * Fehler werden als Exception gemeldet; die Meldung ist für den Nutzer gedacht:
 *  - [Invalid]: Feldfehler fürs Formular
 *  - [Rejected]: Regelverstoß (z.B. gesperrte Fahrt)
 * Bewusst keine IllegalStateException: Davon erbt auch CancellationException, ein breites
 * catch würde sonst den Abbruch der Coroutine (Bildschirm verlassen) verschlucken.
 */
object Trips {

    /** Formularfelder, an denen Validierungsfehler angezeigt werden */
    enum class Field { START_LOCATION, END_LOCATION, DISTANCE, PURPOSE, DATE, END_TIME, START_ODOMETER, ODOMETER, VEHICLE }

    class Invalid(val errors: Map<Field, String>) : Exception(errors.values.joinToString("\n"))
    class Rejected(message: String) : Exception(message)

    /** Wie require(), aber mit [Rejected] */
    private inline fun require(condition: Boolean, message: () -> String) {
        if (!condition) throw Rejected(message())
    }

    private const val MAX_TEXT = 200
    private const val MAX_DISTANCE_KM = 99_999

    // ── Validierung ──────────────────────────────────────────────────────────

    /** Pflichtangaben beim Starten einer Fahrt */
    fun validateStart(t: Trip, now: Date = Date()): Map<Field, String> = buildMap {
        checkText(t.startLocation, "Startort", Field.START_LOCATION)
        if (t.date.after(now)) put(Field.DATE, "Datum darf nicht in der Zukunft liegen")
        when {
            t.startOdometer == null -> put(Field.START_ODOMETER, "Kilometerstand ist erforderlich")
            t.startOdometer < 0 -> put(Field.START_ODOMETER, "Kilometerstand darf nicht negativ sein")
        }
        if (t.vehicleId == null) put(Field.VEHICLE, "Bitte wählen Sie ein Fahrzeug aus")
    }

    /** Pflichtangaben einer abgeschlossenen Fahrt */
    fun validate(t: Trip, now: Date = Date()): Map<Field, String> = buildMap {
        checkText(t.startLocation, "Startort", Field.START_LOCATION)
        checkText(t.endLocation, "Zielort", Field.END_LOCATION)
        if (t.purposeId == null) put(Field.PURPOSE, "Bitte wählen Sie eine Kategorie aus")
        if (t.date.after(now)) put(Field.DATE, "Datum darf nicht in der Zukunft liegen")
        if (t.endTime != null && t.endTime.before(t.date)) put(Field.END_TIME, "Ankunft darf nicht vor der Abfahrt liegen")
        val start = t.startOdometer
        val end = t.endOdometer
        when {
            start == null || end == null -> put(Field.ODOMETER, "Start- und Endkilometerstand müssen angegeben werden")
            start < 0 -> put(Field.ODOMETER, "Kilometerstand darf nicht negativ sein")
            end <= start -> put(Field.ODOMETER, "Endkilometerstand muss größer als Startkilometerstand sein")
            end - start > MAX_DISTANCE_KM -> put(Field.DISTANCE, "Distanz darf maximal $MAX_DISTANCE_KM km betragen")
        }
        if (t.vehicleId == null) put(Field.VEHICLE, "Bitte wählen Sie ein Fahrzeug aus")
    }

    private fun MutableMap<Field, String>.checkText(value: String, label: String, field: Field) {
        if (value.isBlank()) put(field, "$label darf nicht leer sein")
        else if (value.length > MAX_TEXT) put(field, "$label darf maximal $MAX_TEXT Zeichen lang sein")
    }

    // ── Schreiboperationen ───────────────────────────────────────────────────

    /** Startet eine Fahrt (isActive). Es kann immer nur eine laufen. Liefert die neue ID. */
    suspend fun start(context: Context, trip: Trip): Long {
        val t = trip.copy(id = 0, isActive = true, isGhost = false, endTime = null, startLocation = trip.startLocation.trim())
        validateStart(t).let { if (it.isNotEmpty()) throw Invalid(it) }
        val db = context.db
        return db.withTransaction {
            val dao = db.dao()
            require(dao.activeTrip() == null) { "Es läuft bereits eine Fahrt" }
            dao.insertTrip(t).also { recomputeChain(dao, t.vehicleId) }
        }
    }

    /**
     * Speichert eine abgeschlossene Fahrt: neu anlegen (id = 0), laufende Fahrt beenden,
     * automatische Fahrt übernehmen oder bestehende Fahrt bearbeiten.
     * Die Distanz ergibt sich immer aus den Kilometerständen.
     *
     * Wichtig: Beim Bearbeiten [trip] per `geladeneFahrt.copy(...)` erzeugen, damit Felder
     * wie isExported, Koordinaten oder GPS-Strecke erhalten bleiben.
     */
    suspend fun save(context: Context, trip: Trip): Long {
        val t = trip.copy(
            isActive = false,
            isGhost = false,
            startLocation = trip.startLocation.trim(),
            endLocation = trip.endLocation.trim(),
            distanceKm = odometerDistance(trip) ?: trip.distanceKm
        )
        validate(t).let { if (it.isNotEmpty()) throw Invalid(it) }
        val db = context.db
        return db.withTransaction {
            val dao = db.dao()
            val old = if (t.id != 0L) dao.trip(t.id) else null
            // Nur Änderungen an bereits abgeschlossenen Fahrten werden gesperrt bzw. protokolliert
            if (old != null && !old.isActive && !old.isGhost) {
                require(!isProtected(dao, old.vehicleId)) { "Änderungssicher – Bearbeitung gesperrt" }
                dao.insertAuditLogs(auditDiff(old, t))
            }
            // Eine gerade übernommene automatische Fahrt ist noch nie exportiert worden
            val final = if (old?.isGhost == true) t.copy(isExported = false) else t
            val id = if (old == null) dao.insertTrip(final.copy(id = 0)) else final.id.also { dao.updateTrip(final) }
            recomputeChain(dao, t.vehicleId)
            if (old != null && old.vehicleId != t.vehicleId) recomputeChain(dao, old.vehicleId)
            id
        }
    }

    /** Storniert eine abgeschlossene Fahrt. Auch bei änderungssicheren Fahrzeugen erlaubt (wird protokolliert). */
    suspend fun cancel(context: Context, trip: Trip, reason: String) {
        val db = context.db
        db.withTransaction {
            val dao = db.dao()
            val old = dao.trip(trip.id) ?: return@withTransaction
            require(!old.isActive && !old.isGhost && !old.isCancelled) { "Diese Fahrt kann nicht storniert werden" }
            val new = old.copy(isCancelled = true, cancellationReason = reason.trim().ifEmpty { null })
            dao.insertAuditLogs(auditDiff(old, new))
            dao.updateTrip(new)
            recomputeChain(dao, new.vehicleId)
        }
    }

    /** Löscht eine Fahrt. Abgeschlossene Fahrten änderungssicherer Fahrzeuge sind nicht löschbar. */
    suspend fun delete(context: Context, trip: Trip) {
        val db = context.db
        db.withTransaction {
            val dao = db.dao()
            require(trip.isGhost || trip.isActive || !isProtected(dao, trip.vehicleId)) {
                "Fahrten von änderungssicheren Fahrzeugen können nicht gelöscht werden"
            }
            dao.deleteTrip(trip)
            recomputeChain(dao, trip.vehicleId)
        }
        // Lief für diese Fahrt noch die GPS-Aufzeichnung, wird sie beendet
        if (TrackingService.live.value?.tripId == trip.id) TrackingService.stop(context)
    }

    /** Distanz laut Kilometerstand, null wenn ein Stand fehlt */
    fun odometerDistance(t: Trip): Double? {
        val start = t.startOdometer ?: return null
        val end = t.endOdometer ?: return null
        return (end - start).toDouble()
    }

    private suspend fun isProtected(dao: AppDao, vehicleId: Long?): Boolean =
        vehicleId != null && dao.vehicle(vehicleId)?.auditProtected == true

    // ── Hash-Kette ───────────────────────────────────────────────────────────
    //
    // Bei änderungssicheren Fahrzeugen bilden die Fahrten (nach ID sortiert, ohne automatische)
    // eine Kette: hash_n = SHA-256(hash_(n-1) + kanonischeDaten_n), hash_0 = "".
    // Wird eine Fahrt im Backup verändert, eingefügt oder entfernt, passt die Kette ab dort
    // nicht mehr. Format NICHT ändern – sonst sind bestehende Ketten und Backups ungültig.

    /** Berechnet die Kette eines Fahrzeugs neu und speichert geänderte Hashes (nur bei Änderungssicherheit) */
    suspend fun recomputeChain(context: Context, vehicleId: Long?) = recomputeChain(context.dao, vehicleId)

    private suspend fun recomputeChain(dao: AppDao, vehicleId: Long?) {
        if (vehicleId == null || !isProtected(dao, vehicleId)) return
        val trips = dao.chainTrips(vehicleId)
        trips.zip(chainHashes(trips)).forEach { (trip, hash) ->
            if (trip.chainHash != hash) dao.setChainHash(trip.id, hash)
        }
    }

    /** Erwartete Hashes für nach ID sortierte Fahrten eines Fahrzeugs */
    fun chainHashes(trips: List<Trip>): List<String> {
        var previous = ""
        return trips.map { trip -> sha256(previous + canonical(trip)).also { previous = it } }
    }

    /** Erste Fahrt, deren gespeicherter Hash nicht zur Kette passt, oder null wenn alles stimmt */
    fun firstBrokenLink(trips: List<Trip>): Trip? =
        trips.zip(chainHashes(trips)).firstOrNull { (trip, hash) -> trip.chainHash != hash }?.first

    /** Kanonische Textform einer Fahrt für die Hash-Kette */
    fun canonical(t: Trip): String = listOf(
        t.id, t.date.time, t.startLocation, t.endLocation, t.distanceKm, t.purpose, t.purposeId, t.notes,
        t.startOdometer, t.endOdometer, t.vehicleId, t.isCancelled, t.cancellationReason, t.isActive,
        t.endTime?.time, t.gpsDistanceKm, t.businessPartner, t.route
    ).joinToString("|") { it?.toString() ?: "null" }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    // ── Änderungsprotokoll ───────────────────────────────────────────────────

    /** Ein Protokolleintrag je geändertem Feld (lesbare Feldnamen und Werte) */
    fun auditDiff(old: Trip, new: Trip, now: Date = Date()): List<TripAuditLog> {
        fun date(d: Date?) = d?.let { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(it) }
        val fields: List<Triple<String, Any?, Any?>> = listOf(
            Triple("Abfahrt", date(old.date), date(new.date)),
            Triple("Ankunft", date(old.endTime), date(new.endTime)),
            Triple("Startort", old.startLocation, new.startLocation),
            Triple("Zielort", old.endLocation, new.endLocation),
            Triple("Distanz (km)", old.distanceKm, new.distanceKm),
            Triple("Km-Stand Start", old.startOdometer, new.startOdometer),
            Triple("Km-Stand Ende", old.endOdometer, new.endOdometer),
            Triple("Kategorie", old.purpose, new.purpose),
            Triple("Fahrzeug-ID", old.vehicleId, new.vehicleId),
            Triple("Geschäftspartner", old.businessPartner, new.businessPartner),
            Triple("Route", old.route, new.route),
            Triple("Notizen", old.notes, new.notes),
            Triple("Storniert", old.isCancelled, new.isCancelled),
            Triple("Stornogrund", old.cancellationReason, new.cancellationReason)
        )
        return fields.filter { (_, a, b) -> a != b }.map { (name, a, b) ->
            TripAuditLog(tripId = old.id, fieldName = name, oldValue = a?.toString(), newValue = b?.toString(), changedAt = now)
        }
    }
}
