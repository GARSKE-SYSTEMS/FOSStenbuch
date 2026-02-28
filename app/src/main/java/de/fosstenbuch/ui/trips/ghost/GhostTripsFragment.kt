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
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
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
