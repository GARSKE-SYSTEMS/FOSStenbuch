package de.fosstenbuch.ui.trips.ghost

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.fosstenbuch.R
import de.fosstenbuch.data.model.Trip
import de.fosstenbuch.databinding.ItemGhostTripBinding
import java.text.SimpleDateFormat
import java.util.Locale

class GhostTripAdapter(
    private val onItemClick: (Trip) -> Unit
) : ListAdapter<Trip, GhostTripAdapter.GhostTripViewHolder>(GhostTripDiffCallback()) {

    private val dateTimeFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GhostTripViewHolder {
        val binding = ItemGhostTripBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return GhostTripViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GhostTripViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class GhostTripViewHolder(
        private val binding: ItemGhostTripBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
        }

        fun bind(trip: Trip) {
            binding.textGhostDate.text = dateTimeFormat.format(trip.date)
            binding.textGhostRoute.text = itemView.context.getString(
                R.string.trip_route_format, trip.startLocation, trip.endLocation
            )

            val gpsKm = trip.gpsDistanceKm
            binding.textGhostDistance.text = if (gpsKm != null && gpsKm > 0.0) {
                itemView.context.getString(R.string.gps_distance_format, gpsKm)
            } else {
                itemView.context.getString(R.string.ghost_no_distance)
            }

            binding.textGhostEndTime.text = trip.endTime?.let { endTime ->
                itemView.context.getString(
                    R.string.ghost_duration_format,
                    dateTimeFormat.format(endTime)
                )
            } ?: ""
        }
    }

    class GhostTripDiffCallback : DiffUtil.ItemCallback<Trip>() {
        override fun areItemsTheSame(oldItem: Trip, newItem: Trip) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Trip, newItem: Trip) = oldItem == newItem
    }
}
