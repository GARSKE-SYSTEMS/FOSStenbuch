package de.fosstenbuch.ui.locations

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.fosstenbuch.data.model.SavedLocation
import de.fosstenbuch.databinding.ItemSavedLocationBinding
import java.util.Locale

class LocationAdapter(
    private val onLocationClick: (SavedLocation) -> Unit,
    private val onLocationLongClick: (SavedLocation) -> Boolean
) : ListAdapter<SavedLocation, LocationAdapter.LocationViewHolder>(LocationDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LocationViewHolder {
        val binding = ItemSavedLocationBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return LocationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LocationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class LocationViewHolder(
        private val binding: ItemSavedLocationBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onLocationClick(getItem(position))
                }
            }
            binding.root.setOnLongClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onLocationLongClick(getItem(position))
                } else false
            }
        }

        fun bind(location: SavedLocation) {
            binding.textLocationName.text = location.name
            binding.textUsageCount.text = "${location.usageCount}×"

            if (location.address != null) {
                binding.textLocationAddress.text = location.address
                binding.textLocationAddress.visibility = View.VISIBLE
            } else {
                binding.textLocationAddress.visibility = View.GONE
            }

            binding.textLocationCoords.text = String.format(
                Locale.GERMANY,
                "%.4f°%s, %.4f°%s",
                Math.abs(location.latitude),
                if (location.latitude >= 0) "N" else "S",
                Math.abs(location.longitude),
                if (location.longitude >= 0) "E" else "W"
            )
        }
    }

    class LocationDiffCallback : DiffUtil.ItemCallback<SavedLocation>() {
        override fun areItemsTheSame(oldItem: SavedLocation, newItem: SavedLocation): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: SavedLocation, newItem: SavedLocation): Boolean =
            oldItem == newItem
    }
}
