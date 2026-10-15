package de.fosstenbuch.ui.purposes

import android.os.Bundle
import android.view.View
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.room.withTransaction
import de.fosstenbuch.R
import de.fosstenbuch.data.Settings
import de.fosstenbuch.data.TripPurpose
import de.fosstenbuch.data.dao
import de.fosstenbuch.data.db
import de.fosstenbuch.databinding.FragmentMasterdataListBinding
import de.fosstenbuch.databinding.ItemPurposeBinding
import de.fosstenbuch.ui.common.ARG_ID
import de.fosstenbuch.ui.common.SimpleAdapter
import de.fosstenbuch.ui.common.collect
import de.fosstenbuch.ui.common.confirm
import de.fosstenbuch.ui.common.launch
import de.fosstenbuch.ui.common.navigate
import de.fosstenbuch.ui.common.toast

/** Liste der Fahrt-Kategorien. Tippen öffnet das Formular, langes Drücken löscht. */
class PurposesFragment : Fragment(R.layout.fragment_masterdata_list) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentMasterdataListBinding.bind(view)
        val adapter = SimpleAdapter<TripPurpose, ItemPurposeBinding>(ItemPurposeBinding::inflate, { it.id }) { item, p ->
            item.name.text = p.name
            val type = getString(if (p.isBusinessRelevant) R.string.business else R.string.private_trip)
            item.details.text = if (p.isDefault) getString(R.string.masterdata_details, type, getString(R.string.default_purpose)) else type
            val color = hexColor(p.color)
            item.color.isInvisible = color == null
            color?.let(item.color::setBackgroundColor)
            item.root.setOnClickListener { navigate(R.id.navigation_purpose_edit, ARG_ID to p.id) }
            item.root.setOnLongClickListener { delete(p); true }
        }
        b.list.adapter = adapter
        b.empty.setText(R.string.no_purposes)
        b.fab.contentDescription = getString(R.string.add_purpose)
        b.fab.setOnClickListener { navigate(R.id.navigation_purpose_edit, ARG_ID to 0L) }
        collect(requireContext().dao.purposes()) {
            adapter.submitList(it)
            b.empty.isVisible = it.isEmpty()
        }
    }

    /** Standard-Kategorien und Kategorien mit Fahrten (auch automatischen) bleiben erhalten */
    private fun delete(p: TripPurpose) {
        if (p.isDefault) return toast(getString(R.string.cannot_delete_default))
        confirm(getString(R.string.delete_purpose_title), getString(R.string.delete_purpose_message), getString(R.string.delete)) {
            launch {
                val ctx = requireContext()
                // Zählen und Löschen in einer Transaktion, damit keine Fahrt dazwischen entstehen kann
                val trips = ctx.db.withTransaction {
                    ctx.dao.tripCountForPurpose(p.id).also { count ->
                        if (count == 0) {
                            // Vorlagen haben keinen Fremdschlüssel: Verweis lösen, sonst scheitert später das Speichern einer Fahrt aus der Vorlage
                            ctx.dao.allTemplates().filter { it.purposeId == p.id }.forEach { ctx.dao.saveTemplate(it.copy(purposeId = null)) }
                            ctx.dao.deletePurpose(p)
                        }
                    }
                }
                if (trips > 0) return@launch toast(resources.getQuantityString(R.plurals.masterdata_purpose_in_use, trips, trips))
                // Auch die Vorauswahl für neue Fahrten darf nicht auf eine gelöschte Kategorie zeigen
                if (Settings.get(ctx, Settings.DEFAULT_PURPOSE_ID, 0L) == p.id) Settings.set(ctx, Settings.DEFAULT_PURPOSE_ID, null)
            }
        }
    }
}
