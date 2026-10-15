package de.fosstenbuch.ui.vehicles

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.room.withTransaction
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.fosstenbuch.R
import de.fosstenbuch.data.Vehicle
import de.fosstenbuch.data.dao
import de.fosstenbuch.data.db
import de.fosstenbuch.databinding.FragmentMasterdataListBinding
import de.fosstenbuch.databinding.ItemVehicleBinding
import de.fosstenbuch.ui.common.ARG_ID
import de.fosstenbuch.ui.common.SimpleAdapter
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
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.masterdata_two_parts, v.make, v.model))
            .setItems(options.map { getString(it) }.toTypedArray()) { _, i ->
                if (options[i] == R.string.delete) delete(v) else launch { requireContext().dao.setPrimaryVehicle(v.id) }
            }
            .show()
    }

    /**
     * Löscht nur Fahrzeuge ohne Fahrten – sonst verlören alle Fahrten (auch eine laufende oder
     * gerade aufgezeichnete) per Fremdschlüssel ihr Fahrzeug. Gilt auch für änderungssichere Fahrzeuge.
     */
    private fun delete(v: Vehicle) = confirm(getString(R.string.delete_vehicle_title), getString(R.string.delete_vehicle_message), getString(R.string.delete)) {
        launch {
            val ctx = requireContext()
            // Zählen und Löschen in einer Transaktion, damit keine Fahrt dazwischen entstehen kann
            val trips = ctx.db.withTransaction {
                ctx.dao.tripCountForVehicle(v.id).also { count ->
                    if (count == 0) {
                        // Vorlagen haben keinen Fremdschlüssel: Verweis lösen, sonst scheitert später das Speichern einer Fahrt aus der Vorlage
                        ctx.dao.allTemplates().filter { it.vehicleId == v.id }.forEach { ctx.dao.saveTemplate(it.copy(vehicleId = null)) }
                        ctx.dao.deleteVehicle(v)
                    }
                }
            }
            if (trips > 0) toast(resources.getQuantityString(R.plurals.masterdata_vehicle_in_use, trips, trips))
        }
    }
}
