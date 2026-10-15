package de.fosstenbuch.logic

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Deutsche Anzeigeformate für Datum, Uhrzeit, Kilometer und Euro (UI, Export, Änderungsprotokoll) */
object Fmt {
    fun date(d: Date): String = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(d)
    fun dateTime(d: Date): String = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(d)
    fun time(d: Date): String = SimpleDateFormat("HH:mm", Locale.GERMANY).format(d)
    /** z.B. "1.234,5 km" */
    fun km(km: Double): String = String.format(Locale.GERMANY, "%,.1f km", km)
    /** z.B. "1.234,56 €" */
    fun euro(value: Double): String = String.format(Locale.GERMANY, "%,.2f €", value)
}
