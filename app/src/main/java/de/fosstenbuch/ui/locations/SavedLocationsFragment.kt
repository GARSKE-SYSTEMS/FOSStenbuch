package de.fosstenbuch.ui.locations

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import de.fosstenbuch.R
import de.fosstenbuch.data.model.SavedLocation
import de.fosstenbuch.databinding.FragmentSavedLocationsBinding
import de.fosstenbuch.ui.common.safeNavigate
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class SavedLocationsFragment : Fragment() {

    private var _binding: FragmentSavedLocationsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SavedLocationsViewModel by viewModels()
    private lateinit var locationAdapter: LocationAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSavedLocationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupFab()
        observeState()
    }

    private fun setupRecyclerView() {
        locationAdapter = LocationAdapter(
            onLocationClick = { location ->
                val action = SavedLocationsFragmentDirections
                    .actionSavedLocationsToAddEditLocation(locationId = location.id)
                safeNavigate(action)
            },
            onLocationLongClick = { location ->
                showDeleteConfirmation(location)
                true
            }
        )
        binding.recyclerLocations.apply {
            adapter = locationAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun showDeleteConfirmation(location: SavedLocation) {
        if (!isAdded) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_location_title)
            .setMessage(getString(R.string.delete_location_message, location.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.deleteLocation(location)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun setupFab() {
        binding.fabAddLocation.setOnClickListener {
            val action = SavedLocationsFragmentDirections
                .actionSavedLocationsToAddEditLocation(locationId = 0L)
            safeNavigate(action)
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    Timber.d("SavedLocationsUiState: loading=${state.isLoading}, locations=${state.locations.size}")

                    binding.progressBar.visibility =
                        if (state.isLoading) View.VISIBLE else View.GONE
                    binding.layoutEmpty.visibility =
                        if (state.isEmpty) View.VISIBLE else View.GONE
                    binding.recyclerLocations.visibility =
                        if (!state.isLoading && !state.isEmpty) View.VISIBLE else View.GONE

                    locationAdapter.submitList(state.locations)

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
