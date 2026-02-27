package de.fosstenbuch.ui.trips

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import de.fosstenbuch.R
import de.fosstenbuch.data.model.Trip
import de.fosstenbuch.data.model.TripPurpose
import de.fosstenbuch.data.model.TripTemplate
import de.fosstenbuch.data.model.Vehicle
import de.fosstenbuch.data.repository.TripTemplateRepository
import de.fosstenbuch.data.repository.SavedLocationRepository
import de.fosstenbuch.databinding.FragmentAddEditTripBinding
import de.fosstenbuch.domain.service.LocationTrackingService
import de.fosstenbuch.domain.usecase.location.FindNearestSavedLocationUseCase
import de.fosstenbuch.domain.validation.TripValidator
import de.fosstenbuch.ui.common.safeNavigate
import de.fosstenbuch.ui.common.safePopBackStack
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class AddEditTripFragment : Fragment() {

    private var _binding: FragmentAddEditTripBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TripDetailViewModel by viewModels()
    private val dateTimeFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY)
    private var selectedDateTime: Date = Date()
    private var selectedEndTime: Date = Date()
    private var selectedVehicleId: Long? = null
    private var selectedPurposeId: Long? = null
    private var vehicles: List<Vehicle> = emptyList()
    private var purposes: List<TripPurpose> = emptyList()

    @Inject
    lateinit var findNearestSavedLocationUseCase: FindNearestSavedLocationUseCase

    @Inject
    lateinit var tripTemplateRepository: TripTemplateRepository

    @Inject
    lateinit var savedLocationRepository: SavedLocationRepository

    private lateinit var locationAdapterStart: LocationSuggestionAdapter
    private lateinit var locationAdapterEnd: LocationSuggestionAdapter
    private lateinit var locationAdapterStartEdit: LocationSuggestionAdapter
    private lateinit var locationAdapterEndEdit: LocationSuggestionAdapter

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            suggestNearestLocation()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddEditTripBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDateTimePickers()
        setupButtons()
        setupOdometerAutoCalculation()
        setupLocationAutocomplete()
        observeState()
        observeGpsDistance()
        tryLocationSuggestion()
    }

    // ========== Date/Time pickers ==========

    private fun setupDateTimePickers() {
        // Start phase: date+time picker
        binding.editDate.setText(dateTimeFormat.format(selectedDateTime))
        binding.editDate.setOnClickListener { showDateTimePicker(isStart = true) }

        // End phase: arrival time picker
        binding.editEndTime.setText(dateTimeFormat.format(selectedEndTime))
        binding.editEndTime.setOnClickListener { showDateTimePicker(isStart = false) }

        // Edit phase: date+time picker
        binding.editDateEdit.setText(dateTimeFormat.format(selectedDateTime))
        binding.editDateEdit.setOnClickListener { showDateTimePicker(isStart = true) }
    }

    private fun showDateTimePicker(isStart: Boolean) {
        val ctx = context ?: return
        val cal = Calendar.getInstance()
        cal.time = if (isStart) selectedDateTime else selectedEndTime

        DatePickerDialog(
            ctx,
            { _, year, month, day ->
                cal.set(Calendar.YEAR, year)
                cal.set(Calendar.MONTH, month)
                cal.set(Calendar.DAY_OF_MONTH, day)
                // Then show time picker
                val innerCtx = context ?: return@DatePickerDialog
                TimePickerDialog(
                    innerCtx,
                    { _, hour, minute ->
                        cal.set(Calendar.HOUR_OF_DAY, hour)
                        cal.set(Calendar.MINUTE, minute)
                        cal.set(Calendar.SECOND, 0)
                        val date = cal.time
                        if (isStart) {
                            selectedDateTime = date
                            binding.editDate.setText(dateTimeFormat.format(date))
                            binding.editDateEdit.setText(dateTimeFormat.format(date))
                        } else {
                            selectedEndTime = date
                            binding.editEndTime.setText(dateTimeFormat.format(date))
                        }
                    },
                    cal.get(Calendar.HOUR_OF_DAY),
                    cal.get(Calendar.MINUTE),
                    true
                ).show()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    // ========== Location Autocomplete ==========

    private fun setupLocationAutocomplete() {
        val onAddNew = { query: String ->
            safeNavigate(R.id.action_add_edit_trip_to_add_edit_location)
        }

        locationAdapterStart = LocationSuggestionAdapter(requireContext(), onAddNew)
        locationAdapterEnd = LocationSuggestionAdapter(requireContext(), onAddNew)
        locationAdapterStartEdit = LocationSuggestionAdapter(requireContext(), onAddNew)
        locationAdapterEndEdit = LocationSuggestionAdapter(requireContext(), onAddNew)

        binding.editStartLocation.setAdapter(locationAdapterStart)
        binding.editEndLocation.setAdapter(locationAdapterEnd)
        binding.editStartLocationEdit.setAdapter(locationAdapterStartEdit)
        binding.editEndLocationEdit.setAdapter(locationAdapterEndEdit)

        val handleItemClick = { parent: android.widget.AdapterView<*>, position: Int ->
            val item = parent.getItemAtPosition(position)
            if (item is LocationSuggestion.AddNew) {
                onAddNew(item.query)
            }
        }

        binding.editStartLocation.setOnItemClickListener { parent, _, pos, _ -> handleItemClick(parent, pos) }
        binding.editEndLocation.setOnItemClickListener { parent, _, pos, _ -> handleItemClick(parent, pos) }
        binding.editStartLocationEdit.setOnItemClickListener { parent, _, pos, _ -> handleItemClick(parent, pos) }
        binding.editEndLocationEdit.setOnItemClickListener { parent, _, pos, _ -> handleItemClick(parent, pos) }

        // Load saved locations
        viewLifecycleOwner.lifecycleScope.launch {
            savedLocationRepository.getAllSavedLocations().collect { locations ->
                locationAdapterStart.updateLocations(locations)
                locationAdapterEnd.updateLocations(locations)
                locationAdapterStartEdit.updateLocations(locations)
                locationAdapterEndEdit.updateLocations(locations)
            }
        }
    }

    // ========== Button setup ==========

    private fun setupButtons() {
        // Start trip button
        binding.buttonStartTrip.setOnClickListener {
            val trip = buildStartTrip()
            viewModel.startTrip(trip)
        }

        // Save (end trip) button
        binding.buttonSave.setOnClickListener {
            clearEndErrors()
            val trip = buildEndTrip()
            viewModel.endTrip(trip)
        }

        // Save (edit mode) button
        binding.buttonSaveEdit.setOnClickListener {
            clearEditErrors()
            val trip = buildEditTrip()
            viewModel.saveTrip(trip)
        }

        // Template buttons (edit mode only)
        binding.buttonUseTemplate.setOnClickListener { showTemplateBottomSheet() }
        binding.buttonSaveAsTemplate.setOnClickListener { showSaveAsTemplateDialog() }
    }

    // ========== Auto-calculate distance from odometer ==========

    private fun setupOdometerAutoCalculation() {
        // END phase: endOdometer changes → recalculate distance
        binding.editEndOdometer.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                autoCalculateDistanceEnd()
            }
        })
        // EDIT phase: both odometer fields → recalculate distance
        val editOdometerWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                autoCalculateDistanceEdit()
            }
        }
        binding.editStartOdometerEdit.addTextChangedListener(editOdometerWatcher)
        binding.editEndOdometerEdit.addTextChangedListener(editOdometerWatcher)
    }

    private fun autoCalculateDistanceEnd() {
        val state = viewModel.uiState.value
        val startOdo = state.trip?.startOdometer ?: return
        val endOdo = binding.editEndOdometer.text.toString().toIntOrNull() ?: return
        if (endOdo > startOdo) {
            val distanceKm = (endOdo - startOdo).toDouble()
            binding.editDistance.setText(distanceKm.toString())
        } else {
            binding.editDistance.setText("")
        }
    }

    private fun autoCalculateDistanceEdit() {
        val startOdo = binding.editStartOdometerEdit.text.toString().toIntOrNull() ?: return
        val endOdo = binding.editEndOdometerEdit.text.toString().toIntOrNull() ?: return
        if (endOdo > startOdo) {
            val distanceKm = (endOdo - startOdo).toDouble()
            binding.editDistanceEdit.setText(distanceKm.toString())
        } else {
            binding.editDistanceEdit.setText("")
        }
    }

    // ========== Build Trip objects from form ==========

    private fun buildStartTrip(): Trip {
        return Trip(
            date = selectedDateTime,
            startLocation = binding.editStartLocation.text.toString().trim(),
            startOdometer = binding.editStartOdometer.text.toString().toIntOrNull(),
            vehicleId = selectedVehicleId,
            isActive = true
        )
    }

    private fun buildEndTrip(): Trip {
        val activeTrip = viewModel.uiState.value.trip ?: return buildStartTrip()
        return activeTrip.copy(
            endLocation = binding.editEndLocation.text.toString().trim(),
            endOdometer = binding.editEndOdometer.text.toString().toIntOrNull(),
            distanceKm = binding.editDistance.text.toString().toDoubleOrNull() ?: 0.0,
            purpose = binding.editPurpose.text.toString().trim(),
            purposeId = selectedPurposeId,
            notes = binding.editNotes.text.toString().trim().ifEmpty { null },
            endTime = selectedEndTime,
            gpsDistanceKm = viewModel.uiState.value.gpsDistanceKm.takeIf { it > 0 },
            isActive = false
        )
    }

    private fun buildEditTrip(): Trip {
        val existingTrip = viewModel.uiState.value.trip
        return Trip(
            id = existingTrip?.id ?: 0,
            date = selectedDateTime,
            startLocation = binding.editStartLocationEdit.text.toString().trim(),
            endLocation = binding.editEndLocationEdit.text.toString().trim(),
            distanceKm = binding.editDistanceEdit.text.toString().toDoubleOrNull() ?: 0.0,
            purpose = binding.editPurposeEdit.text.toString().trim(),
            purposeId = selectedPurposeId,
            notes = binding.editNotesEdit.text.toString().trim().ifEmpty { null },
            startOdometer = binding.editStartOdometerEdit.text.toString().toIntOrNull(),
            endOdometer = binding.editEndOdometerEdit.text.toString().toIntOrNull(),
            vehicleId = selectedVehicleId,
            isCancelled = existingTrip?.isCancelled ?: false,
            cancellationReason = existingTrip?.cancellationReason,
            endTime = existingTrip?.endTime,
            gpsDistanceKm = existingTrip?.gpsDistanceKm
        )
    }

    // ========== Observe ViewModel state ==========

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // Show correct phase section
                    updatePhaseVisibility(state.phase)

                    // Populate form if editing existing trip
                    state.trip?.let { populateForm(it, state.phase) }

                    // Pre-fill start odometer from last trip
                    if (state.phase == TripPhase.START && state.lastEndOdometer != null && !formPopulated) {
                        binding.editStartOdometer.setText(state.lastEndOdometer.toString())
                    }

                    // Update vehicle dropdown
                    if (state.vehicles != vehicles) {
                        vehicles = state.vehicles
                        setupVehicleDropdown(state.vehicles, state.phase)
                    }

                    // Update purposes dropdown
                    if (state.purposes != purposes) {
                        purposes = state.purposes
                        setupPurposeCategoryDropdown(state.purposes, state.phase)
                    }

                    // Loading/saving states
                    binding.progressSaving.visibility =
                        if (state.isSaving) View.VISIBLE else View.GONE
                    binding.buttonStartTrip.isEnabled = !state.isSaving
                    binding.buttonSave.isEnabled = !state.isSaving
                    binding.buttonSaveEdit.isEnabled = !state.isSaving && !state.isAuditLocked

                    // Audit-locked: disable all edit fields
                    if (state.isAuditLocked && state.phase == TripPhase.EDIT) {
                        applyAuditLock()
                    }

                    // Validation errors
                    state.validationResult?.let { validation ->
                        when (state.phase) {
                            TripPhase.START -> {
                                binding.layoutStartLocation.error =
                                    validation.errorFor(TripValidator.FIELD_START_LOCATION)
                                binding.layoutStartOdometer.error =
                                    validation.errorFor(TripValidator.FIELD_START_ODOMETER)
                                binding.layoutVehicle.error =
                                    validation.errorFor(TripValidator.FIELD_VEHICLE)
                            }
                            TripPhase.END -> {
                                binding.layoutEndLocation.error =
                                    validation.errorFor(TripValidator.FIELD_END_LOCATION)
                                binding.layoutDistance.error =
                                    validation.errorFor(TripValidator.FIELD_DISTANCE)
                                binding.layoutPurpose.error =
                                    validation.errorFor(TripValidator.FIELD_PURPOSE)
                                binding.layoutPurposeCategory.error =
                                    validation.errorFor(TripValidator.FIELD_PURPOSE_ID)
                                binding.layoutEndOdometer.error =
                                    validation.errorFor(TripValidator.FIELD_ODOMETER)
                            }
                            TripPhase.EDIT -> {
                                binding.layoutStartLocationEdit.error =
                                    validation.errorFor(TripValidator.FIELD_START_LOCATION)
                                binding.layoutEndLocationEdit.error =
                                    validation.errorFor(TripValidator.FIELD_END_LOCATION)
                                binding.layoutDistanceEdit.error =
                                    validation.errorFor(TripValidator.FIELD_DISTANCE)
                                binding.layoutPurposeEdit.error =
                                    validation.errorFor(TripValidator.FIELD_PURPOSE)
                                binding.layoutPurposeCategoryEdit.error =
                                    validation.errorFor(TripValidator.FIELD_PURPOSE_ID)
                                binding.layoutDateEdit.error =
                                    validation.errorFor(TripValidator.FIELD_DATE)
                                val odometerError = validation.errorFor(TripValidator.FIELD_ODOMETER)
                                binding.layoutStartOdometerEdit.error = odometerError
                                binding.layoutEndOdometerEdit.error = odometerError
                                binding.layoutVehicleEdit.error =
                                    validation.errorFor(TripValidator.FIELD_VEHICLE)
                            }
                        }
                    }

                    // Error message
                    state.error?.let { error ->
                        Snackbar.make(binding.root, error, Snackbar.LENGTH_LONG).show()
                        viewModel.clearError()
                    }

                    // Successfully saved
                    if (state.savedSuccessfully) {
                        // If we just started a trip, start GPS tracking
                        if (state.phase == TripPhase.START && state.trip?.isActive == true) {
                            context?.let { ctx ->
                                LocationTrackingService.start(ctx, state.trip.id)
                            }
                        }
                        viewModel.onSaveConsumed()
                        safePopBackStack()
                    }
                }
            }
        }
    }

    private fun updatePhaseVisibility(phase: TripPhase) {
        binding.sectionStart.visibility = if (phase == TripPhase.START) View.VISIBLE else View.GONE
        binding.sectionEnd.visibility = if (phase == TripPhase.END) View.VISIBLE else View.GONE
        binding.sectionEdit.visibility = if (phase == TripPhase.EDIT) View.VISIBLE else View.GONE
    }

    // ========== GPS Distance observation ==========

    private fun observeGpsDistance() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                LocationTrackingService.gpsDistanceKm.collect { distanceKm ->
                    viewModel.updateGpsDistance(distanceKm)

                    if (viewModel.uiState.value.phase == TripPhase.END) {
                        binding.textGpsDistance.text =
                            String.format(Locale.GERMANY, getString(R.string.active_trip_gps_km), distanceKm)

                        // Pre-fill end odometer from GPS estimate
                        val startOdo = viewModel.uiState.value.trip?.startOdometer
                        if (startOdo != null && distanceKm > 0) {
                            val estimatedEnd = startOdo + distanceKm.toInt()
                            if (binding.editEndOdometer.text.isNullOrBlank()) {
                                binding.editEndOdometer.setText(estimatedEnd.toString())
                            }
                        }
                    }
                }
            }
        }
    }

    // ========== Form population ==========

    private var formPopulated = false

    private fun populateForm(trip: Trip, phase: TripPhase) {
        if (formPopulated) return
        formPopulated = true

        when (phase) {
            TripPhase.END -> {
                // Show start summary
                val startInfo = "${trip.startLocation}, ${dateTimeFormat.format(trip.date)} — Km-Stand: ${trip.startOdometer ?: "?"}"
                binding.textStartSummary.text = startInfo

                // Pre-fill end time with current time
                selectedEndTime = Date()
                binding.editEndTime.setText(dateTimeFormat.format(selectedEndTime))

                // Pre-fill end odometer from GPS if available
                val gpsKm = LocationTrackingService.gpsDistanceKm.value
                if (trip.startOdometer != null && gpsKm > 0) {
                    val estimatedEnd = trip.startOdometer + gpsKm.toInt()
                    binding.editEndOdometer.setText(estimatedEnd.toString())
                }

                selectedVehicleId = trip.vehicleId
                selectedPurposeId = trip.purposeId
            }
            TripPhase.EDIT -> {
                selectedDateTime = trip.date
                binding.editDateEdit.setText(dateTimeFormat.format(trip.date))
                binding.editStartLocationEdit.setText(trip.startLocation)
                binding.editEndLocationEdit.setText(trip.endLocation)
                binding.editDistanceEdit.setText(trip.distanceKm.toString())
                binding.editPurposeEdit.setText(trip.purpose)
                binding.editNotesEdit.setText(trip.notes ?: "")
                trip.startOdometer?.let { binding.editStartOdometerEdit.setText(it.toString()) }
                trip.endOdometer?.let { binding.editEndOdometerEdit.setText(it.toString()) }
                selectedVehicleId = trip.vehicleId
                selectedPurposeId = trip.purposeId
            }
            TripPhase.START -> {
                // Start phase — form is prefilled by observeState (lastEndOdometer + GPS location)
            }
        }
    }

    // ========== Dropdowns ==========

    private fun setupVehicleDropdown(vehicleList: List<Vehicle>, phase: TripPhase) {
        if (vehicleList.isEmpty()) return

        val items = vehicleList.map { "${it.make} ${it.model} (${it.licensePlate})" }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, items)

        val spinner = when (phase) {
            TripPhase.START -> binding.spinnerVehicle
            TripPhase.END -> return // No vehicle spinner in end phase
            TripPhase.EDIT -> binding.spinnerVehicleEdit
        }
        spinner.setAdapter(adapter)

        // Set current selection
        val currentIndex = selectedVehicleId?.let { id ->
            vehicleList.indexOfFirst { it.id == id }
        } ?: -1
        if (currentIndex in items.indices) {
            spinner.post { spinner.setText(items[currentIndex], false) }
        } else if (vehicleList.size == 1) {
            // Auto-select if only one vehicle exists
            selectedVehicleId = vehicleList[0].id
            spinner.post { spinner.setText(items[0], false) }
            viewModel.onVehicleChanged(selectedVehicleId)
        }

        spinner.setOnItemClickListener { _, _, position, _ ->
            selectedVehicleId = vehicleList[position].id
            viewModel.onVehicleChanged(selectedVehicleId)
        }
    }

    private fun setupPurposeCategoryDropdown(purposeList: List<TripPurpose>, phase: TripPhase) {
        if (purposeList.isEmpty()) return

        val items = purposeList.map { it.name }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, items)

        val spinner = when (phase) {
            TripPhase.START -> return // No purpose spinner in start phase
            TripPhase.END -> binding.spinnerPurposeCategory
            TripPhase.EDIT -> binding.spinnerPurposeCategoryEdit
        }
        spinner.setAdapter(adapter)

        // Set current selection — use post to ensure adapter is ready
        val currentIndex = selectedPurposeId?.let { id ->
            purposeList.indexOfFirst { it.id == id }
        } ?: -1
        if (currentIndex in items.indices) {
            spinner.post { spinner.setText(items[currentIndex], false) }
        }

        spinner.setOnItemClickListener { _, _, position, _ ->
            selectedPurposeId = purposeList[position].id
        }
    }

    // ========== Audit lock (edit phase) ==========

    private var auditLockApplied = false

    private fun applyAuditLock() {
        if (auditLockApplied) return
        auditLockApplied = true

        binding.editDateEdit.isEnabled = false
        binding.editStartLocationEdit.isEnabled = false
        binding.editEndLocationEdit.isEnabled = false
        binding.editDistanceEdit.isEnabled = false
        binding.editStartOdometerEdit.isEnabled = false
        binding.editEndOdometerEdit.isEnabled = false
        binding.editPurposeEdit.isEnabled = false
        binding.spinnerPurposeCategoryEdit.isEnabled = false
        binding.spinnerVehicleEdit.isEnabled = false
        binding.editNotesEdit.isEnabled = false
        binding.buttonSaveEdit.isEnabled = false
        binding.buttonSaveEdit.text = getString(R.string.audit_locked_hint)
        binding.buttonUseTemplate.isEnabled = false
        binding.buttonSaveAsTemplate.isEnabled = false
    }

    // ========== Error clearing ==========

    private fun clearEndErrors() {
        binding.layoutEndLocation.error = null
        binding.layoutEndOdometer.error = null
        binding.layoutDistance.error = null
        binding.layoutPurpose.error = null
        binding.layoutPurposeCategory.error = null
    }

    private fun clearEditErrors() {
        binding.layoutStartLocationEdit.error = null
        binding.layoutEndLocationEdit.error = null
        binding.layoutDistanceEdit.error = null
        binding.layoutPurposeEdit.error = null
        binding.layoutPurposeCategoryEdit.error = null
        binding.layoutDateEdit.error = null
        binding.layoutStartOdometerEdit.error = null
        binding.layoutEndOdometerEdit.error = null
    }

    // ========== Location suggestion ==========

    private fun tryLocationSuggestion() {
        if (hasLocationPermission()) {
            suggestNearestLocation()
        } else {
            locationPermissionRequest.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
    }

    @Suppress("MissingPermission")
    private fun suggestNearestLocation() {
        val act = activity ?: return
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(act)
        val cancellationToken = CancellationTokenSource()

        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            cancellationToken.token
        ).addOnSuccessListener { location ->
            if (location != null && _binding != null) {
                viewLifecycleOwner.lifecycleScope.launch {
                    val nearest = findNearestSavedLocationUseCase(
                        location.latitude, location.longitude
                    )
                    if (nearest != null && _binding != null) {
                        val phase = viewModel.uiState.value.phase
                        // Auto-fill start location (start phase) or end location (end phase)
                        when (phase) {
                            TripPhase.START -> {
                                if (binding.editStartLocation.text.isNullOrBlank()) {
                                    binding.editStartLocation.setText(nearest.name)
                                    Snackbar.make(
                                        binding.root,
                                        getString(R.string.location_suggested, nearest.name),
                                        Snackbar.LENGTH_SHORT
                                    ).show()
                                }
                            }
                            TripPhase.END -> {
                                if (binding.editEndLocation.text.isNullOrBlank()) {
                                    binding.editEndLocation.setText(nearest.name)
                                    Snackbar.make(
                                        binding.root,
                                        getString(R.string.location_suggested, nearest.name),
                                        Snackbar.LENGTH_SHORT
                                    ).show()
                                }
                            }
                            TripPhase.EDIT -> {
                                // Don't auto-fill in edit mode
                            }
                        }
                    }
                }
            }
        }.addOnFailureListener { e ->
            Timber.d(e, "GPS location not available for suggestion")
        }
    }

    // ========== Templates (edit mode) ==========

    private fun showTemplateBottomSheet() {
        viewLifecycleOwner.lifecycleScope.launch {
            val templates = tripTemplateRepository.getAllTemplates().first()
            if (templates.isEmpty()) {
                _binding?.let {
                    Snackbar.make(it.root, getString(R.string.no_templates), Snackbar.LENGTH_SHORT).show()
                }
                return@launch
            }

            val ctx = context ?: return@launch
            val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(ctx)
            val recyclerView = androidx.recyclerview.widget.RecyclerView(ctx).apply {
                layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
                setPadding(0, 16, 0, 16)
            }
            val adapter = TripTemplateAdapter(
                onTemplateClick = { template ->
                    applyTemplate(template)
                    dialog.dismiss()
                },
                onDeleteClick = { template ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        tripTemplateRepository.deleteTemplate(template)
                        Snackbar.make(binding.root, getString(R.string.deleted), Snackbar.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                }
            )
            adapter.submitList(templates)
            recyclerView.adapter = adapter
            dialog.setContentView(recyclerView)
            dialog.show()
        }
    }

    private fun applyTemplate(template: TripTemplate) {
        binding.editStartLocationEdit.setText(template.startLocation)
        binding.editEndLocationEdit.setText(template.endLocation)
        binding.editDistanceEdit.setText(template.distanceKm.toString())
        binding.editPurposeEdit.setText(template.purpose)
        selectedPurposeId = template.purposeId
        selectedVehicleId = template.vehicleId
        template.notes?.let { binding.editNotesEdit.setText(it) }

        // Update dropdowns
        val purposeIndex = purposes.indexOfFirst { it.id == template.purposeId }
        if (purposeIndex >= 0) {
            binding.spinnerPurposeCategoryEdit.setText(purposes[purposeIndex].name, false)
        }
        val vehicleIndex = vehicles.indexOfFirst { it.id == template.vehicleId }
        if (vehicleIndex >= 0) {
            val items = vehicles.map { "${it.make} ${it.model} (${it.licensePlate})" }
            binding.spinnerVehicleEdit.setText(items[vehicleIndex], false)
        }

        Snackbar.make(binding.root, getString(R.string.template_saved), Snackbar.LENGTH_SHORT).show()
    }

    private fun showSaveAsTemplateDialog() {
        val ctx = context ?: return
        val editText = com.google.android.material.textfield.TextInputEditText(ctx)
        editText.hint = getString(R.string.template_name)

        val layout = com.google.android.material.textfield.TextInputLayout(ctx).apply {
            addView(editText)
            setPadding(48, 16, 48, 0)
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
            .setTitle(getString(R.string.save_as_template))
            .setView(layout)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val name = editText.text?.toString()?.trim() ?: return@setPositiveButton
                if (name.isNotBlank()) {
                    saveCurrentAsTemplate(name)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun saveCurrentAsTemplate(name: String) {
        val template = TripTemplate(
            name = name,
            startLocation = binding.editStartLocationEdit.text.toString().trim(),
            endLocation = binding.editEndLocationEdit.text.toString().trim(),
            distanceKm = binding.editDistanceEdit.text.toString().toDoubleOrNull() ?: 0.0,
            purpose = binding.editPurposeEdit.text.toString().trim(),
            purposeId = selectedPurposeId,
            notes = binding.editNotesEdit.text.toString().trim().ifEmpty { null },
            vehicleId = selectedVehicleId
        )

        viewLifecycleOwner.lifecycleScope.launch {
            tripTemplateRepository.insertTemplate(template)
            Snackbar.make(binding.root, getString(R.string.template_saved), Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
