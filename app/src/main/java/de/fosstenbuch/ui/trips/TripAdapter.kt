package de.fosstenbuch.ui.trips

import android.graphics.Color
import android.graphics.Paint
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
    private val onTripClick: (Trip) -> Unit,
    private val onTripLongClick: (Trip) -> Boolean = { false }
) : ListAdapter<Trip, TripAdapter.TripViewHolder>(TripDiffCallback()) {

    private val dateTimeFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY)
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
            binding.root.setOnLongClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onTripLongClick(getItem(position))
                } else false
            }
        }

        fun bind(trip: Trip) {
            binding.textTripDate.text = dateTimeFormat.format(trip.date)

            // Route: show "→ ..." for active trips without end location
            if (trip.isActive) {
                binding.textTripRoute.text = itemView.context.getString(
                    R.string.trip_route_format, trip.startLocation, "..."
                )
            } else {
                binding.textTripRoute.text = itemView.context.getString(
                    R.string.trip_route_format, trip.startLocation, trip.endLocation
                )
            }

            binding.textTripDistance.text = itemView.context.getString(
                R.string.distance_format, trip.distanceKm
            )
            binding.textTripPurpose.text = trip.purpose

            // Active badge
            binding.textTripActive.visibility = if (trip.isActive) View.VISIBLE else View.GONE

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

            // Cancelled indicator + strikethrough
            if (trip.isCancelled) {
                binding.textTripCancelled.visibility = View.VISIBLE
                binding.textTripRoute.paintFlags = binding.textTripRoute.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                binding.textTripDistance.paintFlags = binding.textTripDistance.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                binding.textTripPurpose.paintFlags = binding.textTripPurpose.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                binding.textTripRoute.alpha = 0.5f
                binding.textTripDistance.alpha = 0.5f
                binding.textTripPurpose.alpha = 0.5f
                // Show cancellation reason if available
                if (!trip.cancellationReason.isNullOrBlank()) {
                    binding.textTripCancelled.text = itemView.context.getString(
                        R.string.cancel_trip_reason_format, trip.cancellationReason
                    )
                } else {
                    binding.textTripCancelled.text = itemView.context.getString(R.string.trip_cancelled)
                }
            } else {
                binding.textTripCancelled.visibility = View.GONE
                binding.textTripRoute.paintFlags = binding.textTripRoute.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                binding.textTripDistance.paintFlags = binding.textTripDistance.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                binding.textTripPurpose.paintFlags = binding.textTripPurpose.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                binding.textTripRoute.alpha = 1.0f
                binding.textTripDistance.alpha = 1.0f
                binding.textTripPurpose.alpha = 1.0f
            }
        }
    }

    class TripDiffCallback : DiffUtil.ItemCallback<Trip>() {
        override fun areItemsTheSame(oldItem: Trip, newItem: Trip): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Trip, newItem: Trip): Boolean =
            oldItem == newItem
    }
}
