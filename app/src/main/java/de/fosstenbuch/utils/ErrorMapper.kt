package de.fosstenbuch.utils

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import timber.log.Timber
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject

/**
 * Maps technical exceptions to user-friendly German error messages.
 * Centralizes error handling to provide consistent messages across the app.
 */
class ErrorMapper @Inject constructor() {

    companion object {
        /**
         * Maps an exception to a user-facing German error message.
         * Also logs the technical details via Timber.
         */
        fun map(throwable: Throwable): String {
            Timber.e(throwable, "Error occurred: ${throwable::class.simpleName}")

            return when (throwable) {
                // Database errors
                is SQLiteConstraintException -> "Datenkonflikt: Ein Eintrag mit diesen Daten existiert bereits."
                is SQLiteFullException -> "Speicher voll: Es steht nicht genügend Speicherplatz zur Verfügung."
                is SQLiteException -> "Datenbankfehler: Die Daten konnten nicht gespeichert werden."

                // IO errors
                is IOException -> mapIOException(throwable)

                // Common runtime errors (NumberFormatException before IllegalArgumentException since it's a subclass)
                is NumberFormatException -> "Ungültiges Zahlenformat. Bitte überprüfen Sie Ihre Eingaben."
                is IllegalArgumentException -> "Ungültige Eingabe: ${throwable.localizedMessage ?: "Bitte überprüfen Sie Ihre Eingaben."}"
                is IllegalStateException -> "Unerwarteter Zustand: Bitte starten Sie die App neu."
                is SecurityException -> "Fehlende Berechtigung: Bitte erteilen Sie die erforderlichen Berechtigungen in den Einstellungen."
                is UnsupportedOperationException -> "Diese Aktion wird nicht unterstützt."
                is OutOfMemoryError -> "Zu wenig Arbeitsspeicher. Bitte schließen Sie andere Apps."
                is NullPointerException -> "Fehlende Daten. Bitte versuchen Sie es erneut."
                is ConcurrentModificationException -> "Gleichzeitiger Zugriff. Bitte versuchen Sie es erneut."

                // Fallback
                else -> "Ein unerwarteter Fehler ist aufgetreten. Bitte versuchen Sie es erneut."
            }
        }

        private fun mapIOException(exception: IOException): String {
            return when (exception) {
                is UnknownHostException -> "Keine Internetverbindung. Bitte überprüfen Sie Ihre Netzwerkeinstellungen."
                is SocketTimeoutException -> "Zeitüberschreitung. Bitte versuchen Sie es erneut."
                is java.io.FileNotFoundException -> "Datei nicht gefunden."
                else -> "Ein-/Ausgabefehler: ${exception.localizedMessage ?: "Bitte versuchen Sie es erneut."}"
            }
        }

        /**
         * Maps an exception for a specific context with a custom fallback message.
         */
        fun mapWithContext(throwable: Throwable, contextMessage: String): String {
            Timber.e(throwable, "Error in context: $contextMessage")
            return "$contextMessage: ${map(throwable)}"
        }
    }
}
