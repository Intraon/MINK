package dk.intraon.mink.waterfall

import dk.intraon.mink.dsp.BurstDetector
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Vandfaldets data: et signal skal ligge i den rigtige søjle, og et tryk ved siden af skal ramme det. */
class ScopeTest {
    @Test
    fun signaletLiggerDerHvorFrekvensenSigerOgEtTrykVedSidenAfRammerDet() {
        val rate = 1_024_000; val n = 2048; val hz = 869_612_500.0
        val cfg = BurstDetector.Config(
            tuneHz = BurstDetector.tuneFor(869_400_000.0, 869_650_000.0),
            bandLoHz = 869_400_000.0, bandHiHz = 869_650_000.0, signalBwHz = 6_000.0,
        )
        val det = BurstDetector(cfg)
        val binHz = rate.toDouble() / n
        val lo = det.bandBinLo - 20; val hi = det.bandBinHi + 20
        Scope.configure(lo, hi - lo + 1, det.freqOfBin(lo - 0.5), binHz, n)
        det.spectrumSink = { re, im, floor -> Scope.feed(re, im, floor) }

        val rnd = Random(9); val buf = ByteArray(2 * n); var i = 0L; var ph = 0.0
        repeat((2.0 * rate / n).toInt()) {
            for (k in 0 until n) {
                var x = rnd.nextGaussian() * 3; var y = rnd.nextGaussian() * 3
                val t = i.toDouble() / rate
                if (t >= 0.5 && t < 0.9) { ph += 2 * PI * (hz - cfg.tuneHz) / rate; x += 12 * cos(ph); y += 12 * sin(ph) }
                buf[2 * k] = (127.4 + x).roundToInt().coerceIn(0, 255).toByte()
                buf[2 * k + 1] = (127.4 + y).roundToInt().coerceIn(0, 255).toByte()
                i++
            }
            det.processBlock(buf, 0, (i - n) * 1000 / rate)
        }
        assertEquals("25 rækker i sekundet", 50L, Scope.seq)
        assertEquals("bånd + 10 kHz i hver side", 250_000 / 500 + 1 + 40, Scope.width)
        assertEquals(869_390_000.0, Scope.hzOf(0.5), 600.0)

        val col = Scope.columnOf(hz).toInt()
        fun at(row: Int, c: Int) = Scope.data[row * Scope.width + c].toInt() and 0xff
        assertTrue("kraftigt midt i burstet (række 15 = 0,60–0,64 s)", at(15, col) > 150)
        assertTrue("mørkt før burstet", at(5, col) < 40)
        assertTrue("mørkt ved siden af", at(15, col + 30) < 40)
        assertTrue("og skarpt: 2,5 kHz fra signalet er der allerede mørkt", at(15, col + 5) < 40 && at(15, col - 5) < 40)

        assertEquals("tryk 5 kHz ved siden af rammer signalet", hz, Scope.peakNear(hz + 5_000, 8_000.0)!!, 600.0)
        assertNull("intet at ramme 60 kHz væk", Scope.peakNear(hz - 60_000, 8_000.0))
    }
}
