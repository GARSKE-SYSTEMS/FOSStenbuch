package de.fosstenbuch.ui.stats

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.fosstenbuch.databinding.ItemStatCardBinding

class StatsAdapter : ListAdapter<StatItem, StatsAdapter.StatViewHolder>(StatDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StatViewHolder {
        val binding = ItemStatCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return StatViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StatViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class StatViewHolder(
        private val binding: ItemStatCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: StatItem) {
            binding.textStatLabel.text = item.label
            binding.textStatValue.text = item.value

            if (item.accentColor != null) {
                binding.viewAccent.visibility = View.VISIBLE
                val drawable = binding.viewAccent.background as? GradientDrawable
                    ?: GradientDrawable()
                drawable.setColor(item.accentColor)
                binding.viewAccent.background = drawable
            } else {
                binding.viewAccent.visibility = View.GONE
            }
        }
    }

    private class StatDiffCallback : DiffUtil.ItemCallback<StatItem>() {
        override fun areItemsTheSame(oldItem: StatItem, newItem: StatItem): Boolean {
            return oldItem.label == newItem.label
        }

        override fun areContentsTheSame(oldItem: StatItem, newItem: StatItem): Boolean {
            return oldItem == newItem
        }
    }
}
