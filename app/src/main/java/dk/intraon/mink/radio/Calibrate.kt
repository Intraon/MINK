package dk.intraon.mink.radio

import dk.intraon.mink.waterfall.Scope
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Finder ud af, hvor meget donglens frekvens ligger ved siden af, ved at se på trafikken på brugerens egen kanal.
 * En billig dongle rammer gerne 30–60 kHz forkert ved 869 MHz. Trafikken på kanalen (ens egne noder og resten af
 * nettet) ligger derimod præcist, og den er nem at kende: den er lige så bred som kanalen. Ligger de brede udbrud
 * ved siden af kanalens midte, er det donglen, der viser forkert.
 */
object Calibrate {
    /** Så mange rækker (a 40 ms) med et bredt udbrud skal der til, før der regnes på det. */
    const val MIN_ROWS = 8
    /** Så langt ved siden af kanalen ledes der (ca. 100 ppm ved 869 MHz). */
    const val REACH_HZ = 90_000.0
    /** Mindre end det er ikke værd at rette (ca. 7 ppm ved 869 MHz). */
    const val WORTH_HZ = 6_000.0

    /**
     * Hvor mange Hz trafikken på kanalen ligger over (+) eller under (−) kanalens midte i vandfaldet.
     * Kun rækker nyere end [sinceSeq] tæller, så en rettelse ikke bliver lagt oven i sig selv.
     * Null = ikke nok brede udbrud, eller de er ikke enige.
     */
    fun offsetHz(s: Scope.Snap, sinceSeq: Long, chanHz: Double, chanBwHz: Double): Double? {
        if (chanHz <= 0 || chanBwHz < 20_000) return null
        val lo = max(0, floor((chanHz - chanBwHz / 2 - REACH_HZ - s.loHz) / s.binHz).toInt())
        val hi = min(s.bins - 1, ceil((chanHz + chanBwHz / 2 + REACH_HZ - s.loHz) / s.binHz).toInt())
        if ((hi - lo) * s.binHz < chanBwHz * 1.5) return null
        val first = max(0L, s.rows - (s.endSeq - sinceSeq)).toInt().coerceAtMost(s.rows)
        val centers = ArrayList<Double>()
        for (r in first until s.rows) {
            val off = r * s.bins
            var peak = 0
            for (c in lo..hi) { val v = s.db[off + c].toInt() and 0xff; if (v > peak) peak = v }
            val peakDb = Scope.dbOf(peak.toByte())
            if (peakDb < 12) continue
            // kanterne måles 12 dB under toppen: de ligger lige langt fra midten, uanset hvor kraftigt signalet er
            val thrDb = max(6.0, peakDb - 12)
            // det længste sammenhængende stykke over tærsklen (huller på op til to søjler tæller med)
            var bestA = -1; var bestB = -1
            var a = -1; var last = -1
            for (c in lo..hi) {
                if (Scope.dbOf(s.db[off + c]) < thrDb) continue
                if (a < 0 || c - last > 3) { a = c }
                last = c
                if (bestA < 0 || last - a > bestB - bestA) { bestA = a; bestB = last }
            }
            if (bestA < 0) continue
            val width = (bestB - bestA + 1) * s.binHz
            if (width < 0.75 * chanBwHz || width > 1.5 * chanBwHz) continue
            if (bestA == lo || bestB == hi) continue   // skåret af i kanten: midten kendes ikke
            centers.add(s.loHz + (bestA + bestB + 1) / 2.0 * s.binHz)
        }
        if (centers.size < MIN_ROWS) return null
        centers.sort()
        val spread = centers[centers.size * 3 / 4] - centers[centers.size / 4]
        if (spread > WORTH_HZ) return null
        return centers[centers.size / 2] - chanHz
    }

    /**
     * Den PPM-korrektion, der flytter trafikken ind på kanalen. Ligger signalerne for højt, skal korrektionen ned:
     * én ppm flytter alt [chanHz]/1e6 Hz (0,87 kHz ved 869 MHz).
     */
    fun ppm(current: Int, offsetHz: Double, chanHz: Double): Int =
        (current - (offsetHz / chanHz * 1e6).roundToInt()).coerceIn(-200, 200)
}
