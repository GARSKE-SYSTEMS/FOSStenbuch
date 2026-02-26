package de.fosstenbuch.ui.settings

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
import dagger.hilt.android.AndroidEntryPoint
import de.fosstenbuch.R
import de.fosstenbuch.databinding.FragmentSettingsBinding
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by viewModels()

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
        setupVehicleManagement()
        setupLocationManagement()
        setupPurposeManagement()
        observeState()
    }

    private fun setupVehicleManagement() {
        binding.cardVehicles.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_vehicles)
        }
    }

    private fun setupLocationManagement() {
        binding.cardLocations.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_saved_locations)
        }
    }

    private fun setupPurposeManagement() {
        binding.cardPurposes.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_purposes)
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    Timber.d("SettingsUiState: vehicles=${state.vehicleCount}, trips=${state.tripCount}, locations=${state.locationCount}, purposes=${state.purposeCount}")
                    binding.textTripCount.text = getString(R.string.trip_count_format, state.tripCount)
                    binding.textVehicleCount.text = getString(R.string.vehicle_count_format, state.vehicleCount)
                    binding.textLocationCount.text = getString(R.string.location_count_format, state.locationCount)
                    binding.textPurposeCount.text = getString(R.string.purpose_count_format, state.purposeCount)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}