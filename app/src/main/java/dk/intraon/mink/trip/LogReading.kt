package dk.intraon.mink.trip

import dk.intraon.mink.direction.Bearing
import dk.intraon.mink.direction.Lobe
import org.json.JSONObject
import java.time.OffsetDateTime

/**
 * Det, kortet i appen og kortfilen læser ens ud af en logfil: målets målinger ([kept]) og pejlingerne
 * ([sightings]) med den retning, der regnes af drejningen på stedet. Begge kort bygger på denne læsning, så de
 * viser de samme retninger og anslår det samme sted. [recs] er alle filens linjer, der kunne læses. Ren Kotlin.
 */
class LogReading private constructor(
    val recs: List<JSONObject>,
    private val times: Map<JSONObject, OffsetDateTime>,
    val kept: List<JSONObject>,
    val sightings: List<Sighting>,
) {
    /**
     * En pejling i loggen: tidspunktet [at] (sekunder), stedet, den retning telefonen pegede ([hdg]) og rosens
     * retning ([best]), da den blev gemt, målets niveau, om kompasset var usikkert, og toppen i drejningen på stedet
     * ([lobe], null = der er ikke drejet nok). [swing] og [others] er toppens; uden en top er det dem, loggen har.
     */
    class Sighting(
        val at: Double, val lat: Double, val lon: Double, val n: Int, val hdg: Double?, val best: Double?, val level: Double?,
        val poor: Boolean, val auto: Boolean, val photo: String?, val lobe: Lobe?, val swing: Double?, val others: List<Double>,
    ) {
        /** Pejlingens retning: midten af toppen i drejningen på stedet, og uden en drejning den retning, loggen har. */
        val deg: Double? get() = lobe?.center ?: best ?: hdg
    }

    /** Har linjen et tidspunkt, der kan læses? */
    fun timed(o: JSONObject): Boolean = o in times
    fun time(o: JSONObject): OffsetDateTime? = times[o]
    /** Linjens tidspunkt i sekunder. */
    fun t(o: JSONObject): Double? = times[o]?.let { it.toInstant().toEpochMilli() / 1000.0 }

    companion object {
        /** Drejningen på et sted er målingerne fra så mange sekunder før pejlingen … */
        private const val TURN_S = 300.0
        /** … taget inden for så mange meter af den, mens telefonen stod stille. */
        private const val TURN_M = 30.0

        fun of(lines: List<String>): LogReading {
            val recs = lines.mapNotNull { try { JSONObject(it) } catch (_: Exception) { null } }
            val times = HashMap<JSONObject, OffsetDateTime>()
            for (o in recs) if (o.has("t")) try { times[o] = OffsetDateTime.parse(o.getString("t")) } catch (_: Exception) {}
            fun t(o: JSONObject) = times[o]!!.toInstant().toEpochMilli() / 1000.0
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
            val gainTimes = gains.map { t(it) }
            // Lige efter et gain-skift er effekten målt med det gamle gain, og et enkelt hop på over 20 dB i forhold til
            // målingerne lige før og efter, mens telefonen står stille, er et andet signal oven i målet.
            val kept = hits.filterIndexed { i, o ->
                val at = t(o)
                if (gainTimes.any { at - it in -0.5..3.0 }) return@filterIndexed false
                if (o.optDouble("spd", 0.0) > 5) return@filterIndexed true
                val near = (hits.subList(maxOf(0, i - 2), i) + hits.subList(i + 1, minOf(hits.size, i + 3))).filter { kotlin.math.abs(t(it) - at) <= 13 }
                !(near.size >= 2 && near.all { o.getDouble("lvl") - it.getDouble("lvl") > 20 })
            }

            fun num(o: JSONObject, key: String) = if (o.has(key) && !o.isNull(key)) o.getDouble(key) else null
            val sightings = recs.filter { it.optString("type") == "bearing" && it in times && placed(it) }.map { o ->
                val at = t(o); val lat = o.getDouble("lat"); val lon = o.getDouble("lon")
                val turn = kept.filter { x ->
                    placed(x) && !x.isNull("hdg") && !x.optBoolean("hdg_poor") && t(x) in (at - TURN_S)..(at + 5) &&
                        x.optDouble("spd", 0.0) < 2 && Bearing.meters(x.getDouble("lat"), x.getDouble("lon"), lat, lon) <= TURN_M
                }.map { it.getDouble("hdg") to it.getDouble("lvl") }
                val lobe = Lobe.of(turn)
                val logged = o.optJSONArray("others")?.let { a -> (0 until a.length()).map { a.getDouble(it) } } ?: emptyList()
                Sighting(
                    at, lat, lon, o.optInt("n"), num(o, "hdg"), num(o, "best"), num(o, "lvl"),
                    o.optString("cmp") == "poor", o.optBoolean("auto"),
                    if (o.has("photo") && !o.isNull("photo")) o.getString("photo").substringAfterLast('/') else null,
                    lobe, lobe?.swing ?: num(o, "swing"), lobe?.others ?: logged,
                )
            }
            return LogReading(recs, times, kept, sightings)
        }
    }
}
