package de.fosstenbuch.ui.vehicles

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.room.withTransaction
import de.fosstenbuch.R
import de.fosstenbuch.data.AppDao
import de.fosstenbuch.data.TripTemplate
import de.fosstenbuch.data.Vehicle
import de.fosstenbuch.data.dao
import de.fosstenbuch.data.db
import de.fosstenbuch.databinding.FragmentMasterdataListBinding
import de.fosstenbuch.databinding.ItemVehicleBinding
import de.fosstenbuch.ui.common.ARG_ID
import de.fosstenbuch.ui.common.SimpleAdapter
import de.fosstenbuch.ui.common.choose
import de.fosstenbuch.ui.common.collect
import de.fosstenbuch.ui.common.confirm
import de.fosstenbuch.ui.common.launch
import de.fosstenbuch.ui.common.navigate
import de.fosstenbuch.ui.common.toast

/**
 * Fahrzeugliste. Tippen öffnet das Formular, langes Drücken bietet
 * "Als Primärfahrzeug setzen" und "Löschen" an.
 */
class VehiclesFragment : Fragment(R.layout.fragment_masterdata_list) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentMasterdataListBinding.bind(view)
        val adapter = SimpleAdapter<Vehicle, ItemVehicleBinding>(ItemVehicleBinding::inflate, { it.id }) { item, v ->
            item.name.text = getString(R.string.masterdata_two_parts, v.make, v.model)
            item.details.text = getString(R.string.masterdata_details, v.licensePlate, v.fuelType)
            item.primary.isVisible = v.isPrimary
            item.locked.isVisible = v.auditProtected
            item.root.setOnClickListener { navigate(R.id.navigation_vehicle_edit, ARG_ID to v.id) }
            item.root.setOnLongClickListener { showOptions(v); true }
        }
        b.list.adapter = adapter
        b.empty.text = getString(R.string.masterdata_two_lines, getString(R.string.no_vehicles), getString(R.string.add_first_vehicle))
        b.fab.contentDescription = getString(R.string.add_vehicle)
        b.fab.setOnClickListener { navigate(R.id.navigation_vehicle_edit, ARG_ID to 0L) }
        collect(requireContext().dao.vehicles()) {
            adapter.submitList(it)
            b.empty.isVisible = it.isEmpty()
        }
    }

    private fun showOptions(v: Vehicle) {
        val options = listOfNotNull(R.string.set_primary.takeIf { !v.isPrimary }, R.string.delete)
        choose(getString(R.string.masterdata_two_parts, v.make, v.model), options.map { getString(it) }) { i ->
            if (options[i] != R.string.delete) {
                launch { requireContext().dao.setPrimaryVehicle(v.id) }
            } else {
                // Gilt auch für änderungssichere Fahrzeuge: ohne Fahrten gibt es nichts zu schützen
                deleteUnused(
                    R.string.delete_vehicle_title, R.string.delete_vehicle_message, R.plurals.masterdata_vehicle_in_use,
                    count = { tripCountForVehicle(v.id) },
                    unlink = { t -> t.copy(vehicleId = null).takeIf { t.vehicleId == v.id } },
                    delete = { deleteVehicle(v) }
                )
            }
        }
    }
}

/**
 * Gemeinsamer Lösch-Ablauf für Fahrzeuge und Kategorien: Nach Bestätigung wird nur gelöscht, wenn keine
 * Fahrt (auch keine automatische oder laufende) den Eintrag verwendet – sonst verlören diese Fahrten per
 * Fremdschlüssel ihre Zuordnung; dann nennt [inUse] (Plural) die Anzahl. Zählen und Löschen laufen in
 * einer Transaktion, damit dazwischen keine Fahrt entstehen kann.
 *
 * Vorlagen haben keinen Fremdschlüssel: [unlink] liefert eine betroffene Vorlage ohne den Verweis
 * (sonst null), sonst scheiterte später das Speichern einer Fahrt aus der Vorlage.
 */
internal fun Fragment.deleteUnused(
    @StringRes title: Int,
    @StringRes message: Int,
    @PluralsRes inUse: Int,
    count: suspend AppDao.() -> Int,
    unlink: (TripTemplate) -> TripTemplate?,
    delete: suspend AppDao.() -> Unit,
    onDeleted: suspend (Context) -> Unit = {}
) = confirm(getString(title), getString(message), getString(R.string.delete)) {
    launch {
        val ctx = requireContext()
        val dao = ctx.dao
        val trips = ctx.db.withTransaction {
            dao.count().also { n ->
                if (n == 0) {
                    dao.allTemplates().mapNotNull(unlink).forEach { dao.saveTemplate(it) }
                    dao.delete()
                }
            }
        }
        if (trips > 0) toast(resources.getQuantityString(inUse, trips, trips)) else onDeleted(ctx)
    }
}
