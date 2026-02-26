package de.fosstenbuch.ui.stats

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
import dagger.hilt.android.AndroidEntryPoint
import de.fosstenbuch.R
import de.fosstenbuch.databinding.FragmentStatsBinding
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class StatsFragment : Fragment() {

    private var _binding: FragmentStatsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: StatsViewModel by viewModels()
    private lateinit var statsAdapter: StatsAdapter

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
        setupYearSelector()
        observeState()
    }

    private fun setupRecyclerView() {
        statsAdapter = StatsAdapter()
        binding.recyclerStats.adapter = statsAdapter
    }

    private fun setupYearSelector() {
        binding.buttonPreviousYear.setOnClickListener {
            viewModel.setYear(viewModel.uiState.value.selectedYear - 1)
        }
        binding.buttonNextYear.setOnClickListener {
            viewModel.setYear(viewModel.uiState.value.selectedYear + 1)
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    Timber.d("StatsUiState: loading=${state.isLoading}, totalKm=${state.totalDistanceKm}")

                    binding.textYear.text = state.selectedYear.toString()

                    binding.progressLoading.visibility =
                        if (state.isLoading) View.VISIBLE else View.GONE

                    val hasData = state.tripCountCurrentYear > 0 ||
                        state.totalDistanceKm > 0.0

                    binding.layoutEmpty.visibility =
                        if (!state.isLoading && !hasData) View.VISIBLE else View.GONE
                    binding.recyclerStats.visibility =
                        if (!state.isLoading && hasData) View.VISIBLE else View.GONE

                    if (hasData) {
                        statsAdapter.submitList(buildStatItems(state))
                    }
                }
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

        state.distanceByType?.let { dist ->
            items.add(
                StatItem(
                    label = getString(R.string.stats_business_distance),
                    value = getString(R.string.stats_km_format, dist.businessDistanceKm),
                    accentColor = Color.parseColor("#6200EE")
                )
            )
            items.add(
                StatItem(
                    label = getString(R.string.stats_private_distance),
                    value = getString(R.string.stats_km_format, dist.privateDistanceKm),
                    accentColor = Color.parseColor("#018786")
                )
            )

            // Business percentage
            val total = dist.businessDistanceKm + dist.privateDistanceKm
            if (total > 0) {
                val percent = (dist.businessDistanceKm / total) * 100
                items.add(
                    StatItem(
                        label = getString(R.string.stats_business_percentage),
                        value = getString(R.string.stats_percent_format, percent),
                        accentColor = Color.parseColor("#6200EE")
                    )
                )
            }
        }

        items.add(
            StatItem(
                label = getString(R.string.stats_total_trips),
                value = getString(R.string.stats_count_format, state.tripCountCurrentYear)
            )
        )

        items.add(
            StatItem(
                label = getString(R.string.stats_trips_this_month),
                value = getString(R.string.stats_count_format, state.tripCountCurrentMonth)
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