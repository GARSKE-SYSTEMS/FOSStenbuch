package de.fosstenbuch.ui.common

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.Filter
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.withStarted
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import de.fosstenbuch.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar
import java.util.Date

// Kleine Helfer, die alle Bildschirme gemeinsam nutzen.

// ── Coroutinen & Flows ───────────────────────────────────────────────────────

/** Sammelt [flow], solange der Bildschirm sichtbar ist (stoppt automatisch, kein Leak) */
fun <T> Fragment.collect(flow: Flow<T>, action: suspend (T) -> Unit) {
    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { flow.collect { action(it) } }
    }
}

/** Startet eine Coroutine, die mit der Ansicht des Bildschirms endet */
fun Fragment.launch(block: suspend CoroutineScope.() -> Unit) = viewLifecycleOwner.lifecycleScope.launch(block = block)

// ── Navigation ───────────────────────────────────────────────────────────────

/** Bundle-Schlüssel für die Datensatz-ID eines Detail-Bildschirms (0 = neu) */
const val ARG_ID = "id"

/** Datensatz-ID, mit der dieser Bildschirm geöffnet wurde (0 = neu anlegen) */
val Fragment.argId: Long get() = arguments?.getLong(ARG_ID) ?: 0L

private var lastNavigation = 0L

/** Navigiert zu einem Ziel aus nav_graph.xml; ignoriert Doppel-Tipps und Navigation nach Verlassen */
fun Fragment.navigate(destination: Int, vararg args: Pair<String, Any?>) {
    val now = SystemClock.elapsedRealtime()
    if (now - lastNavigation < 400 || !isAdded) return
    lastNavigation = now
    runCatching { findNavController().navigate(destination, bundleOf(*args)) }
}

/**
 * Zurück zum vorherigen Bildschirm. Wartet ggf., bis der Bildschirm wieder sichtbar ist:
 * Nach onStop (z.B. Home direkt nach "Speichern") ignoriert Navigation das Zurück sonst stillschweigend.
 */
fun Fragment.close() {
    if (!isAdded) return
    lifecycleScope.launch { withStarted { if (isAdded) findNavController().navigateUp() } }
}

// ── Meldungen & Dialoge ──────────────────────────────────────────────────────

fun Context.toast(text: CharSequence) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
fun Fragment.toast(text: CharSequence) = requireContext().toast(text)
fun Fragment.toast(@StringRes text: Int) = toast(getString(text))

/** Sicherheitsabfrage; [onConfirm] läuft nur nach Bestätigung */
fun Fragment.confirm(title: String, message: String, positive: String, onConfirm: () -> Unit) {
    MaterialAlertDialogBuilder(requireContext())
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton(positive) { _, _ -> onConfirm() }
        // App-eigener Text: android.R.string.cancel wäre auf englischen Geräten "Cancel"
        .setNegativeButton(R.string.cancel, null)
        .show()
}

/** Auswahl aus einer Liste (Aktionsmenü, Suchtreffer, Gerät …); [onPick] bekommt den Index */
fun Fragment.choose(title: CharSequence, items: List<CharSequence>, onPick: (Int) -> Unit) {
    MaterialAlertDialogBuilder(requireContext())
        .setTitle(title)
        .setItems(items.toTypedArray()) { _, i -> onPick(i) }
        .setNegativeButton(R.string.cancel, null)
        .show()
}

/** Fragt einen kurzen Text ab (z.B. Stornogrund, Vorlagenname); [onOk] bekommt ihn getrimmt */
fun Fragment.askText(title: String, hint: String, positive: String, onOk: (String) -> Unit) {
    val input = TextInputEditText(requireContext())
    val padding = (24 * resources.displayMetrics.density).toInt()
    val layout = TextInputLayout(requireContext()).apply {
        this.hint = hint
        addView(input)
        setPadding(padding, padding / 2, padding, 0)
    }
    MaterialAlertDialogBuilder(requireContext())
        .setTitle(title)
        .setView(layout)
        .setPositiveButton(positive) { _, _ -> onOk(input.text.toString().trim()) }
        .setNegativeButton(R.string.cancel, null)
        .show()
}

// ── Berechtigungen & Farben ──────────────────────────────────────────────────

fun Context.granted(permission: String) = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

/** Farbwert für "#RRGGBB" (Kategoriefarbe), sonst null – alle Bildschirme zeigen damit dieselbe Farbe */
fun hexColor(text: String?): Int? =
    text?.takeIf { Regex("#[0-9A-Fa-f]{6}").matches(it) }?.let { it.substring(1).toInt(16) or 0xFF000000.toInt() }

// ── Listen ───────────────────────────────────────────────────────────────────

/**
 * Universeller RecyclerView-Adapter: Layout per ViewBinding, Befüllen per [bind].
 * [id] identifiziert ein Element (für Animationen bei Änderungen).
 */
class SimpleAdapter<T : Any, B : ViewBinding>(
    private val inflate: (LayoutInflater, ViewGroup, Boolean) -> B,
    id: (T) -> Any,
    private val bind: (B, T) -> Unit
) : ListAdapter<T, SimpleAdapter.Holder<B>>(object : DiffUtil.ItemCallback<T>() {
    override fun areItemsTheSame(a: T, b: T) = id(a) == id(b)
    @android.annotation.SuppressLint("DiffUtilEquals") // T sind data classes
    override fun areContentsTheSame(a: T, b: T) = a == b
}) {
    class Holder<B : ViewBinding>(val binding: B) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder<B>, position: Int) = bind(holder.binding, getItem(position))
}

// ── Formulare ────────────────────────────────────────────────────────────────

/** Eingabetext ohne Leerzeichen am Rand, null wenn leer */
fun EditText.textOrNull(): String? = text?.toString()?.trim()?.ifEmpty { null }

fun EditText.intOrNull(): Int? = textOrNull()?.toIntOrNull()

/** Kommazahl; akzeptiert "," und "." als Dezimaltrenner */
fun EditText.doubleOrNull(): Double? = textOrNull()?.replace(',', '.')?.toDoubleOrNull()

/**
 * Auswahlliste (Material "Exposed Dropdown"). Filtert nie – sonst zeigt die Liste nach
 * setText() nur noch den gewählten Eintrag. [selected] = vorausgewählter Index oder -1.
 */
fun AutoCompleteTextView.setDropdown(items: List<String>, selected: Int = -1, onSelected: (Int) -> Unit) {
    setAdapter(object : ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, items) {
        override fun getFilter() = object : Filter() {
            override fun performFiltering(c: CharSequence?) = FilterResults().apply { values = items; count = items.size }
            override fun publishResults(c: CharSequence?, r: FilterResults?) = notifyDataSetChanged()
        }
    })
    setText(items.getOrNull(selected) ?: "", false)
    setOnItemClickListener { _, _, position, _ -> onSelected(position) }
}

/** Datum und danach Uhrzeit wählen (Sekunden = 0) */
fun Fragment.pickDateTime(initial: Date, onPicked: (Date) -> Unit) {
    val cal = Calendar.getInstance().apply { time = initial }
    DatePickerDialog(requireContext(), { _, y, m, d ->
        TimePickerDialog(requireContext(), { _, h, min ->
            cal.set(y, m, d, h, min, 0)
            cal.set(Calendar.MILLISECOND, 0)
            onPicked(cal.time)
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
    }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
}

/** Nur ein Datum wählen; liefert 00:00 Uhr des gewählten Tages */
fun Fragment.pickDate(initial: Date, onPicked: (Date) -> Unit) {
    val cal = Calendar.getInstance().apply { time = initial }
    DatePickerDialog(requireContext(), { _, y, m, d ->
        cal.clear()
        cal.set(y, m, d)
        onPicked(cal.time)
    }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
}

// ── Teilen & Links ───────────────────────────────────────────────────────────

/** Teilt eine Datei aus dem Cache-Verzeichnis (Export, Backup) über den Android-Teilen-Dialog */
fun Context.shareFile(file: File, mimeType: String, title: String) {
    val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType(mimeType)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, file.name)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    startActivity(Intent.createChooser(send, title))
}

fun Context.openUrl(url: String) {
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
