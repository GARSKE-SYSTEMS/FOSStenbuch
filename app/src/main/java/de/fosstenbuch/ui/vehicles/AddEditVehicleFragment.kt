package de.fosstenbuch.ui.vehicles

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import de.fosstenbuch.domain.service.BluetoothTrackingService
import de.fosstenbuch.ui.common.safePopBackStack
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AddEditVehicleFragment : Fragment() {

    private var _binding: FragmentAddEditVehicleBinding? = null
    private val binding get() = _binding!!

    private val viewModel: VehicleDetailViewModel by viewModels()

    /** Currently selected Bluetooth device for automatic tracking. */
    private var selectedBtAddress: String? = null
    private var selectedBtName: String? = null

    private val bluetoothPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.any { it }
        if (granted) showBluetoothDevicePicker()
        else Snackbar.make(
            binding.root,
            R.string.bluetooth_permission_denied,
            Snackbar.LENGTH_LONG
        ).show()
    }

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
        setupBluetoothSection()
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
                (existingVehicle?.auditProtected ?: false), // Cannot uncheck once set
            bluetoothDeviceAddress = selectedBtAddress,
            bluetoothDeviceName = selectedBtName
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

        // Restore Bluetooth device
        if (vehicle.bluetoothDeviceAddress != null) {
            selectedBtAddress = vehicle.bluetoothDeviceAddress
            selectedBtName = vehicle.bluetoothDeviceName
            showSelectedBtDevice(vehicle.bluetoothDeviceName ?: vehicle.bluetoothDeviceAddress, vehicle.bluetoothDeviceAddress)
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

    // ── Bluetooth device picker ───────────────────────────────────────────

    private fun setupBluetoothSection() {
        binding.buttonPickBtDevice.setOnClickListener {
            requestBluetoothPermissionAndPick()
        }
        binding.buttonRemoveBtDevice.setOnClickListener {
            selectedBtAddress = null
            selectedBtName = null
            binding.cardSelectedBtDevice.visibility = View.GONE
            binding.buttonPickBtDevice.text = getString(R.string.bluetooth_pick_device)
        }
    }

    private fun requestBluetoothPermissionAndPick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val connectGranted = ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
            if (!connectGranted) {
                bluetoothPermissionRequest.launch(
                    arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
                )
                return
            }
        }
        showBluetoothDevicePicker()
    }

    @Suppress("MissingPermission")
    private fun showBluetoothDevicePicker() {
        val ctx = context ?: return
        val bluetoothManager = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
        if (adapter == null || !adapter.isEnabled) {
            Snackbar.make(binding.root, R.string.bluetooth_not_available, Snackbar.LENGTH_LONG).show()
            return
        }

        val bonded = adapter.bondedDevices?.toList() ?: emptyList()
        if (bonded.isEmpty()) {
            Snackbar.make(binding.root, R.string.bluetooth_no_paired_devices, Snackbar.LENGTH_LONG).show()
            return
        }

        val names = bonded.map { device ->
            val name = try { device.name } catch (_: SecurityException) { null }
            "${name ?: getString(R.string.bluetooth_unknown_device)} (${device.address})"
        }.toTypedArray()

        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.bluetooth_pick_title)
            .setItems(names) { _, which ->
                val device = bonded[which]
                val name = try { device.name } catch (_: SecurityException) { null }
                selectedBtAddress = device.address
                selectedBtName = name
                showSelectedBtDevice(name ?: device.address, device.address)
                // Ensure BluetoothTrackingService is running after a device is configured
                BluetoothTrackingService.start(requireContext())
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showSelectedBtDevice(name: String, address: String) {
        binding.textBtDeviceName.text = name
        binding.textBtDeviceAddress.text = address
        binding.cardSelectedBtDevice.visibility = View.VISIBLE
        binding.buttonPickBtDevice.text = getString(R.string.bluetooth_change_device)
    }}