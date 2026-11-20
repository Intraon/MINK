package dk.intraon.mink.trip

import dk.intraon.mink.direction.Crossing
import org.json.JSONArray
import org.json.JSONObject
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin

/**
 * Kortfilen: én HTML-fil med en eller flere ture, som åbnes i en browser. Sporet er farvet efter målets niveau, og
 * pejlinger, mærker og pejlingernes fotos ligger i filen. Filen henter intet, når den åbnes. Ren Kotlin.
 */
object MapPage {
    /** En logfil: navnet og dens linjer. */
    class Log(val name: String, val lines: List<String>)

    private const val STILL_M = 100.0       // en tur, der holder sig inden for så mange meter, er stillestående
    private const val JOIN_S = 6 * 60L      // ture med kortere pause end dette vises som én

    private class Trip(
        val id: String, val t0: Double, val t1: Double, val offset: Int, var kind: String,
        val target: List<JSONArray>, val quiet: List<JSONArray>, val bearings: List<JSONObject>, val marks: List<JSONObject>,
    )

    /** Navnene på de fotos, loggenes pejlinger peger på. */
    fun photoNames(logs: List<Log>): List<String> = logs.flatMap { log ->
        log.lines.mapNotNull { line ->
            val o = try { JSONObject(line) } catch (_: Exception) { return@mapNotNull null }
            if (o.optString("type") == "bearing" && !o.isNull("photo")) o.optString("photo").substringAfterLast('/').takeIf { it.isNotEmpty() } else null
        }
    }.distinct()

    /** Alle steder med position i loggene som (bredde, længde): det, baggrundskortet skal dække. */
    fun positions(logs: List<Log>): List<Pair<Double, Double>> = logs.flatMap { log ->
        log.lines.mapNotNull { line ->
            val o = try { JSONObject(line) } catch (_: Exception) { return@mapNotNull null }
            if (o.isNull("lat") || o.isNull("lon")) null else o.getDouble("lat") to o.getDouble("lon")
        }
    }

    /** Turenes data til kortfilen som JSON: én post pr. tur, med ture lige efter hinanden slået sammen. */
    fun data(logs: List<Log>): String {
        val trips = logs.mapNotNull { trip(it) }.sortedBy { it.t0 }
        // et kort stop midt i en tur hører til turen; stillestående målinger for sig selv samles til sidst i én post
        val chains = ArrayList<MutableList<Trip>>()
        for (tr in trips) {
            val c = chains.lastOrNull()
            val kind = c?.firstOrNull { it.kind != "stille" }?.kind ?: "stille"
            if (c != null && tr.t0 - c.last().t1 <= JOIN_S && (tr.kind == kind || tr.kind == "stille" || kind == "stille")) c.add(tr) else chains.add(mutableListOf(tr))
        }
        val (still, moving) = chains.partition { c -> c.all { it.kind == "stille" } }
        val out = JSONArray()
        val groups = (if (still.isEmpty()) emptyList() else listOf(still.flatten())) + moving
        for (g in groups) {
            val t0 = g.first().t0; val kind = g.firstOrNull { it.kind != "stille" }?.kind ?: "stille"
            fun shifted(rows: List<JSONArray>, base: Double, at: Int) = rows.map { r -> JSONArray(r.toString()).also { it.put(at, it.getLong(at) + (base - t0).roundToLong()) } }
            val target = JSONArray(); val quiet = JSONArray(); val bearings = JSONArray(); val marks = JSONArray()
            for (tr in g) {
                shifted(tr.target, tr.t0, 3).forEach { target.put(it) }
                shifted(tr.quiet, tr.t0, 2).forEach { quiet.put(it) }
                for (b in tr.bearings) bearings.put(JSONObject(b.toString()).put("t", b.getLong("t") + (tr.t0 - t0).roundToLong()))
                for (m in tr.marks) marks.put(JSONObject(m.toString()).put("t", m.getLong("t") + (tr.t0 - t0).roundToLong()))
            }
            var max: Double? = null
            for (i in 0 until target.length()) { val l = target.getJSONArray(i).getDouble(2); if (max == null || l > max) max = l }
            val day = DateTimeFormatter.ofPattern("dd.MM."); val clock = DateTimeFormatter.ofPattern("HH:mm")
            fun at(t: Double, off: Int) = OffsetDateTime.ofInstant(java.time.Instant.ofEpochSecond(t.toLong()), java.time.ZoneOffset.ofTotalSeconds(off))
            val first = at(t0, g.first().offset); val last = at(g.last().t1, g.last().offset)
            val label = if (kind == "stille") "Stillestående målinger (${g.size} ${if (g.size == 1) "kort" else "korte"}, ${first.format(day)}" +
                (if (first.format(day) == at(g.last().t0, g.last().offset).format(day)) ")" else "–${at(g.last().t0, g.last().offset).format(day)})")
            else "${first.format(day)} ${first.format(clock)}–${last.format(clock)}, $kind"
            out.put(JSONObject().put("id", g.first().id).put("label", label).put("kind", kind).put("t0", t0.roundToLong()).put("off", g.first().offset)
                .put("n", target.length()).put("max", max ?: JSONObject.NULL)
                .put("target", target).put("quiet", quiet).put("bearings", bearings).put("marks", marks))
        }
        return out.toString()
    }

    /**
     * Krydspejlingen af alle pejlingerne i [data] (turenes data, som [data] laver dem) til kortfilen: stedet,
     * ellipsens omrids og tallene til teksten. "null", når pejlingerne ikke giver et sted. En pejling taget med et
     * usikkert kompas, eller uden at målet blev hørt, regnes ikke med.
     */
    fun crossing(data: String): String {
        val sights = ArrayList<Crossing.Sight>()
        val trips = JSONArray(data)
        for (i in 0 until trips.length()) {
            val list = trips.getJSONObject(i).getJSONArray("bearings")
            for (j in 0 until list.length()) {
                val b = list.getJSONObject(j)
                if (b.optBoolean("poor") || b.isNull("lvl")) continue
                val deg = listOf("center", "best", "hdg").firstOrNull { !b.isNull(it) }?.let { b.getDouble(it) } ?: continue
                sights.add(Crossing.Sight(b.getDouble("lat"), b.getDouble("lon"), deg, if (b.isNull("swing")) null else b.getDouble("swing"), b.getJSONArray("others").length() > 0))
            }
        }
        val c = Crossing.of(sights) ?: return "null"
        return JSONObject().put("lat", r6(c.lat)).put("lon", r6(c.lon)).put("n", c.used).put("left", c.left)
            .put("a", c.majorM.roundToInt()).put("b", c.minorM.roundToInt())
            .put("outline", JSONArray(c.outline().map { JSONArray().put(r6(it.first)).put(r6(it.second)) })).toString()
    }

    /**
     * Sætter kortfilen sammen af skabelonen, turenes data, baggrundskortet (null = intet kunne hentes) og fotoerne
     * (navn til JPEG som base64). [made] er tidspunktet, filen blev lavet, som det skal stå i filen.
     */
    fun html(template: String, data: String, base: String?, photos: Map<String, String>, made: String): String {
        fun safe(json: String) = json.replace("</", "<\\/")
        return template.replace("/*DATA*/", safe(data)).replace("/*KRYDS*/", safe(crossing(data))).replace("/*BASE*/", safe(base ?: "null"))
            .replace("/*FOTO*/", safe(JSONObject(photos).toString())).replace("{{LAVET}}", made)
    }

    private fun trip(log: Log): Trip? {
        val read = LogReading.of(log.lines)
        val recs = read.recs; val kept = read.kept
        fun t(o: JSONObject) = read.t(o)
        val timed = recs.filter { read.timed(it) }
        if (timed.isEmpty()) return null
        val t0 = t(timed.first())!!; val t1 = t(timed.last())!!
        val id = recs.firstOrNull { it.optString("type") == "meta" }?.optString("session")?.takeIf { it.isNotEmpty() } ?: TripFiles.stamp(log.name) ?: log.name
        fun placed(o: JSONObject) = !o.isNull("lat") && !o.isNull("lon")

        val target = kept.filter { placed(it) }.map { o ->
            JSONArray().put(r6(o.getDouble("lat"))).put(r6(o.getDouble("lon"))).put(r1(o.getDouble("lvl"))).put((t(o)!! - t0).roundToLong())
                .put(r1(o.optDouble("snr", 0.0))).put(o.optDouble("spd", 0.0).let { if (it.isNaN()) 0 else it.roundToInt() })
        }
        // tb = målinger fra målet i perioden; mangler feltet (ældre logge), er perioden stille, når intet blev målt
        val quiet = recs.filter { o ->
            o.optString("type") == "tick" && read.timed(o) && placed(o) &&
                (if (o.has("tb")) (!o.isNull("tb") && o.optInt("tb") == 0) else o.optInt("bursts", 1) == 0)
        }.map { o -> JSONArray().put(r6(o.getDouble("lat"))).put(r6(o.getDouble("lon"))).put((t(o)!! - t0).roundToLong()) }

        // pejlingerne med den retning, drejningen på stedet giver; kortet i appen læser dem på samme måde
        val bearings = read.sightings.map { b ->
            JSONObject().put("lat", b.lat).put("lon", b.lon).put("t", (b.at - t0).roundToLong()).put("n", b.n)
                .put("hdg", b.hdg ?: JSONObject.NULL).put("best", b.best ?: JSONObject.NULL).put("lvl", b.level ?: JSONObject.NULL)
                .put("poor", b.poor).put("auto", b.auto).put("photo", b.photo ?: JSONObject.NULL)
                .put("center", b.lobe?.let { r1(it.center) } ?: JSONObject.NULL).put("swing", b.swing?.let { r1(it) } ?: JSONObject.NULL)
                .put("others", JSONArray(b.others.map { r1(it) }))
        }
        val marks = recs.filter { it.optString("type") == "mark" && read.timed(it) && placed(it) }.map { o ->
            JSONObject().put("lat", o.getDouble("lat")).put("lon", o.getDouble("lon")).put("t", (t(o)!! - t0).roundToLong())
                .put("n", o.optInt("n")).put("kind", o.optString("kind", "mark"))
        }

        val where = recs.filter { (it.optString("type") == "burst" || it.optString("type") == "tick") && placed(it) }
        if (where.isEmpty()) return null
        val speeds = where.map { it.optDouble("spd", 0.0) }.filter { !it.isNaN() }.sorted()
        val fast = speeds.isNotEmpty() && speeds[(speeds.size * 0.9).toInt().coerceAtMost(speeds.size - 1)] > 15
        val a0 = where.first().getDouble("lat"); val o0 = where.first().getDouble("lon")
        val spread = where.maxOf { meters(it.getDouble("lat"), it.getDouble("lon"), a0, o0) }
        val kind = if (fast) "bil" else if (spread > STILL_M) "til fods" else "stille"
        return Trip(id, t0, t1, read.time(timed.first())!!.offset.totalSeconds, kind, target, quiet, bearings, marks)
    }

    private fun median(v: List<Double>): Double { val s = v.sorted(); return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2 }
    private fun meters(a: Double, b: Double, c: Double, d: Double) = hypot((a - c) * 111320.0, (b - d) * 111320.0 * cos(Math.toRadians(a)))
    private fun r6(v: Double) = (v * 1e6).roundToLong() / 1e6
    private fun r1(v: Double) = (v * 10).roundToLong() / 10.0
}
