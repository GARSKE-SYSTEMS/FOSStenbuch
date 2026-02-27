package de.fosstenbuch.ui.export

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import de.fosstenbuch.R
import de.fosstenbuch.databinding.FragmentExportBinding
import de.fosstenbuch.domain.export.ExportFormat
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@AndroidEntryPoint
class ExportFragment : Fragment() {

    private var _binding: FragmentExportBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ExportViewModel by viewModels()
    private lateinit var purposeFilterAdapter: PurposeFilterAdapter
    private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    // Dropdown change tracking
    private var lastExportVehicles: List<de.fosstenbuch.data.model.Vehicle> = emptyList()
    private var lastSelectedVehicleId: Long? = -1L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupPurposeFilter()
        setupDatePickers()
        setupFormatSelection()
        setupExportTracking()
        setupExportButton()
        observeState()
    }

    private fun setupPurposeFilter() {
        purposeFilterAdapter = PurposeFilterAdapter { purposeId, checked ->
            viewModel.togglePurpose(purposeId, checked)
        }
        binding.recyclerPurposes.adapter = purposeFilterAdapter
    }

    private fun setupDatePickers() {
        binding.editDateFrom.setOnClickListener {
            showDatePicker(viewModel.uiState.value.dateFrom) { date ->
                viewModel.setDateFrom(date)
            }
        }
        binding.editDateTo.setOnClickListener {
            showDatePicker(viewModel.uiState.value.dateTo) { date ->
                viewModel.setDateTo(date)
            }
        }
    }

    private fun showDatePicker(current: LocalDate, onDateSelected: (LocalDate) -> Unit) {
        val ctx = context ?: return
        DatePickerDialog(
            ctx,
            { _, year, month, day ->
                onDateSelected(LocalDate.of(year, month + 1, day))
            },
            current.year,
            current.monthValue - 1,
            current.dayOfMonth
        ).show()
    }

    private fun setupFormatSelection() {
        binding.radioFormat.setOnCheckedChangeListener { _, checkedId ->
            val format = when (checkedId) {
                R.id.radio_csv -> ExportFormat.CSV
                R.id.radio_pdf -> ExportFormat.PDF
                else -> ExportFormat.CSV
            }
            viewModel.setFormat(format)
        }

        binding.switchAuditLog.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setIncludeAuditLog(isChecked)
        }
    }

    private fun setupExportTracking() {
        binding.switchOnlyNew.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setOnlyNew(isChecked)
        }
        binding.switchMarkExported.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setMarkAsExported(isChecked)
        }
    }

    private fun setupExportButton() {
        binding.buttonExport.setOnClickListener {
            viewModel.performExport()
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // Date fields
                    binding.editDateFrom.setText(state.dateFrom.format(dateFormatter))
                    binding.editDateTo.setText(state.dateTo.format(dateFormatter))

                    // Purpose filter list
                    val filterItems = state.purposes.map { purpose ->
                        PurposeFilterAdapter.PurposeFilterItem(
                            purpose = purpose,
                            isChecked = purpose.id in state.selectedPurposeIds
                        )
                    }
                    purposeFilterAdapter.submitList(filterItems)

                    // Vehicle dropdown — only rebuild when data changes
                    if (state.vehicles != lastExportVehicles || state.selectedVehicleId != lastSelectedVehicleId) {
                        lastExportVehicles = state.vehicles
                        lastSelectedVehicleId = state.selectedVehicleId
                        setupVehicleDropdown(state)
                    }

                    // Trip count preview
                    binding.textTripCount.text = getString(
                        R.string.export_trip_count_preview, state.tripCount
                    )

                    // Format selection
                    when (state.format) {
                        ExportFormat.CSV -> binding.radioCsv.isChecked = true
                        ExportFormat.PDF -> binding.radioPdf.isChecked = true
                    }
                    binding.switchAuditLog.isChecked = state.includeAuditLog
                    binding.switchOnlyNew.isChecked = state.onlyNew
                    binding.switchMarkExported.isChecked = state.markAsExported

                    // Loading/exporting
                    binding.progressExporting.visibility =
                        if (state.isExporting) View.VISIBLE else View.GONE
                    binding.buttonExport.isEnabled = !state.isExporting && state.tripCount > 0

                    // Error
                    state.error?.let { error ->
                        Snackbar.make(binding.root, error, Snackbar.LENGTH_LONG).show()
                        viewModel.clearError()
                    }

                    // Export success → share
                    if (state.exportSuccess && state.exportedFilePath != null && isAdded) {
                        shareFile(File(state.exportedFilePath), state.format)
                        viewModel.consumeExportSuccess()
                    }
                }
            }
        }
    }

    private fun setupVehicleDropdown(state: ExportUiState) {
        val ctx = context ?: return
        val items = listOf(getString(R.string.export_all_vehicles)) +
            state.vehicles.map { "${it.make} ${it.model} (${it.licensePlate})" }
        val adapter = ArrayAdapter(ctx, android.R.layout.simple_list_item_1, items)
        binding.spinnerVehicle.setAdapter(adapter)

        val currentIndex = state.selectedVehicleId?.let { id ->
            state.vehicles.indexOfFirst { it.id == id } + 1
        } ?: 0
        binding.spinnerVehicle.post {
            if (currentIndex in items.indices) {
                binding.spinnerVehicle.setText(items[currentIndex], false)
            }
        }

        binding.spinnerVehicle.setOnItemClickListener { _, _, position, _ ->
            viewModel.setVehicle(if (position == 0) null else state.vehicles[position - 1].id)
        }
    }

    private fun shareFile(file: File, format: ExportFormat) {
        val ctx = context ?: return
        val contentUri = FileProvider.getUriForFile(
            ctx,
            "${ctx.packageName}.fileprovider",
            file
        )

        val mimeType = when (format) {
            ExportFormat.CSV -> "text/csv"
            ExportFormat.PDF -> "application/pdf"
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.export_share_subject))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(shareIntent, getString(R.string.export_share_title)))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
