package de.fosstenbuch.ui.vehicles

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.room.withTransaction
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import de.fosstenbuch.R
import de.fosstenbuch.data.Vehicle
import de.fosstenbuch.data.dao
import de.fosstenbuch.data.db
import de.fosstenbuch.databinding.FragmentVehicleEditBinding
import de.fosstenbuch.logic.Geo
import de.fosstenbuch.logic.Trips
import de.fosstenbuch.tracking.Ghosts
import de.fosstenbuch.ui.common.clearErrorsOnEdit
import de.fosstenbuch.ui.common.argId
import de.fosstenbuch.ui.common.choose
import de.fosstenbuch.ui.common.close
import de.fosstenbuch.ui.common.confirm
import de.fosstenbuch.ui.common.granted
import de.fosstenbuch.ui.common.launch
import de.fosstenbuch.ui.common.setDropdown
import de.fosstenbuch.ui.common.textOrNull
import de.fosstenbuch.ui.common.toast

/**
 * Fahrzeug anlegen/bearbeiten ([argId], 0 = neu).
 *
 * - Änderungssicherheit ist nicht umkehrbar. Bei geschützten Fahrzeugen sind Hersteller, Modell,
 *   Kennzeichen und Kraftstoff gesperrt, weil sie in den Exporten der bestehenden Fahrten erscheinen.
 * - Bluetooth-Gerät für die automatische Fahrterkennung. Die Erkennung selbst läuft über einen
 *   Manifest-Receiver (tracking/SystemReceiver); hier werden nur das Gerät und die nötigen
 *   Berechtigungen eingerichtet. Vor beidem steht der Hinweis zum Standort im Hintergrund ([disclose]).
 */
class VehicleEditFragment : Fragment(R.layout.fragment_vehicle_edit) {

    private var _b: FragmentVehicleEditBinding? = null

    /** Formular schon befüllt? Danach stellt Android die Eingaben selbst wieder her. */
    private var loaded = false
    /** War das Fahrzeug schon beim Öffnen änderungssicher? (aus der Datenbank) */
    private var wasProtected = false
    private var btAddress: String? = null
    private var btName: String? = null

    /** BLUETOOTH_CONNECT für die Geräteauswahl; nach Zustimmung direkt die Auswahl zeigen */
    private val btPermission = registerForActivityResult(RequestPermission()) { granted ->
        if (granted) pickDevice() else denied()
    }

    /** Berechtigungen der Voraussetzungen (Standort, Benachrichtigungen, Bluetooth) */
    private val permissions = registerForActivityResult(RequestMultiplePermissions()) { result ->
        when {
            result.isEmpty() -> Unit // Anfrage abgebrochen
            result.values.none { it } -> denied()
            // Standort im Vordergrund erlaubt → zweiter Schritt "Immer zulassen"
            Manifest.permission.ACCESS_FINE_LOCATION in result && !Geo.hasBackgroundPermission(requireContext()) -> fixLocation()
        }
        render()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.run {
            loaded = getBoolean("loaded")
            btAddress = getString("btAddress")
            btName = getString("btName")
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("loaded", loaded)
        outState.putString("btAddress", btAddress)
        outState.putString("btName", btName)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentVehicleEditBinding.bind(view).also { _b = it }
        view.clearErrorsOnEdit()
        b.fuel.setDropdown(resources.getStringArray(R.array.fuel_types).toList()) {}

        // Klick- statt Checked-Listener: feuert nur bei echter Nutzeraktion, nicht beim Befüllen
        // oder Wiederherstellen – sonst käme die Warnung schon beim Öffnen eines geschützten Fahrzeugs
        b.audit.setOnClickListener {
            if (!b.audit.isChecked) return@setOnClickListener
            b.audit.isChecked = false // erst nach Bestätigung einschalten
            confirm(getString(R.string.audit_protection_warning_title), getString(R.string.audit_protection_warning_message), getString(R.string.enable)) {
                _b?.audit?.isChecked = true
            }
        }

        b.btPick.setOnClickListener { pickDevice() }
        b.btRemove.setOnClickListener {
            btAddress = null
            btName = null
            render()
        }
        b.checkLocation.setOnClickListener { disclose { fixLocation() } }
        b.checkNotifications.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permissions.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }
        b.checkBluetooth.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) permissions.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
        }
        b.save.setOnClickListener { save(b) }

        launch {
            val v = requireContext().dao.vehicle(argId)
            if (!loaded && v != null) fill(b, v)
            loaded = true
            wasProtected = v?.auditProtected == true
            render()
        }
    }

    // Nach Rückkehr aus den System-Einstellungen den Stand der Voraussetzungen neu prüfen
    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    private fun fill(b: FragmentVehicleEditBinding, v: Vehicle) {
        b.make.setText(v.make)
        b.model.setText(v.model)
        b.plate.setText(v.licensePlate)
        b.fuel.setText(v.fuelType, false)
        b.notes.setText(v.notes)
        b.primary.isChecked = v.isPrimary
        b.audit.isChecked = v.auditProtected
        btAddress = v.bluetoothDeviceAddress
        btName = v.bluetoothDeviceName
    }

    /** Zeigt den Zustand, den Android nicht selbst wiederherstellt: Sperren, Bluetooth-Gerät, Voraussetzungen */
    @SuppressLint("InlinedApi") // Berechtigungs-Konstanten neuerer Versionen, siehe unten
    private fun render() {
        val b = _b ?: return
        b.audit.isEnabled = !wasProtected
        b.auditHint.setText(if (wasProtected) R.string.audit_protection_active else R.string.audit_protection_hint)
        for (field in listOf(b.makeLayout, b.modelLayout, b.plateLayout, b.fuelLayout)) field.isEnabled = !wasProtected

        val address = btAddress
        b.btSelected.isVisible = address != null
        b.btDevice.text = btName?.let { getString(R.string.masterdata_device, it, address) } ?: address
        b.btPick.setText(if (address == null) R.string.bluetooth_pick_device else R.string.bluetooth_change_device)
        b.checks.isVisible = address != null

        val ctx = requireContext()
        b.checkLocation.status(true, Geo.hasBackgroundPermission(ctx), R.string.masterdata_check_location)
        // Diese Berechtigungen gibt es erst ab Android 13 bzw. 12 – davor sind die Zeilen ausgeblendet
        b.checkNotifications.status(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU, ctx.granted(Manifest.permission.POST_NOTIFICATIONS), R.string.masterdata_check_notifications)
        b.checkBluetooth.status(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S, ctx.granted(Manifest.permission.BLUETOOTH_CONNECT), R.string.masterdata_check_bluetooth)
    }

    /** Eine Voraussetzung: erfüllt = "✓ …" und nicht antippbar, sonst antippbar zum Einrichten */
    private fun MaterialButton.status(applicable: Boolean, ok: Boolean, label: Int) {
        isVisible = applicable
        isEnabled = !ok
        text = getString(if (ok) R.string.masterdata_check_ok else R.string.masterdata_check_missing, getString(label))
    }

    /**
     * Prüft die Pflichtfelder und speichert. Bei einem änderungssicheren Fahrzeug bleiben Hersteller,
     * Modell, Kennzeichen und Kraftstoff wie in der Datenbank – unabhängig vom (gesperrten) Formular.
     */
    private fun save(b: FragmentVehicleEditBinding) {
        if (!loaded) return
        val make = b.make.textOrNull()
        val model = b.model.textOrNull()
        // Bewusst ohne Formatprüfung: Bundeswehr-, Kurzzeit- und Auslandskennzeichen sind auch gültig
        val plate = b.plate.textOrNull()?.uppercase()
        val fuel = b.fuel.textOrNull()
        for ((layout, value) in listOf(b.makeLayout to make, b.modelLayout to model, b.plateLayout to plate, b.fuelLayout to fuel)) {
            layout.error = if (value == null) getString(R.string.masterdata_required, layout.hint) else null
        }
        if (make == null || model == null || plate == null || fuel == null) return

        val primary = b.primary.isChecked
        val notes = b.notes.textOrNull()
        val audit = b.audit.isChecked
        b.save.isEnabled = false // Doppel-Tipp würde das Fahrzeug sonst zweimal anlegen
        launch {
            val ctx = requireContext()
            // Gerät geändert oder entfernt, während dieses Fahrzeug automatisch aufgezeichnet wird: Erst die
            // Aufzeichnung beenden – das Trennen des alten Geräts erkennt Ghosts danach nicht mehr als Fahrtende
            val ghost = ctx.dao.recordingGhost()
            if (ghost != null && ghost.vehicleId == argId && !btAddress.equals(ctx.dao.vehicle(argId)?.bluetoothDeviceAddress, ignoreCase = true)) {
                Ghosts.end(ctx, ghost.id)
            }
            ctx.db.withTransaction {
                val old = ctx.dao.vehicle(argId)
                val v = if (old?.auditProtected == true) {
                    // Einmal änderungssicher, immer änderungssicher – samt der Angaben aus den Exporten
                    old.copy(isPrimary = primary, notes = notes, bluetoothDeviceAddress = btAddress, bluetoothDeviceName = btName)
                } else {
                    Vehicle(
                        id = argId, make = make, model = model, licensePlate = plate, fuelType = fuel, isPrimary = primary,
                        notes = notes, auditProtected = audit, bluetoothDeviceAddress = btAddress, bluetoothDeviceName = btName
                    )
                }
                // Upsert liefert beim Aktualisieren -1 statt der ID
                val id = if (argId == 0L) ctx.dao.saveVehicle(v) else argId.also { ctx.dao.saveVehicle(v) }
                if (primary) ctx.dao.setPrimaryVehicle(id)
                // Wird der Schutz gerade aktiviert, brauchen die vorhandenen Fahrten sofort ihre
                // Hash-Kette – sonst scheitert die Wiederherstellung eines danach erstellten Backups
                Trips.recomputeChain(ctx, id)
            }
            close()
        }
    }

    /** Zeigt die gekoppelten Geräte zur Auswahl; ab Android 12 braucht schon das Auflisten BLUETOOTH_CONNECT */
    @SuppressLint("MissingPermission") // wird direkt davor geprüft
    private fun pickDevice() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !requireContext().granted(Manifest.permission.BLUETOOTH_CONNECT)) {
            btPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
            return
        }
        val adapter = requireContext().getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) return toast(R.string.bluetooth_not_available)
        val devices = adapter.bondedDevices.orEmpty().toList()
        if (devices.isEmpty()) return toast(R.string.bluetooth_no_paired_devices)
        val names = devices.map { getString(R.string.masterdata_device, it.name ?: getString(R.string.bluetooth_unknown_device), it.address) }
        choose(getString(R.string.bluetooth_pick_title), names) { select(devices[it].address, devices[it].name) }
    }

    /**
     * Ein Gerät gehört zu höchstens einem Fahrzeug – sonst landen erkannte Fahrten beim falschen.
     * Mit dem Gerät beginnt die Aufzeichnung im Hintergrund, deshalb erst der Hinweis, dann ggf. die Standort-Anfrage
     * (vor Android 10 ist schon die normale Standortberechtigung "immer", die Anfrage käme sonst nie mehr).
     */
    private fun select(address: String, name: String?) = launch {
        val owner = requireContext().dao.vehicleByBluetooth(address)
        if (owner != null && owner.id != argId) return@launch toast(getString(R.string.masterdata_bt_in_use, owner.label))
        disclose {
            btAddress = address
            btName = name
            render()
            if (!Geo.hasBackgroundPermission(requireContext())) fixLocation()
        }
    }

    /**
     * Deutlicher Hinweis zum Standort im Hintergrund, bevor die App ihn nutzt bzw. anfragt (Google-Play-Richtlinie).
     * [then] läuft nur, wenn der Bildschirm noch offen ist (der Dialog gehört zur Activity).
     */
    private fun disclose(then: () -> Unit) =
        confirm(getString(R.string.masterdata_location_disclosure_title), getString(R.string.masterdata_location_disclosure), getString(R.string.masterdata_continue)) {
            if (_b != null) then()
        }

    /**
     * Standort "Immer zulassen" in zwei Schritten: erst Vordergrund, danach Hintergrund. Ab Android 11
     * zeigt das System für den Hintergrund keinen Dialog, sondern öffnet die Standort-Einstellung der App.
     */
    private fun fixLocation() {
        when {
            !Geo.hasPermission(requireContext()) -> permissions.launch(Geo.PERMISSIONS)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                permissions.launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
        }
    }

    /** Nach "Nicht mehr fragen" zeigt Android keinen Dialog mehr – dann hilft nur die App-Einstellung */
    private fun denied() {
        val b = _b ?: return
        Snackbar.make(b.root, R.string.masterdata_permission_denied, Snackbar.LENGTH_LONG)
            .setAction(R.string.title_settings) { openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS) }
            .show()
    }

    /** Öffnet eine System-Einstellung für diese App (fehlt sie auf dem Gerät, passiert nichts) */
    private fun openSettings(action: String) {
        runCatching { startActivity(Intent(action, ("package:" + requireContext().packageName).toUri())) }
    }
}
