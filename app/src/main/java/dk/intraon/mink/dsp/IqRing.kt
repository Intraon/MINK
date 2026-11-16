package dk.intraon.mink.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Ringlager med de seneste rå IQ-blokke, så selve bølgeformen af et burst kan gemmes, efter det er fundet.
 *
 * [clip] blander de valgte blokke ned, så signalets frekvens havner i 0 Hz, og decimerer med et CIC-filter af
 * 3. orden. Resultatet er et smalt IQ-udsnit (16 bit, I og Q skiftevis), der kan demoduleres bagefter.
 * Skala: 256 = ét trin på dongle'ns 8-bit ADC. Dæmpning i kanten af det nyttige område (±¼ af udsnittets
 * sample rate): ca. 2,7 dB.
 *
 * Ren Kotlin uden Android. En blok får nummer efter, hvornår den kom ([count] = nummeret på den seneste), og det
 * er samme tælling som BurstDetector bruger, når begge får hver blok.
 */
class IqRing(private val blockBytes: Int, private val blocks: Int) {
    private val buf = ByteArray(blockBytes * blocks)

    @Volatile var count = 0L
        private set

    fun push(src: ByteArray) {
        System.arraycopy(src, 0, buf, ((count % blocks) * blockBytes).toInt(), blockBytes)
        count++
    }

    companion object {
        /** De første output-samples er filterets indsvingning og udelades. */
        const val SKIP = 3
        private const val PRE_BITS = 6

        /** Decimering (2-potens, 4…64), så udsnittets sample rate er mindst 2,6 × signalets bredde. */
        fun decimFor(signalBwHz: Double, sampleRate: Int): Int {
            var d = 64
            while (d > 4 && sampleRate / d < 2.6 * signalBwHz) d /= 2
            return d
        }
    }

    /**
     * IQ-udsnit af blokkene [first0]..[last0] (1-baseret), flyttet [offsetHz] ned og decimeret [decim] gange.
     * Blokke, der er skrevet over eller ikke kommet endnu, udelades. Returnerer I,Q,I,Q…
     */
    fun clip(first0: Long, last0: Long, offsetHz: Double, sampleRate: Int, decim: Int): ShortArray {
        val first = max(first0, max(1L, count - blocks + 2))
        val last = min(last0, count)
        if (last < first) return ShortArray(0)
        val perBlock = blockBytes / 2
        val nOut = ((last - first + 1) * perBlock / decim).toInt() - SKIP
        if (nOut <= 0) return ShortArray(0)
        val out = ShortArray(2 * nOut)
        val shift = 3 * Integer.numberOfTrailingZeros(decim) + PRE_BITS - 8
        val scale = (1 shl PRE_BITS).toDouble()

        // roterende viser i stedet for cos/sin pr. sample
        val w = -2 * PI * offsetHz / sampleRate
        val wr = cos(w); val wi = sin(w)
        var zr = 1.0; var zi = 0.0
        // CIC: tre integratorer ved fuld rate, tre differenser ved den lave rate (heltal, må gerne løbe over)
        var a1r = 0L; var a2r = 0L; var a3r = 0L; var a1i = 0L; var a2i = 0L; var a3i = 0L
        var d1r = 0L; var d2r = 0L; var d3r = 0L; var d1i = 0L; var d2i = 0L; var d3i = 0L
        var k = 0; var produced = 0; var o = 0

        for (b in first..last) {
            var p = ((b - 1) % blocks).toInt() * blockBytes
            for (s in 0 until perBlock) {
                val xi = (buf[p].toInt() and 0xff) - 127.4
                val xq = (buf[p + 1].toInt() and 0xff) - 127.4
                p += 2
                a1r += ((xi * zr - xq * zi) * scale).roundToLong(); a2r += a1r; a3r += a2r
                a1i += ((xi * zi + xq * zr) * scale).roundToLong(); a2i += a1i; a3i += a2i
                val t = zr * wr - zi * wi; zi = zr * wi + zi * wr; zr = t
                if (++k == decim) {
                    k = 0
                    val c1r = a3r - d1r; d1r = a3r; val c2r = c1r - d2r; d2r = c1r; val c3r = c2r - d3r; d3r = c2r
                    val c1i = a3i - d1i; d1i = a3i; val c2i = c1i - d2i; d2i = c1i; val c3i = c2i - d3i; d3i = c2i
                    if (produced >= SKIP && o < out.size) {
                        out[o++] = (c3r shr shift).coerceIn(-32768L, 32767L).toShort()
                        out[o++] = (c3i shr shift).coerceIn(-32768L, 32767L).toShort()
                    }
                    produced++
                }
            }
            val mag = sqrt(zr * zr + zi * zi); zr /= mag; zi /= mag
        }
        return out
    }
}
