package dk.intraon.mink.trip

import android.app.AlertDialog
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import dk.intraon.mink.ui.MainActivity
import dk.intraon.mink.ui.dp

/** Dialogen bag Del-knappen. */
object TripShare {
    /**
     * Del en eller flere ture. Brugeren vælger ét trin: kun loggen, loggen med bølgeformen (IQ), eller det hele med
     * turens billeder. Kun de trin, turen har filer til, vises. Filerne kan pakkes i én zip-fil. Valgene huskes.
     */
    fun show(a: MainActivity, chosen: List<JsonlLog.Entry>, all: List<JsonlLog.Entry>, offerOlder: Boolean) {
        val sessions = chosen.mapNotNull { TripFiles.stamp(it.name) }
        val allStamps = all.mapNotNull { TripFiles.stamp(it.name) }
        val iq = JsonlLog.list(a, JsonlLog.IQ_SUFFIX).filter { TripFiles.stamp(it.name) in sessions }
        val pics = Export.pictures(a).filter { p -> sessions.any { s -> TripFiles.inTrip(p.name, s, TripFiles.nextAfter(s, allStamps)) } }
        fun filesAt(level: Int) = chosen +
            (if (level >= TripFiles.SHARE_WAVE) iq else emptyList()) + (if (level >= TripFiles.SHARE_ALL) pics else emptyList())
        fun label(level: Int): String {
            val what = when (level) {
                TripFiles.SHARE_LOG -> if (chosen.size == 1) "Kun loggen" else "Kun loggene"
                TripFiles.SHARE_WAVE -> "Log og bølgeform"
                else -> (if (iq.isEmpty()) "Log og " else "Det hele: log, bølgeform og ") + (if (pics.size == 1) "1 billede" else "${pics.size} billeder")
            }
            return "$what · ${TripFiles.size(filesAt(level).sumOf { it.bytes })}"
        }

        val levels = TripFiles.shareLevels(iq.isNotEmpty(), pics.isNotEmpty())
        val group = RadioGroup(a)
        for (level in levels) group.addView(RadioButton(a).apply { id = ID0 + level; text = label(level); textSize = 16f; minHeight = a.dp(44) })
        group.check(ID0 + TripFiles.shareLevel(a.settings.shareLevel, levels))
        val zipBox = CheckBox(a).apply { text = "Pak som én zip-fil"; textSize = 16f; minHeight = a.dp(44); isChecked = a.settings.shareZip }
        val box = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(a.dp(20), a.dp(8), a.dp(20), 0)
            addView(group); addView(zipBox)
        }
        val b = AlertDialog.Builder(a)
            .setTitle(if (chosen.size == 1) "Del turen ${TripFiles.title(chosen[0].name)}" else "Del ${chosen.size} ture")
            .setView(box)
            .setPositiveButton("Del") { _, _ ->
                val level = group.checkedRadioButtonId - ID0
                a.settings.shareLevel = level; a.settings.shareZip = zipBox.isChecked
                val files = filesAt(level)
                if (!zipBox.isChecked) { Export.share(a, files.map { it.uri }, if (files.size == 1) "text/plain" else "*/*"); return@setPositiveButton }
                a.toast("Pakker ${contents(chosen.size, files.count { it in iq }, files.count { it in pics })} …")
                val name = if (sessions.size == 1) "mink_${sessions[0]}.zip" else "mink_${sessions.size}_ture_${sessions.maxOrNull()}.zip"
                Thread {
                    val zip = Export.zip(a, name, files)
                    a.runOnUiThread {
                        if (zip == null) { a.toast("Zip-filen kunne ikke skrives"); return@runOnUiThread }
                        if (zip.unread.isNotEmpty()) a.toast("Kunne ikke læse ${zip.unread.joinToString(", ")}. Resten er pakket.")
                        Export.share(a, listOf(zip.uri), "application/zip")
                    }
                }.start()
            }
            .setNegativeButton("Annullér", null)
        if (offerOlder) b.setNeutralButton("Tidligere ture …") { _, _ -> a.sessionsDialog() }
        b.show()
    }

    /** Første id til dialogens valgknapper; trinnet lægges til. */
    private const val ID0 = 0x4D494E00

    /** "loggen, bølgeformen og 2 billeder": det, der kommer med i zippen, sagt i ord. */
    private fun contents(logs: Int, iq: Int, pics: Int): String {
        val parts = ArrayList<String>()
        parts.add(if (logs == 1) "loggen" else "$logs logge")
        if (iq > 0) parts.add(if (iq == 1) "bølgeformen" else "$iq bølgeformer")
        if (pics > 0) parts.add(if (pics == 1) "1 billede" else "$pics billeder")
        return if (parts.size == 1) parts[0] else parts.dropLast(1).joinToString(", ") + " og " + parts.last()
    }
}
