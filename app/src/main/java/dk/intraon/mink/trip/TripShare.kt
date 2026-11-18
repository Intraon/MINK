package dk.intraon.mink.trip

import android.app.AlertDialog
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import dk.intraon.mink.map.MapData
import dk.intraon.mink.map.MapExport
import dk.intraon.mink.ui.MainActivity
import dk.intraon.mink.ui.Ui
import dk.intraon.mink.ui.dp

/** Dialogen bag Del-knappen. */
object TripShare {
    /**
     * Del en eller flere ture. Brugeren vælger ét trin: kun loggen, loggen med bølgeformen (IQ), eller det hele med
     * et kort over turen og turens billeder. Der kan lægges en kortfil ved, som åbnes i en browser ([MapPage]), og
     * filerne kan pakkes i én zip-fil. Valgene huskes.
     */
    fun show(a: MainActivity, chosen: List<JsonlLog.Entry>, all: List<JsonlLog.Entry>, offerOlder: Boolean) {
        val sessions = chosen.mapNotNull { TripFiles.stamp(it.name) }
        val allStamps = all.mapNotNull { TripFiles.stamp(it.name) }
        val iq = JsonlLog.list(a, JsonlLog.IQ_SUFFIX).filter { TripFiles.stamp(it.name) in sessions }
        // kortet over turen laves på ny ved delingen; et ældre fra en tidligere deling tælles ikke med
        val tripMaps = sessions.map { TripFiles.tripMapName(it) }
        val pics = Export.pictures(a).filter { p ->
            p.name !in tripMaps && sessions.any { s -> TripFiles.inTrip(p.name, s, TripFiles.nextAfter(s, allStamps)) }
        }
        fun filesAt(level: Int) = chosen +
            (if (level >= TripFiles.SHARE_WAVE) iq else emptyList()) + (if (level >= TripFiles.SHARE_ALL) pics else emptyList())
        fun label(level: Int): String {
            val what = when (level) {
                TripFiles.SHARE_LOG -> if (chosen.size == 1) "Kun loggen" else "Kun loggene"
                TripFiles.SHARE_WAVE -> "Log og bølgeform"
                else -> "Det hele: " + inWords(listOfNotNull(
                    "log", "bølgeform".takeIf { iq.isNotEmpty() }, "kort",
                    (if (pics.size == 1) "1 billede" else "${pics.size} billeder").takeIf { pics.isNotEmpty() },
                ))
            }
            return "$what · ${TripFiles.size(filesAt(level).sumOf { it.bytes })}"
        }

        val levels = TripFiles.shareLevels(iq.isNotEmpty())
        val group = RadioGroup(a)
        for (level in levels) group.addView(RadioButton(a).apply { id = ID0 + level; text = label(level); textSize = 16f; minHeight = a.dp(44) })
        group.check(ID0 + TripFiles.shareLevel(a.settings.shareLevel, levels))
        val zipBox = CheckBox(a).apply { text = "Pak som én zip-fil"; textSize = 16f; minHeight = a.dp(44); isChecked = a.settings.shareZip }
        val pageBox = CheckBox(a).apply {
            text = "Læg et kort ved, der kan åbnes i en browser. Baggrundskortet hentes fra nettet for det område, turen går igennem."
            textSize = 16f; minHeight = a.dp(44); isChecked = a.settings.shareMapPage
        }
        val box = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(a.dp(20), a.dp(8), a.dp(20), 0)
            addView(group); addView(pageBox); addView(zipBox)
        }
        val b = AlertDialog.Builder(a)
            .setTitle(if (chosen.size == 1) "Del turen ${TripFiles.title(chosen[0].name)}" else "Del ${chosen.size} ture")
            .setView(box)
            .setPositiveButton("Del") { _, _ ->
                val level = group.checkedRadioButtonId - ID0
                val zip = zipBox.isChecked
                a.settings.shareLevel = level; a.settings.shareZip = zip; a.settings.shareMapPage = pageBox.isChecked
                val waves = if (level >= TripFiles.SHARE_WAVE) iq else emptyList()
                makePage(a, chosen, pics, pageBox.isChecked) { page ->
                    if (level < TripFiles.SHARE_ALL) { deliver(a, sessions, chosen, waves, emptyList(), emptyList(), page, zip); return@makePage }
                    a.toast(if (chosen.size == 1) "Laver kort over turen …" else "Laver kort over turene …")
                    makeMaps(a, chosen) { maps -> deliver(a, sessions, chosen, waves, maps, pics, page, zip) }
                }
            }
            .setNegativeButton("Annullér", null)
        if (offerOlder) b.setNeutralButton("Tidligere ture …") { _, _ -> a.sessionsDialog() }
        b.show()
    }

    /** Første id til dialogens valgknapper; trinnet lægges til. */
    private const val ID0 = 0x4D494E00

    /**
     * Laver kortfilen, hvis brugeren har bedt om den, og kalder [then] på hovedtråden med den (eller en tom liste).
     * Pejlingernes fotos lægges i filen, også når billederne ellers ikke deles.
     */
    private fun makePage(a: MainActivity, trips: List<JsonlLog.Entry>, pics: List<JsonlLog.Entry>, wanted: Boolean, then: (List<JsonlLog.Entry>) -> Unit) {
        if (!wanted) { then(emptyList()); return }
        a.toast("Laver kortfilen …")
        Thread {
            val made = MapPageExport.make(a, trips, pics)
            a.runOnUiThread {
                if (made == null) a.toast("Kortfilen kunne ikke skrives")
                else if (!made.background) a.toast("Kortdata kunne ikke hentes, så kortfilen er uden baggrundskort.")
                then(listOfNotNull(made?.file))
            }
        }.start()
    }

    /**
     * Laver et kort over hver tur og gemmer det i galleriet, én tur ad gangen. [then] kaldes på hovedtråden med de
     * kort, der blev til noget; en tur uden steder får intet kort.
     */
    private fun makeMaps(a: MainActivity, trips: List<JsonlLog.Entry>, then: (List<JsonlLog.Entry>) -> Unit) {
        val made = ArrayList<JsonlLog.Entry>()
        fun next(i: Int) {
            if (i >= trips.size) { then(made); return }
            val trip = trips[i]
            val session = TripFiles.stamp(trip.name)
            if (session == null) { next(i + 1); return }
            Thread {
                val points = MapData.read(a, listOf(trip.uri))
                a.runOnUiThread {
                    val target = points.bursts.firstNotNullOfOrNull { it.freqHz }?.let { " · mål ${Ui.mhz(it)} MHz" } ?: ""
                    val caption = "MINK · tur ${TripFiles.title(trip.name)}$target · ${points.bursts.size} målinger"
                    MapExport.tripPicture(a, points, caption) { bmp ->
                        if (bmp == null) { next(i + 1); return@tripPicture }
                        Thread {
                            val saved = MapExport.save(a, bmp, TripFiles.tripMapName(session), replace = true)
                            a.runOnUiThread { if (saved != null) made.add(saved); next(i + 1) }
                        }.start()
                    }
                }
            }.start()
        }
        next(0)
    }

    /** Sender filerne til Androids delemenu, enkeltvis eller pakket i én zip. */
    private fun deliver(
        a: MainActivity, sessions: List<String>, logs: List<JsonlLog.Entry>, waves: List<JsonlLog.Entry>,
        maps: List<JsonlLog.Entry>, pics: List<JsonlLog.Entry>, page: List<JsonlLog.Entry>, zip: Boolean,
    ) {
        val files = logs + waves + maps + pics + page
        if (!zip) { Export.share(a, files.map { it.uri }, if (files.size == 1) "text/plain" else "*/*"); return }
        a.toast("Pakker " + inWords(listOfNotNull(
            if (logs.size == 1) "loggen" else "${logs.size} logge",
            (if (waves.size == 1) "bølgeformen" else "${waves.size} bølgeformer").takeIf { waves.isNotEmpty() },
            (if (maps.size == 1) "kortet" else "${maps.size} kort").takeIf { maps.isNotEmpty() },
            "kortfilen".takeIf { page.isNotEmpty() },
            (if (pics.size == 1) "1 billede" else "${pics.size} billeder").takeIf { pics.isNotEmpty() },
        )) + " …")
        val name = if (sessions.size == 1) "mink_${sessions[0]}.zip" else "mink_${sessions.size}_ture_${sessions.maxOrNull()}.zip"
        Thread {
            val packed = Export.zip(a, name, files)
            a.runOnUiThread {
                if (packed == null) { a.toast("Zip-filen kunne ikke skrives"); return@runOnUiThread }
                if (packed.unread.isNotEmpty()) a.toast("Kunne ikke læse ${packed.unread.joinToString(", ")}. Resten er pakket.")
                Export.share(a, listOf(packed.uri), "application/zip")
            }
        }.start()
    }

    /** "log, bølgeform og kort": en opremsning, som man siger den. */
    private fun inWords(parts: List<String>): String =
        if (parts.size <= 1) parts.joinToString() else parts.dropLast(1).joinToString(", ") + " og " + parts.last()
}
