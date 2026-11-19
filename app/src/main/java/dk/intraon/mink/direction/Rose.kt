package dk.intraon.mink.direction

/**
 * Styrken pr. retning fra ét sted: kompasset delt i felter a [binDeg] grader. Hver måling lægges i det felt,
 * antennen pegede i. Flytter man sig, gælder retningerne ikke længere, og rosen begynder forfra.
 *
 * Et sted, hvor der er målt mindst en halv omgang rundt, afleveres som en [Stop], når man går videre ([add]) eller
 * beder om det ([finish]): det er stedets pejling. Hvert sted afleveres højst én gang.
 */
class Rose(val binDeg: Int = 10) {
    /**
     * Et sted, der er målt færdigt: hvor det var, den stærkeste retning [best] (midten af toppen, se [lobe]), den
     * stærkeste enkelte måling [level], tidspunktet [at] for den måling, der blev taget nærmest retningen, og rosen,
     * som den så ud.
     */
    class Stop(val lat: Double, val lon: Double, val best: Double, val level: Double, val at: Long, val rose: Rose, val lobe: Lobe)

    val bins = 360 / binDeg
    private val level = DoubleArray(bins) { Double.NaN }
    private val count = IntArray(bins)
    private var topLevel = Double.NaN
    private var handed = false
    /** Hver måling på stedet som (retning, niveau): det, [lobe] regner toppen ud fra. */
    private val samples = ArrayList<Pair<Double, Double>>()
    /** Toppen, regnet ud ved den seneste måling, så skærmen ikke skal regne den ud for hvert billede. */
    private var live: Lobe? = null
    /** Retningen for den måling, [topAt] hører til. */
    private var topDeg = Double.NaN
    /**
     * Tidspunktet for den måling, der indtil nu er taget nærmest stedets stærkeste retning; 0, før der er målt.
     * Det er den måling, et foto af stedet skal vise. Før der er en retning, er det den stærkeste måling.
     */
    @Volatile var topAt = 0L
        private set
    /** Stedet, rosen er målt fra. */
    @Volatile var lat = Double.NaN
        private set
    @Volatile var lon = Double.NaN
        private set

    @Synchronized
    fun reset() { clear(); lat = Double.NaN; lon = Double.NaN }

    private fun clear() {
        level.fill(Double.NaN); count.fill(0); samples.clear(); live = null
        topLevel = Double.NaN; topDeg = Double.NaN; topAt = 0L; handed = false
    }

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
        if (samples.size >= MAX_SAMPLES) samples.removeAt(0)
        samples.add(Bearing.norm(deg) to lvl)
        val strongest = topLevel.isNaN() || lvl > topLevel
        if (strongest) topLevel = lvl
        // fotoet følger retningen: den nye måling tager over, når den ligger mindst lige så tæt på toppens midte
        live = Lobe.of(samples, LIVE_DIRS)
        val mid = live?.center
        val closer = mid != null && (topDeg.isNaN() || kotlin.math.abs(Bearing.diff(deg, mid)) <= kotlin.math.abs(Bearing.diff(topDeg, mid)))
        if (closer || (mid == null && strongest)) { topAt = t; topDeg = deg }
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
        val lobe = Lobe.of(samples) ?: return null
        handed = true
        val copy = Rose(binDeg)
        System.arraycopy(level, 0, copy.level, 0, bins); System.arraycopy(count, 0, copy.count, 0, bins)
        copy.samples.addAll(samples); copy.live = live
        return Stop(lat, lon, lobe.center, topLevel, topAt, copy, lobe)
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
     * Toppen, som den ser ud nu ([Lobe]). Null, før der er målt i mindst fire retninger – ellers er "stærkest"
     * bare der, man tilfældigvis pegede.
     */
    @Synchronized fun lobe(): Lobe? = live

    /** Den stærkeste retning: midten af toppen. Null, før der er en ([lobe]). */
    fun best(): Double? = lobe()?.center

    private companion object {
        /** Så mange retninger skal der til, før skærmen viser en retning, mens der drejes. */
        const val LIVE_DIRS = 4
        /** Et sted gemmer højst så mange målinger; de ældste glider ud. */
        const val MAX_SAMPLES = 720
    }
}
