package dk.intraon.mink.dsp

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Test

/** IQ-udsnittet: signalet skal ende på den rigtige frekvens med den rigtige styrke. */
class IqRingTest {
    private val rate = 1_024_000
    private val n = 2048

    private fun fill(ring: IqRing, blocks: Int, offsetHz: Double, amp: Double) {
        val buf = ByteArray(2 * n); var i = 0L
        repeat(blocks) {
            for (k in 0 until n) {
                val ph = 2 * PI * offsetHz * i / rate
                buf[2 * k] = (127.4 + amp * cos(ph)).roundToInt().toByte()
                buf[2 * k + 1] = (127.4 + amp * sin(ph)).roundToInt().toByte()
                i++
            }
            ring.push(buf)
        }
    }

    @Test
    fun udsnitLiggerOmkringNulHertzMedRigtigStyrke() {
        val ring = IqRing(2 * n, 50)
        fill(ring, 40, 112_500.0 + 1_000.0, 40.0)        // signalet ligger 1 kHz over den frekvens, der klippes om
        val iq = ring.clip(5, 30, 112_500.0, rate, 64)
        val m = iq.size / 2
        assertEquals(26 * n / 64 - IqRing.SKIP, m)
        var re = 0.0; var im = 0.0; var mag = 0.0
        for (k in 0 until m - 1) {
            val ar = iq[2 * k].toDouble(); val ai = iq[2 * k + 1].toDouble()
            val br = iq[2 * k + 2].toDouble(); val bi = iq[2 * k + 3].toDouble()
            re += ar * br + ai * bi; im += ar * bi - ai * br          // conj(a)·b
            mag += sqrt(ar * ar + ai * ai)
        }
        assertEquals("1 kHz ved 16 kHz", 2 * PI * 1000 / 16000, atan2(im, re), 0.005)
        assertEquals("256 pr. ADC-trin, minus filterets dæmpning ved 1 kHz", 40 * 256 * 0.981, mag / (m - 1), 300.0)
    }

    @Test
    fun overskrevneBlokkeUdelades() {
        val ring = IqRing(2 * n, 50)
        fill(ring, 60, 10_000.0, 20.0)
        // blok 1–11 er skrevet over; 12–60 er tilbage
        assertEquals(49 * n / 64 - IqRing.SKIP, ring.clip(1, 99, 10_000.0, rate, 64).size / 2)
    }

    @Test
    fun decimeringFoelgerSignaletsBredde() {
        assertEquals(64, IqRing.decimFor(6_000.0, rate))
        assertEquals(32, IqRing.decimFor(10_000.0, rate))
        assertEquals(4, IqRing.decimFor(100_000.0, rate))
    }
}
