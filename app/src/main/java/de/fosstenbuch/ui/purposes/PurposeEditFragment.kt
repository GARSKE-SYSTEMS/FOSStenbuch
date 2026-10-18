package de.fosstenbuch.ui.purposes

import android.os.Bundle
import android.view.View
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import de.fosstenbuch.R
import de.fosstenbuch.data.TripPurpose
import de.fosstenbuch.data.dao
import de.fosstenbuch.databinding.FragmentPurposeEditBinding
import de.fosstenbuch.ui.common.clearErrorsOnEdit
import de.fosstenbuch.ui.common.argId
import de.fosstenbuch.ui.common.close
import de.fosstenbuch.ui.common.hexColor
import de.fosstenbuch.ui.common.launch
import de.fosstenbuch.ui.common.textOrNull

/** Farbe neuer Kategorien (wie die Standard-Kategorie "Beruflich") */
private const val DEFAULT_COLOR = "#0E8DBF"

/**
 * Kategorie anlegen/bearbeiten ([argId], 0 = neu). Der Name ist Pflicht und eindeutig
 * (Groß-/Kleinschreibung egal). Standard-Kategorien behalten ihren Namen; bereits verwendete
 * Kategorien ihre Einstufung geschäftlich/privat.
 */
class PurposeEditFragment : Fragment(R.layout.fragment_purpose_edit) {

    /** Formular schon befüllt? Danach stellt Android die Eingaben selbst wieder her. */
    private var loaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loaded = savedInstanceState?.getBoolean("loaded") ?: false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("loaded", loaded)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentPurposeEditBinding.bind(view)
        view.clearErrorsOnEdit()
        // Die Vorschau folgt der Eingabe; das feuert auch beim Befüllen und beim Wiederherstellen
        b.color.doAfterTextChanged { text ->
            val color = hexColor(text?.toString())
            b.preview.isInvisible = color == null
            color?.let(b.preview::setBackgroundColor)
        }
        b.save.setOnClickListener { save(b) }

        launch {
            val dao = requireContext().dao
            val p = dao.purpose(argId)
            if (!loaded) {
                b.name.setText(p?.name)
                b.business.isChecked = p?.isBusinessRelevant == true
                b.color.setText(p?.color ?: DEFAULT_COLOR)
                loaded = true
            }
            b.nameLayout.isEnabled = p?.isDefault != true
            // Die Einstufung gilt für alle Fahrten dieser Kategorie – eine Änderung würde
            // bestehende (auch gesperrte) Fahrten unbemerkt umklassifizieren
            val used = dao.tripCountForPurpose(argId)
            b.business.isEnabled = used == 0
            b.businessHint.isVisible = used > 0
            b.businessHint.text = resources.getQuantityString(R.plurals.masterdata_purpose_locked, used, used)
        }
    }

    /** Prüft Name (Pflicht, eindeutig) und Farbe und speichert */
    private fun save(b: FragmentPurposeEditBinding) {
        if (!loaded) return
        val name = b.name.textOrNull()
        val color = b.color.textOrNull() ?: DEFAULT_COLOR
        val business = b.business.isChecked
        b.colorLayout.error = if (hexColor(color) == null) getString(R.string.masterdata_color_invalid) else null
        b.save.isEnabled = false // Doppel-Tipp würde die Kategorie sonst zweimal anlegen
        launch {
            try {
                val dao = requireContext().dao
                b.nameLayout.error = when {
                    name == null -> getString(R.string.error_name_required)
                    dao.purposeByName(name).let { it != null && it.id != argId } -> getString(R.string.purpose_name_duplicate)
                    else -> null
                }
                if (name == null || b.nameLayout.error != null || b.colorLayout.error != null) return@launch
                val old = dao.purpose(argId)
                dao.savePurpose(
                    TripPurpose(
                        id = argId,
                        name = name,
                        // Erneut aus der Datenbank geprüft, nicht nur über den gesperrten Schalter
                        isBusinessRelevant = old?.takeIf { dao.tripCountForPurpose(it.id) > 0 }?.isBusinessRelevant ?: business,
                        color = color,
                        isDefault = old?.isDefault == true
                    )
                )
                close()
            } finally {
                b.save.isEnabled = true
            }
        }
    }
}
