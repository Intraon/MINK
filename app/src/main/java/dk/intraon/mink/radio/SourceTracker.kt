package dk.intraon.mink.radio

import kotlin.math.abs

/**
 * Holder styr på, hvilke sendere der høres, og hvilken af dem brugeren har valgt som mål.
 *
 * Målinger med næsten samme frekvens hører til samme kilde. Kildens frekvens følger med målingerne, så en
 * dongle, der driver mens den varmer op, ikke deler én sender i flere.
 *
 * Målet er den kilde, tal, pil, bip og kort følger. Det vælges kun af brugeren (tryk i vandfaldet): den kilde,
 * der ligger inden for [targetTolHz] af den valgte frekvens. Når den er fundet, følges den, selv om dens frekvens
 * driver længere væk. Målet skifter aldrig af sig selv – heller ikke når en anden sender bliver stærkere.
 * Uden et valgt mål er ingen kilde mål.
 *
 * Ren Kotlin uden Android. Metoderne er synkroniserede.
 */
class SourceTracker(
    private val joinHz: Double,
    targetHz: Double?,
    private val targetTolHz: Double = 10_000.0,
    private val expireMs: Long = 120_000,
) {
    class Source(val id: Int, var freqHz: Double, var lastT: Long, var snr: Double, var level: Double, var n: Int) {
        /** Senderens målte bredde, udjævnet over målingerne ([bwN] = hvor mange der kunne måles). */
        var bwHz: Double? = null
        var bwN = 0
    }

    /** Et øjebliksbillede til skærmen. */
    data class View(
        val id: Int, val freqHz: Double, val lastT: Long, val snr: Double, val level: Double, val n: Int, val target: Boolean,
        val bwHz: Double? = null, val bwN: Int = 0,
    )

    private val sources = ArrayList<Source>()
    private var nextId = 1
    private var targetId: Int? = null

    /** Den valgte frekvens – eller der, hvor målet er drevet hen. null = intet mål valgt. */
    @Volatile var targetHz: Double? = targetHz
        private set

    /** Lægger en måling ind ([bwHz] = målt bredde, hvis den kunne måles). Returnerer kildens id, og om den er målet. */
    @Synchronized
    fun add(t: Long, freqHz: Double, snr: Double, level: Double, bwHz: Double? = null): Pair<Int, Boolean> {
        sources.removeAll { t - it.lastT > expireMs }
        if (targetId != null && sources.none { it.id == targetId }) targetId = null

        var src = sources.filter { abs(it.freqHz - freqHz) <= joinHz }.minByOrNull { abs(it.freqHz - freqHz) }
        if (src == null) {
            src = Source(nextId++, freqHz, t, snr, level, 0)
            sources.add(src)
        }
        src.freqHz = freqHz; src.lastT = t; src.snr = snr; src.n++
        src.level = if (src.n == 1) level else 0.5 * src.level + 0.5 * level
        if (bwHz != null) {
            src.bwHz = src.bwHz?.let { 0.8 * it + 0.2 * bwHz } ?: bwHz
            src.bwN++
        }

        val wanted = targetHz
        if (wanted != null) {
            if (targetId == null && abs(src.freqHz - wanted) <= targetTolHz) targetId = src.id
            if (src.id == targetId) targetHz = src.freqHz
        }
        return src.id to (src.id == targetId)
    }

    /** Er der en kendt kilde så tæt på [freqHz], at en måling dér ville høre til den? */
    @Synchronized
    fun near(freqHz: Double): Boolean = sources.any { abs(it.freqHz - freqHz) <= joinHz }

    /** Brugeren vælger mål: en frekvens, eller null for intet mål. */
    @Synchronized
    fun retarget(hz: Double?) {
        targetHz = hz
        val src = if (hz == null) null else sources.filter { abs(it.freqHz - hz) <= targetTolHz }.minByOrNull { abs(it.freqHz - hz) }
        targetId = src?.id
        if (src != null) targetHz = src.freqHz
    }

    /**
     * Donglens frekvenskorrektion er ændret, så alt står [dHz] højere i vandfaldet end før. Kilderne og målet
     * flyttes med, så målet stadig er målet, og hver sender ikke bliver til to.
     */
    @Synchronized
    fun shift(dHz: Double) {
        for (s in sources) s.freqHz += dHz
        targetHz = targetHz?.plus(dHz)
    }

    /** Kilderne, målet først og derefter efter frekvens. */
    @Synchronized
    fun snapshot(): List<View> = sources
        .map { View(it.id, it.freqHz, it.lastT, it.snr, it.level, it.n, it.id == targetId, it.bwHz, it.bwN) }
        .sortedWith(compareByDescending<View> { it.target }.thenBy { it.freqHz })
}
