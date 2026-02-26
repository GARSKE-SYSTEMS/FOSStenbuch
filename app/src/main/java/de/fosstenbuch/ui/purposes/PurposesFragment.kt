package de.fosstenbuch.ui.purposes

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
import de.fosstenbuch.data.model.TripPurpose
import de.fosstenbuch.databinding.FragmentPurposesBinding
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class PurposesFragment : Fragment() {

    private var _binding: FragmentPurposesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PurposesViewModel by viewModels()
    private lateinit var purposeAdapter: PurposeAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPurposesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupFab()
        observeState()
    }

    private fun setupRecyclerView() {
        purposeAdapter = PurposeAdapter(
            onPurposeClick = { purpose ->
                val action = PurposesFragmentDirections
                    .actionPurposesToAddEditPurpose(purposeId = purpose.id)
                findNavController().navigate(action)
            },
            onPurposeLongClick = { purpose ->
                if (!purpose.isDefault) {
                    showDeleteConfirmation(purpose)
                }
                true
            }
        )
        binding.recyclerPurposes.apply {
            adapter = purposeAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun showDeleteConfirmation(purpose: TripPurpose) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_purpose_title)
            .setMessage(getString(R.string.delete_purpose_message, purpose.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.deletePurpose(purpose)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun setupFab() {
        binding.fabAddPurpose.setOnClickListener {
            val action = PurposesFragmentDirections
                .actionPurposesToAddEditPurpose(purposeId = 0L)
            findNavController().navigate(action)
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    Timber.d("PurposesUiState: loading=${state.isLoading}, purposes=${state.purposes.size}")

                    binding.progressBar.visibility =
                        if (state.isLoading) View.VISIBLE else View.GONE
                    binding.layoutEmpty.visibility =
                        if (state.isEmpty) View.VISIBLE else View.GONE
                    binding.recyclerPurposes.visibility =
                        if (!state.isLoading && !state.isEmpty) View.VISIBLE else View.GONE

                    purposeAdapter.submitList(state.purposes)

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
