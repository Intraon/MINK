package dk.intraon.mink.replay

import dk.intraon.mink.dsp.Fft
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10

/**
 * Spektret af et IQ-udsnit over tid: det ægte signal i et udbrud, som det ser ud i et vandfald.
 *
 * Ren Kotlin uden Android.
 */
object ClipSpectrum {
    /** Felter pr. række. Ved 16 kHz er et felt 250 Hz bredt, og en række varer 4 ms. */
    const val N = 64

    /**
     * Én række pr. [N] samples, i tidsrækkefølge. Hver række har [N] felter fra udsnittets laveste frekvens til dets
     * højeste, med 0 Hz i felt N/2, i dB over udsnittets eget støjgulv. [iq] er I,Q,I,Q…
     *
     * Støjgulvet måles i de første [quiet] samples, som ligger før udbruddet. Er der færre end to rækker af dem,
     * bruges den nederste fjerdedel af alle felter i stedet: støjen ved siden af signalet.
     */
    fun rows(iq: ShortArray, quiet: Int = 0): Array<FloatArray> {
        val count = iq.size / 2 / N
        if (count == 0) return emptyArray()
        val fft = Fft(N)
        val window = DoubleArray(N) { 0.5 - 0.5 * cos(2 * PI * it / (N - 1)) }
        val re = DoubleArray(N); val im = DoubleArray(N)
        val out = Array(count) { FloatArray(N) }
        for (r in 0 until count) {
            for (i in 0 until N) { re[i] = iq[2 * (r * N + i)] * window[i]; im[i] = iq[2 * (r * N + i) + 1] * window[i] }
            fft.transform(re, im)
            // FFT'en har 0 Hz først og de negative frekvenser sidst: de to halvdele byttes, så frekvensen stiger gennem rækken
            for (i in 0 until N) {
                val k = (i + N / 2) % N
                out[r][i] = (10 * log10(re[k] * re[k] + im[k] * im[k] + 1e-9)).toFloat()
            }
        }
        val lead = minOf(quiet / N, count)
        val floor = if (lead >= 2) FloatArray(lead * N) { out[it / N][it % N] }.also { it.sort() }.let { it[it.size / 2] }
        else FloatArray(count * N) { out[it / N][it % N] }.also { it.sort() }.let { it[it.size / 4] }
        for (row in out) for (i in 0 until N) row[i] -= floor
        return out
    }
}
