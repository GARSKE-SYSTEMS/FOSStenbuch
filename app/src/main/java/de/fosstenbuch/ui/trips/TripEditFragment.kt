package de.fosstenbuch.ui.trips

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Filter
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import de.fosstenbuch.R
import de.fosstenbuch.data.SavedLocation
import de.fosstenbuch.data.Settings
import de.fosstenbuch.data.Trip
import de.fosstenbuch.data.TripPurpose
import de.fosstenbuch.data.TripTemplate
import de.fosstenbuch.data.Vehicle
import de.fosstenbuch.data.dao
import de.fosstenbuch.databinding.FragmentTripEditBinding
import de.fosstenbuch.databinding.ItemTemplateBinding
import de.fosstenbuch.logic.Geo
import de.fosstenbuch.logic.Trips
import de.fosstenbuch.tracking.TrackingService
import de.fosstenbuch.ui.common.Fmt
import de.fosstenbuch.ui.common.SimpleAdapter
import de.fosstenbuch.ui.common.argId
import de.fosstenbuch.ui.common.close
import de.fosstenbuch.ui.common.collect
import de.fosstenbuch.ui.common.confirm
import de.fosstenbuch.ui.common.intOrNull
import de.fosstenbuch.ui.common.launch
import de.fosstenbuch.ui.common.navigate
import de.fosstenbuch.ui.common.pickDateTime
import de.fosstenbuch.ui.common.setDropdown
import de.fosstenbuch.ui.common.textOrNull
import de.fosstenbuch.ui.common.toast
import kotlinx.coroutines.Job
import java.util.Date
import kotlin.math.roundToInt

/**
 * Formular für eine Fahrt. Was es anbietet, ergibt sich allein aus der geladenen Fahrt ([Mode]):
 * neue Fahrt starten oder nachtragen, laufende Fahrt beenden oder verwerfen, automatisch erkannte Fahrt
 * übernehmen oder verwerfen, abgeschlossene Fahrt bearbeiten.
 *
 * Zustand: Feldtexte stellt Android nach Rotation und nach der Rückkehr aus "Neuen Standort
 * speichern" selbst wieder her. Alles andere (Zeiten, gewählte IDs) steht in Feldern dieser
 * Klasse und im savedInstanceState; befüllt wird das Formular nur einmal ([populated]).
 */
class TripEditFragment : Fragment(R.layout.fragment_trip_edit) {

    /** Was das Formular gerade tut */
    enum class Mode {
        NEW, ACTIVE, GHOST, RECORDING, EDIT;

        companion object {
            /** [trip] = geladene Fahrt, null bei einer neuen */
            fun of(trip: Trip?): Mode = when {
                trip == null -> NEW
                trip.isActive -> ACTIVE
                trip.isGhost && trip.endTime == null -> RECORDING
                trip.isGhost -> GHOST
                else -> EDIT
            }
        }
    }

    private var _b: FragmentTripEditBinding? = null
    private val b get() = _b!!

    // Formularzustand außerhalb der Textfelder (überlebt Rotation über onSaveInstanceState)
    private var populated = false
    private var date = Date()
    private var endTime: Date? = null
    private var vehicleId: Long? = null
    private var purposeId: Long? = null
    /** Zuletzt automatisch eingetragener Start-Km-Stand – nur diesen darf ein neuer Vorschlag ersetzen */
    private var suggestedKm: String? = null

    // Aus der Datenbank, bei jeder neuen Ansicht frisch geladen (siehe load)
    private var trip: Trip? = null
    private var mode = Mode.NEW
    private var vehicles = emptyList<Vehicle>()
    private var purposes = emptyList<TripPurpose>()
    private var loading: Job? = null

    private val suggestPermission = registerForActivityResult(RequestMultiplePermissions()) { suggestLocation() }
    private val startPermission = registerForActivityResult(RequestMultiplePermissions()) { start() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Hier statt in onViewCreated: Liegt das Fragment beim Drehen auf dem Zurück-Stapel,
        // entsteht seine Ansicht erst später – den Zustand braucht es trotzdem.
        savedInstanceState?.run {
            populated = getBoolean("populated")
            date = Date(getLong("date"))
            endTime = getLong("endTime", -1).takeIf { it >= 0 }?.let { Date(it) }
            vehicleId = getLong("vehicleId", -1).takeIf { it >= 0 }
            purposeId = getLong("purposeId", -1).takeIf { it >= 0 }
            suggestedKm = getString("suggestedKm")
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("populated", populated)
        outState.putLong("date", date.time)
        outState.putLong("endTime", endTime?.time ?: -1)
        outState.putLong("vehicleId", vehicleId ?: -1)
        outState.putLong("purposeId", purposeId ?: -1)
        outState.putString("suggestedKm", suggestedKm)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentTripEditBinding.bind(view)
        b.editDate.setOnClickListener { pickDateTime(date) { date = it; showTimes() } }
        b.editEndTime.setOnClickListener { pickDateTime(endTime ?: Date()) { endTime = it; showTimes() } }
        b.editStartKm.doAfterTextChanged { showDistance() }
        b.editEndKm.doAfterTextChanged { showDistance() }

        // Ortsfelder: Vorschläge aus gespeicherten Orten, Partner des Orts übernehmen oder neuen Ort anlegen.
        // Beim Anlegen bleibt das Formular erhalten, Android stellt die Felder bei der Rückkehr wieder her.
        for (field in listOf(b.editStart, b.editEnd)) {
            field.setOnItemClickListener { parent, _, position, _ ->
                val item = parent.getItemAtPosition(position) as PlaceAdapter.Item
                if (item.place != null) takePartner(item.place) else navigate(R.id.navigation_location_edit, "name" to item.name)
            }
        }
        val res = resources // nicht getString(): Der Filter läuft im Hintergrund, evtl. nach dem Schließen
        collect(requireContext().dao.locations()) { places ->
            for (field in listOf(b.editStart, b.editEnd)) {
                field.setAdapter(PlaceAdapter(requireContext(), places) { res.getString(R.string.trips_add_location, it) })
            }
        }

        b.buttonStart.setOnClickListener {
            // Erst prüfen, dann nach Berechtigungen fragen – sonst kämen die Dialoge vor der Fehlermeldung
            val errors = Trips.validateStart(formTrip())
            showErrors(errors)
            if (errors.isEmpty()) {
                setBusy(true)
                startPermission.launch(START_PERMISSIONS)
            }
        }
        b.buttonSave.setOnClickListener { save() }
        b.buttonDiscard.setOnClickListener { trip?.let { confirmDelete(it) { close() } } }
        b.buttonUseTemplate.setOnClickListener { chooseTemplate() }
        b.buttonSaveTemplate.setOnClickListener { saveTemplate() }
        loading = launch { load() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    /** Lädt Fahrt und Auswahllisten, befüllt das Formular beim ersten Mal und zeigt die passenden Knöpfe */
    private suspend fun load() {
        val ctx = requireContext()
        trip = if (argId == 0L) null else ctx.dao.trip(argId) ?: return close() // inzwischen gelöscht
        mode = Mode.of(trip)
        vehicles = ctx.dao.allVehicles()
        purposes = ctx.dao.allPurposes()
        if (!populated && mode != Mode.RECORDING) populate(ctx)
        showTimes()
        b.inputVehicle.setDropdown(vehicles.map { it.label }, vehicles.indexOfFirst { it.id == vehicleId }) {
            vehicleId = vehicles[it].id
            launch { suggestStartKm() }
        }
        b.inputPurpose.setDropdown(purposes.map { it.name }, purposes.indexOfFirst { it.id == purposeId }) {
            purposeId = purposes[it].id
        }
        render(ctx)
    }

    /** Erstbefüllung: Werte der Fahrt bzw. Vorschläge für eine neue (Hauptfahrzeug, Standard-Kategorie, Km-Stand) */
    private suspend fun populate(ctx: Context) {
        val t = trip
        if (t == null) {
            vehicleId = (vehicles.firstOrNull { it.isPrimary } ?: vehicles.singleOrNull())?.id
            val defaultPurpose = Settings.get(ctx, Settings.DEFAULT_PURPOSE_ID, 0L)
            purposeId = purposes.firstOrNull { it.id == defaultPurpose }?.id
        } else {
            date = t.date
            endTime = t.endTime ?: if (mode == Mode.ACTIVE) Date() else null // Beenden: Ankunft = jetzt
            b.editStart.setText(t.startLocation, false) // false: Vorschlagsliste nicht aufklappen
            b.editEnd.setText(t.endLocation, false)
            b.editStartKm.setText(t.startOdometer?.toString())
            b.editEndKm.setText(t.endOdometer?.toString())
            vehicleId = t.vehicleId
            purposeId = t.purposeId
            b.editPartner.setText(t.businessPartner)
            b.editRoute.setText(t.route)
            b.editNotes.setText(t.notes)
        }
        populated = true
        suggestStartKm()
        if (mode == Mode.GHOST) suggestEndKm(t?.gpsDistanceKm)
        // Ortsvorschlag nur, wo die Fahrt gerade hier beginnt bzw. endet
        if (mode == Mode.NEW || mode == Mode.ACTIVE) suggestPermission.launch(LOCATION)
    }

    /** Hinweis und Knöpfe passend zum Modus; Fahrten änderungssicherer Fahrzeuge sind komplett gesperrt */
    private fun render(ctx: Context) {
        val t = trip
        val locked = mode == Mode.EDIT && vehicles.any { it.id == t?.vehicleId && it.auditProtected }
        val editable = mode != Mode.RECORDING && !locked
        b.form.isVisible = mode != Mode.RECORDING
        if (locked) b.form.children.forEach { it.isEnabled = false }
        b.rowTemplates.isVisible = editable
        b.buttonStart.isVisible = mode == Mode.NEW
        b.buttonSave.isVisible = editable
        b.buttonSave.setText(
            when (mode) {
                Mode.NEW -> R.string.trips_save_completed
                Mode.ACTIVE -> R.string.end_trip
                Mode.GHOST -> R.string.ghost_accept
                else -> R.string.save
            }
        )
        // Laufende Fahrt verwerfen (z.B. versehentlich gestartet) – Trips.delete erlaubt das auch bei
        // änderungssicheren Fahrzeugen und beendet die GPS-Aufzeichnung selbst
        b.buttonDiscard.isVisible = mode == Mode.GHOST || mode == Mode.ACTIVE
        showHint(
            when {
                locked -> getString(R.string.audit_locked_hint)
                mode == Mode.NEW -> getString(R.string.trips_new_hint)
                mode == Mode.RECORDING -> getString(R.string.trips_recording_hint)
                // GPS-Strecke als Anhaltspunkt, falls die Km-Stände erst noch eingetragen werden müssen
                mode == Mode.GHOST -> listOfNotNull(
                    getString(R.string.ghost_detail_hint),
                    t?.gpsDistanceKm?.takeIf { it > 0 }?.let { getString(R.string.gps_distance_format, it) }
                ).joinToString("\n")
                t?.isCancelled == true -> t.cancellationReason?.takeIf { it.isNotBlank() }
                    ?.let { getString(R.string.cancel_trip_reason_format, it) } ?: getString(R.string.trip_cancelled)
                else -> null
            }
        )
        if (mode == Mode.ACTIVE) collect(TrackingService.live) { live ->
            val km = live?.takeIf { it.tripId == t?.id }?.km
            showHint(listOfNotNull(getString(R.string.trip_in_progress), km?.let { getString(R.string.active_trip_gps_km, it) }).joinToString("\n"))
            if (suggestEndKm(km)) b.layoutEndKm.helperText = getString(R.string.estimated_odometer)
        }
        if (mode == Mode.EDIT && t != null) collect(ctx.dao.auditLog(t.id)) { logs ->
            b.groupAudit.isVisible = logs.isNotEmpty()
            b.textAudit.text = logs.joinToString("\n") {
                getString(R.string.trips_audit_entry, Fmt.dateTime(it.changedAt), it.fieldName, it.oldValue ?: "–", it.newValue ?: "–")
            }
        }
    }

    /**
     * Die Fahrt laut Formular. Basis ist immer die geladene Fahrt (copy), damit isExported,
     * Koordinaten, GPS-Strecke und Hash erhalten bleiben.
     */
    private fun formTrip(): Trip {
        val base = trip ?: Trip(date = date, startLocation = "")
        val live = TrackingService.live.value?.takeIf { it.tripId == trip?.id && it.km > 0 }
        return base.copy(
            date = date,
            endTime = endTime,
            startLocation = b.editStart.text.toString().trim(),
            endLocation = b.editEnd.text.toString().trim(),
            startOdometer = b.editStartKm.intOrNull(),
            endOdometer = b.editEndKm.intOrNull(),
            vehicleId = vehicleId,
            purposeId = purposeId,
            purpose = purposeText(base, purposeId, purposes.firstOrNull { it.id == purposeId }?.name),
            businessPartner = b.editPartner.textOrNull(),
            route = b.editRoute.textOrNull(),
            notes = b.editNotes.textOrNull(),
            gpsDistanceKm = live?.km ?: base.gpsDistanceKm
        )
    }

    /** Startet die Fahrt (nach der Berechtigungsabfrage). GPS nur mit Standortberechtigung, die Fahrt auch ohne. */
    private fun start() = launch {
        loading?.join() // nach einer Rotation kann die Antwort vor dem Laden ankommen
        val ctx = requireContext()
        try {
            val id = Trips.start(ctx, formTrip())
            if (Geo.hasPermission(ctx)) TrackingService.startManual(ctx, id)
            close()
        } catch (e: Trips.Invalid) {
            showErrors(e.errors)
            setBusy(false)
        } catch (e: IllegalStateException) {
            toast(e.message!!)
            setBusy(false)
        }
    }

    /** Speichert: Nachtrag, laufende Fahrt beenden, automatische Fahrt übernehmen oder Änderungen */
    private fun save() = launch {
        val ctx = requireContext()
        val old = trip ?: Trip(date = date, startLocation = "")
        val t = formTrip()
        showErrors(emptyMap())
        setBusy(true)
        try {
            Trips.save(ctx, t)
            locationsToCount(old, t).forEach { ctx.dao.countLocationUsage(it) }
            // Nur die Aufzeichnung DIESER Fahrt beenden, keine parallel laufende automatische
            if (mode == Mode.ACTIVE && TrackingService.live.value.let { it == null || it.tripId == t.id }) {
                TrackingService.stop(ctx)
            }
            if (mode == Mode.GHOST) toast(getString(R.string.ghost_accepted_success))
            close()
        } catch (e: Trips.Invalid) {
            showErrors(e.errors)
            setBusy(false)
        } catch (e: IllegalStateException) {
            toast(e.message!!)
            setBusy(false)
        }
    }

    /** Verhindert Doppel-Tipps (sonst z.B. zwei nachgetragene Fahrten) */
    private fun setBusy(busy: Boolean) = listOf(b.buttonStart, b.buttonSave, b.buttonDiscard).forEach { it.isEnabled = !busy }

    /** Zeigt Feldfehler (leere Map = alle entfernen) und scrollt zum obersten */
    private fun showErrors(errors: Map<Trips.Field, String>) {
        Trips.Field.entries.forEach { layoutOf(it).error = null }
        errors.forEach { (field, message) -> layoutOf(field).error = message }
        errors.keys.minOfOrNull { layoutOf(it).top }?.let { b.scroll.smoothScrollTo(0, b.form.top + it) }
    }

    /** Wo ein Feldfehler erscheint. "when" ohne else: Ein neues Feld in Trips.Field fällt beim Kompilieren auf. */
    private fun layoutOf(field: Trips.Field): TextInputLayout = when (field) {
        Trips.Field.START_LOCATION -> b.layoutStart
        Trips.Field.END_LOCATION -> b.layoutEnd
        Trips.Field.DATE -> b.layoutDate
        Trips.Field.END_TIME -> b.layoutEndTime
        Trips.Field.START_ODOMETER -> b.layoutStartKm
        Trips.Field.ODOMETER, Trips.Field.DISTANCE -> b.layoutEndKm
        Trips.Field.PURPOSE -> b.layoutPurpose
        Trips.Field.VEHICLE -> b.layoutVehicle
    }

    private fun showHint(text: String?) {
        b.textHint.text = text
        b.textHint.isVisible = text != null
    }

    private fun showTimes() {
        b.editDate.setText(Fmt.dateTime(date))
        b.editEndTime.setText(endTime?.let { Fmt.dateTime(it) })
    }

    /** Distanz nur zur Info – Trips.save berechnet sie selbst aus den Km-Ständen */
    private fun showDistance() {
        val start = b.editStartKm.intOrNull()
        val end = b.editEndKm.intOrNull()
        b.textDistance.text =
            if (start != null && end != null && end > start) getString(R.string.trips_distance, Fmt.km((end - start).toDouble())) else ""
    }

    /**
     * Schlägt den letzten Km-Stand des Fahrzeugs als Start vor – in ein leeres Feld oder anstelle
     * des eigenen früheren Vorschlags (Fahrzeugwechsel), nie über eine Eingabe des Nutzers.
     * Nur für neue und automatische Fahrten: Bei einer alten Fahrt ohne Start-Km (Altdaten) wäre
     * der heutige Höchststand falsch und sähe trotzdem wie der gespeicherte Wert aus.
     */
    private suspend fun suggestStartKm() {
        if (mode != Mode.NEW && mode != Mode.GHOST) return
        val km = vehicleId?.let { requireContext().dao.lastOdometer(it) }?.toString()
        if (isReplaceable(b.editStartKm.text.toString(), suggestedKm)) {
            b.editStartKm.setText(km)
            suggestedKm = km
        }
    }

    /** Trägt Start + Strecke (GPS oder Vorlage) als End-Km-Stand ein, nur in ein leeres Feld; true wenn eingetragen */
    private fun suggestEndKm(km: Double?): Boolean {
        val end = endKmSuggestion(b.editStartKm.intOrNull(), km) ?: return false
        if (!b.editEndKm.text.isNullOrBlank()) return false
        b.editEndKm.setText(end.toString())
        return true
    }

    /** Nächster gespeicherter Ort (≤ 1 km) als Vorschlag: neue Fahrt → Startort, laufende Fahrt → Zielort */
    private fun suggestLocation() = launch {
        loading?.join()
        val ctx = requireContext()
        val field = when (mode) {
            Mode.NEW -> b.editStart
            Mode.ACTIVE -> b.editEnd
            else -> return@launch
        }
        val here = Geo.currentLocation(ctx) ?: return@launch
        val place = Geo.nearest(ctx.dao.allLocations(), here.latitude, here.longitude, 1000.0) ?: return@launch
        if (!field.text.isNullOrBlank()) return@launch // inzwischen selbst ausgefüllt (GPS kann 10 s dauern)
        field.setText(place.name, false)
        takePartner(place)
        toast(getString(R.string.location_suggested, place.name))
    }

    /** Übernimmt den Geschäftspartner eines gewählten Orts, ohne eine Eingabe zu überschreiben */
    private fun takePartner(place: SavedLocation) {
        if (b.editPartner.text.isNullOrBlank() && !place.businessPartner.isNullOrBlank()) b.editPartner.setText(place.businessPartner)
    }

    /** Vorlagen-Auswahl: Tippen wendet die Vorlage an, der Mülleimer löscht sie (nach Rückfrage) */
    private fun chooseTemplate() = launch {
        val templates = requireContext().dao.allTemplates()
        if (templates.isEmpty()) return@launch toast(getString(R.string.no_templates))
        val list = RecyclerView(requireContext()).apply { layoutManager = LinearLayoutManager(context) }
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.use_template)
            .setView(list)
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        list.adapter = SimpleAdapter(ItemTemplateBinding::inflate, { t: TripTemplate -> t.id }) { item, t ->
            item.textName.text = t.name
            item.textRoute.text = getString(R.string.trip_route_format, t.startLocation, t.endLocation) +
                (if (t.distanceKm > 0) " · " + Fmt.km(t.distanceKm) else "")
            item.root.setOnClickListener {
                dialog.dismiss()
                applyTemplate(t)
            }
            item.buttonDelete.setOnClickListener {
                dialog.dismiss()
                confirm(getString(R.string.delete_template_title), getString(R.string.delete_template_message), getString(R.string.delete)) {
                    launch { requireContext().dao.deleteTemplate(t) }
                }
            }
        }.also { it.submitList(templates) }
    }

    /**
     * Übernimmt eine Vorlage. Fahrzeug und Kategorie nur, wenn es sie noch gibt (Vorlagen haben keine
     * Fremdschlüssel); leere Werte überschreiben nichts. Die Distanz der Vorlage hilft nur beim
     * End-Km-Stand – gespeichert wird immer die Distanz aus den Km-Ständen.
     */
    private fun applyTemplate(t: TripTemplate) = launch {
        t.startLocation.takeIf { it.isNotBlank() }?.let { b.editStart.setText(it, false) }
        t.endLocation.takeIf { it.isNotBlank() }?.let { b.editEnd.setText(it, false) }
        t.businessPartner?.let { b.editPartner.setText(it) }
        t.route?.let { b.editRoute.setText(it) }
        t.notes?.let { b.editNotes.setText(it) }
        purposes.firstOrNull { it.id == t.purposeId }?.let {
            purposeId = it.id
            b.inputPurpose.setText(it.name, false)
        }
        vehicles.firstOrNull { it.id == t.vehicleId }?.let {
            vehicleId = it.id
            b.inputVehicle.setText(it.label, false)
            suggestStartKm()
        }
        suggestEndKm(t.distanceKm)
        toast(getString(R.string.trips_template_applied))
    }

    /** Speichert das Formular als Vorlage (inkl. Geschäftspartner und Route) */
    private fun saveTemplate() = askText(
        getString(R.string.save_as_template), getString(R.string.template_name), getString(R.string.save)
    ) { name ->
        if (name.isEmpty()) return@askText
        val t = formTrip()
        launch {
            requireContext().dao.saveTemplate(
                TripTemplate(
                    name = name,
                    startLocation = t.startLocation,
                    endLocation = t.endLocation,
                    distanceKm = (Trips.odometerDistance(t) ?: 0.0).coerceAtLeast(0.0),
                    purpose = t.purpose,
                    purposeId = t.purposeId,
                    notes = t.notes,
                    vehicleId = t.vehicleId,
                    businessPartner = t.businessPartner,
                    route = t.route
                )
            )
            toast(getString(R.string.template_saved))
        }
    }

    private companion object {
        val LOCATION = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        /** Ab Android 13 braucht auch die Benachrichtigung der GPS-Aufzeichnung eine Berechtigung */
        val START_PERMISSIONS =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) LOCATION + Manifest.permission.POST_NOTIFICATIONS else LOCATION
    }
}

/**
 * Vorschläge für ein Ortsfeld: gespeicherte Orte, deren Name oder Adresse den Text enthält
 * (häufig genutzte zuerst), dahinter „Text“ als neuen Standort speichern…, falls es ihn noch nicht gibt.
 */
private class PlaceAdapter(
    context: Context,
    private val places: List<SavedLocation>,
    private val addNewLabel: (String) -> String
) : ArrayAdapter<PlaceAdapter.Item>(context, android.R.layout.simple_dropdown_item_1line) {

    /** Ein Vorschlag; [place] == null heißt "als neuen Standort speichern" */
    class Item(val name: String, val place: SavedLocation?, private val label: String) {
        override fun toString() = label // So zeigt ArrayAdapter den Eintrag an
    }

    override fun getFilter(): Filter = object : Filter() {
        // Läuft im Hintergrund – nur auf die unveränderliche Liste zugreifen
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val query = constraint?.toString()?.trim().orEmpty()
            val matches = places
                .filter { query.isEmpty() || it.name.contains(query, true) || it.address?.contains(query, true) == true }
                .map { Item(it.name, it, listOfNotNull(it.name, it.address?.takeIf(String::isNotBlank)).joinToString(" — ")) }
            val known = query.isEmpty() || places.any { it.name.equals(query, true) }
            val items = if (known) matches else matches + Item(query, null, addNewLabel(query))
            return FilterResults().apply { values = items; count = items.size }
        }

        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            clear()
            @Suppress("UNCHECKED_CAST")
            addAll(results?.values as? List<Item> ?: emptyList())
        }

        // Ins Feld kommt der Ortsname (bzw. der getippte Text), nicht die Anzeige mit Adresse
        override fun convertResultToString(resultValue: Any?): CharSequence = (resultValue as Item).name
    }
}

/** Ein Km-Vorschlag darf ersetzt werden, solange der Nutzer das Feld nicht selbst geändert hat */
internal fun isReplaceable(current: String, lastSuggestion: String?) = current.isBlank() || current == lastSuggestion

/** End-Km-Vorschlag = Start + gerundete Strecke; null ohne Start oder ohne Strecke */
internal fun endKmSuggestion(startKm: Int?, km: Double?): Int? =
    if (startKm != null && km != null && km > 0) startKm + km.roundToInt() else null

/**
 * Kategorie-Text der Fahrt: Bei unveränderter Kategorie bleibt der gespeicherte Text (alter
 * Freitext-Zweck bzw. Name zum Zeitpunkt der Fahrt), sonst der Name der gewählten Kategorie.
 */
internal fun purposeText(old: Trip, purposeId: Long?, categoryName: String?): String =
    if (purposeId == old.purposeId && old.purpose.isNotBlank()) old.purpose else categoryName.orEmpty()

/**
 * Orte, deren Nutzungszähler beim Speichern steigt: Jede Fahrt zählt einmal je Start- und Zielort;
 * beim Bearbeiten einer abgeschlossenen Fahrt nur geänderte Orte (sonst zählte jedes Speichern mit).
 */
internal fun locationsToCount(old: Trip, new: Trip): List<String> {
    val edited = old.id != 0L && !old.isActive && !old.isGhost
    return listOf(old.startLocation to new.startLocation, old.endLocation to new.endLocation)
        .filter { (before, after) -> !edited || before != after }
        .map { it.second }
}
