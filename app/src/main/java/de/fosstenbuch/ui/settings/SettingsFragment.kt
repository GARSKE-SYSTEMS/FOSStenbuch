package de.fosstenbuch.ui.settings

import android.Manifest
import android.net.Uri
import android.os.Build
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

    // Dropdown change tracking to avoid unnecessary adapter recreation
    private var lastDarkMode: PreferencesManager.DarkMode? = null
    private var lastDistanceUnit: PreferencesManager.DistanceUnit? = null
    private var lastPurposes: List<de.fosstenbuch.data.model.TripPurpose> = emptyList()
    private var lastDefaultPurposeId: Long? = -1L
    private var lastVehicles: List<de.fosstenbuch.data.model.Vehicle> = emptyList()
    private var lastDefaultVehicleId: Long? = -1L
    private var lastDriverName: String? = null

    private val restoreFilePicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { confirmRestore(it) }
    }

    /**
     * Launcher for the POST_NOTIFICATIONS runtime permission (API 33+).
     * On grant: enable the reminder switch and schedule the alarm.
     * On deny: turn the switch back off and show a hint.
     */
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            enableReminderAfterPermissionGrant()
        } else {
            binding.switchReminder.isChecked = false
            viewModel.setReminderEnabled(false)
            Toast.makeText(
                requireContext(),
                R.string.notification_permission_required,
                Toast.LENGTH_LONG
            ).show()
        }
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
        setupDriverName()
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

    private fun setupDriverName() {
        binding.editDriverName.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val name = binding.editDriverName.text?.toString()?.trim() ?: ""
                viewModel.setDriverName(name)
            }
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
            if (isChecked) {
                // On API 33+ we need POST_NOTIFICATIONS at runtime
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    !TripReminderReceiver.hasNotificationPermission(requireContext())
                ) {
                    // Don't persist yet – wait for the permission callback
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    return@setOnCheckedChangeListener
                }
                enableReminderAfterPermissionGrant()
            } else {
                viewModel.setReminderEnabled(false)
                binding.buttonReminderTime.visibility = View.GONE
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

    /**
     * Called after we know the notification permission is available (API < 33 or granted).
     */
    private fun enableReminderAfterPermissionGrant() {
        viewModel.setReminderEnabled(true)
        binding.switchReminder.isChecked = true
        binding.buttonReminderTime.visibility = View.VISIBLE
        val time = viewModel.uiState.value.reminderTime
        val parts = time.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 18
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        TripReminderReceiver.scheduleReminder(requireContext(), hour, minute)
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

                    // Dark mode dropdown — only update when changed
                    if (state.darkMode != lastDarkMode) {
                        lastDarkMode = state.darkMode
                        val darkModeLabels = resources.getStringArray(R.array.dark_mode_options)
                        binding.dropdownDarkMode.setText(
                            darkModeLabels[state.darkMode.ordinal],
                            false
                        )
                        // Reset filter so all options remain visible when the dropdown opens next time
                        binding.dropdownDarkMode.post {
                            (binding.dropdownDarkMode.adapter as? ArrayAdapter<*>)?.filter?.filter(null)
                        }
                    }

                    // Distance unit dropdown — only update when changed
                    if (state.distanceUnit != lastDistanceUnit) {
                        lastDistanceUnit = state.distanceUnit
                        val unitLabels = resources.getStringArray(R.array.distance_unit_options)
                        binding.dropdownDistanceUnit.setText(
                            unitLabels[state.distanceUnit.ordinal],
                            false
                        )
                        // Reset filter so all options remain visible when the dropdown opens next time
                        binding.dropdownDistanceUnit.post {
                            (binding.dropdownDistanceUnit.adapter as? ArrayAdapter<*>)?.filter?.filter(null)
                        }
                    }

                    // Default purpose dropdown — only rebuild adapter when data changes
                    if (state.purposes != lastPurposes || state.defaultPurposeId != lastDefaultPurposeId) {
                        lastPurposes = state.purposes
                        lastDefaultPurposeId = state.defaultPurposeId
                        updatePurposeDropdown(state)
                    }

                    // Default vehicle dropdown — only rebuild adapter when data changes
                    if (state.vehicles != lastVehicles || state.defaultVehicleId != lastDefaultVehicleId) {
                        lastVehicles = state.vehicles
                        lastDefaultVehicleId = state.defaultVehicleId
                        updateVehicleDropdown(state)
                    }

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

                    // Driver name — only update when changed to avoid cursor jump
                    if (state.driverName != lastDriverName) {
                        lastDriverName = state.driverName
                        if (binding.editDriverName.text?.toString() != state.driverName) {
                            binding.editDriverName.setText(state.driverName)
                        }
                    }
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
        binding.dropdownDefaultPurpose.post {
            _binding?.dropdownDefaultPurpose?.setText(purposeNames[selectedIndex], false)
            (_binding?.dropdownDefaultPurpose?.adapter as? ArrayAdapter<*>)?.filter?.filter(null)
        }

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
        binding.dropdownDefaultVehicle.post {
            _binding?.dropdownDefaultVehicle?.setText(vehicleNames[selectedIndex], false)
            (_binding?.dropdownDefaultVehicle?.adapter as? ArrayAdapter<*>)?.filter?.filter(null)
        }

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