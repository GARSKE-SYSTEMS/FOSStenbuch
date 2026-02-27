package de.fosstenbuch.ui.common

import android.os.SystemClock
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.NavDirections
import androidx.navigation.fragment.findNavController
import timber.log.Timber

/**
 * Safe navigation extensions to prevent crashes from:
 * - Double-click / rapid taps causing duplicate navigation
 * - Navigation after fragment is detached
 */

private var lastNavigationTime = 0L
private const val NAVIGATION_DEBOUNCE_MS = 300L

/**
 * Navigates safely, preventing duplicate navigation and catching
 * IllegalArgumentException when the current destination has already changed.
 */
fun Fragment.safeNavigate(directions: NavDirections) {
    val now = SystemClock.elapsedRealtime()
    if (now - lastNavigationTime < NAVIGATION_DEBOUNCE_MS) return
    lastNavigationTime = now

    try {
        if (isAdded) {
            findNavController().navigate(directions)
        }
    } catch (e: IllegalArgumentException) {
        Timber.w(e, "Navigation failed — destination already changed")
    } catch (e: IllegalStateException) {
        Timber.w(e, "Navigation failed — fragment not attached")
    }
}

/**
 * Navigates to a destination ID safely.
 */
fun Fragment.safeNavigate(destinationId: Int) {
    val now = SystemClock.elapsedRealtime()
    if (now - lastNavigationTime < NAVIGATION_DEBOUNCE_MS) return
    lastNavigationTime = now

    try {
        if (isAdded) {
            findNavController().navigate(destinationId)
        }
    } catch (e: IllegalArgumentException) {
        Timber.w(e, "Navigation failed — destination already changed")
    } catch (e: IllegalStateException) {
        Timber.w(e, "Navigation failed — fragment not attached")
    }
}

/**
 * Pops back stack safely.
 */
fun Fragment.safePopBackStack(): Boolean {
    return try {
        if (isAdded) {
            findNavController().popBackStack()
        } else {
            false
        }
    } catch (e: IllegalStateException) {
        Timber.w(e, "popBackStack failed — fragment not attached")
        false
    }
}
