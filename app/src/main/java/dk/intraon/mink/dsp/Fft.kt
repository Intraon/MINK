package dk.intraon.mink.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Radix-2 kompleks FFT, in-place, uden allokeringer pr. kald. */
class Fft(val n: Int) {
    private val log2n = Integer.numberOfTrailingZeros(n)
    private val rev = IntArray(n)
    private val cosT = DoubleArray(n / 2)
    private val sinT = DoubleArray(n / 2)

    init {
        require(n > 1 && n and (n - 1) == 0) { "n skal være en potens af 2" }
        for (i in 0 until n) {
            var r = 0
            for (b in 0 until log2n) r = r or (((i shr b) and 1) shl (log2n - 1 - b))
            rev[i] = r
        }
        for (i in 0 until n / 2) {
            cosT[i] = cos(2 * PI * i / n)
            sinT[i] = -sin(2 * PI * i / n)
        }
    }

    fun transform(re: DoubleArray, im: DoubleArray) {
        for (i in 0 until n) {
            val j = rev[i]
            if (j > i) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var size = 2
        while (size <= n) {
            val half = size shr 1
            val step = n / size
            var i = 0
            while (i < n) {
                for (k in 0 until half) {
                    val wr = cosT[k * step]
                    val wi = sinT[k * step]
                    val a = i + k
                    val b = a + half
                    val tr = re[b] * wr - im[b] * wi
                    val ti = re[b] * wi + im[b] * wr
                    re[b] = re[a] - tr; im[b] = im[a] - ti
                    re[a] += tr; im[a] += ti
                }
                i += size
            }
            size = size shl 1
        }
    }
}
