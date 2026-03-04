package de.fosstenbuch.ui.trips.ghost

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import de.fosstenbuch.R
import de.fosstenbuch.data.model.Trip
import de.fosstenbuch.databinding.FragmentGhostTripsBinding
import de.fosstenbuch.ui.common.safeNavigate
import kotlinx.coroutines.launch

@AndroidEntryPoint
class GhostTripsFragment : Fragment() {

    private var _binding: FragmentGhostTripsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: GhostTripsViewModel by viewModels()
    private lateinit var adapter: GhostTripAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGhostTripsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeState()
    }

    private fun setupRecyclerView() {
        adapter = GhostTripAdapter { trip ->
            val action = GhostTripsFragmentDirections.actionGhostTripsToGhostTripDetail(tripId = trip.id)
            safeNavigate(action)
        }
        binding.recyclerGhostTrips.apply {
            this.adapter = this@GhostTripsFragment.adapter
            layoutManager = LinearLayoutManager(requireContext())
        }

        // Swipe left to delete
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) return
                val trip = adapter.currentList.getOrNull(position) ?: return
                // Restore item visually before showing the confirmation dialog
                adapter.submitList(adapter.currentList.toList())
                showDeleteConfirmation(trip)
            }
        }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(binding.recyclerGhostTrips)
    }

    private fun showDeleteConfirmation(trip: Trip) {
        if (!isAdded) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.ghost_discard_title)
            .setMessage(R.string.ghost_discard_message)
            .setPositiveButton(R.string.ghost_discard_confirm) { _, _ ->
                viewModel.deleteGhostTrip(trip)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressBar.visibility =
                        if (state.isLoading) View.VISIBLE else View.GONE
                    binding.layoutEmpty.visibility =
                        if (state.isEmpty) View.VISIBLE else View.GONE
                    binding.recyclerGhostTrips.visibility =
                        if (!state.isLoading && !state.isEmpty) View.VISIBLE else View.GONE

                    adapter.submitList(state.ghostTrips)

                    state.error?.let { error ->
                        Snackbar.make(binding.root, error, Snackbar.LENGTH_LONG).show()
                        viewModel.clearError()
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
