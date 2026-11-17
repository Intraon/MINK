package dk.intraon.mink.replay

import java.text.SimpleDateFormat
import java.util.Locale
import org.json.JSONObject

/**
 * En tur læst tilbage fra sin log: alle senderes udbrud med tid, frekvens, bredde, varighed og styrke, og de steder,
 * turen gik. Det er det, et vandfald over turen kan genskabes af. Støjen mellem udbruddene er ikke gemt og kan ikke
 * vises.
 *
 * Ren Kotlin uden Android.
 */
class Replay(
    val startMs: Long,
    val endMs: Long,
    /** Det bånd, der blev lyttet på. */
    val bandLoHz: Double,
    val bandHiHz: Double,
    /** Brugerens egen kanal, hvis den var sat. */
    val chanHz: Double?,
    val chanBwHz: Double?,
    /** Bølgeform-filens navn og sample rate; navnet er null, hvis bølgeformen ikke blev gemt. */
    val iqFile: String?,
    val iqRate: Int,
    /** Alle udbrud, ordnet efter starttid. */
    val bursts: List<Burst>,
    /** Turens steder, ordnet efter tid. */
    val fixes: List<Fix>,
) {
    /** Et udbrud. [iqn] er 0, hvis udbruddet ikke har et udsnit i bølgeform-filen. */
    class Burst(
        val t: Long, val durMs: Int, val freqHz: Double, val bwHz: Double, val snr: Double, val level: Double, val target: Boolean,
        val iq: Long, val iqn: Int, val iqFcHz: Double, val iqPreMs: Int,
    )

    class Fix(val t: Long, val lat: Double, val lon: Double)

    /** Målets udbrud. */
    val target: List<Burst> = bursts.filter { it.target }

    /** Målets højeste niveau på turen, eller null, hvis målet ikke blev hørt. */
    val strongest: Double? = target.maxOfOrNull { it.level }

    /** De udbrud, der helt eller delvist ligger mellem [from] og [to]. */
    fun between(from: Long, to: Long): List<Burst> {
        // et udbrud kan være begyndt før vinduet og række ind i det
        var lo = 0; var hi = bursts.size
        while (lo < hi) { val mid = (lo + hi) ushr 1; if (bursts[mid].t < from - LONGEST_MS) lo = mid + 1 else hi = mid }
        val res = ArrayList<Burst>()
        var i = lo
        while (i < bursts.size && bursts[i].t <= to) { val b = bursts[i]; if (b.t + b.durMs >= from) res.add(b); i++ }
        return res
    }

    /** Det sted, turen var nærmest tidspunktet [t]. Null, hvis det nærmeste sted er mere end [withinMs] væk i tid. */
    fun fixAt(t: Long, withinMs: Long = 15_000L): Fix? {
        if (fixes.isEmpty()) return null
        var lo = 0; var hi = fixes.size
        while (lo < hi) { val mid = (lo + hi) ushr 1; if (fixes[mid].t < t) lo = mid + 1 else hi = mid }
        val best = listOfNotNull(fixes.getOrNull(lo - 1), fixes.getOrNull(lo)).minByOrNull { kotlin.math.abs(it.t - t) } ?: return null
        return best.takeIf { kotlin.math.abs(it.t - t) <= withinMs }
    }

    companion object {
        /** Længere end dette varer et udbrud i loggen ikke: et længere signal deles i flere poster. */
        private const val LONGEST_MS = 5_000L
        /** Bredden, et udbrud tegnes med, når loggen ikke har målt den. */
        private const val UNKNOWN_BW_HZ = 6_000.0

        /** Læser en logfils linjer. Null, hvis filen ikke har nogen poster med tid. */
        fun parse(lines: List<String>): Replay? {
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
            var meta: JSONObject? = null
            var first = Long.MAX_VALUE; var last = Long.MIN_VALUE
            val bursts = ArrayList<Burst>(); val fixes = ArrayList<Fix>()
            for (line in lines) {
                val o = try { JSONObject(line) } catch (_: Exception) { continue }
                val type = o.optString("type")
                if (type == "meta") { if (meta == null) meta = o; continue }
                val t = try { fmt.parse(o.optString("t"))?.time } catch (_: Exception) { null } ?: continue
                if (t < first) first = t
                if (t > last) last = t
                if ((type == "burst" || type == "tick") && !o.isNull("lat") && !o.isNull("lon")) fixes.add(Fix(t, o.getDouble("lat"), o.getDouble("lon")))
                if (type != "burst" || o.isNull("lvl")) continue
                val freq = o.optString("f").toDoubleOrNull()?.times(1e6) ?: continue
                bursts.add(
                    Burst(
                        t, o.optInt("dur"), freq, if (o.isNull("bw")) UNKNOWN_BW_HZ else o.optDouble("bw", UNKNOWN_BW_HZ),
                        o.optDouble("snr", 0.0), o.getDouble("lvl"), o.optBoolean("tgt"),
                        o.optLong("iq", 0L), if (o.has("iq")) o.optInt("iqn") else 0, o.optDouble("iqfc", freq), o.optInt("iqpre"),
                    )
                )
            }
            if (first > last) return null
            val m = meta
            return Replay(
                first, last,
                m?.optDouble("band_lo")?.takeUnless { it.isNaN() } ?: bursts.minOfOrNull { it.freqHz } ?: 0.0,
                m?.optDouble("band_hi")?.takeUnless { it.isNaN() } ?: bursts.maxOfOrNull { it.freqHz } ?: 0.0,
                m?.optDouble("chan")?.takeUnless { it.isNaN() || it <= 0 }, m?.optDouble("chan_bw")?.takeUnless { it.isNaN() || it <= 0 },
                m?.takeUnless { it.isNull("iq_file") }?.optString("iq_file")?.takeIf { it.isNotEmpty() }, m?.optInt("iq_rate", 16_000) ?: 16_000,
                bursts.sortedBy { it.t }, fixes.sortedBy { it.t },
            )
        }
    }
}
