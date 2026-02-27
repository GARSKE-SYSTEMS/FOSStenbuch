package de.fosstenbuch.ui.trips

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
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import de.fosstenbuch.R
import de.fosstenbuch.data.model.Trip
import de.fosstenbuch.databinding.FragmentTripsBinding
import de.fosstenbuch.domain.service.LocationTrackingService
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Locale

@AndroidEntryPoint
class TripsFragment : Fragment() {

    private var _binding: FragmentTripsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TripsViewModel by viewModels()
    private lateinit var tripAdapter: TripAdapter
    private val dateTimeFormat = SimpleDateFormat("HH:mm", Locale.GERMANY)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTripsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupFilterChips()
        setupSortDropdown()
        setupFab()
        observeState()
        observeGpsForBanner()
    }

    private fun setupRecyclerView() {
        tripAdapter = TripAdapter { trip ->
            val action = TripsFragmentDirections.actionTripsToAddEditTrip(tripId = trip.id)
            findNavController().navigate(action)
        }
        binding.recyclerTrips.apply {
            adapter = tripAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }

        // Swipe to delete
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                val trip = tripAdapter.currentList[position]
                showDeleteConfirmation(trip, position)
            }
        }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(binding.recyclerTrips)
    }

    private fun showDeleteConfirmation(trip: Trip, position: Int) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_trip_title)
            .setMessage(R.string.delete_trip_message)
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.deleteTrip(trip)
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                // Restore item in list
                tripAdapter.notifyItemChanged(position)
            }
            .setOnCancelListener {
                tripAdapter.notifyItemChanged(position)
            }
            .show()
    }

    private fun setupFilterChips() {
        binding.chipAll.setOnClickListener { viewModel.setFilter(TripFilter.ALL) }
        binding.chipBusiness.setOnClickListener { viewModel.setFilter(TripFilter.BUSINESS) }
        binding.chipPrivate.setOnClickListener { viewModel.setFilter(TripFilter.PRIVATE) }
    }

    private fun setupSortDropdown() {
        val sortOptions = resources.getStringArray(R.array.sort_options)
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, sortOptions)
        binding.spinnerSort.setAdapter(adapter)
        binding.spinnerSort.setText(sortOptions[0], false)

        binding.spinnerSort.setOnItemClickListener { _, _, position, _ ->
            val sort = when (position) {
                0 -> TripSort.DATE_DESC
                1 -> TripSort.DATE_ASC
                2 -> TripSort.DISTANCE_DESC
                3 -> TripSort.DISTANCE_ASC
                else -> TripSort.DATE_DESC
            }
            viewModel.setSort(sort)
        }
    }

    private fun setupFab() {
        binding.fabAddTrip.setOnClickListener {
            val action = TripsFragmentDirections.actionTripsToAddEditTrip(tripId = 0L)
            findNavController().navigate(action)
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    Timber.d("TripsUiState: loading=${state.isLoading}, trips=${state.trips.size}, empty=${state.isEmpty}, activeTrip=${state.activeTrip?.id}")

                    binding.progressBar.visibility =
                        if (state.isLoading) View.VISIBLE else View.GONE
                    binding.layoutEmpty.visibility =
                        if (state.isEmpty && !state.hasActiveTrip) View.VISIBLE else View.GONE
                    binding.recyclerTrips.visibility =
                        if (!state.isLoading && !state.isEmpty) View.VISIBLE else View.GONE

                    // Active trip banner
                    updateActiveTripBanner(state.activeTrip)

                    // Hide FAB when a trip is already active
                    binding.fabAddTrip.visibility =
                        if (state.hasActiveTrip) View.GONE else View.VISIBLE

                    tripAdapter.setPurposes(state.purposes)
                    tripAdapter.submitList(state.trips)

                    // Update filter chip selection
                    when (state.filter) {
                        TripFilter.ALL -> binding.chipAll.isChecked = true
                        TripFilter.BUSINESS -> binding.chipBusiness.isChecked = true
                        TripFilter.PRIVATE -> binding.chipPrivate.isChecked = true
                    }

                    state.error?.let { error ->
                        Snackbar.make(binding.root, error, Snackbar.LENGTH_LONG).show()
                        viewModel.clearError()
                    }
                }
            }
        }
    }

    private fun updateActiveTripBanner(activeTrip: Trip?) {
        if (activeTrip != null) {
            binding.cardActiveTrip.visibility = View.VISIBLE
            val startTime = dateTimeFormat.format(activeTrip.date)
            binding.textActiveTripInfo.text = getString(
                R.string.active_trip_banner_text,
                activeTrip.startLocation,
                startTime
            )
            // GPS distance will be updated by observeGpsForBanner
            binding.buttonEndTrip.setOnClickListener {
                val action = TripsFragmentDirections.actionTripsToAddEditTrip(tripId = activeTrip.id)
                findNavController().navigate(action)
            }
        } else {
            binding.cardActiveTrip.visibility = View.GONE
        }
    }

    private fun observeGpsForBanner() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                LocationTrackingService.gpsDistanceKm.collect { distanceKm ->
                    if (viewModel.uiState.value.hasActiveTrip) {
                        binding.textActiveTripGps.text = getString(
                            R.string.active_trip_gps_km,
                            distanceKm
                        )
                        binding.textActiveTripGps.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}