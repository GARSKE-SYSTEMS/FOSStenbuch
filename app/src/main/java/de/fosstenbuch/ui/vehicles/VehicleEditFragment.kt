package de.fosstenbuch.ui.vehicles

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.room.withTransaction
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import de.fosstenbuch.R
import de.fosstenbuch.data.Vehicle
import de.fosstenbuch.data.dao
import de.fosstenbuch.data.db
import de.fosstenbuch.databinding.FragmentVehicleEditBinding
import de.fosstenbuch.logic.Geo
import de.fosstenbuch.logic.Trips
import de.fosstenbuch.ui.common.argId
import de.fosstenbuch.ui.common.close
import de.fosstenbuch.ui.common.confirm
import de.fosstenbuch.ui.common.launch
import de.fosstenbuch.ui.common.setDropdown
import de.fosstenbuch.ui.common.textOrNull
import de.fosstenbuch.ui.common.toast

/**
 * Fahrzeug anlegen/bearbeiten ([argId], 0 = neu).
 *
 * - Änderungssicherheit ist nicht umkehrbar. Bei geschützten Fahrzeugen sind Hersteller, Modell und
 *   Kennzeichen gesperrt, weil sie in den Exporten der bestehenden Fahrten erscheinen.
 * - Bluetooth-Gerät für die automatische Fahrterkennung. Die Erkennung selbst läuft über einen
 *   Manifest-Receiver (tracking/SystemReceiver); hier werden nur das Gerät und die nötigen
 *   Berechtigungen/Einstellungen eingerichtet.
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
            // Standort im Vordergrund erlaubt → zweiter Schritt "Immer erlauben"
            Manifest.permission.ACCESS_FINE_LOCATION in result && !locationAlways() -> fixLocation()
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
        // Adapter ohne Filter: sonst zeigt die Liste nach Rotation nur noch den gewählten Eintrag
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
        b.checkLocation.setOnClickListener { fixLocation() }
        b.checkBattery.setOnClickListener { fixBattery() }
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
        for (field in listOf(b.makeLayout, b.modelLayout, b.plateLayout)) field.isEnabled = !wasProtected

        val address = btAddress
        b.btSelected.isVisible = address != null
        b.btDevice.text = btName?.let { getString(R.string.masterdata_device, it, address) } ?: address
        b.btPick.setText(if (address == null) R.string.bluetooth_pick_device else R.string.bluetooth_change_device)
        b.checks.isVisible = address != null

        val power = requireContext().getSystemService(PowerManager::class.java)
        b.checkLocation.status(true, locationAlways(), R.string.masterdata_check_location)
        b.checkBattery.status(true, power?.isIgnoringBatteryOptimizations(requireContext().packageName) == true, R.string.masterdata_check_battery)
        // Diese Berechtigungen gibt es erst ab Android 13 bzw. 12 – davor sind die Zeilen ausgeblendet
        b.checkNotifications.status(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU, granted(Manifest.permission.POST_NOTIFICATIONS), R.string.masterdata_check_notifications)
        b.checkBluetooth.status(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S, granted(Manifest.permission.BLUETOOTH_CONNECT), R.string.masterdata_check_bluetooth)
    }

    /** Eine Voraussetzung: erfüllt = "✓ …" und nicht antippbar, sonst antippbar zum Einrichten */
    private fun MaterialButton.status(applicable: Boolean, ok: Boolean, label: Int) {
        isVisible = applicable
        isEnabled = !ok
        text = getString(if (ok) R.string.masterdata_check_ok else R.string.masterdata_check_missing, getString(label))
    }

    private fun save(b: FragmentVehicleEditBinding) {
        if (!loaded) return
        val make = b.make.textOrNull()
        val model = b.model.textOrNull()
        // Kennzeichen bewusst ohne Formatprüfung (nur Pflicht, Länge per maxLength): Das alte Muster
        // lehnte gültige Kennzeichen ab (Bundeswehr, Kurzzeit, Ausland, "B-AB1234" …)
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
            ctx.db.withTransaction {
                val old = ctx.dao.vehicle(argId)
                val v = Vehicle(
                    id = argId, make = make, model = model, licensePlate = plate, fuelType = fuel,
                    isPrimary = primary, notes = notes,
                    auditProtected = audit || old?.auditProtected == true, // einmal aktiv, immer aktiv
                    bluetoothDeviceAddress = btAddress, bluetoothDeviceName = btName
                )
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !granted(Manifest.permission.BLUETOOTH_CONNECT)) {
            btPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
            return
        }
        val adapter = requireContext().getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) return toast(getString(R.string.bluetooth_not_available))
        val devices = adapter.bondedDevices.orEmpty().toList()
        if (devices.isEmpty()) return toast(getString(R.string.bluetooth_no_paired_devices))
        val names = devices.map { getString(R.string.masterdata_device, it.name ?: getString(R.string.bluetooth_unknown_device), it.address) }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.bluetooth_pick_title)
            .setItems(names.toTypedArray()) { _, i -> select(devices[i].address, devices[i].name) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Ein Gerät gehört zu höchstens einem Fahrzeug – sonst landen erkannte Fahrten beim falschen */
    private fun select(address: String, name: String?) = launch {
        val owner = requireContext().dao.vehicleByBluetooth(address)
        if (owner != null && owner.id != argId) {
            toast(getString(R.string.masterdata_bt_in_use, owner.label))
        } else {
            btAddress = address
            btName = name
            render()
        }
    }

    /**
     * Standort "Immer erlauben" in zwei Schritten: erst Vordergrund (genau und ungefähr zusammen,
     * sonst ignoriert Android 12+ die Anfrage), danach Hintergrund. Ab Android 11 zeigt das System
     * für den Hintergrund keinen Dialog, sondern öffnet die Standort-Einstellung der App.
     */
    private fun fixLocation() {
        when {
            !Geo.hasPermission(requireContext()) ->
                permissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                permissions.launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
        }
    }

    /** Akku-Optimierung abschalten (die Berechtigung dafür steht im Manifest) */
    @SuppressLint("BatteryLife") // bewusst: sonst verhindert Android den Start der Aufzeichnung aus dem Hintergrund
    private fun fixBattery() {
        // Manche Hersteller-ROMs haben den direkten Dialog nicht → App-Einstellungen als Ausweg
        if (!openSettings(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)) openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
    }

    /** Standort auch im Hintergrund erlaubt? (vor Android 10 gilt die normale Berechtigung immer) */
    private fun locationAlways() = Geo.hasPermission(requireContext()) &&
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION))

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED

    /** Nach "Nicht mehr fragen" zeigt Android keinen Dialog mehr – dann hilft nur die App-Einstellung */
    private fun denied() {
        val b = _b ?: return
        Snackbar.make(b.root, R.string.masterdata_permission_denied, Snackbar.LENGTH_LONG)
            .setAction(R.string.masterdata_open_settings) { openSettings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS) }
            .show()
    }

    /** Öffnet eine System-Einstellung für diese App; false, wenn es sie auf dem Gerät nicht gibt */
    private fun openSettings(action: String) =
        runCatching { startActivity(Intent(action, ("package:" + requireContext().packageName).toUri())) }.isSuccess
}
