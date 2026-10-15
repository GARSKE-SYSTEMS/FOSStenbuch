package de.fosstenbuch.ui.export

import android.app.Dialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.CompoundButton
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.fosstenbuch.R
import de.fosstenbuch.databinding.FragmentExportBinding
import de.fosstenbuch.logic.Fmt
import de.fosstenbuch.logic.export.Format
import de.fosstenbuch.logic.export.toDate
import de.fosstenbuch.ui.common.collect
import de.fosstenbuch.ui.common.confirm
import de.fosstenbuch.ui.common.hexColor
import de.fosstenbuch.ui.common.pickDate
import de.fosstenbuch.ui.common.setDropdown
import de.fosstenbuch.ui.common.shareFile
import de.fosstenbuch.ui.common.toast
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/**
 * Datenexport: Filter wählen, Datei (CSV, PDF, Finanzamt-PDF) im Hintergrund erzeugen und teilen.
 * Der Zustand liegt im [ExportViewModel]; die Ansicht zeigt ihn nur an und meldet Änderungen zurück.
 */
class ExportFragment : Fragment(R.layout.fragment_export) {

    private val vm: ExportViewModel by viewModels()

    /** Die offene Rückfrage "Als exportiert markieren?" – nur, um sie nicht doppelt zu zeigen */
    private var markDialog: Dialog? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentExportBinding.bind(view)
        val initial = vm.options.value
        // Format und Firma erst aus dem ViewModel setzen, dann die Listener – sonst meldeten diese den
        // Startwert als Nutzeränderung. Stellt Android danach eigene Zustände wieder her (Drehen),
        // sind das dieselben Werte wie im ViewModel.
        val radios = listOf(b.radioCsv, b.radioPdf, b.radioFinanzamt)
        radios[initial.format.ordinal].isChecked = true
        b.radioFormat.setOnCheckedChangeListener { _, id ->
            val format = Format.entries[radios.indexOfFirst { it.id == id }]
            vm.options.update { it.copy(format = format) }
        }
        b.editCompany.setText(initial.company)
        b.editCompany.doAfterTextChanged { text -> vm.options.update { it.copy(company = text.toString().trim()) } }

        // Von ≤ Bis: Das jeweils andere Datum wird bei Bedarf mitgezogen
        b.editFrom.setOnClickListener {
            pickDate(vm.options.value.from.toDate()) { picked ->
                val from = picked.toLocalDate()
                vm.options.update { it.copy(from = from, to = maxOf(it.to, from)) }
            }
        }
        b.editTo.setOnClickListener {
            pickDate(vm.options.value.to.toDate()) { picked ->
                val to = picked.toLocalDate()
                vm.options.update { it.copy(to = to, from = minOf(it.from, to)) }
            }
        }
        b.buttonExport.setOnClickListener {
            confirm(getString(R.string.export_confirm_title), getString(R.string.export_confirm_message), getString(R.string.export_confirm_yes)) {
                vm.export()
            }
        }

        collect(vm.options) { o ->
            b.editFrom.setText(Fmt.date(o.from.toDate()))
            b.editTo.setText(Fmt.date(o.to.toDate()))
            val finanzamt = o.format == Format.FINANZAMT_PDF
            b.textFinanzamtHint.isVisible = finanzamt
            b.layoutCompany.isVisible = finanzamt
            // Das Finanzamt-PDF enthält das Änderungsprotokoll immer: Der Schalter zeigt dann "an",
            // die eigene Wahl (includeAudit) für CSV/PDF bleibt unverändert erhalten
            b.switchAudit.isEnabled = !finanzamt
            b.switchAudit.show(o.withAudit) { c -> vm.options.update { it.copy(includeAudit = c) } }
            b.switchOnlyNew.show(o.onlyNew) { c -> vm.options.update { it.copy(onlyNew = c) } }
            b.switchMark.show(o.markExported) { c -> vm.options.update { it.copy(markExported = c) } }
            b.groupTracking.isVisible = o.incremental
            b.textPdfHint.isVisible = !o.incremental
        }

        // Fahrzeugauswahl wird für jede neue Ansicht aufgebaut (auch nach Rückkehr vom Zurück-Stapel)
        collect(combine(vm.vehicles, vm.options.map { it.vehicleId }.distinctUntilChanged(), ::Pair)) { (vehicles, id) ->
            val items = listOf(getString(R.string.export_all_vehicles)) + vehicles.map { it.label }
            b.dropdownVehicle.setDropdown(items, vehicles.indexOfFirst { it.id == id } + 1) { i ->
                vm.options.update { it.copy(vehicleId = vehicles.getOrNull(i - 1)?.id) }
            }
        }

        collect(vm.purposes) { purposes ->
            b.chipsPurposes.removeAllViews()
            for (p in purposes) {
                val chip = layoutInflater.inflate(R.layout.item_export_purpose, b.chipsPurposes, false) as Chip
                chip.text = getString(if (p.isBusinessRelevant) R.string.export_purpose_business else R.string.export_purpose_private, p.name)
                hexColor(p.color)?.let { chip.chipStrokeColor = ColorStateList.valueOf(it) }
                chip.show(p.id !in vm.options.value.excludedPurposes) { checked ->
                    vm.options.update { it.copy(excludedPurposes = if (checked) it.excludedPurposes - p.id else it.excludedPurposes + p.id) }
                }
                b.chipsPurposes.addView(chip)
            }
        }

        val excludedCount = combine(vm.purposes, vm.options) { purposes, o -> purposes.count { it.id in o.excludedPurposes } to purposes.size }
        collect(excludedCount.distinctUntilChanged()) { (excluded, all) ->
            b.textPurposeHint.setText(
                when (excluded) {
                    0 -> R.string.export_purpose_filter_hint
                    all -> R.string.export_purposes_none
                    else -> R.string.export_purposes_some
                }
            )
        }

        collect(combine(vm.trips, vm.busy, ::Pair)) { (trips, busy) ->
            b.textCount.text = resources.getQuantityString(R.plurals.export_count, trips.size, trips.size)
            b.buttonExport.isEnabled = !busy && trips.isNotEmpty()
            b.progress.isVisible = busy
        }

        // Endet der Export, während der Bildschirm nicht sichtbar ist, wird beim Zurückkehren geteilt
        collect(vm.result) { result ->
            if (result == null) return@collect
            vm.result.value = null
            result.onSuccess { done ->
                requireContext().shareFile(done.file, done.mime, getString(R.string.export_share_title))
                // Der Teilen-Dialog meldet nicht, ob die Datei wirklich gespeichert oder versendet wurde.
                // Würde sofort markiert, fehlten die Fahrten nach einem Abbruch in jedem "Nur neue"-Export.
                if (done.markIds.isNotEmpty()) vm.askMark(done.markIds)
            }.onFailure { toast(getString(R.string.export_error, it.message.orEmpty())) }
        }

        // Die Rückfrage hängt am Zustand im ViewModel, nicht am Dialog: Wird die Activity neu erzeugt,
        // während der Nutzer in der Ziel-App ist (Drehen, Dunkelmodus, Prozess-Tod), erscheint sie wieder
        collect(vm.pendingMark) { ids ->
            if (ids.isEmpty() || markDialog?.isShowing == true) return@collect
            markDialog = MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.export_mark_title)
                .setMessage(resources.getQuantityString(R.plurals.export_mark_message, ids.size, ids.size))
                .setPositiveButton(R.string.export_mark_yes) { _, _ -> vm.answerMark(true) }
                .setNegativeButton(R.string.export_mark_no) { _, _ -> vm.answerMark(false) }
                .setOnCancelListener { vm.answerMark(false) } // Zurück-Taste oder Tippen daneben = Nein
                .show()
        }
    }

    override fun onDestroyView() {
        // Nur das Fenster schließen (löst keinen Abbruch aus): Die Rückfrage bleibt offen und
        // wird von der neuen Ansicht erneut gezeigt
        markDialog?.dismiss()
        markDialog = null
        super.onDestroyView()
    }

    /**
     * Zeigt [checked] an, ohne dass das als Nutzeränderung zählt: Der Listener wird erst danach
     * gesetzt und meldet nur echte Änderungen über [onUser]. (Ein OnClickListener genügte nicht –
     * Ziehen des Schalters löst keinen Klick aus.)
     */
    private fun CompoundButton.show(checked: Boolean, onUser: (Boolean) -> Unit) {
        setOnCheckedChangeListener(null)
        isChecked = checked
        setOnCheckedChangeListener { _, c -> onUser(c) }
    }

    private fun Date.toLocalDate(): LocalDate = toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
}
