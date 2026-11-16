package dk.intraon.mink.trip

import android.app.AlertDialog
import dk.intraon.mink.ui.MainActivity

/** Dialogen bag Del-knappen. */
object TripShare {
    /**
     * Del en eller flere ture. Loggen er altid med; bølgeformen (IQ) og turens billeder kan vælges til, og det hele
     * kan pakkes i én zip-fil. Valgene huskes til næste gang.
     */
    fun show(a: MainActivity, chosen: List<JsonlLog.Entry>, all: List<JsonlLog.Entry>, offerOlder: Boolean) {
        val sessions = chosen.mapNotNull { TripFiles.stamp(it.name) }
        val allStamps = all.mapNotNull { TripFiles.stamp(it.name) }
        val iq = JsonlLog.list(a, JsonlLog.IQ_SUFFIX).filter { TripFiles.stamp(it.name) in sessions }
        val pics = Export.pictures(a).filter { p -> sessions.any { s -> TripFiles.inTrip(p.name, s, TripFiles.nextAfter(s, allStamps)) } }
        val items = arrayOf(
            if (iq.isEmpty()) "Bølgeform (IQ) · ingen gemt" else "Bølgeform (IQ) · ${TripFiles.size(iq.sumOf { it.bytes })}",
            if (pics.isEmpty()) "Billeder · ingen taget" else "Billeder · ${pics.size} stk., ${TripFiles.size(pics.sumOf { it.bytes })}",
            "Pak det hele i én zip-fil",
        )
        val on = booleanArrayOf(a.settings.shareIq, a.settings.sharePics, a.settings.shareZip)
        val b = AlertDialog.Builder(a)
            .setTitle(if (chosen.size == 1) "Del turen ${TripFiles.title(chosen[0].name)} – loggen er altid med" else "Del ${chosen.size} ture – loggene er altid med")
            .setMultiChoiceItems(items, on) { _, i, v -> on[i] = v }
            .setPositiveButton("Del") { _, _ ->
                a.settings.shareIq = on[0]; a.settings.sharePics = on[1]; a.settings.shareZip = on[2]
                val files = chosen + (if (on[0]) iq else emptyList()) + (if (on[1]) pics else emptyList())
                if (!on[2]) { Export.share(a, files.map { it.uri }, if (files.size == 1) "text/plain" else "*/*"); return@setPositiveButton }
                a.toast("Pakker ${files.size} filer …")
                val name = if (sessions.size == 1) "mink_${sessions[0]}.zip" else "mink_${sessions.size}_ture_${sessions.maxOrNull()}.zip"
                Thread {
                    val uri = Export.zip(a, name, files)
                    a.runOnUiThread {
                        if (uri == null) a.toast("Zip-filen kunne ikke skrives") else Export.share(a, listOf(uri), "application/zip")
                    }
                }.start()
            }
            .setNegativeButton("Annullér", null)
        if (offerOlder) b.setNeutralButton("Tidligere ture …") { _, _ -> a.sessionsDialog() }
        b.show()
    }
}
