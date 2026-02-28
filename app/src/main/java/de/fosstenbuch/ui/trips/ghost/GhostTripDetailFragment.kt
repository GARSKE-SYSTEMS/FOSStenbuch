package de.fosstenbuch.ui.trips.ghost

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import androidx.navigation.fragment.navArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import de.fosstenbuch.R
import de.fosstenbuch.data.model.Trip
import de.fosstenbuch.data.model.TripPurpose
import de.fosstenbuch.data.model.Vehicle
import de.fosstenbuch.databinding.FragmentGhostTripDetailBinding
import de.fosstenbuch.domain.validation.TripValidator
import de.fosstenbuch.ui.common.safePopBackStack
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class GhostTripDetailFragment : Fragment() {

    private var _binding: FragmentGhostTripDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: GhostTripDetailViewModel by viewModels()
    private val args: GhostTripDetailFragmentArgs by navArgs()

    private val dateTimeFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY)

    private var selectedStartTime: Date = Date()
    private var selectedEndTime: Date = Date()
    private var selectedVehicleId: Long? = null
    private var selectedPurposeId: Long? = null
    private var vehicles: List<Vehicle> = emptyList()
    private var purposes: List<TripPurpose> = emptyList()
    private var formPopulated = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGhostTripDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupButtons()
        observeState()
    }

    private fun setupButtons() {
        binding.buttonAccept.setOnClickListener { onAcceptClicked() }

        binding.buttonDiscard.setOnClickListener {
            if (!isAdded) return@setOnClickListener
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.ghost_discard_title)
                .setMessage(R.string.ghost_discard_message)
                .setPositiveButton(R.string.ghost_discard_confirm) { _, _ ->
                    viewModel.discard()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        binding.editStartTime.setOnClickListener { pickDateTime(isStart = true) }
        binding.editEndTime.setOnClickListener { pickDateTime(isStart = false) }
    }

    private fun onAcceptClicked() {
        val trip = viewModel.uiState.value.trip ?: return
        clearErrors()

        val startOdometer = binding.editStartOdometer.text.toString().trim().toIntOrNull()
        val endOdometer = binding.editEndOdometer.text.toString().trim().toIntOrNull()
        val distanceKm = binding.editDistanceKm.text.toString().trim().toDoubleOrNull() ?: 0.0

        val updatedTrip = trip.copy(
            date = selectedStartTime,
            endTime = selectedEndTime,
            startLocation = binding.editStartLocation.text.toString().trim(),
            endLocation = binding.editEndLocation.text.toString().trim(),
            distanceKm = distanceKm,
            startOdometer = startOdometer,
            endOdometer = endOdometer,
            purposeId = selectedPurposeId,
            purpose = binding.spinnerPurpose.text.toString().trim(),
            vehicleId = selectedVehicleId,
            notes = binding.editNotes.text.toString().trim().ifEmpty { null }
        )

        viewModel.accept(updatedTrip)
    }

    private fun pickDateTime(isStart: Boolean) {
        val current = if (isStart) selectedStartTime else selectedEndTime
        val calendar = Calendar.getInstance().apply { time = current }

        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                calendar.set(year, month, day)
                TimePickerDialog(
                    requireContext(),
                    { _, hour, minute ->
                        calendar.set(Calendar.HOUR_OF_DAY, hour)
                        calendar.set(Calendar.MINUTE, minute)
                        calendar.set(Calendar.SECOND, 0)
                        if (isStart) {
                            selectedStartTime = calendar.time
                            binding.editStartTime.setText(dateTimeFormat.format(selectedStartTime))
                        } else {
                            selectedEndTime = calendar.time
                            binding.editEndTime.setText(dateTimeFormat.format(selectedEndTime))
                        }
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    true
                ).show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressBar.visibility =
                        if (state.isLoading) View.VISIBLE else View.GONE
                    binding.contentLayout.visibility =
                        if (state.isLoading) View.GONE else View.VISIBLE
                    binding.buttonAccept.isEnabled = !state.isSaving
                    binding.buttonDiscard.isEnabled = !state.isSaving
                    binding.progressSaving.visibility =
                        if (state.isSaving) View.VISIBLE else View.GONE

                    vehicles = state.vehicles
                    purposes = state.purposes

                    populateDropdowns()

                    state.trip?.let { if (!formPopulated) populateForm(it) }

                    applyValidationErrors(state.validationErrors)

                    state.error?.let { error ->
                        Snackbar.make(binding.root, error, Snackbar.LENGTH_LONG).show()
                        viewModel.clearError()
                    }

                    if (state.acceptedSuccessfully) {
                        viewModel.onAcceptConsumed()
                        Snackbar.make(
                            binding.root,
                            R.string.ghost_accepted_success,
                            Snackbar.LENGTH_SHORT
                        ).show()
                        safePopBackStack()
                    }

                    if (state.discardedSuccessfully) {
                        viewModel.onDiscardConsumed()
                        safePopBackStack()
                    }
                }
            }
        }
    }

    private fun populateForm(trip: Trip) {
        formPopulated = true

        selectedStartTime = trip.date
        selectedEndTime = trip.endTime ?: trip.date
        selectedVehicleId = trip.vehicleId
        selectedPurposeId = trip.purposeId

        binding.editStartLocation.setText(trip.startLocation)
        binding.editEndLocation.setText(trip.endLocation)
        binding.editStartTime.setText(dateTimeFormat.format(selectedStartTime))
        binding.editEndTime.setText(dateTimeFormat.format(selectedEndTime))
        binding.editDistanceKm.setText(
            if ((trip.gpsDistanceKm ?: 0.0) > 0.0) "%.1f".format(trip.gpsDistanceKm) else ""
        )
        trip.startOdometer?.let { binding.editStartOdometer.setText(it.toString()) }
        trip.endOdometer?.let { binding.editEndOdometer.setText(it.toString()) }
        trip.notes?.let { binding.editNotes.setText(it) }

        // Pre-select vehicle from spinner after vehicles are loaded
        setVehicleSelection()
        setPurposeSelection()
    }

    private fun populateDropdowns() {
        // Vehicles dropdown
        val vehicleNames = vehicles.map { "${it.make} ${it.model} (${it.licensePlate})" }
        val vehicleAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, vehicleNames)
        binding.spinnerVehicle.setAdapter(vehicleAdapter)
        binding.spinnerVehicle.setOnItemClickListener { _, _, position, _ ->
            selectedVehicleId = vehicles.getOrNull(position)?.id
        }

        // Purposes dropdown
        val purposeNames = purposes.map { it.name }
        val purposeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, purposeNames)
        binding.spinnerPurpose.setAdapter(purposeAdapter)
        binding.spinnerPurpose.setOnItemClickListener { _, _, position, _ ->
            selectedPurposeId = purposes.getOrNull(position)?.id
        }

        if (formPopulated) {
            setVehicleSelection()
            setPurposeSelection()
        }
    }

    private fun setVehicleSelection() {
        val vehicleId = selectedVehicleId ?: return
        val idx = vehicles.indexOfFirst { it.id == vehicleId }
        if (idx >= 0) {
            val v = vehicles[idx]
            binding.spinnerVehicle.setText("${v.make} ${v.model} (${v.licensePlate})", false)
        }
    }

    private fun setPurposeSelection() {
        val purposeId = selectedPurposeId ?: return
        val idx = purposes.indexOfFirst { it.id == purposeId }
        if (idx >= 0) {
            binding.spinnerPurpose.setText(purposes[idx].name, false)
        }
    }

    private fun applyValidationErrors(errors: Map<String, String>) {
        binding.layoutStartLocation.error = errors[TripValidator.FIELD_START_LOCATION]
        binding.layoutEndLocation.error = errors[TripValidator.FIELD_END_LOCATION]
        binding.layoutDistanceKm.error = errors[TripValidator.FIELD_DISTANCE]
        binding.layoutStartOdometer.error = errors[TripValidator.FIELD_ODOMETER]
        binding.layoutEndOdometer.error = errors[TripValidator.FIELD_ODOMETER]
        binding.layoutPurpose.error = errors[TripValidator.FIELD_PURPOSE_ID]
        binding.layoutVehicle.error = errors[TripValidator.FIELD_VEHICLE]
    }

    private fun clearErrors() {
        binding.layoutStartLocation.error = null
        binding.layoutEndLocation.error = null
        binding.layoutDistanceKm.error = null
        binding.layoutStartOdometer.error = null
        binding.layoutEndOdometer.error = null
        binding.layoutPurpose.error = null
        binding.layoutVehicle.error = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
