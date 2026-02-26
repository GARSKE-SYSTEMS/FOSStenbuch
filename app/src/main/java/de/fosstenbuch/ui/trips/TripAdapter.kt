package de.fosstenbuch.ui.trips

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.fosstenbuch.R
import de.fosstenbuch.data.model.Trip
import de.fosstenbuch.data.model.TripPurpose
import de.fosstenbuch.databinding.ItemTripBinding
import java.text.SimpleDateFormat
import java.util.Locale

class TripAdapter(
    private val onTripClick: (Trip) -> Unit
) : ListAdapter<Trip, TripAdapter.TripViewHolder>(TripDiffCallback()) {

    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)
    private var purposeMap: Map<Long, TripPurpose> = emptyMap()

    fun setPurposes(purposes: List<TripPurpose>) {
        purposeMap = purposes.associateBy { it.id }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TripViewHolder {
        val binding = ItemTripBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TripViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TripViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TripViewHolder(
        private val binding: ItemTripBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onTripClick(getItem(position))
                }
            }
        }

        fun bind(trip: Trip) {
            binding.textTripDate.text = dateFormat.format(trip.date)
            binding.textTripRoute.text = itemView.context.getString(
                R.string.trip_route_format, trip.startLocation, trip.endLocation
            )
            binding.textTripDistance.text = itemView.context.getString(
                R.string.distance_format, trip.distanceKm
            )
            binding.textTripPurpose.text = trip.purpose

            // Purpose category badge
            val purpose = trip.purposeId?.let { purposeMap[it] }
            if (purpose != null) {
                binding.textTripType.text = purpose.name
                try {
                    binding.textTripType.setBackgroundColor(Color.parseColor(purpose.color))
                } catch (_: IllegalArgumentException) {
                    binding.textTripType.setBackgroundColor(Color.GRAY)
                }
            } else {
                binding.textTripType.text = itemView.context.getString(R.string.no_category)
                binding.textTripType.setBackgroundColor(Color.GRAY)
            }

            // Cancelled indicator
            binding.textTripCancelled.visibility = if (trip.isCancelled) View.VISIBLE else View.GONE
        }
    }

    class TripDiffCallback : DiffUtil.ItemCallback<Trip>() {
        override fun areItemsTheSame(oldItem: Trip, newItem: Trip): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Trip, newItem: Trip): Boolean =
            oldItem == newItem
    }
}
