package de.fosstenbuch.ui.vehicles

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import de.fosstenbuch.data.model.Vehicle
import de.fosstenbuch.databinding.ItemVehicleBinding

class VehicleAdapter(
    private val onVehicleClick: (Vehicle) -> Unit,
    private val onVehicleLongClick: (Vehicle) -> Boolean
) : ListAdapter<Vehicle, VehicleAdapter.VehicleViewHolder>(VehicleDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VehicleViewHolder {
        val binding = ItemVehicleBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VehicleViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VehicleViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VehicleViewHolder(
        private val binding: ItemVehicleBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onVehicleClick(getItem(position))
                }
            }
            binding.root.setOnLongClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onVehicleLongClick(getItem(position))
                } else false
            }
        }

        fun bind(vehicle: Vehicle) {
            binding.textVehicleName.text = "${vehicle.make} ${vehicle.model}"
            binding.textVehiclePlate.text = vehicle.licensePlate
            binding.textVehicleFuel.text = vehicle.fuelType
            binding.iconPrimary.visibility = if (vehicle.isPrimary) View.VISIBLE else View.GONE
            binding.iconAuditProtected.visibility =
                if (vehicle.auditProtected) View.VISIBLE else View.GONE
        }
    }

    class VehicleDiffCallback : DiffUtil.ItemCallback<Vehicle>() {
        override fun areItemsTheSame(oldItem: Vehicle, newItem: Vehicle): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Vehicle, newItem: Vehicle): Boolean =
            oldItem == newItem
    }
}
