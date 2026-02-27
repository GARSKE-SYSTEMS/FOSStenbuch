package de.fosstenbuch.ui.mileage

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import de.fosstenbuch.R
import de.fosstenbuch.databinding.FragmentMileageCalculatorBinding
import de.fosstenbuch.domain.usecase.mileage.CalculateMileageAllowanceUseCase
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

@AndroidEntryPoint
class MileageCalculatorFragment : Fragment() {

    private var _binding: FragmentMileageCalculatorBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MileageCalculatorViewModel by viewModels()
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale.GERMANY)
    private val numberFormat = NumberFormat.getNumberInstance(Locale.GERMANY).apply {
        maximumFractionDigits = 1
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMileageCalculatorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        observeState()
    }

    private fun setupListeners() {
        binding.buttonPreviousYear.setOnClickListener {
            viewModel.setYear(viewModel.uiState.value.selectedYear - 1)
        }

        binding.buttonNextYear.setOnClickListener {
            viewModel.setYear(viewModel.uiState.value.selectedYear + 1)
        }

        binding.buttonUseStats.setOnClickListener {
            viewModel.toggleStatsMode()
        }

        binding.editOneWayDistance.doAfterTextChanged {
            viewModel.setOneWayDistance(it?.toString() ?: "")
        }

        binding.editWorkingDays.doAfterTextChanged {
            viewModel.setWorkingDays(it?.toString() ?: "")
        }

        binding.buttonCalculate.setOnClickListener {
            viewModel.calculate()
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.textYear.text = state.selectedYear.toString()

                    // Stats info
                    val distance = state.businessDistanceFromStats ?: 0.0
                    binding.textStatsDistance.text = getString(
                        R.string.mileage_km_format, distance
                    )
                    binding.textStatsTripCount.text = getString(
                        R.string.trip_count_format,
                        state.businessTripCount
                    )

                    // Toggle stats mode UI
                    if (state.useStatsMode) {
                        binding.buttonUseStats.text = getString(R.string.mileage_one_way_distance)
                        binding.layoutOneWayDistance.visibility = View.GONE
                    } else {
                        binding.buttonUseStats.text = getString(R.string.mileage_use_stats)
                        binding.layoutOneWayDistance.visibility = View.VISIBLE
                    }

                    // Result
                    val result = state.result
                    if (result != null && result.totalAmount > 0) {
                        binding.cardResult.visibility = View.VISIBLE
                        binding.textResultTotal.text = currencyFormat.format(result.totalAmount)
                        binding.textResultStandard.text = getString(
                            R.string.mileage_rate_standard
                        ) + ": " + currencyFormat.format(result.standardAmount)
                        binding.textResultExtended.text = getString(
                            R.string.mileage_rate_extended
                        ) + ": " + currencyFormat.format(result.extendedAmount)
                        binding.textResultDetails.text = buildString {
                            append(numberFormat.format(result.oneWayDistanceKm))
                            append(" km × ")
                            append(result.workingDays)
                            append(" Tage")
                        }
                    } else if (result != null) {
                        binding.cardResult.visibility = View.VISIBLE
                        binding.textResultTotal.text = currencyFormat.format(0.0)
                        binding.textResultStandard.text = getString(R.string.mileage_no_data)
                        binding.textResultExtended.text = ""
                        binding.textResultDetails.text = ""
                    } else {
                        binding.cardResult.visibility = View.GONE
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
