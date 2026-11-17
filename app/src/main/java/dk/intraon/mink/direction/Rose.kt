package dk.intraon.mink.direction

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Styrken pr. retning fra ét sted: kompasset delt i felter a [binDeg] grader. Hver måling lægges i det felt,
 * antennen pegede i. Flytter man sig, gælder retningerne ikke længere, og rosen begynder forfra.
 *
 * Et sted, hvor der er målt mindst en halv omgang rundt, afleveres som en [Stop], når man går videre ([add]) eller
 * beder om det ([finish]): det er stedets pejling. Hvert sted afleveres højst én gang.
 */
class Rose(val binDeg: Int = 10) {
    /**
     * Et sted, der er målt færdigt: hvor det var, den stærkeste retning [best], den stærkeste enkelte måling
     * ([level], taget [at]) og rosen, som den så ud.
     */
    class Stop(val lat: Double, val lon: Double, val best: Double, val level: Double, val at: Long, val rose: Rose)

    val bins = 360 / binDeg
    private val level = DoubleArray(bins) { Double.NaN }
    private val count = IntArray(bins)
    private var topLevel = Double.NaN
    private var handed = false
    /** Tidspunktet for stedets stærkeste måling indtil nu; 0, før der er målt. */
    @Volatile var topAt = 0L
        private set
    /** Stedet, rosen er målt fra. */
    @Volatile var lat = Double.NaN
        private set
    @Volatile var lon = Double.NaN
        private set

    @Synchronized
    fun reset() { clear(); lat = Double.NaN; lon = Double.NaN }

    private fun clear() { level.fill(Double.NaN); count.fill(0); topLevel = Double.NaN; topAt = 0L; handed = false }

    /**
     * En måling i retningen [deg], taget [t]. Er man gået mere end [moveM] meter fra det sted, rosen blev startet,
     * begynder den forfra herfra, og det sted, man forlod, returneres, hvis det er en pejling værd (se [finish]).
     * Flere målinger i samme felt udjævnes, så en enkelt afviger ikke bestemmer.
     */
    @Synchronized
    fun add(deg: Double, lvl: Double, atLat: Double? = null, atLon: Double? = null, moveM: Double = 15.0, t: Long = 0L): Stop? {
        var left: Stop? = null
        if (atLat != null && atLon != null) {
            if (!lat.isNaN() && Bearing.meters(lat, lon, atLat, atLon) > moveM) { left = finish(); clear(); lat = Double.NaN }
            if (lat.isNaN()) { lat = atLat; lon = atLon }
        }
        val i = (Bearing.norm(deg) / binDeg).toInt() % bins
        level[i] = if (count[i] == 0) lvl else 0.5 * level[i] + 0.5 * lvl
        count[i]++
        if (topLevel.isNaN() || lvl > topLevel) { topLevel = lvl; topAt = t }
        return left
    }

    /**
     * Stedet, som det er målt indtil nu. Null, hvis stedet ikke har en position, hvis der er målt i færre end
     * halvdelen af retningerne (så er "stærkest" bare der, man tilfældigvis pegede), eller hvis stedet allerede
     * er afleveret.
     */
    @Synchronized
    fun finish(): Stop? {
        if (handed || lat.isNaN() || filled() < bins / 2) return null
        val best = best() ?: return null
        handed = true
        val copy = Rose(binDeg)
        System.arraycopy(level, 0, copy.level, 0, bins); System.arraycopy(count, 0, copy.count, 0, bins)
        return Stop(lat, lon, best, topLevel, topAt, copy)
    }

    /** Feltets niveau, eller null hvis der ikke er målt i den retning. */
    @Synchronized fun at(i: Int): Double? = level[i].takeUnless { it.isNaN() }
    fun center(i: Int): Double = (i + 0.5) * binDeg
    @Synchronized fun filled(): Int = count.count { it > 0 }

    /** Svageste og stærkeste felt. */
    @Synchronized
    fun range(): Pair<Double, Double>? {
        var lo = Double.MAX_VALUE; var hi = -Double.MAX_VALUE
        for (v in level) if (!v.isNaN()) { if (v < lo) lo = v; if (v > hi) hi = v }
        return if (hi >= lo) lo to hi else null
    }

    /**
     * Den stærkeste retning: tyngdepunktet af det stærkeste felt og dets naboer, vægtet efter effekt.
     * Null, før der er målt i mindst [minBins] retninger – ellers er "stærkest" bare der, man tilfældigvis pegede.
     */
    @Synchronized
    fun best(minBins: Int = 4): Double? {
        if (count.count { it > 0 } < minBins) return null
        var top = -1
        for (i in 0 until bins) if (!level[i].isNaN() && (top < 0 || level[i] > level[top])) top = i
        if (top < 0) return null
        var sx = 0.0; var sy = 0.0
        for (d in -1..1) {
            val i = (top + d + bins) % bins
            if (level[i].isNaN()) continue
            val w = Math.pow(10.0, (level[i] - level[top]) / 10)
            sx += w * sin(Math.toRadians(center(i))); sy += w * cos(Math.toRadians(center(i)))
        }
        return Bearing.norm(Math.toDegrees(atan2(sx, sy)))
    }
}
