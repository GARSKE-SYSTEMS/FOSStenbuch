package de.fosstenbuch.ui.locations

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import de.fosstenbuch.data.model.SavedLocation
import de.fosstenbuch.databinding.FragmentAddEditLocationBinding
import de.fosstenbuch.ui.common.safePopBackStack
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale

@AndroidEntryPoint
class AddEditLocationFragment : Fragment() {

    private var _binding: FragmentAddEditLocationBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LocationDetailViewModel by viewModels()

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            getCurrentLocation()
        } else {
            Snackbar.make(binding.root, R.string.location_permission_denied, Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddEditLocationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupGetLocationButton()
        setupSaveButton()
        setupCommaToDotsWatcher()
        observeState()
    }

    private fun setupGetLocationButton() {
        binding.buttonGetLocation.setOnClickListener {
            if (hasLocationPermission()) {
                getCurrentLocation()
            } else {
                locationPermissionRequest.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
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
    private fun getCurrentLocation() {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        val cancellationToken = CancellationTokenSource()

        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            cancellationToken.token
        ).addOnSuccessListener { location ->
            if (location != null) {
                binding.editLatitude.setText(String.format(Locale.US, "%.6f", location.latitude))
                binding.editLongitude.setText(String.format(Locale.US, "%.6f", location.longitude))
            } else {
                Snackbar.make(binding.root, R.string.location_not_available, Snackbar.LENGTH_SHORT).show()
            }
        }.addOnFailureListener { e ->
            Timber.e(e, "Failed to get current location")
            Snackbar.make(binding.root, R.string.location_not_available, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun setupCommaToDotsWatcher() {
        val commaWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                s ?: return
                val text = s.toString()
                if (',' in text) {
                    val replaced = text.replace(',', '.')
                    s.replace(0, s.length, replaced)
                }
            }
        }
        binding.editLatitude.addTextChangedListener(commaWatcher)
        binding.editLongitude.addTextChangedListener(commaWatcher)
    }

    private fun setupSaveButton() {
        binding.buttonSave.setOnClickListener {
            val name = binding.editName.text.toString().trim()
            val address = binding.editAddress.text.toString().trim().ifEmpty { null }
            val businessPartner = binding.editBusinessPartner.text.toString().trim().ifEmpty { null }
            val latitude = binding.editLatitude.text.toString().toDoubleOrNull()
            val longitude = binding.editLongitude.text.toString().toDoubleOrNull()

            // Validation
            binding.layoutName.error = null
            binding.layoutLatitude.error = null
            binding.layoutLongitude.error = null

            var hasError = false
            if (name.isBlank()) {
                binding.layoutName.error = getString(R.string.error_name_required)
                hasError = true
            }
            if (latitude == null) {
                binding.layoutLatitude.error = getString(R.string.error_latitude_required)
                hasError = true
            }
            if (longitude == null) {
                binding.layoutLongitude.error = getString(R.string.error_longitude_required)
                hasError = true
            }

            if (hasError) return@setOnClickListener

            val existingLocation = viewModel.uiState.value.location
            val location = SavedLocation(
                id = existingLocation?.id ?: 0,
                name = name,
                latitude = latitude!!,
                longitude = longitude!!,
                address = address,
                usageCount = existingLocation?.usageCount ?: 0,
                businessPartner = businessPartner
            )
            viewModel.saveLocation(location)
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    state.location?.let { populateForm(it) }

                    binding.progressSaving.visibility =
                        if (state.isSaving) View.VISIBLE else View.GONE
                    binding.buttonSave.isEnabled = !state.isSaving

                    state.error?.let { error ->
                        Snackbar.make(binding.root, error, Snackbar.LENGTH_LONG).show()
                        viewModel.clearError()
                    }

                    if (state.savedSuccessfully) {
                        viewModel.onSaveConsumed()
                        safePopBackStack()
                    }
                }
            }
        }
    }

    private var formPopulated = false

    private fun populateForm(location: SavedLocation) {
        if (formPopulated) return
        formPopulated = true

        binding.editName.setText(location.name)
        binding.editAddress.setText(location.address ?: "")
        binding.editBusinessPartner.setText(location.businessPartner ?: "")
        binding.editLatitude.setText(String.format(Locale.US, "%.6f", location.latitude))
        binding.editLongitude.setText(String.format(Locale.US, "%.6f", location.longitude))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
