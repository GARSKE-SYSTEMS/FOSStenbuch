package de.fosstenbuch.ui.stats

import androidx.annotation.ColorInt

/**
 * UI model for a single statistics card.
 */
data class StatItem(
    val label: String,
    val value: String,
    @ColorInt val accentColor: Int? = null
)
