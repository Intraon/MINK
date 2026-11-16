package dk.intraon.mink.waterfall

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min

/**
 * Vandfaldets data: spektret af det lyttede bånd, én række pr. [BLOCKS_PER_ROW] blokke (40 ms), [ROWS] rækker
 * tilbage i tid. Hver værdi er dB over støjgulvet, pakket i en byte fra [MIN_DB] til [MAX_DB].
 * Fyldes af måletråden ([feed]) og læses af skærmen.
 */
object Scope {
    /** 750 rækker a 40 ms = de seneste 30 sekunder. */
    const val ROWS = 750
    const val BLOCKS_PER_ROW = 20
    const val MIN_DB = -2.0
    const val MAX_DB = 40.0
    const val ROWS_PER_SECOND = 25

    @Volatile var width = 0
        private set
    /** Frekvensen i venstre kant og bredden af én søjle. */
    @Volatile var loHz = 0.0
        private set
    @Volatile var binHz = 500.0
        private set
    @Volatile var data = ByteArray(0)
        private set
    /** Antal rækker skrevet siden [configure]; række r ligger på plads r % ROWS. */
    @Volatile var seq = 0L
        private set

    private var first = 0
    private var n = 2048
    private var hold = DoubleArray(0)
    private var count = 0
    private var floorSum = 0.0

    @Synchronized
    fun configure(firstBin: Int, bins: Int, loHz: Double, binHz: Double, fftSize: Int) {
        first = firstBin; n = fftSize; hold = DoubleArray(bins); count = 0; floorSum = 0.0
        this.loHz = loHz; this.binHz = binHz
        data = ByteArray(ROWS * bins)
        seq = 0
        width = bins
    }

    /**
     * Én bloks rå FFT ([re], [im], bin 0 = tunerfrekvensen) og støjgulvet. Kaldes for hver blok.
     * Spektret får et Hann-vindue her (i frekvensdomænet: ½·X[k] − ¼·X[k−1] − ¼·X[k+1]), så et kraftigt signal
     * står skarpt i vandfaldet uden brede skørter – som i en almindelig SDR-modtager.
     */
    @Synchronized
    fun feed(re: DoubleArray, im: DoubleArray, floor: Double) {
        val w = width
        if (w == 0) return
        val half = n / 2
        for (i in 0 until w) {
            val k = (first + i + half) % n
            val kl = (k - 1 + n) % n; val kr = (k + 1) % n
            val hr = 0.5 * re[k] - 0.25 * (re[kl] + re[kr])
            val hi = 0.5 * im[k] - 0.25 * (im[kl] + im[kr])
            hold[i] += hr * hr + hi * hi
        }
        floorSum += floor
        if (++count < BLOCKS_PER_ROW) return
        // støj gennem Hann-vinduet er 0,375 × støjen pr. bin uden vindue; floor er skaleret med 1/n²
        val scale = 1.0 / (0.375 * n.toDouble() * n * floorSum)
        val row = ((seq % ROWS) * w).toInt()
        for (i in 0 until w) {
            val db = 10 * log10(hold[i] * scale + 1e-12)
            data[row + i] = (((db - MIN_DB) / (MAX_DB - MIN_DB)).coerceIn(0.0, 1.0) * 255).toInt().toByte()
            hold[i] = 0.0
        }
        count = 0; floorSum = 0.0
        seq++
    }

    /** Et øjebliksbillede af vandfaldet med den ældste række først. */
    /** [endSeq] = [seq], da billedet blev taget: den nyeste række har nummer endSeq − 1. */
    class Snap(val rows: Int, val bins: Int, val db: ByteArray, val loHz: Double, val binHz: Double, val endSeq: Long = rows.toLong())

    /** De rækker, der ligger i vandfaldet lige nu (højst [ROWS]), kopieret ud i tidsrækkefølge. Null = intet målt endnu. */
    @Synchronized
    fun snapshot(): Snap? {
        val w = width; val s = seq
        if (w == 0 || s == 0L) return null
        val rows = min(s, ROWS.toLong()).toInt()
        val out = ByteArray(rows * w)
        for (r in 0 until rows) System.arraycopy(data, (((s - rows + r) % ROWS) * w).toInt(), out, r * w, w)
        return Snap(rows, w, out, loHz, binHz, s)
    }

    /** dB over støjgulvet for en pakket værdi fra [data]. */
    fun dbOf(b: Byte): Double = MIN_DB + (b.toInt() and 0xff) / 255.0 * (MAX_DB - MIN_DB)

    /** Mørkeblå → blå → lyseblå → hvid → gul → rød. */
    fun color(x: Float): Int {
        val stops = floatArrayOf(0f, 0.22f, 0.45f, 0.62f, 0.80f, 1f)
        val cols = intArrayOf(0xFF03103A.toInt(), 0xFF0B35C8.toInt(), 0xFF4FA8F7.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFE030.toInt(), 0xFFFF2A10.toInt())
        var i = 0
        while (i < stops.size - 2 && x > stops[i + 1]) i++
        val t = ((x - stops[i]) / (stops[i + 1] - stops[i])).coerceIn(0f, 1f)
        fun mix(shift: Int): Int {
            val a = (cols[i] shr shift) and 0xff; val b = (cols[i + 1] shr shift) and 0xff
            return (a + (b - a) * t).toInt()
        }
        return (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    fun hzOf(column: Double) = loHz + column * binHz
    fun columnOf(hz: Double) = (hz - loHz) / binHz

    /**
     * Den kraftigste frekvens inden for ±[withinHz] af [hz] i de seneste [seconds] – så et tryk lidt ved siden af
     * et signal rammer signalet. Returnerer null, hvis der ikke er noget tydeligt dér.
     */
    fun peakNear(hz: Double, withinHz: Double, seconds: Int = 10): Double? {
        val w = width; val d = data; val s = seq
        if (w == 0 || s == 0L) return null
        val lo = max(0, columnOf(hz - withinHz).toInt()); val hi = min(w - 1, columnOf(hz + withinHz).toInt())
        if (hi < lo) return null
        val rows = min(s, (seconds * ROWS_PER_SECOND).toLong()).toInt()
        var best = -1; var bestV = 0
        for (c in lo..hi) {
            var m = 0
            for (r in 0 until rows) {
                val v = d[(((s - 1 - r) % ROWS) * w + c).toInt()].toInt() and 0xff
                if (v > m) m = v
            }
            if (m > bestV) { bestV = m; best = c }
        }
        // mindst ca. 8 dB over støjen, ellers er der intet at snappe til
        val needed = ((8.0 - MIN_DB) / (MAX_DB - MIN_DB) * 255).toInt()
        return if (best >= 0 && bestV >= needed) hzOf(best + 0.5) else null
    }
}
