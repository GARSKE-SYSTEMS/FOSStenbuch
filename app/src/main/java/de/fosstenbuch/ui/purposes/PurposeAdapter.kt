package de.fosstenbuch.ui.purposes

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.fosstenbuch.R
import de.fosstenbuch.data.model.TripPurpose
import de.fosstenbuch.databinding.ItemPurposeBinding

class PurposeAdapter(
    private val onPurposeClick: (TripPurpose) -> Unit,
    private val onPurposeLongClick: (TripPurpose) -> Boolean
) : ListAdapter<TripPurpose, PurposeAdapter.PurposeViewHolder>(PurposeDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PurposeViewHolder {
        val binding = ItemPurposeBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PurposeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PurposeViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PurposeViewHolder(
        private val binding: ItemPurposeBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onPurposeClick(getItem(position))
                }
            }
            binding.root.setOnLongClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onPurposeLongClick(getItem(position))
                } else false
            }
        }

        fun bind(purpose: TripPurpose) {
            binding.textPurposeName.text = purpose.name

            // Color indicator
            try {
                binding.viewColor.setBackgroundColor(Color.parseColor(purpose.color))
            } catch (_: IllegalArgumentException) {
                binding.viewColor.setBackgroundColor(Color.GRAY)
            }

            // Business/Private badge
            val context = itemView.context
            if (purpose.isBusinessRelevant) {
                binding.textPurposeBadge.text = context.getString(R.string.business)
                binding.textPurposeBadge.setBackgroundColor(Color.parseColor("#6200EE"))
            } else {
                binding.textPurposeBadge.text = context.getString(R.string.private_trip)
                binding.textPurposeBadge.setBackgroundColor(Color.parseColor("#018786"))
            }

            // Default indicator
            binding.textDefault.visibility = if (purpose.isDefault) View.VISIBLE else View.GONE
        }
    }

    class PurposeDiffCallback : DiffUtil.ItemCallback<TripPurpose>() {
        override fun areItemsTheSame(oldItem: TripPurpose, newItem: TripPurpose): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: TripPurpose, newItem: TripPurpose): Boolean =
            oldItem == newItem
    }
}
