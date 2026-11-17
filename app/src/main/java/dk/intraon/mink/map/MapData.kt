package dk.intraon.mink.map

import android.content.Context
import android.net.Uri
import dk.intraon.mink.core.Hub
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import org.json.JSONObject

/** Tidligere ture, der er lagt ind på kortet. Vises sammen med den tur, der kører. */
object MapData {
    val bursts = CopyOnWriteArrayList<Hub.Point>()
    val silent = CopyOnWriteArrayList<Hub.Point>()
    val marks = CopyOnWriteArrayList<Hub.Mark>()
    val bearings = CopyOnWriteArrayList<Hub.BearingLine>()
    @Volatile var sessions = 0

    fun clear() {
        bursts.clear(); silent.clear(); marks.clear(); bearings.clear(); sessions = 0
    }

    /** Læser logfilerne ind på kortet (kald fra en baggrundstråd). Returnerer antal målinger med position. */
    fun load(ctx: Context, uris: List<Uri>): Int {
        val t = read(ctx, uris)
        bursts.clear(); bursts.addAll(t.bursts)
        silent.clear(); silent.addAll(t.silent)
        marks.clear(); marks.addAll(t.marks)
        bearings.clear(); bearings.addAll(t.bearings)
        sessions = uris.size
        return t.bursts.size
    }

    /**
     * Læser logfilerne uden at røre det, kortet viser (kald fra en baggrundstråd). En fil, der ikke kan læses,
     * springes over.
     */
    fun read(ctx: Context, uris: List<Uri>): TripPoints = parse(uris.map { u ->
        try { ctx.contentResolver.openInputStream(u)?.bufferedReader()?.use { it.readLines() } ?: emptyList() } catch (_: Exception) { emptyList() }
    })

    /** Finder kortets punkter i logfilernes linjer; én liste af linjer pr. fil. Ren Kotlin. */
    fun parse(files: List<List<String>>): TripPoints {
        val b = ArrayList<Hub.Point>(); val s = ArrayList<Hub.Point>(); val m = ArrayList<Hub.Mark>(); val br = ArrayList<Hub.BearingLine>()
        val srcOf = ArrayList<Int>()        // kildenummer for hver måling i b (0 = ældre log uden numre)
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
        fun time(o: JSONObject) = try { fmt.parse(o.optString("t"))?.time ?: 0L } catch (_: Exception) { 0L }
        for (lines in files) {
            val fileStart = b.size
            for (line in lines) {
                val o = try { JSONObject(line) } catch (_: Exception) { continue }
                if (o.isNull("lat") || o.isNull("lon")) continue
                val lat = o.getDouble("lat"); val lon = o.getDouble("lon")
                when (o.optString("type")) {
                    // nyere logfiler mærker målets målinger; andre sendere hører ikke til på kortet
                    "burst" -> if (!o.isNull("lvl") && (!o.has("src") || o.optBoolean("tgt"))) { srcOf.add(o.optInt("src", 0)); b.add(
                        Hub.Point(
                            time(o), lat, lon, o.getDouble("lvl"), o.optDouble("snr"),
                            o.optString("f").toDoubleOrNull()?.times(1e6), o.optInt("dur"), o.optDouble("gain", 0.0),
                        )
                    ) }
                    // tb = målinger fra målet; null = intet mål var valgt, og så er der ikke noget "stille"
                    "tick" -> if (if (o.has("tb")) (!o.isNull("tb") && o.optInt("tb") == 0) else o.optInt("bursts", 1) == 0) s.add(Hub.Point(time(o), lat, lon, null, null, null, 0, o.optDouble("gain", 0.0)))
                    "mark" -> m.add(Hub.Mark(time(o), lat, lon, o.optInt("n")))
                    "bearing" -> if (!o.isNull("hdg")) br.add(Hub.BearingLine(time(o), lat, lon, o.getDouble("hdg"), if (o.isNull("lvl")) null else o.optDouble("lvl"), o.optInt("n")))
                }
            }
            // skiftede målet undervejs i turen, vises kun den sender, der var mål i flest målinger
            val mine = (fileStart until b.size).toList()
            val main = mine.groupingBy { srcOf[it] }.eachCount().maxByOrNull { it.value }?.key
            for (i in mine.reversed()) if (srcOf[i] != main) { b.removeAt(i); srcOf.removeAt(i) }
        }
        return TripPoints(b, s, m, br)
    }

    /** Niveau-skalaen for alt, der vises: indlæste ture og den tur, der kører. */
    fun range(): Pair<Double, Double>? {
        var lo = Double.MAX_VALUE; var hi = -Double.MAX_VALUE
        for (list in listOf(bursts, Hub.bursts)) for (p in list) {
            val l = p.level ?: continue
            if (l < lo) lo = l
            if (l > hi) hi = l
        }
        return if (hi >= lo) lo to hi else null
    }
}
