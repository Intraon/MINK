package dk.intraon.mink.dsp

import dk.intraon.mink.measure.MeasureService
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Detektor og ringlager sammen, som MeasureService bruger dem: udsnittet skal ramme burstet (20 ms stilhed før,
 * signal i midten, stilhed til sidst), og det skal kunne demoduleres til det FSK-sving, der blev sendt.
 */
class ClipAlignmentTest {
    @Test
    fun udsnittetRammerBurstetOgKanDemoduleres() {
        val rate = 1_024_000; val n = 2048; val hz = 869_612_500.0; val dev = 1200.0
        val cfg = BurstDetector.Config(
            tuneHz = BurstDetector.tuneFor(869_400_000.0, 869_650_000.0),
            bandLoHz = 869_400_000.0, bandHiHz = 869_650_000.0, signalBwHz = 6_000.0,
        )
        val det = BurstDetector(cfg)
        val ring = IqRing(2 * n, 1100)
        val decim = IqRing.decimFor(cfg.signalBwHz, rate)
        var clip: ShortArray? = null
        det.onBurst = { b ->
            val first = b.startFrame - 10
            clip = ring.clip(first, b.lastFrame + 5, b.freqHz - cfg.tuneHz, rate, decim)
        }
        val rnd = Random(5); val buf = ByteArray(2 * n); var i = 0L; var ph = 0.0; var bit = 1
        repeat((1.0 * rate / n).toInt()) {
            for (k in 0 until n) {
                var x = rnd.nextGaussian() * 3; var y = rnd.nextGaussian() * 3
                val t = i.toDouble() / rate
                if (t >= 0.3 && t < 0.7) {
                    if (i % (rate / 1200) == 0L) bit = if (rnd.nextBoolean()) 1 else -1
                    ph += 2 * PI * (hz - cfg.tuneHz + bit * dev) / rate
                    x += 20 * cos(ph); y += 20 * sin(ph)
                }
                buf[2 * k] = (127.4 + x).roundToInt().coerceIn(0, 255).toByte()
                buf[2 * k + 1] = (127.4 + y).roundToInt().coerceIn(0, 255).toByte()
                i++
            }
            ring.push(buf)
            det.processBlock(buf, 0, (i - n) * 1000 / rate)
        }
        val iq = clip ?: throw AssertionError("intet burst fundet")
        val m = iq.size / 2
        fun power(from: Int, to: Int): Double {
            var s = 0.0
            for (k in from until to) s += iq[2 * k].toDouble() * iq[2 * k] + iq[2 * k + 1].toDouble() * iq[2 * k + 1]
            return s / (to - from)
        }
        val mid = power(600, m - 600)
        assertTrue("stille før burstet", power(0, 250) < mid / 50)
        assertTrue("stille efter burstet", power(m - 60, m) < mid / 50)
        assertTrue("signal lige efter starten", power(400, 600) > mid / 2)
        // FM-demodulation: gennemsnitligt frekvenssving skal ligge tæt på de ±1200 Hz, der blev sendt
        var sum = 0.0
        for (k in 600 until m - 600) {
            val re = iq[2 * k].toDouble() * iq[2 * k + 2] + iq[2 * k + 1].toDouble() * iq[2 * k + 3]
            val im = iq[2 * k].toDouble() * iq[2 * k + 3] - iq[2 * k + 1].toDouble() * iq[2 * k + 2]
            sum += abs(atan2(im, re))
        }
        val hzDev = sum / (m - 1200) * (rate / decim) / (2 * PI)
        assertEquals("frekvenssving i Hz", dev, hzDev, 150.0)
    }
}
