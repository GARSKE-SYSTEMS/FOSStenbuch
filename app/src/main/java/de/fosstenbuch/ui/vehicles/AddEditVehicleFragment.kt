package de.fosstenbuch.ui.vehicles

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import de.fosstenbuch.R
import de.fosstenbuch.data.model.Vehicle
import de.fosstenbuch.databinding.FragmentAddEditVehicleBinding
import de.fosstenbuch.domain.validation.VehicleValidator
import de.fosstenbuch.ui.common.safePopBackStack
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AddEditVehicleFragment : Fragment() {

    private var _binding: FragmentAddEditVehicleBinding? = null
    private val binding get() = _binding!!

    private val viewModel: VehicleDetailViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddEditVehicleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupFuelTypeDropdown()
        setupAuditProtection()
        setupSaveButton()
        observeState()
    }

    private fun setupFuelTypeDropdown() {
        val fuelTypes = resources.getStringArray(R.array.fuel_types)
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, fuelTypes)
        binding.spinnerFuelType.setAdapter(adapter)
    }

    private fun setupAuditProtection() {
        binding.switchAuditProtected.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val ctx = context ?: return@setOnCheckedChangeListener
                // Show warning dialog before enabling
                MaterialAlertDialogBuilder(ctx)
                    .setTitle(R.string.audit_protection_warning_title)
                    .setMessage(R.string.audit_protection_warning_message)
                    .setPositiveButton(R.string.enable) { _, _ ->
                        // Keep checked
                    }
                    .setNegativeButton(R.string.cancel) { _, _ ->
                        _binding?.switchAuditProtected?.isChecked = false
                    }
                    .setOnCancelListener {
                        _binding?.switchAuditProtected?.isChecked = false
                    }
                    .show()
            }
        }
    }

    private fun setupSaveButton() {
        binding.buttonSave.setOnClickListener {
            clearErrors()
            val vehicle = buildVehicleFromForm()
            viewModel.saveVehicle(vehicle)
        }
    }

    private fun buildVehicleFromForm(): Vehicle {
        val existingVehicle = viewModel.uiState.value.vehicle
        return Vehicle(
            id = existingVehicle?.id ?: 0,
            make = binding.editMake.text.toString().trim(),
            model = binding.editModel.text.toString().trim(),
            licensePlate = binding.editLicensePlate.text.toString().trim().uppercase(),
            fuelType = binding.spinnerFuelType.text.toString().trim(),
            isPrimary = binding.switchPrimary.isChecked,
            notes = binding.editNotes.text.toString().trim().ifEmpty { null },
            auditProtected = binding.switchAuditProtected.isChecked ||
                (existingVehicle?.auditProtected ?: false) // Cannot uncheck once set
        )
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // Populate form if editing existing vehicle
                    state.vehicle?.let { populateForm(it) }

                    // Loading/saving states
                    binding.progressSaving.visibility =
                        if (state.isSaving) View.VISIBLE else View.GONE
                    binding.buttonSave.isEnabled = !state.isSaving

                    // Validation errors
                    state.validationResult?.let { validation ->
                        binding.layoutMake.error =
                            validation.errorFor(VehicleValidator.FIELD_MAKE)
                        binding.layoutModel.error =
                            validation.errorFor(VehicleValidator.FIELD_MODEL)
                        binding.layoutLicensePlate.error =
                            validation.errorFor(VehicleValidator.FIELD_LICENSE_PLATE)
                        binding.layoutFuelType.error =
                            validation.errorFor(VehicleValidator.FIELD_FUEL_TYPE)
                    }

                    // Error message
                    state.error?.let { error ->
                        Snackbar.make(binding.root, error, Snackbar.LENGTH_LONG).show()
                        viewModel.clearError()
                    }

                    // Successfully saved
                    if (state.savedSuccessfully) {
                        viewModel.onSaveConsumed()
                        safePopBackStack()
                    }
                }
            }
        }
    }

    private var formPopulated = false

    private fun populateForm(vehicle: Vehicle) {
        if (formPopulated) return
        formPopulated = true

        binding.editMake.setText(vehicle.make)
        binding.editModel.setText(vehicle.model)
        binding.editLicensePlate.setText(vehicle.licensePlate)
        binding.spinnerFuelType.setText(vehicle.fuelType, false)
        binding.switchPrimary.isChecked = vehicle.isPrimary
        binding.editNotes.setText(vehicle.notes ?: "")

        // Audit protection - once enabled, cannot be disabled
        if (vehicle.auditProtected) {
            binding.switchAuditProtected.isChecked = true
            binding.switchAuditProtected.isEnabled = false
            binding.textAuditHint.setText(R.string.audit_protection_active)
        }
    }

    private fun clearErrors() {
        binding.layoutMake.error = null
        binding.layoutModel.error = null
        binding.layoutLicensePlate.error = null
        binding.layoutFuelType.error = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
