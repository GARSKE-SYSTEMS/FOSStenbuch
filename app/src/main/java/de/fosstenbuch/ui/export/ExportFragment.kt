package de.fosstenbuch.ui.export

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.CompoundButton
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.chip.Chip
import de.fosstenbuch.R
import de.fosstenbuch.databinding.FragmentExportBinding
import de.fosstenbuch.logic.export.Export
import de.fosstenbuch.logic.export.Format
import de.fosstenbuch.logic.export.Options
import de.fosstenbuch.ui.common.collect
import de.fosstenbuch.ui.common.confirm
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val b = FragmentExportBinding.bind(view)
        val initial = vm.options.value
        // Erst die Werte aus dem ViewModel setzen, dann die Listener – sonst schreiben die Listener
        // beim Befüllen alte Werte zurück. Stellt Android danach eigene Zustände wieder her (Drehen),
        // melden die Listener genau diese ans ViewModel.
        val radios = listOf(b.radioCsv, b.radioPdf, b.radioFinanzamt)
        radios[initial.format.ordinal].isChecked = true
        b.radioFormat.setOnCheckedChangeListener { _, id ->
            val format = Format.entries[radios.indexOfFirst { it.id == id }]
            vm.options.update { it.copy(format = format) }
        }
        fun bind(switch: CompoundButton, value: Boolean, change: Options.(Boolean) -> Options) {
            switch.isChecked = value
            switch.setOnCheckedChangeListener { _, checked -> vm.options.update { it.change(checked) } }
        }
        bind(b.switchAudit, initial.withAudit) { copy(includeAudit = it) }
        bind(b.switchOnlyNew, initial.onlyNew) { copy(onlyNew = it) }
        bind(b.switchMark, initial.markExported) { copy(markExported = it) }
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
            b.editFrom.setText(Export.day(o.from))
            b.editTo.setText(Export.day(o.to))
            val finanzamt = o.format == Format.FINANZAMT_PDF
            b.textFinanzamtHint.isVisible = finanzamt
            b.layoutCompany.isVisible = finanzamt
            // Das Finanzamt-PDF enthält das Änderungsprotokoll immer
            b.switchAudit.isEnabled = !finanzamt
            b.switchAudit.isChecked = o.withAudit
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
                chip.chipStrokeColor = ColorStateList.valueOf(runCatching { p.color.toColorInt() }.getOrDefault(Color.GRAY))
                // Zustand vor dem Listener setzen, sonst meldet der Listener ihn als Nutzeränderung
                chip.isChecked = p.id !in vm.options.value.excludedPurposes
                chip.setOnCheckedChangeListener { _, checked ->
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
                if (done.markIds.isNotEmpty()) {
                    confirm(
                        getString(R.string.export_mark_title),
                        resources.getQuantityString(R.plurals.export_mark_message, done.markIds.size, done.markIds.size),
                        getString(R.string.export_mark_yes)
                    ) { vm.markExported(done.markIds) }
                }
            }.onFailure { toast(getString(R.string.export_error, it.message)) }
        }
    }

    private fun LocalDate.toDate(): Date = Date.from(atStartOfDay(ZoneId.systemDefault()).toInstant())

    private fun Date.toLocalDate(): LocalDate = toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
}
