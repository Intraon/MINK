package dk.intraon.mink.map

import kotlin.math.cos
import kotlin.math.floor

/**
 * Varmekortets felter: målingerne samles i kvadrater på [sizeM] meter, og hvert felt får gennemsnittet af sine
 * målingers niveau. Felter, hvor der kun er lyttet uden at høre noget, huskes også – de viser, hvor senderen ikke
 * kan høres. Der gættes ikke mellem felterne: et felt uden målinger er tomt.
 *
 * Ren Kotlin uden Android. [lat0] bruges kun til at regne længdegrader om til meter og skal være den samme,
 * så længe de samme data vises.
 */
class HeatGrid(val sizeM: Double, lat0: Double) {

    class Cell {
        var sum = 0.0
        var n = 0
        var quiet = 0
        val mean get() = if (n > 0) sum / n else null
    }

    val cells = HashMap<Long, Cell>()
    private val kx = 111_320.0 * cos(Math.toRadians(lat0))
    private val ky = 110_540.0

    private fun key(ix: Long, iy: Long) = (ix shl 32) or (iy and 0xffffffffL)
    private fun ix(key: Long) = key shr 32
    private fun iy(key: Long) = key.toInt().toLong()

    /** [level] = null betyder "lyttet her, intet hørt". */
    fun add(lat: Double, lon: Double, level: Double?) {
        val k = key(floor(lon * kx / sizeM).toLong(), floor(lat * ky / sizeM).toLong())
        val c = cells.getOrPut(k) { Cell() }
        if (level == null) c.quiet++ else { c.sum += level; c.n++ }
    }

    /** Feltets hjørner: syd, vest, nord, øst. */
    fun bounds(key: Long): DoubleArray {
        val x = ix(key); val y = iy(key)
        return doubleArrayOf(y * sizeM / ky, x * sizeM / kx, (y + 1) * sizeM / ky, (x + 1) * sizeM / kx)
    }

    /** Feltet med det højeste gennemsnit (nøgle og niveau), eller null uden målinger. */
    fun strongest(): Pair<Long, Double>? {
        var best: Pair<Long, Double>? = null
        for ((k, c) in cells) { val m = c.mean ?: continue; if (best == null || m > best.second) best = k to m }
        return best
    }

    /** Laveste og højeste feltgennemsnit. */
    fun range(): Pair<Double, Double>? {
        var lo = Double.MAX_VALUE; var hi = -Double.MAX_VALUE
        for (c in cells.values) { val m = c.mean ?: continue; if (m < lo) lo = m; if (m > hi) hi = m }
        return if (hi >= lo) lo to hi else null
    }

    companion object {
        private val SIZES = doubleArrayOf(25.0, 50.0, 100.0, 200.0, 500.0, 1000.0, 2000.0, 5000.0, 10_000.0, 20_000.0, 50_000.0)

        /** Mindste feltstørrelse fra en pæn række, der er mindst [meters] bred. */
        fun sizeFor(meters: Double): Double = SIZES.firstOrNull { it >= meters } ?: SIZES.last()
    }
}
