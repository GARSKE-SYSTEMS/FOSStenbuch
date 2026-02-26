package de.fosstenbuch.ui.trips

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
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
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import de.fosstenbuch.R
import de.fosstenbuch.data.model.Trip
import de.fosstenbuch.data.model.TripPurpose
import de.fosstenbuch.data.model.Vehicle
import de.fosstenbuch.databinding.FragmentAddEditTripBinding
import de.fosstenbuch.domain.usecase.location.FindNearestSavedLocationUseCase
import de.fosstenbuch.domain.validation.TripValidator
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
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)
    private var selectedDate: Date = Date()
    private var selectedVehicleId: Long? = null
    private var selectedPurposeId: Long? = null
    private var vehicles: List<Vehicle> = emptyList()
    private var purposes: List<TripPurpose> = emptyList()

    @Inject
    lateinit var findNearestSavedLocationUseCase: FindNearestSavedLocationUseCase

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
        setupDatePicker()
        setupSaveButton()
        observeState()
        tryLocationSuggestion()
    }

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
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
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
                        // Auto-fill start location if empty
                        if (binding.editStartLocation.text.isNullOrBlank()) {
                            binding.editStartLocation.setText(nearest.name)
                            Snackbar.make(
                                binding.root,
                                getString(R.string.location_suggested, nearest.name),
                                Snackbar.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }.addOnFailureListener { e ->
            Timber.d(e, "GPS location not available for suggestion")
        }
    }

    private fun setupDatePicker() {
        binding.editDate.setText(dateFormat.format(selectedDate))
        binding.editDate.setOnClickListener {
            val cal = Calendar.getInstance()
            cal.time = selectedDate
            DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    cal.set(year, month, day)
                    selectedDate = cal.time
                    binding.editDate.setText(dateFormat.format(selectedDate))
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    }

    private fun setupSaveButton() {
        binding.buttonSave.setOnClickListener {
            clearErrors()
            val trip = buildTripFromForm()
            viewModel.saveTrip(trip)
        }
    }

    private fun buildTripFromForm(): Trip {
        val existingTrip = viewModel.uiState.value.trip
        return Trip(
            id = existingTrip?.id ?: 0,
            date = selectedDate,
            startLocation = binding.editStartLocation.text.toString().trim(),
            endLocation = binding.editEndLocation.text.toString().trim(),
            distanceKm = binding.editDistance.text.toString().toDoubleOrNull() ?: 0.0,
            purpose = binding.editPurpose.text.toString().trim(),
            purposeId = selectedPurposeId,
            notes = binding.editNotes.text.toString().trim().ifEmpty { null },
            startOdometer = binding.editStartOdometer.text.toString().toIntOrNull(),
            endOdometer = binding.editEndOdometer.text.toString().toIntOrNull(),
            vehicleId = selectedVehicleId,
            isCancelled = existingTrip?.isCancelled ?: false,
            cancellationReason = existingTrip?.cancellationReason
        )
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // Populate form if editing existing trip
                    state.trip?.let { populateForm(it) }

                    // Update vehicles dropdown
                    if (state.vehicles != vehicles) {
                        vehicles = state.vehicles
                        setupVehicleDropdown(state.vehicles)
                    }

                    // Update purposes dropdown
                    if (state.purposes != purposes) {
                        purposes = state.purposes
                        setupPurposeCategoryDropdown(state.purposes)
                    }

                    // Loading/saving states
                    binding.progressSaving.visibility =
                        if (state.isSaving) View.VISIBLE else View.GONE
                    binding.buttonSave.isEnabled = !state.isSaving

                    // Validation errors
                    state.validationResult?.let { validation ->
                        binding.layoutStartLocation.error =
                            validation.errorFor(TripValidator.FIELD_START_LOCATION)
                        binding.layoutEndLocation.error =
                            validation.errorFor(TripValidator.FIELD_END_LOCATION)
                        binding.layoutDistance.error =
                            validation.errorFor(TripValidator.FIELD_DISTANCE)
                        binding.layoutPurpose.error =
                            validation.errorFor(TripValidator.FIELD_PURPOSE)
                        binding.layoutPurposeCategory.error =
                            validation.errorFor(TripValidator.FIELD_PURPOSE_ID)
                        binding.layoutDate.error =
                            validation.errorFor(TripValidator.FIELD_DATE)
                        val odometerError = validation.errorFor(TripValidator.FIELD_ODOMETER)
                        binding.layoutStartOdometer.error = odometerError
                        binding.layoutEndOdometer.error = odometerError
                    }

                    // Error message
                    state.error?.let { error ->
                        Snackbar.make(binding.root, error, Snackbar.LENGTH_LONG).show()
                        viewModel.clearError()
                    }

                    // Successfully saved
                    if (state.savedSuccessfully) {
                        viewModel.onSaveConsumed()
                        findNavController().popBackStack()
                    }
                }
            }
        }
    }

    private var formPopulated = false

    private fun populateForm(trip: Trip) {
        if (formPopulated) return
        formPopulated = true

        selectedDate = trip.date
        binding.editDate.setText(dateFormat.format(trip.date))
        binding.editStartLocation.setText(trip.startLocation)
        binding.editEndLocation.setText(trip.endLocation)
        binding.editDistance.setText(trip.distanceKm.toString())
        binding.editPurpose.setText(trip.purpose)
        binding.editNotes.setText(trip.notes ?: "")
        trip.startOdometer?.let { binding.editStartOdometer.setText(it.toString()) }
        trip.endOdometer?.let { binding.editEndOdometer.setText(it.toString()) }
        selectedVehicleId = trip.vehicleId
        selectedPurposeId = trip.purposeId
    }

    private fun setupVehicleDropdown(vehicleList: List<Vehicle>) {
        val items = listOf(getString(R.string.no_vehicle)) +
            vehicleList.map { "${it.make} ${it.model} (${it.licensePlate})" }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, items)
        binding.spinnerVehicle.setAdapter(adapter)

        // Set current selection
        val currentIndex = selectedVehicleId?.let { id ->
            vehicleList.indexOfFirst { it.id == id } + 1
        } ?: 0
        if (currentIndex in items.indices) {
            binding.spinnerVehicle.setText(items[currentIndex], false)
        }

        binding.spinnerVehicle.setOnItemClickListener { _, _, position, _ ->
            selectedVehicleId = if (position == 0) null else vehicleList[position - 1].id
        }
    }

    private fun setupPurposeCategoryDropdown(purposeList: List<TripPurpose>) {
        val items = purposeList.map { it.name }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, items)
        binding.spinnerPurposeCategory.setAdapter(adapter)

        // Set current selection
        val currentIndex = selectedPurposeId?.let { id ->
            purposeList.indexOfFirst { it.id == id }
        } ?: -1
        if (currentIndex in items.indices) {
            binding.spinnerPurposeCategory.setText(items[currentIndex], false)
        }

        binding.spinnerPurposeCategory.setOnItemClickListener { _, _, position, _ ->
            selectedPurposeId = purposeList[position].id
        }
    }

    private fun clearErrors() {
        binding.layoutStartLocation.error = null
        binding.layoutEndLocation.error = null
        binding.layoutDistance.error = null
        binding.layoutPurpose.error = null
        binding.layoutPurposeCategory.error = null
        binding.layoutDate.error = null
        binding.layoutStartOdometer.error = null
        binding.layoutEndOdometer.error = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
