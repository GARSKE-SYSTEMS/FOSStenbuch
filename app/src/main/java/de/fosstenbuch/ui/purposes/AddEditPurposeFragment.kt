package de.fosstenbuch.ui.purposes

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import de.fosstenbuch.R
import de.fosstenbuch.data.model.TripPurpose
import de.fosstenbuch.databinding.FragmentAddEditPurposeBinding
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AddEditPurposeFragment : Fragment() {

    private var _binding: FragmentAddEditPurposeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PurposeDetailViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddEditPurposeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupColorPreview()
        setupSaveButton()
        observeState()

        // Set default color
        if (binding.editColor.text.isNullOrEmpty()) {
            binding.editColor.setText("#6200EE")
        }
    }

    private fun setupColorPreview() {
        binding.editColor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                try {
                    val color = Color.parseColor(s.toString())
                    binding.viewColorPreview.setBackgroundColor(color)
                } catch (_: Exception) {
                    // Invalid color, keep current preview
                }
            }
        })
    }

    private fun setupSaveButton() {
        binding.buttonSave.setOnClickListener {
            binding.layoutName.error = null

            val name = binding.editName.text.toString().trim()
            if (name.isBlank()) {
                binding.layoutName.error = getString(R.string.error_name_required)
                return@setOnClickListener
            }

            val existingPurpose = viewModel.uiState.value.purpose
            val purpose = TripPurpose(
                id = existingPurpose?.id ?: 0,
                name = name,
                isBusinessRelevant = binding.switchBusinessRelevant.isChecked,
                color = binding.editColor.text.toString().trim().ifEmpty { "#6200EE" },
                isDefault = existingPurpose?.isDefault ?: false
            )
            viewModel.savePurpose(purpose)
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    state.purpose?.let { populateForm(it) }

                    binding.progressSaving.visibility =
                        if (state.isSaving) View.VISIBLE else View.GONE
                    binding.buttonSave.isEnabled = !state.isSaving

                    state.error?.let { error ->
                        Snackbar.make(binding.root, error, Snackbar.LENGTH_LONG).show()
                        viewModel.clearError()
                    }

                    if (state.savedSuccessfully) {
                        viewModel.onSaveConsumed()
                        findNavController().popBackStack()
                    }
                }
            }
        }
    }

    private var formPopulated = false

    private fun populateForm(purpose: TripPurpose) {
        if (formPopulated) return
        formPopulated = true

        binding.editName.setText(purpose.name)
        binding.switchBusinessRelevant.isChecked = purpose.isBusinessRelevant
        binding.editColor.setText(purpose.color)

        // Disable name editing for default purposes
        if (purpose.isDefault) {
            binding.editName.isEnabled = false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
