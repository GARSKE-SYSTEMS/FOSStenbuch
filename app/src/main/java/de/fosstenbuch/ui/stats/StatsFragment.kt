package de.fosstenbuch.ui.stats

import android.app.DatePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import de.fosstenbuch.R
import de.fosstenbuch.databinding.FragmentStatsBinding
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class StatsFragment : Fragment() {

    private var _binding: FragmentStatsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: StatsViewModel by viewModels()
    private lateinit var statsAdapter: StatsAdapter
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupFilterChips()
        setupYearSelector()
        setupMonthSelector()
        setupCustomDateRange()
        observeState()
    }

    private fun setupRecyclerView() {
        statsAdapter = StatsAdapter()
        binding.recyclerStats.adapter = statsAdapter
    }

    private fun setupFilterChips() {
        binding.chipYear.setOnClickListener {
            viewModel.setFilterMode(StatsFilterMode.YEAR)
        }
        binding.chipMonth.setOnClickListener {
            viewModel.setFilterMode(StatsFilterMode.MONTH)
        }
        binding.chipCustom.setOnClickListener {
            viewModel.setFilterMode(StatsFilterMode.CUSTOM)
        }
    }

    private fun setupYearSelector() {
        binding.buttonPreviousYear.setOnClickListener {
            viewModel.setYear(viewModel.uiState.value.selectedYear - 1)
        }
        binding.buttonNextYear.setOnClickListener {
            viewModel.setYear(viewModel.uiState.value.selectedYear + 1)
        }
    }

    private fun setupMonthSelector() {
        binding.buttonPreviousMonth.setOnClickListener {
            viewModel.previousMonth()
        }
        binding.buttonNextMonth.setOnClickListener {
            viewModel.nextMonth()
        }
    }

    private fun setupCustomDateRange() {
        binding.editDateFrom.setOnClickListener {
            val ctx = context ?: return@setOnClickListener
            val cal = Calendar.getInstance()
            cal.timeInMillis = viewModel.uiState.value.customDateFromMs
            DatePickerDialog(ctx, { _, year, month, day ->
                val selected = Calendar.getInstance()
                selected.set(year, month, day, 0, 0, 0)
                selected.set(Calendar.MILLISECOND, 0)
                viewModel.setCustomDateFrom(selected.timeInMillis)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
        binding.editDateTo.setOnClickListener {
            val ctx = context ?: return@setOnClickListener
            val cal = Calendar.getInstance()
            cal.timeInMillis = viewModel.uiState.value.customDateToMs
            DatePickerDialog(ctx, { _, year, month, day ->
                val selected = Calendar.getInstance()
                selected.set(year, month, day, 23, 59, 59)
                selected.set(Calendar.MILLISECOND, 999)
                viewModel.setCustomDateTo(selected.timeInMillis)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    Timber.d("StatsUiState: loading=${state.isLoading}, totalKm=${state.totalDistanceKm}")

                    // Filter mode visibility
                    updateFilterVisibility(state)

                    // Year label
                    binding.textYear.text = state.selectedYear.toString()

                    // Month label
                    val monthNames = resources.getStringArray(R.array.month_names)
                    binding.textMonth.text = monthNames[state.selectedMonth]

                    // Custom date range labels
                    binding.editDateFrom.setText(dateFormat.format(Date(state.customDateFromMs)))
                    binding.editDateTo.setText(dateFormat.format(Date(state.customDateToMs)))

                    // Loading
                    binding.progressLoading.visibility =
                        if (state.isLoading) View.VISIBLE else View.GONE

                    val hasData = state.tripCount > 0 || state.totalDistanceKm > 0.0

                    binding.layoutEmpty.visibility =
                        if (!state.isLoading && !hasData) View.VISIBLE else View.GONE
                    binding.recyclerStats.visibility =
                        if (!state.isLoading && hasData) View.VISIBLE else View.GONE

                    if (hasData) {
                        statsAdapter.submitList(buildStatItems(state))
                    }

                    // Error
                    state.error?.let { error ->
                        Snackbar.make(binding.root, error, Snackbar.LENGTH_LONG).show()
                        viewModel.clearError()
                    }
                }
            }
        }
    }

    private fun updateFilterVisibility(state: StatsUiState) {
        when (state.filterMode) {
            StatsFilterMode.YEAR -> {
                binding.chipYear.isChecked = true
                binding.layoutYearSelector.visibility = View.VISIBLE
                binding.layoutMonthSelector.visibility = View.GONE
                binding.layoutCustomRange.visibility = View.GONE
            }
            StatsFilterMode.MONTH -> {
                binding.chipMonth.isChecked = true
                binding.layoutYearSelector.visibility = View.VISIBLE
                binding.layoutMonthSelector.visibility = View.VISIBLE
                binding.layoutCustomRange.visibility = View.GONE
            }
            StatsFilterMode.CUSTOM -> {
                binding.chipCustom.isChecked = true
                binding.layoutYearSelector.visibility = View.GONE
                binding.layoutMonthSelector.visibility = View.GONE
                binding.layoutCustomRange.visibility = View.VISIBLE
            }
        }
    }

    private fun buildStatItems(state: StatsUiState): List<StatItem> {
        val items = mutableListOf<StatItem>()

        items.add(
            StatItem(
                label = getString(R.string.stats_total_distance),
                value = getString(R.string.stats_km_format, state.totalDistanceKm)
            )
        )

        items.add(
            StatItem(
                label = getString(R.string.stats_business_distance),
                value = getString(R.string.stats_km_format, state.businessDistanceKm),
                accentColor = Color.parseColor("#6200EE")
            )
        )
        items.add(
            StatItem(
                label = getString(R.string.stats_private_distance),
                value = getString(R.string.stats_km_format, state.privateDistanceKm),
                accentColor = Color.parseColor("#018786")
            )
        )

        val total = state.businessDistanceKm + state.privateDistanceKm
        if (total > 0) {
            items.add(
                StatItem(
                    label = getString(R.string.stats_business_percentage),
                    value = getString(R.string.stats_percent_format, state.businessPercentage),
                    accentColor = Color.parseColor("#6200EE")
                )
            )
        }

        items.add(
            StatItem(
                label = getString(R.string.stats_total_trips),
                value = getString(R.string.stats_count_format, state.tripCount)
            )
        )

        items.add(
            StatItem(
                label = getString(R.string.stats_average_distance),
                value = getString(R.string.stats_km_format, state.averageDistancePerTrip)
            )
        )

        return items
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}