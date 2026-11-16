package dk.intraon.mink.radio

import dk.intraon.mink.waterfall.Scope
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalibrateTest {
    private val lo = 869_380_000.0
    private val binHz = 500.0
    private val bins = 740
    private val chan = 869_618_000.0
    private val bw = 62_500.0

    private fun pack(db: Double) = (((db - Scope.MIN_DB) / (Scope.MAX_DB - Scope.MIN_DB)).coerceIn(0.0, 1.0) * 255).toInt().toByte()

    /** [rows] rækker støj. [wide] = (første række, antal rækker, midte, dB) for brede udbrud; et smalt signal kommer hvert 4. s. */
    private fun snap(rows: Int, vararg wide: DoubleArray, splatterDb: Double = 0.0): Scope.Snap {
        val rnd = Random(3)
        val db = ByteArray(rows * bins)
        for (r in 0 until rows) for (c in 0 until bins) {
            val hz = lo + (c + 0.5) * binHz
            var v = rnd.nextGaussian()
            if (r % 100 < 10 && Math.abs(hz - 869_624_000.0) < 5_000) v = 22.0
            for (w in wide) if (r >= w[0] && r < w[0] + w[1]) {
                if (Math.abs(hz - w[2]) < bw / 2) v = w[3] + rnd.nextGaussian()
                else if (splatterDb > 0) v = splatterDb + rnd.nextGaussian()
            }
            db[r * bins + c] = pack(v)
        }
        return Scope.Snap(rows, bins, db, lo, binHz, rows.toLong())
    }

    @Test fun finderForskydningenAfDeBredeUdbrud() {
        // trafikken ligger 14 kHz for lavt, som på en dongle der er rettet lidt for meget
        val s = snap(750, doubleArrayOf(100.0, 12.0, chan - 14_000, 34.0), doubleArrayOf(400.0, 20.0, chan - 14_000, 38.0))
        val off = Calibrate.offsetHz(s, 0, chan, bw)!!
        assertEquals(-14_000.0, off, 600.0)
        assertEquals("−65 rettes til −49", -49, Calibrate.ppm(-65, off, chan))
    }

    @Test fun forHoejtGiverLavereKorrektion() {
        val s = snap(750, doubleArrayOf(320.0, 15.0, chan + 40_000, 30.0))
        val off = Calibrate.offsetHz(s, 0, chan, bw)!!
        assertEquals(40_000.0, off, 600.0)
        assertEquals(-46, Calibrate.ppm(0, off, chan))
    }

    @Test fun smalleSignalerTaellerIkke() {
        assertNull(Calibrate.offsetHz(snap(750), 0, chan, bw))
    }

    @Test fun raekkerFraFoerRettelsenTaellerIkke() {
        val s = snap(750, doubleArrayOf(100.0, 12.0, chan + 40_000, 34.0))
        // korrektionen blev ændret ved række 200: udbruddet ved række 100 ligger på den gamle plads
        assertNull(Calibrate.offsetHz(s, 200, chan, bw))
        assertEquals(40_000.0, Calibrate.offsetHz(s, 50, chan, bw)!!, 600.0)
    }

    @Test fun uenigeUdbrudGiverIntetSvar() {
        val s = snap(750, doubleArrayOf(100.0, 10.0, chan - 30_000, 34.0), doubleArrayOf(400.0, 10.0, chan + 30_000, 34.0))
        assertNull(Calibrate.offsetHz(s, 0, chan, bw))
    }

}
