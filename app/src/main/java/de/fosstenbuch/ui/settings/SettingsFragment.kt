package de.fosstenbuch.ui.settings

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import de.fosstenbuch.R
import de.fosstenbuch.data.local.PreferencesManager
import de.fosstenbuch.databinding.FragmentSettingsBinding
import de.fosstenbuch.domain.notification.TripReminderReceiver
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by viewModels()

    private val restoreFilePicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { confirmRestore(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupExport()
        setupMileageCalculator()
        setupAppearance()
        setupGeneralPreferences()
        setupVehicleManagement()
        setupLocationManagement()
        setupPurposeManagement()
        setupDataManagement()
        setupNotifications()
        observeState()
    }

    private fun setupExport() {
        binding.cardExport.setOnClickListener {
            findNavController().navigate(R.id.navigation_export)
        }
    }

    private fun setupMileageCalculator() {
        binding.cardMileageCalculator.setOnClickListener {
            findNavController().navigate(R.id.navigation_mileage_calculator)
        }
    }

    private fun setupAppearance() {
        val darkModeLabels = resources.getStringArray(R.array.dark_mode_options)
        val darkModeAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            darkModeLabels
        )
        binding.dropdownDarkMode.setAdapter(darkModeAdapter)
        binding.dropdownDarkMode.setOnItemClickListener { _, _, position, _ ->
            val mode = PreferencesManager.DarkMode.entries[position]
            viewModel.setDarkMode(mode)
            applyDarkMode(mode)
        }
    }

    private fun setupGeneralPreferences() {
        // Distance unit dropdown
        val unitLabels = resources.getStringArray(R.array.distance_unit_options)
        val unitAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            unitLabels
        )
        binding.dropdownDistanceUnit.setAdapter(unitAdapter)
        binding.dropdownDistanceUnit.setOnItemClickListener { _, _, position, _ ->
            val unit = PreferencesManager.DistanceUnit.entries[position]
            viewModel.setDistanceUnit(unit)
        }

        // Default purpose & vehicle are set up dynamically in observeState
    }

    private fun setupVehicleManagement() {
        binding.cardVehicles.setOnClickListener {
            findNavController().navigate(R.id.navigation_vehicles)
        }
    }

    private fun setupLocationManagement() {
        binding.cardLocations.setOnClickListener {
            findNavController().navigate(R.id.navigation_saved_locations)
        }
    }

    private fun setupPurposeManagement() {
        binding.cardPurposes.setOnClickListener {
            findNavController().navigate(R.id.navigation_purposes)
        }
    }

    private fun setupDataManagement() {
        binding.buttonBackup.setOnClickListener {
            viewModel.performBackup()
        }

        binding.buttonRestore.setOnClickListener {
            restoreFilePicker.launch("application/json")
        }

        binding.buttonDeleteAll.setOnClickListener {
            confirmDeleteAll()
        }
    }

    private fun setupNotifications() {
        TripReminderReceiver.createNotificationChannel(requireContext())

        binding.switchReminder.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setReminderEnabled(isChecked)
            binding.buttonReminderTime.visibility = if (isChecked) View.VISIBLE else View.GONE
            if (isChecked) {
                val time = viewModel.uiState.value.reminderTime
                val parts = time.split(":")
                val hour = parts.getOrNull(0)?.toIntOrNull() ?: 18
                val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
                TripReminderReceiver.scheduleReminder(requireContext(), hour, minute)
            } else {
                TripReminderReceiver.cancelReminder(requireContext())
            }
        }

        binding.buttonReminderTime.setOnClickListener {
            val current = viewModel.uiState.value.reminderTime
            val parts = current.split(":")
            val hour = parts.getOrNull(0)?.toIntOrNull() ?: 18
            val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0

            android.app.TimePickerDialog(
                requireContext(),
                { _, selectedHour, selectedMinute ->
                    val time = "%02d:%02d".format(selectedHour, selectedMinute)
                    viewModel.setReminderTime(time)
                    binding.buttonReminderTime.text = getString(R.string.notification_time) + ": " + time
                    TripReminderReceiver.scheduleReminder(requireContext(), selectedHour, selectedMinute)
                },
                hour,
                minute,
                true
            ).show()
        }
    }

    private fun confirmRestore(uri: Uri) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.restore_warning_title)
            .setMessage(R.string.restore_warning_message)
            .setPositiveButton(R.string.settings_restore) { _, _ ->
                viewModel.performRestore(uri)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDeleteAll() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_delete_all_title)
            .setMessage(R.string.settings_delete_all_message)
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.deleteAllData()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun applyDarkMode(mode: PreferencesManager.DarkMode) {
        val nightMode = when (mode) {
            PreferencesManager.DarkMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            PreferencesManager.DarkMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            PreferencesManager.DarkMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    Timber.d("SettingsUiState: vehicles=${state.vehicleCount}, trips=${state.tripCount}")

                    // Data summary
                    binding.textTripCount.text = getString(R.string.trip_count_format, state.tripCount)
                    binding.textVehicleCount.text = getString(R.string.vehicle_count_format, state.vehicleCount)
                    binding.textLocationCount.text = getString(R.string.location_count_format, state.locationCount)
                    binding.textPurposeCount.text = getString(R.string.purpose_count_format, state.purposeCount)

                    // Loading
                    binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE

                    // Dark mode dropdown
                    val darkModeLabels = resources.getStringArray(R.array.dark_mode_options)
                    binding.dropdownDarkMode.setText(
                        darkModeLabels[state.darkMode.ordinal],
                        false
                    )

                    // Distance unit dropdown
                    val unitLabels = resources.getStringArray(R.array.distance_unit_options)
                    binding.dropdownDistanceUnit.setText(
                        unitLabels[state.distanceUnit.ordinal],
                        false
                    )

                    // Default purpose dropdown
                    updatePurposeDropdown(state)

                    // Default vehicle dropdown
                    updateVehicleDropdown(state)

                    // Error
                    state.error?.let {
                        Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                        viewModel.clearError()
                    }

                    // Backup success
                    if (state.backupSuccess) {
                        state.backupFilePath?.let { path ->
                            shareBackupFile(File(path))
                        }
                        Toast.makeText(requireContext(), R.string.backup_success, Toast.LENGTH_SHORT).show()
                        viewModel.consumeBackupSuccess()
                    }

                    // Restore success
                    if (state.restoreSuccess) {
                        Toast.makeText(requireContext(), R.string.restore_success, Toast.LENGTH_SHORT).show()
                        viewModel.consumeRestoreSuccess()
                    }

                    // Delete success
                    if (state.deleteSuccess) {
                        Toast.makeText(requireContext(), R.string.delete_all_success, Toast.LENGTH_SHORT).show()
                        viewModel.consumeDeleteSuccess()
                    }

                    // Reminder
                    binding.switchReminder.isChecked = state.reminderEnabled
                    binding.buttonReminderTime.visibility = if (state.reminderEnabled) View.VISIBLE else View.GONE
                    binding.buttonReminderTime.text = getString(R.string.notification_time) + ": " + state.reminderTime
                }
            }
        }
    }

    private fun updatePurposeDropdown(state: SettingsUiState) {
        val purposeNames = mutableListOf(getString(R.string.no_default))
        val purposeIds = mutableListOf<Long?>(null)
        state.purposes.forEach { p ->
            purposeNames.add(p.name)
            purposeIds.add(p.id)
        }

        val purposeAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            purposeNames
        )
        binding.dropdownDefaultPurpose.setAdapter(purposeAdapter)

        val selectedIndex = purposeIds.indexOf(state.defaultPurposeId).takeIf { it >= 0 } ?: 0
        binding.dropdownDefaultPurpose.setText(purposeNames[selectedIndex], false)

        binding.dropdownDefaultPurpose.setOnItemClickListener { _, _, position, _ ->
            viewModel.setDefaultPurpose(purposeIds[position])
        }
    }

    private fun updateVehicleDropdown(state: SettingsUiState) {
        val vehicleNames = mutableListOf(getString(R.string.no_default))
        val vehicleIds = mutableListOf<Long?>(null)
        state.vehicles.forEach { v ->
            vehicleNames.add("${v.make} ${v.model} (${v.licensePlate})")
            vehicleIds.add(v.id)
        }

        val vehicleAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            vehicleNames
        )
        binding.dropdownDefaultVehicle.setAdapter(vehicleAdapter)

        val selectedIndex = vehicleIds.indexOf(state.defaultVehicleId).takeIf { it >= 0 } ?: 0
        binding.dropdownDefaultVehicle.setText(vehicleNames[selectedIndex], false)

        binding.dropdownDefaultVehicle.setOnItemClickListener { _, _, position, _ ->
            viewModel.setDefaultVehicle(vehicleIds[position])
        }
    }

    private fun shareBackupFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )
            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                putExtra(android.content.Intent.EXTRA_SUBJECT, getString(R.string.export_share_subject))
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(android.content.Intent.createChooser(shareIntent, getString(R.string.export_share_title)))
        } catch (e: Exception) {
            Timber.e(e, "Failed to share backup file")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}