package de.fosstenbuch.ui.trips

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.Filterable
import android.widget.TextView
import de.fosstenbuch.R
import de.fosstenbuch.data.model.SavedLocation

/**
 * An item that can appear in the location suggestions dropdown.
 * Either a saved location or an "Add new" action.
 */
sealed class LocationSuggestion {
    data class Saved(val location: SavedLocation) : LocationSuggestion() {
        override fun toString(): String = location.name
    }

    data class AddNew(val query: String) : LocationSuggestion() {
        override fun toString(): String = query
    }
}

/**
 * Adapter for location AutoCompleteTextView that searches saved locations
 * by name and address, and offers an "Add new location" option.
 */
class LocationSuggestionAdapter(
    context: Context,
    private val onAddNewClicked: (String) -> Unit
) : ArrayAdapter<LocationSuggestion>(context, android.R.layout.simple_dropdown_item_1line), Filterable {

    private var allLocations: List<SavedLocation> = emptyList()
    private var filteredSuggestions: List<LocationSuggestion> = emptyList()

    fun updateLocations(locations: List<SavedLocation>) {
        allLocations = locations
    }

    override fun getCount(): Int = filteredSuggestions.size

    override fun getItem(position: Int): LocationSuggestion? =
        filteredSuggestions.getOrNull(position)

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(android.R.layout.simple_dropdown_item_1line, parent, false)

        val item = getItem(position)
        val textView = view.findViewById<TextView>(android.R.id.text1)

        when (item) {
            is LocationSuggestion.Saved -> {
                val loc = item.location
                textView.text = if (loc.address != null) {
                    "${loc.name} — ${loc.address}"
                } else {
                    loc.name
                }
            }
            is LocationSuggestion.AddNew -> {
                textView.text = context.getString(R.string.add_new_location_suggestion, item.query)
            }
            null -> textView.text = ""
        }

        return view
    }

    override fun getFilter(): Filter = locationFilter

    private val locationFilter = object : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val query = constraint?.toString()?.trim()?.lowercase() ?: ""

            val matches = if (query.isEmpty()) {
                allLocations.map { LocationSuggestion.Saved(it) }
            } else {
                val saved = allLocations.filter { loc ->
                    loc.name.lowercase().contains(query) ||
                        (loc.address?.lowercase()?.contains(query) == true)
                }.map { LocationSuggestion.Saved(it) }

                // Add "Add new" option if the query doesn't exactly match an existing location name
                val exactMatch = allLocations.any { it.name.equals(query, ignoreCase = true) }
                if (!exactMatch && query.isNotEmpty()) {
                    saved + LocationSuggestion.AddNew(constraint.toString().trim())
                } else {
                    saved
                }
            }

            return FilterResults().apply {
                values = matches
                count = matches.size
            }
        }

        @Suppress("UNCHECKED_CAST")
        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            filteredSuggestions = (results?.values as? List<LocationSuggestion>) ?: emptyList()
            if (filteredSuggestions.isNotEmpty()) {
                notifyDataSetChanged()
            } else {
                notifyDataSetInvalidated()
            }
        }

        override fun convertResultToString(resultValue: Any?): CharSequence {
            return when (resultValue) {
                is LocationSuggestion.Saved -> resultValue.location.name
                is LocationSuggestion.AddNew -> resultValue.query
                else -> ""
            }
        }
    }
}
