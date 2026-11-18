package dk.intraon.mink.trip

import android.app.AlertDialog
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import dk.intraon.mink.map.MapData
import dk.intraon.mink.map.MapExport
import dk.intraon.mink.ui.MainActivity
import dk.intraon.mink.ui.Ui
import dk.intraon.mink.ui.dp

/** Dialogen bag Del-knappen. */
object TripShare {
    /**
     * Del en eller flere ture. Brugeren krydser af, hvad der skal med: loggen, bølgeformen (IQ), turens billeder, et
     * billede af kortet over hver tur og en kortfil, som åbnes i en browser ([MapPage]). Det hele kan pakkes i én
     * zip-fil, og valgene huskes. Kun det, turene har, tilbydes. [today] siger, at [chosen] er dagens udelte ture,
     * så dialogen kan fortælle det. Når brugeren har valgt en app i delemenuen, og loggene var med, huskes turene
     * som delt.
     */
    fun show(a: MainActivity, chosen: List<JsonlLog.Entry>, all: List<JsonlLog.Entry>, today: Boolean = false) {
        val sessions = chosen.mapNotNull { TripFiles.stamp(it.name) }
        val allStamps = all.mapNotNull { TripFiles.stamp(it.name) }
        val iq = JsonlLog.list(a, JsonlLog.IQ_SUFFIX).filter { TripFiles.stamp(it.name) in sessions }
        // kortet over turen laves på ny ved delingen; et ældre fra en tidligere deling tælles ikke med
        val tripMaps = sessions.map { TripFiles.tripMapName(it) }
        val pics = Export.pictures(a).filter { p ->
            p.name !in tripMaps && sessions.any { s -> TripFiles.inTrip(p.name, s, TripFiles.nextAfter(s, allStamps)) }
        }
        val one = chosen.size == 1
        val parts = a.settings.shareParts
        fun size(files: List<JsonlLog.Entry>) = TripFiles.size(files.sumOf { it.bytes })
        fun box(label: String, on: Boolean) = CheckBox(a).apply { text = label; textSize = 16f; minHeight = a.dp(44); isChecked = on }
        val logBox = box("${if (one) "Loggen" else "Loggene"} · ${size(chosen)}", parts and TripFiles.PART_LOG != 0)
        val waveBox = if (iq.isEmpty()) null else box("Bølgeformen · ${size(iq)}", parts and TripFiles.PART_WAVE != 0)
        val picsBox = if (pics.isEmpty()) null else box(
            "${if (pics.size == 1) "1 billede" else "${pics.size} billeder"} fra ${if (one) "turen" else "turene"} · ${size(pics)}",
            parts and TripFiles.PART_PICTURES != 0,
        )
        val mapBox = box(if (one) "Et billede af kortet over turen" else "Et billede af kortet over hver tur", parts and TripFiles.PART_MAP != 0)
        val pageBox = box("Et kort, der kan åbnes i en browser. Baggrundskortet hentes fra nettet for det område, turen går igennem.", a.settings.shareMapPage)
        val zipBox = box("Pak som én zip-fil", a.settings.shareZip)
        val box = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(a.dp(20), a.dp(8), a.dp(20), 0)
            if (!one) addView(TextView(a).apply {
                val names = chosen.map { it.name }.sorted()
                text = if (today) "Ture fra i dag, der ikke er delt: ${inWords(names.map { TripFiles.clock(it) })}."
                else inWords(names.map { TripFiles.title(it) }) + "."
                textSize = 14f; setTextColor(Ui.DIM); setPadding(a.dp(6), 0, a.dp(6), a.dp(8))
            })
            for (b in listOfNotNull(logBox, waveBox, picsBox, mapBox, pageBox, zipBox)) addView(b)
        }
        AlertDialog.Builder(a)
            .setTitle(if (one) "Del turen ${TripFiles.title(chosen[0].name)}" else "Del ${chosen.size} ture" + if (today) " fra i dag" else "")
            .setView(box)
            .setPositiveButton("Del") { _, _ ->
                val zip = zipBox.isChecked
                // en del, turene ikke har, tilbydes ikke og beholder sit gemte valg
                var kept = parts
                for ((b, part) in listOf(logBox to TripFiles.PART_LOG, waveBox to TripFiles.PART_WAVE, picsBox to TripFiles.PART_PICTURES, mapBox to TripFiles.PART_MAP)) {
                    if (b != null) kept = if (b.isChecked) kept or part else kept and part.inv()
                }
                a.settings.shareParts = kept; a.settings.shareZip = zip; a.settings.shareMapPage = pageBox.isChecked
                val logs = if (logBox.isChecked) chosen else emptyList()
                val waves = if (waveBox?.isChecked == true) iq else emptyList()
                val photos = if (picsBox?.isChecked == true) pics else emptyList()
                if (logs.isEmpty() && waves.isEmpty() && photos.isEmpty() && !mapBox.isChecked && !pageBox.isChecked) {
                    a.toast("Der er ikke valgt noget at dele"); return@setPositiveButton
                }
                val picked = {
                    if (logs.isNotEmpty()) a.settings.sharedTrips = a.settings.sharedTrips.after(
                        chosen.mapNotNull { e -> TripFiles.stamp(e.name)?.let { it to e.bytes } }.toMap(), allStamps,
                    )
                }
                makePage(a, chosen, pics, pageBox.isChecked) { page ->
                    if (!mapBox.isChecked) { deliver(a, sessions, logs, waves, emptyList(), photos, page, zip, picked); return@makePage }
                    a.toast(if (one) "Laver kort over turen …" else "Laver kort over turene …")
                    makeMaps(a, chosen) { maps -> deliver(a, sessions, logs, waves, maps, photos, page, zip, picked) }
                }
            }
            .setNegativeButton("Annullér", null)
            .setNeutralButton("Flere ture …") { _, _ -> a.tripsDialog(chosen.map { it.name }) }
            .show()
    }

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
                if (made == null) a.toast("Kortfilen kunne ikke laves. Resten deles uden den.")
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
                    val target = points.targetHz()?.let { " · mål ${Ui.mhz(it)} MHz" } ?: ""
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

    /**
     * Sender filerne til Androids delemenu, enkeltvis eller pakket i én zip. [picked] kaldes, når brugeren har valgt
     * en app i menuen.
     */
    private fun deliver(
        a: MainActivity, sessions: List<String>, logs: List<JsonlLog.Entry>, waves: List<JsonlLog.Entry>,
        maps: List<JsonlLog.Entry>, pics: List<JsonlLog.Entry>, page: List<JsonlLog.Entry>, zip: Boolean, picked: () -> Unit,
    ) {
        val files = logs + waves + maps + pics + page
        if (files.isEmpty()) { a.toast("Der blev ikke noget at dele"); return }
        if (!zip) { Export.share(a, files.map { it.uri }, mime(files), picked = picked); return }
        a.toast("Pakker " + inWords(listOfNotNull(
            (if (logs.size == 1) "loggen" else "${logs.size} logge").takeIf { logs.isNotEmpty() },
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
                Export.share(a, listOf(packed.uri), "application/zip", picked = picked)
            }
        }.start()
    }

    /** Filtypen, delemenuen får at vide: den ene fils egen type, eller "blandet" for flere. */
    private fun mime(files: List<JsonlLog.Entry>): String {
        val name = files.singleOrNull()?.name ?: return "*/*"
        return when {
            name.endsWith(".png") -> "image/png"
            name.endsWith(".jpg") -> "image/jpeg"
            name.endsWith(".html") -> "text/html"
            name.endsWith(".wav") -> "audio/x-wav"
            else -> "text/plain"
        }
    }

    /** "log, bølgeform og kort": en opremsning, som man siger den. */
    private fun inWords(parts: List<String>): String =
        if (parts.size <= 1) parts.joinToString() else parts.dropLast(1).joinToString(", ") + " og " + parts.last()
}
