package de.fosstenbuch.logic

import android.content.Context
import androidx.room.withTransaction
import de.fosstenbuch.data.AppDao
import de.fosstenbuch.data.Trip
import de.fosstenbuch.data.TripAuditLog
import de.fosstenbuch.data.Vehicle
import de.fosstenbuch.data.dao
import de.fosstenbuch.data.db
import de.fosstenbuch.tracking.TrackingService
import java.security.MessageDigest
import java.util.Date
import kotlin.math.roundToInt

/**
 * Alle Schreibzugriffe auf Fahrten mit ihren Geschäftsregeln:
 * Validierung, Bearbeitungssperre, Änderungsprotokoll, Siegel und Hash-Kette.
 * Jede Operation läuft in einer Transaktion – entweder alles oder nichts.
 * Abgeschlossene Fahrten werden nie gelöscht, nur storniert.
 *
 * Fehler werden als Exception gemeldet; die Meldung ist für den Nutzer gedacht:
 *  - [Invalid]: Feldfehler fürs Formular
 *  - [Rejected]: Regelverstoß (z.B. gesperrte Fahrt)
 * Bewusst keine IllegalStateException: Davon erbt auch CancellationException, ein breites
 * catch würde sonst den Abbruch der Coroutine (Bildschirm verlassen) verschlucken.
 */
object Trips {

    /** Formularfelder, an denen Validierungsfehler angezeigt werden */
    enum class Field { START_LOCATION, END_LOCATION, DISTANCE, PURPOSE, DATE, END_TIME, START_ODOMETER, ODOMETER, VEHICLE, PARTNER }

    /** Validierungsfehler je Formularfeld; message = alle Fehler zeilenweise */
    class Invalid(val errors: Map<Field, String>) : Exception(errors.values.joinToString("\n"))

    /** Regelverstoß mit nutzertauglicher Meldung (z.B. gesperrte Fahrt) */
    class Rejected(message: String) : Exception(message)

    /** Wie require(), aber mit [Rejected] */
    private inline fun require(condition: Boolean, message: () -> String) {
        if (!condition) throw Rejected(message())
    }

    private const val MAX_TEXT = 200
    private const val MAX_DISTANCE_KM = 99_999
    /** Spielraum für die Ankunft gegenüber der Uhr (Formular offen, Uhr leicht abweichend) */
    private const val FUTURE_TOLERANCE_MS = 60_000L
    /** So lange lässt sich eine versehentlich gestartete Fahrt noch verwerfen (danach beenden und stornieren) */
    const val DISCARD_MS = 5 * 60_000L

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

    /** Pflichtangaben einer abgeschlossenen Fahrt; [business] = Kategorie ist beruflich (isBusinessRelevant) */
    fun validate(t: Trip, now: Date = Date(), business: Boolean = false): Map<Field, String> = buildMap {
        checkText(t.startLocation, "Startort", Field.START_LOCATION)
        checkText(t.endLocation, "Zielort", Field.END_LOCATION)
        if (t.purposeId == null) put(Field.PURPOSE, "Bitte wählen Sie eine Kategorie aus")
        if (t.date.after(now)) put(Field.DATE, "Datum darf nicht in der Zukunft liegen")
        when {
            t.endTime == null -> Unit
            t.endTime.before(t.date) -> put(Field.END_TIME, "Ankunft darf nicht vor der Abfahrt liegen")
            t.endTime.time > now.time + FUTURE_TOLERANCE_MS -> put(Field.END_TIME, "Ankunft darf nicht in der Zukunft liegen")
        }
        // Ohne Anlass ist eine berufliche Fahrt nicht nachvollziehbar
        if (business && t.businessPartner.isNullOrBlank() && t.notes.isNullOrBlank()) {
            put(Field.PARTNER, "Bei beruflichen Fahrten bitte Geschäftspartner oder Anlass (Notiz) angeben")
        }
        val start = t.startOdometer
        val end = t.endOdometer
        when {
            start == null || end == null -> put(Field.ODOMETER, "Start- und Endkilometerstand müssen angegeben werden")
            start < 0 -> put(Field.ODOMETER, "Kilometerstand darf nicht negativ sein")
            // 0 km erlaubt: Sonst ließe sich eine gestartete, aber nicht gefahrene Fahrt nach Ablauf von
            // DISCARD_MS weder verwerfen noch wahrheitsgemäß beenden (und danach stornieren)
            end < start -> put(Field.ODOMETER, "Endkilometerstand darf nicht kleiner als Startkilometerstand sein")
            end - start > MAX_DISTANCE_KM -> put(Field.DISTANCE, "Distanz darf maximal $MAX_DISTANCE_KM km betragen")
        }
        if (t.vehicleId == null) put(Field.VEHICLE, "Bitte wählen Sie ein Fahrzeug aus")
    }

    private fun MutableMap<Field, String>.checkText(value: String, label: String, field: Field) {
        if (value.isBlank()) put(field, "$label darf nicht leer sein")
        else if (value.length > MAX_TEXT) put(field, "$label darf maximal $MAX_TEXT Zeichen lang sein")
    }

    // ── Schreiboperationen ───────────────────────────────────────────────────

    /**
     * Startet eine Fahrt (isActive) – der einzige Weg zu einer neuen Fahrt (neben der automatischen
     * Erkennung). Es kann immer nur eine laufen. Ende und Km-Stand Ende kommen erst beim Beenden. Liefert die neue ID.
     */
    suspend fun start(context: Context, trip: Trip): Long {
        // Ziel, Ankunft und End-Km kommen erst beim Beenden
        val t = trip.copy(
            id = 0, isActive = true, isGhost = false, endTime = null, endOdometer = null, endLocation = "",
            startLocation = trip.startLocation.trim()
        )
        validateStart(t).let { if (it.isNotEmpty()) throw Invalid(it) }
        val db = context.db
        return db.withTransaction {
            val dao = db.dao()
            require(dao.activeTrip() == null) { "Es läuft bereits eine Fahrt" }
            dao.insertTrip(t).also { recomputeChain(dao, t.vehicleId) }
        }
    }

    /**
     * Speichert eine abgeschlossene Fahrt: laufende Fahrt beenden, automatische Fahrt übernehmen oder
     * bestehende Fahrt bearbeiten. Nachtragen (neu anlegen) gibt es bewusst nicht: Fahrten werden gestartet,
     * beendet und danach höchstens geändert – jede Änderung landet im Protokoll ([auditDiff]), jeder neue
     * Stand bekommt ein Siegel ([Seals]). Die Distanz ergibt sich immer aus den Kilometerständen.
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
        require(t.id != 0L) { "Neue Fahrten bitte starten und beenden" }
        val business = t.purposeId?.let { context.dao.purpose(it) }?.isBusinessRelevant == true
        validate(t, business = business).let { if (it.isNotEmpty()) throw Invalid(it) }
        val db = context.db
        return db.withTransaction {
            val dao = db.dao()
            val old = dao.trip(t.id) ?: throw Rejected("Die Fahrt wurde inzwischen gelöscht")
            // Bluetooth hat sich kurz nach dem Trennen wieder verbunden: Die Fahrt läuft weiter
            require(!old.isGhost || old.endTime != null) {
                "Die Fahrt wird gerade wieder aufgezeichnet – bitte nach dem Ende übernehmen"
            }
            val completed = !old.isActive && !old.isGhost
            if (completed) {
                // Abgeschlossene Fahrten von Fahrzeugen mit Bearbeitungssperre sind gesperrt – und dürfen auch
                // nicht nachträglich zu einem solchen Fahrzeug wandern
                require(!isProtected(dao, old.vehicleId)) { "Bearbeitungssperre – abgeschlossene Fahrten lassen sich nur stornieren" }
                require(old.vehicleId == t.vehicleId || !isProtected(dao, t.vehicleId)) {
                    "Fahrzeug mit Bearbeitungssperre – Verschieben nicht möglich"
                }
            }
            val vehicles = dao.allVehicles().associateBy { it.id }
            dao.insertAuditLogs(auditDiff(old, t) { id -> vehicles[id]?.let(::vehicleName) ?: id?.toString() })
            // Eine gerade übernommene automatische Fahrt ist noch nie exportiert worden
            val final = if (old.isGhost) t.copy(isExported = false) else t
            dao.updateTrip(final)
            recomputeChain(dao, t.vehicleId)
            if (old.vehicleId != t.vehicleId) recomputeChain(dao, old.vehicleId)
            when {
                old.isActive -> Seals.write(context, dao, t.id, Seals.ENDED)
                old.isGhost -> Seals.write(context, dao, t.id, Seals.ACCEPTED)
                else -> Seals.write(context, dao, t.id, Seals.EDITED, before = old)
            }
            t.id
        }.also { Tsa.stampPending(context) } // Zeitstempel fürs neue Siegel im Hintergrund (falls eingeschaltet)
    }

    /**
     * Storniert eine abgeschlossene Fahrt mit Pflicht-Grund. Auch bei Fahrzeugen mit Bearbeitungssperre erlaubt
     * (wird protokolliert und gesiegelt).
     */
    suspend fun cancel(context: Context, trip: Trip, reason: String) {
        require(reason.isNotBlank()) { "Bitte einen Grund für die Stornierung angeben" }
        val db = context.db
        db.withTransaction {
            val dao = db.dao()
            val old = dao.trip(trip.id) ?: return@withTransaction
            require(!old.isActive && !old.isGhost && !old.isCancelled) { "Diese Fahrt kann nicht storniert werden" }
            val new = old.copy(isCancelled = true, cancellationReason = reason.trim())
            dao.insertAuditLogs(auditDiff(old, new))
            dao.updateTrip(new)
            recomputeChain(dao, new.vehicleId)
            Seals.write(context, dao, new.id, Seals.CANCELLED)
        }
        Tsa.stampPending(context)
    }

    /**
     * Verwirft eine automatische (noch nicht übernommene) oder eine gerade erst gestartete Fahrt ([canDiscard]).
     * Abgeschlossene Fahrten lassen sich nicht löschen, nur stornieren – auf keinem Fahrzeug.
     */
    suspend fun delete(context: Context, trip: Trip) {
        val db = context.db
        db.withTransaction {
            val dao = db.dao()
            val t = dao.trip(trip.id) ?: return@withTransaction
            checkDelete(t)
            dao.deleteTrip(t)
            recomputeChain(dao, t.vehicleId)
        }
        // Lief für diese Fahrt noch die GPS-Aufzeichnung, wird sie beendet
        if (TrackingService.live.value?.tripId == trip.id) TrackingService.stop(context)
    }

    /** Wirft [Rejected], wenn [t] nicht gelöscht werden darf (siehe [delete]) */
    fun checkDelete(t: Trip, now: Date = Date()) {
        require(t.isGhost || t.isActive) { "Abgeschlossene Fahrten lassen sich nur stornieren" }
        require(t.isGhost || canDiscard(t, now)) { "Laufende Fahrt bitte beenden und dann stornieren" }
    }

    /** Laufende Fahrt, die vor höchstens [DISCARD_MS] gestartet wurde (z.B. versehentlich) */
    fun canDiscard(t: Trip, now: Date = Date()): Boolean = t.isActive && now.time - t.date.time <= DISCARD_MS

    /** Geschätzter End-Kilometerstand aus Start + gerundeter GPS-Strecke; null ohne Start oder GPS-Strecke */
    fun estimatedEndOdometer(startOdometer: Int?, gpsKm: Double?): Int? =
        if (startOdometer != null && gpsKm != null && gpsKm > 0) startOdometer + gpsKm.roundToInt() else null

    /** Distanz laut Kilometerstand, null wenn ein Stand fehlt */
    fun odometerDistance(t: Trip): Double? {
        val start = t.startOdometer ?: return null
        val end = t.endOdometer ?: return null
        return (end - start).toDouble()
    }

    private suspend fun isProtected(dao: AppDao, vehicleId: Long?): Boolean =
        vehicleId != null && dao.vehicle(vehicleId)?.auditProtected == true

    /** Fahrzeug im Änderungsprotokoll: Kennzeichen, sonst Hersteller und Modell, sonst die ID */
    fun vehicleName(v: Vehicle): String = v.licensePlate.ifBlank { "${v.make} ${v.model}".trim() }.ifBlank { v.id.toString() }

    // ── Hash-Kette ───────────────────────────────────────────────────────────
    //
    // Bei Fahrzeugen mit Bearbeitungssperre bilden die Fahrten (nach ID sortiert, ohne automatische)
    // eine Kette: hash_n = SHA-256(hash_(n-1) + kanonischeDaten_n), hash_0 = "".
    // Wird eine Fahrt im Backup verändert, eingefügt oder entfernt, passt die Kette ab dort
    // nicht mehr. Format NICHT ändern – sonst sind bestehende Ketten und Backups ungültig.

    /** Berechnet die Kette eines Fahrzeugs neu und speichert geänderte Hashes (nur bei Bearbeitungssperre) */
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

    /**
     * Ein Protokolleintrag je geändertem Feld (lesbare Feldnamen und Werte). Beim Beenden einer laufenden
     * bzw. Übernehmen einer automatischen Fahrt ist das erste Ausfüllen (Ziel, Ankunft, Km-Stand Ende …)
     * keine Änderung: Protokolliert wird dann nur das Überschreiben schon erfasster Werte; die Distanz
     * folgt ohnehin aus den Km-Ständen. Ein Fahrzeugwechsel erscheint mit [vehicle] (Kennzeichen, siehe [vehicleName])
     * statt der ID.
     */
    fun auditDiff(old: Trip, new: Trip, now: Date = Date(), vehicle: (Long?) -> String? = { it?.toString() }): List<TripAuditLog> {
        fun date(d: Date?) = d?.let(Fmt::dateTime)
        val fields: List<Triple<String, Any?, Any?>> = listOf(
            Triple("Abfahrt", date(old.date), date(new.date)),
            Triple("Ankunft", date(old.endTime), date(new.endTime)),
            Triple("Startort", old.startLocation, new.startLocation),
            Triple("Zielort", old.endLocation, new.endLocation),
            Triple("Distanz (km)", old.distanceKm, new.distanceKm),
            Triple("Km-Stand Start", old.startOdometer, new.startOdometer),
            Triple("Km-Stand Ende", old.endOdometer, new.endOdometer),
            Triple("Kategorie", old.purpose, new.purpose),
            Triple("Fahrzeug", old.vehicleId, new.vehicleId),
            Triple("Geschäftspartner", old.businessPartner, new.businessPartner),
            Triple("Route", old.route, new.route),
            Triple("Notizen", old.notes, new.notes),
            Triple("Storniert", old.isCancelled, new.isCancelled),
            Triple("Stornogrund", old.cancellationReason, new.cancellationReason)
        )
        val completing = old.isActive || old.isGhost
        // Verglichen wird die Fahrzeug-ID, angezeigt der Name
        fun show(name: String, v: Any?) = if (name == "Fahrzeug") vehicle(v as Long?) else v?.toString()
        return fields.filter { (name, a, b) -> a != b && !(completing && (a == null || a == "" || name == "Distanz (km)")) }.map { (name, a, b) ->
            TripAuditLog(tripId = old.id, fieldName = name, oldValue = show(name, a), newValue = show(name, b), changedAt = now)
        }
    }
}
