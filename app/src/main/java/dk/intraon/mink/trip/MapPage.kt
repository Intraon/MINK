package dk.intraon.mink.trip

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

    /** En pejlings brede top: tyngdepunktet i grader og udsvinget i dB mellem den kraftigste og den svageste retning. */
    class Lobe(val center: Double, val swing: Double)

    private const val STILL_M = 100.0       // en tur, der holder sig inden for så mange meter, er stillestående
    private const val JOIN_S = 6 * 60L      // ture med kortere pause end dette vises som én
    private const val STEP = 10             // toppen måles i retninger med så mange graders mellemrum
    private const val HALF = 20.0           // hver retning er medianen af målingerne inden for så mange grader til hver side
    private const val MIN = 2               // en retning tæller først med så mange målinger

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

    /**
     * Tyngdepunktet af en pejlings top ud fra målingerne under en drejning: (retning, niveau). Toppen er typisk
     * bred og skæv, så den ene kraftigste retning kan ligge 20 grader fra midten. Retningerne inden for 6 dB af den
     * kraftigste vejes med deres effekt. Null, hvis der er målt nok i færre end halvdelen af retningerne.
     */
    fun lobe(samples: List<Pair<Double, Double>>): Lobe? {
        val dirs = (0 until 360 step STEP).mapNotNull { deg ->
            val near = samples.filter { kotlin.math.abs(((it.first - deg) % 360 + 540) % 360 - 180) <= HALF }
            if (near.size >= MIN) deg to median(near.map { it.second }) else null
        }
        if (dirs.size < 360 / STEP / 2) return null
        val top = dirs.maxOf { it.second }
        var x = 0.0; var y = 0.0
        for ((deg, level) in dirs) if (level >= top - 6) {
            val w = 10.0.pow(level / 10); val a = Math.toRadians(deg.toDouble())
            x += w * sin(a); y += w * cos(a)
        }
        return Lobe((Math.toDegrees(atan2(x, y)) + 360) % 360, top - dirs.minOf { it.second })
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
     * Sætter kortfilen sammen af skabelonen, turenes data, baggrundskortet (null = intet kunne hentes) og fotoerne
     * (navn til JPEG som base64). [made] er tidspunktet, filen blev lavet, som det skal stå i filen.
     */
    fun html(template: String, data: String, base: String?, photos: Map<String, String>, made: String): String {
        fun safe(json: String) = json.replace("</", "<\\/")
        return template.replace("/*DATA*/", safe(data)).replace("/*BASE*/", safe(base ?: "null"))
            .replace("/*FOTO*/", safe(JSONObject(photos).toString())).replace("{{LAVET}}", made)
    }

    private fun trip(log: Log): Trip? {
        val recs = log.lines.mapNotNull { try { JSONObject(it) } catch (_: Exception) { null } }
        val times = HashMap<JSONObject, OffsetDateTime>()
        for (o in recs) if (o.has("t")) try { times[o] = OffsetDateTime.parse(o.getString("t")) } catch (_: Exception) {}
        fun t(o: JSONObject) = times[o]?.let { it.toInstant().toEpochMilli() / 1000.0 }
        val timed = recs.filter { it in times }
        if (timed.isEmpty()) return null
        val t0 = t(timed.first())!!; val t1 = t(timed.last())!!
        val id = recs.firstOrNull { it.optString("type") == "meta" }?.optString("session")?.takeIf { it.isNotEmpty() } ?: TripFiles.stamp(log.name) ?: log.name
        fun placed(o: JSONObject) = !o.isNull("lat") && !o.isNull("lon")

        // Målets målinger. Skiftede målet undervejs, vises kun den sender, der var mål i flest målinger.
        var hits = recs.filter { it.optString("type") == "burst" && it in times && !it.isNull("lvl") && (!it.has("src") || it.optBoolean("tgt")) }
        val main = hits.groupingBy { it.optInt("src", 0) }.eachCount().maxByOrNull { it.value }?.key
        hits = hits.filter { it.optInt("src", 0) == main }
        // Er der skruet på gain i hånden, bruges kun målingerne ved turens sædvanlige gain: niveauet følger ikke
        // altid med, når der skrues, og så kan de øvrige ikke sammenlignes med resten.
        val gains = recs.filter { it.optString("type") == "gain" && it in times }
        if (gains.any { it.optString("why") == "manuel" }) {
            val usual = hits.groupingBy { it.optDouble("gain") }.eachCount().maxByOrNull { it.value }?.key
            hits = hits.filter { it.optDouble("gain") == usual }
        }
        val gainTimes = gains.map { t(it)!! }
        // Lige efter et gain-skift er effekten målt med det gamle gain, og et enkelt hop på over 20 dB i forhold til
        // målingerne lige før og efter, mens telefonen står stille, er et andet signal oven i målet.
        val kept = hits.filterIndexed { i, o ->
            val at = t(o)!!
            if (gainTimes.any { at - it in -0.5..3.0 }) return@filterIndexed false
            if (o.optDouble("spd", 0.0) > 5) return@filterIndexed true
            val near = (hits.subList(maxOf(0, i - 2), i) + hits.subList(i + 1, minOf(hits.size, i + 3))).filter { kotlin.math.abs(t(it)!! - at) <= 13 }
            !(near.size >= 2 && near.all { o.getDouble("lvl") - it.getDouble("lvl") > 20 })
        }
        val target = kept.filter { placed(it) }.map { o ->
            JSONArray().put(r6(o.getDouble("lat"))).put(r6(o.getDouble("lon"))).put(r1(o.getDouble("lvl"))).put((t(o)!! - t0).roundToLong())
                .put(r1(o.optDouble("snr", 0.0))).put(o.optDouble("spd", 0.0).let { if (it.isNaN()) 0 else it.roundToInt() })
        }
        // tb = målinger fra målet i perioden; mangler feltet (ældre logge), er perioden stille, når intet blev målt
        val quiet = recs.filter { o ->
            o.optString("type") == "tick" && o in times && placed(o) &&
                (if (o.has("tb")) (!o.isNull("tb") && o.optInt("tb") == 0) else o.optInt("bursts", 1) == 0)
        }.map { o -> JSONArray().put(r6(o.getDouble("lat"))).put(r6(o.getDouble("lon"))).put((t(o)!! - t0).roundToLong()) }

        val bearings = recs.filter { it.optString("type") == "bearing" && it in times && placed(it) }.map { o ->
            val at = t(o)!!; val lat = o.getDouble("lat"); val lon = o.getDouble("lon")
            // drejningen på stedet: målingerne, mens telefonen stod stille her de sidste fem minutter før pejlingen
            val turn = kept.filter { x ->
                placed(x) && !x.isNull("hdg") && !x.optBoolean("hdg_poor") && t(x)!! in (at - 300)..(at + 5) &&
                    x.optDouble("spd", 0.0) < 2 && meters(x.getDouble("lat"), x.getDouble("lon"), lat, lon) <= 30
            }.map { it.getDouble("hdg") to it.getDouble("lvl") }
            val lobe = lobe(turn)
            JSONObject().put("lat", lat).put("lon", lon).put("t", (at - t0).roundToLong()).put("n", o.optInt("n"))
                .put("hdg", if (o.isNull("hdg")) JSONObject.NULL else o.getDouble("hdg"))
                .put("best", if (o.isNull("best") || !o.has("best")) JSONObject.NULL else o.getDouble("best"))
                .put("lvl", if (o.isNull("lvl") || !o.has("lvl")) JSONObject.NULL else o.getDouble("lvl"))
                .put("poor", o.optString("cmp") == "poor").put("auto", o.optBoolean("auto"))
                .put("photo", if (o.isNull("photo") || !o.has("photo")) JSONObject.NULL else o.getString("photo").substringAfterLast('/'))
                .put("center", lobe?.let { r1(it.center) } ?: JSONObject.NULL).put("swing", lobe?.let { r1(it.swing) } ?: JSONObject.NULL)
        }
        val marks = recs.filter { it.optString("type") == "mark" && it in times && placed(it) }.map { o ->
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
        return Trip(id, t0, t1, times[timed.first()]!!.offset.totalSeconds, kind, target, quiet, bearings, marks)
    }

    private fun median(v: List<Double>): Double { val s = v.sorted(); return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2 }
    private fun meters(a: Double, b: Double, c: Double, d: Double) = hypot((a - c) * 111320.0, (b - d) * 111320.0 * cos(Math.toRadians(a)))
    private fun r6(v: Double) = (v * 1e6).roundToLong() / 1e6
    private fun r1(v: Double) = (v * 10).roundToLong() / 10.0
}
