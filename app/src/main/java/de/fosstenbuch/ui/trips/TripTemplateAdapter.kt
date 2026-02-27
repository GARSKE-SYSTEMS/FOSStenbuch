package de.fosstenbuch.ui.trips

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.fosstenbuch.data.model.TripTemplate
import de.fosstenbuch.databinding.ItemTripTemplateBinding
import java.text.NumberFormat
import java.util.Locale

class TripTemplateAdapter(
    private val onTemplateClick: (TripTemplate) -> Unit,
    private val onDeleteClick: (TripTemplate) -> Unit
) : ListAdapter<TripTemplate, TripTemplateAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTripTemplateBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemTripTemplateBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val numberFormat = NumberFormat.getNumberInstance(Locale.GERMANY).apply {
            maximumFractionDigits = 1
        }

        fun bind(template: TripTemplate) {
            binding.textTemplateName.text = template.name
            binding.textTemplateRoute.text = "${template.startLocation} → ${template.endLocation}"
            binding.textTemplateDistance.text = "${numberFormat.format(template.distanceKm)} km"

            binding.root.setOnClickListener { onTemplateClick(template) }
            binding.buttonDeleteTemplate.setOnClickListener { onDeleteClick(template) }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<TripTemplate>() {
        override fun areItemsTheSame(oldItem: TripTemplate, newItem: TripTemplate) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: TripTemplate, newItem: TripTemplate) =
            oldItem == newItem
    }
}
