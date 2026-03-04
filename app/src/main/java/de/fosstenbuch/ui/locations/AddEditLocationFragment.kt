package de.fosstenbuch.ui.locations

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import android.content.Context
import android.location.LocationManager
import android.os.CancellationSignal
import android.widget.LinearLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import de.fosstenbuch.R
import de.fosstenbuch.data.model.SavedLocation
import de.fosstenbuch.data.remote.NominatimResult
import de.fosstenbuch.data.remote.NominatimService
import de.fosstenbuch.databinding.FragmentAddEditLocationBinding
import de.fosstenbuch.ui.common.safePopBackStack
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class AddEditLocationFragment : Fragment() {

    private var _binding: FragmentAddEditLocationBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LocationDetailViewModel by viewModels()

    @Inject
    lateinit var nominatimService: NominatimService

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
        setupSearchAddressButton()
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

    private fun setupSearchAddressButton() {
        binding.buttonSearchAddress.setOnClickListener {
            showSearchAddressDialog()
        }
    }

    private fun showSearchAddressDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_search_address, null)

        val editSearch = dialogView.findViewById<EditText>(R.id.edit_search_query)
        val listResults = dialogView.findViewById<ListView>(R.id.list_search_results)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.progress_search)
        val textStatus = dialogView.findViewById<TextView>(R.id.text_search_status)

        // Pre-fill search field with address input if present
        val currentAddress = binding.editAddress.text.toString().trim()
        if (currentAddress.isNotEmpty()) {
            editSearch.setText(currentAddress)
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.search_address_title)
            .setView(dialogView)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.search_address_title, null)
            .create()

        dialog.show()

        // Override positive button to perform search without closing dialog
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val query = editSearch.text.toString().trim()
            if (query.isBlank()) return@setOnClickListener

            progressBar.visibility = View.VISIBLE
            textStatus.visibility = View.GONE
            listResults.visibility = View.GONE

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val results = nominatimService.search(query)

                    progressBar.visibility = View.GONE
                    if (results.isEmpty()) {
                        textStatus.text = getString(R.string.search_address_no_results)
                        textStatus.visibility = View.VISIBLE
                    } else {
                        val adapter = ArrayAdapter(
                            requireContext(),
                            android.R.layout.simple_list_item_1,
                            results.map { it.displayName }
                        )
                        listResults.adapter = adapter
                        listResults.visibility = View.VISIBLE

                        listResults.setOnItemClickListener { _, _, position, _ ->
                            applyNominatimResult(results[position])
                            dialog.dismiss()
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Nominatim search failed")
                    progressBar.visibility = View.GONE
                    textStatus.text = getString(R.string.search_address_error, e.localizedMessage ?: "")
                    textStatus.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun applyNominatimResult(result: NominatimResult) {
        binding.editLatitude.setText(String.format(Locale.US, "%.6f", result.latitude))
        binding.editLongitude.setText(String.format(Locale.US, "%.6f", result.longitude))

        // Also fill address field if it's empty
        if (binding.editAddress.text.isNullOrBlank()) {
            binding.editAddress.setText(result.displayName)
        }
    }

    @Suppress("MissingPermission")
    private fun getCurrentLocation() {
        val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val provider = when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
                LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
                LocationManager.NETWORK_PROVIDER
            else -> {
                Snackbar.make(binding.root, R.string.location_not_available, Snackbar.LENGTH_SHORT).show()
                return
            }
        }

        val callback = { location: android.location.Location? ->
            if (location != null) {
                binding.editLatitude.setText(String.format(Locale.US, "%.6f", location.latitude))
                binding.editLongitude.setText(String.format(Locale.US, "%.6f", location.longitude))
            } else {
                Snackbar.make(binding.root, R.string.location_not_available, Snackbar.LENGTH_SHORT).show()
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            locationManager.getCurrentLocation(
                provider,
                CancellationSignal(),
                requireActivity().mainExecutor,
                callback
            )
        } else {
            // API 26-29: use last known location as best-effort
            val lastKnown = locationManager.getLastKnownLocation(provider)
            callback(lastKnown)
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
