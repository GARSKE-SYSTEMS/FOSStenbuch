package de.fosstenbuch.ui.vehicles

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
import de.fosstenbuch.data.model.Vehicle
import de.fosstenbuch.databinding.FragmentVehiclesBinding
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class VehiclesFragment : Fragment() {

    private var _binding: FragmentVehiclesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: VehiclesViewModel by viewModels()
    private lateinit var vehicleAdapter: VehicleAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVehiclesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupFab()
        observeState()
    }

    private fun setupRecyclerView() {
        vehicleAdapter = VehicleAdapter(
            onVehicleClick = { vehicle ->
                val action = VehiclesFragmentDirections.actionVehiclesToAddEditVehicle(
                    vehicleId = vehicle.id
                )
                findNavController().navigate(action)
            },
            onVehicleLongClick = { vehicle ->
                showVehicleOptions(vehicle)
                true
            }
        )
        binding.recyclerVehicles.apply {
            adapter = vehicleAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun showVehicleOptions(vehicle: Vehicle) {
        val options = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        // Set as primary
        if (!vehicle.isPrimary) {
            options.add(getString(R.string.set_primary))
            actions.add { viewModel.setPrimaryVehicle(vehicle.id) }
        }

        // Delete (only if not audit protected)
        if (!vehicle.auditProtected) {
            options.add(getString(R.string.delete))
            actions.add { showDeleteConfirmation(vehicle) }
        }

        if (options.isNotEmpty()) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("${vehicle.make} ${vehicle.model}")
                .setItems(options.toTypedArray()) { _, which ->
                    actions[which]()
                }
                .show()
        }
    }

    private fun showDeleteConfirmation(vehicle: Vehicle) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_vehicle_title)
            .setMessage(R.string.delete_vehicle_message)
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.deleteVehicle(vehicle)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun setupFab() {
        binding.fabAddVehicle.setOnClickListener {
            val action = VehiclesFragmentDirections.actionVehiclesToAddEditVehicle(vehicleId = 0L)
            findNavController().navigate(action)
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    Timber.d("VehiclesUiState: loading=${state.isLoading}, vehicles=${state.vehicles.size}")

                    binding.progressBar.visibility =
                        if (state.isLoading) View.VISIBLE else View.GONE
                    binding.layoutEmpty.visibility =
                        if (state.isEmpty) View.VISIBLE else View.GONE
                    binding.recyclerVehicles.visibility =
                        if (!state.isLoading && !state.isEmpty) View.VISIBLE else View.GONE

                    vehicleAdapter.submitList(state.vehicles)

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
