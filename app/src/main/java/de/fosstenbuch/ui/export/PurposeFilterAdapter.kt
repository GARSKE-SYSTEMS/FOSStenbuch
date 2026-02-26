package de.fosstenbuch.ui.export

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.fosstenbuch.data.model.TripPurpose
import de.fosstenbuch.databinding.ItemExportPurposeFilterBinding

class PurposeFilterAdapter(
    private val onCheckedChange: (Long, Boolean) -> Unit
) : ListAdapter<PurposeFilterAdapter.PurposeFilterItem, PurposeFilterAdapter.ViewHolder>(DiffCallback()) {

    data class PurposeFilterItem(
        val purpose: TripPurpose,
        val isChecked: Boolean
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemExportPurposeFilterBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemExportPurposeFilterBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: PurposeFilterItem) {
            binding.checkboxPurpose.text = item.purpose.name
            binding.checkboxPurpose.isChecked = item.isChecked

            try {
                binding.viewPurposeColor.setBackgroundColor(Color.parseColor(item.purpose.color))
            } catch (_: Exception) {
                binding.viewPurposeColor.setBackgroundColor(Color.GRAY)
            }

            val badgeText = if (item.purpose.isBusinessRelevant) "G" else "P"
            binding.textBadge.text = badgeText

            binding.checkboxPurpose.setOnCheckedChangeListener { _, isChecked ->
                onCheckedChange(item.purpose.id, isChecked)
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<PurposeFilterItem>() {
        override fun areItemsTheSame(oldItem: PurposeFilterItem, newItem: PurposeFilterItem): Boolean {
            return oldItem.purpose.id == newItem.purpose.id
        }

        override fun areContentsTheSame(oldItem: PurposeFilterItem, newItem: PurposeFilterItem): Boolean {
            return oldItem == newItem
        }
    }
}
