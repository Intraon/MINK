package dk.intraon.mink.waterfall

import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Billedet af en måling: de seneste ca. 30 sekunder af vandfaldet med tiden vandret og frekvensen lodret,
 * brugerens kanal markeret, og nedenunder styrken over tid i ét udvalgt frekvensområde (målet eller kanalen).
 * Det er den figur, man viser andre, når man vil dokumentere en forstyrrelse.
 */
object Figure {
    const val W = 1400
    const val H = 910
    /** Farveskalaen i dB over støjgulvet. */
    const val CMIN = -3.0
    const val CMAX = 35.0

    class Input(
        val rows: Int,
        val bins: Int,
        /** dB over gulvet pakket som i [Scope]; række 0 er den ældste, [rows]·[bins] værdier. */
        val db: ByteArray,
        val loHz: Double,
        val binHz: Double,
        val rowSec: Double,
        /** Brugerens kanal; 0 = ingen. */
        val chanHz: Double,
        val chanBwHz: Double,
        /** Frekvensområdet til kurven nederst, og hvad det er ("målet", "din kanal" …). */
        val traceLoHz: Double,
        val traceHiHz: Double,
        val traceName: String,
        /** Linjen øverst: app, tidspunkt og lignende. */
        val caption: String,
    )

    private val DA: Locale = Locale.forLanguageTag("da-DK")
    private const val INK = 0xFF202124.toInt()
    private const val GRID = 0xFFE0E0E0.toInt()
    private const val TRACE = 0xFFC62828.toInt()

    /** Turbo-farveskalaen (mørkeblå → blå → grøn → gul → rød → mørkerød) for x i 0..1. */
    fun turbo(x0: Double): Int {
        val x = x0.coerceIn(0.0, 1.0)
        val x2 = x * x; val x3 = x2 * x; val x4 = x2 * x2; val x5 = x4 * x
        val r = 0.13572138 + 4.61539260 * x - 42.66032258 * x2 + 132.13108234 * x3 - 152.94239396 * x4 + 59.28637943 * x5
        val g = 0.09140261 + 2.19418839 * x + 4.84296658 * x2 - 14.18503333 * x3 + 4.27729857 * x4 + 2.82956604 * x5
        val b = 0.10667330 + 12.64194608 * x - 60.58204836 * x2 + 110.36276771 * x3 - 89.90310912 * x4 + 27.34824973 * x5
        fun c(v: Double) = (v.coerceIn(0.0, 1.0) * 255).toInt()
        return (0xFF shl 24) or (c(r) shl 16) or (c(g) shl 8) or c(b)
    }

    /** Styrken pr. række i [Input.traceLoHz]..[Input.traceHiHz]: middel af effekten over søjlerne, i dB over gulvet. */
    fun trace(inp: Input): DoubleArray {
        val lo = floor((inp.traceLoHz - inp.loHz) / inp.binHz).toInt().coerceIn(0, inp.bins - 1)
        val hi = (ceil((inp.traceHiHz - inp.loHz) / inp.binHz).toInt() - 1).coerceIn(lo, inp.bins - 1)
        return DoubleArray(inp.rows) { r ->
            var sum = 0.0
            for (c in lo..hi) sum += 10.0.pow(Scope.dbOf(inp.db[r * inp.bins + c]) / 10)
            10 * log10(sum / (hi - lo + 1))
        }
    }

    /**
     * De rækker, hvor et udbrud begynder: kurven kommer over [onDb] efter at have ligget under i mindst et halvt sekund.
     * Er optagelsen allerede i gang med et udbrud, tæller det ikke som en begyndelse.
     */
    fun onsets(tr: DoubleArray, rowSec: Double, onDb: Double = 8.0): List<Int> {
        val quiet = max(1, (0.5 / rowSec).toInt())
        val res = ArrayList<Int>()
        var below = 0
        for (i in tr.indices) {
            if (tr[i] >= onDb) { if (below >= quiet) res.add(i); below = 0 } else below++
        }
        return res
    }

    /** Pæn afstand mellem aksemærker, så der højst bliver [most] af dem på [span]. */
    private fun step(span: Double, most: Int, choices: DoubleArray): Double = choices.firstOrNull { span / it <= most } ?: choices.last()

    fun draw(pen: Pen, inp: Input) {
        pen.rect(0f, 0f, W.toFloat(), H.toFloat(), 0xFFFFFFFF.toInt())
        val x0 = 100f; val x1 = 1180f
        val topY = 40f; val topH = 562f
        val lowY = 652f; val lowH = 190f
        val secs = inp.rows * inp.rowSec
        fun xOf(sec: Double) = x0 + (sec / secs * (x1 - x0)).toFloat()
        val hiHz = inp.loHz + inp.bins * inp.binHz
        fun yOfHz(hz: Double) = topY + ((hiHz - hz) / (hiHz - inp.loHz) * topH).toFloat()

        pen.text(inp.caption, x0, 24f, 17f, 0xFF5F6368.toInt())

        // vandfaldet: tiden mod højre, høj frekvens øverst
        val px = IntArray(inp.rows * inp.bins)
        val pal = IntArray(256) { turbo((Scope.dbOf(it.toByte()) - CMIN) / (CMAX - CMIN)) }
        for (r in 0 until inp.rows) for (c in 0 until inp.bins) {
            px[(inp.bins - 1 - c) * inp.rows + r] = pal[inp.db[r * inp.bins + c].toInt() and 0xff]
        }
        pen.image(px, inp.rows, inp.bins, x0, topY, x1 - x0, topH)

        // brugerens kanal: lyst felt og stiplet midterlinje
        if (inp.chanHz > 0 && inp.chanBwHz > 0) {
            val ya = max(topY, yOfHz(inp.chanHz + inp.chanBwHz / 2)); val yb = min(topY + topH, yOfHz(inp.chanHz - inp.chanBwHz / 2))
            if (yb > ya) {
                pen.rect(x0, ya, x1 - x0, yb - ya, 0x2CFFFFFF)
                val ym = yOfHz(inp.chanHz)
                if (ym > topY && ym < topY + topH) pen.line(x0, ym, x1, ym, 0xE6FFFFFF.toInt(), 1.2f, 5f)
            }
        }
        pen.frame(x0, topY, x1 - x0, topH, INK, 1.2f)

        // frekvensakse
        val fStep = step(hiHz - inp.loHz, 9, doubleArrayOf(5e3, 10e3, 25e3, 50e3, 100e3, 250e3, 500e3, 1e6))
        val fFmt = if (fStep % 10e3 == 0.0) "%.2f" else "%.3f"
        var f = ceil(inp.loHz / fStep) * fStep
        while (f <= hiHz) {
            val y = yOfHz(f)
            if (y >= topY + 2 && y <= topY + topH - 2) {
                pen.line(x0 - 6, y, x0, y, INK, 1.2f)
                pen.text(String.format(DA, fFmt, f / 1e6), x0 - 10, y + 6, 17f, INK, 1)
            }
            f += fStep
        }
        pen.text("MHz", 14f, topY + topH / 2, 17f, INK, 0, true)

        // tidsakse (fælles for de to felter)
        val tStep = step(secs, 8, doubleArrayOf(1.0, 2.0, 5.0, 10.0, 20.0, 30.0, 60.0))
        var t = 0.0
        while (t <= secs + 1e-9) {
            val x = xOf(t)
            pen.line(x, topY + topH, x, topY + topH + 6, INK, 1.2f)
            pen.text(String.format(DA, "%.0f", t), x, topY + topH + 26, 17f, INK, 0)
            t += tStep
        }

        // farveskala
        val cbX = 1196f; val cbW = 30f
        val cb = IntArray(256) { turbo(1.0 - it / 255.0) }
        pen.image(cb, 1, 256, cbX, topY, cbW, topH)
        pen.frame(cbX, topY, cbW, topH, INK, 1.2f)
        var v = ceil(CMIN / 5) * 5
        while (v <= CMAX) {
            val y = topY + ((CMAX - v) / (CMAX - CMIN) * topH).toFloat()
            pen.line(cbX + cbW, y, cbX + cbW + 6, y, INK, 1.2f)
            pen.text(String.format(DA, "%.0f", v + 0.0), cbX + cbW + 11, y + 6, 17f, INK)
            v += 5
        }
        pen.text("dB over gulv", cbX + cbW + 62, topY + topH / 2, 17f, INK, 0, true)

        // kurven nederst: styrken i ét frekvensområde
        val tr = trace(inp)
        val top = max(20.0, ceil(((tr.maxOrNull() ?: 0.0) + 2) / 20) * 20)
        val bot = -4.0
        fun yOfDb(db: Double) = lowY + ((top - db) / (top - bot) * lowH).toFloat()
        var g = 0.0
        while (g <= top) {
            val y = yOfDb(g)
            pen.line(x0, y, x1, y, GRID, 1f)
            pen.line(x0 - 6, y, x0, y, INK, 1.2f)
            pen.text(String.format(DA, "%.0f", g), x0 - 10, y + 6, 17f, INK, 1)
            g += if (top > 60) 20.0 else 10.0
        }
        t = 0.0
        while (t <= secs + 1e-9) {
            val x = xOf(t)
            pen.line(x, lowY, x, lowY + lowH, GRID, 1f)
            pen.line(x, lowY + lowH, x, lowY + lowH + 6, INK, 1.2f)
            pen.text(String.format(DA, "%.0f", t), x, lowY + lowH + 26, 17f, INK, 0)
            t += tStep
        }
        for (r in onsets(tr, inp.rowSec)) {
            val x = xOf(r * inp.rowSec)
            pen.line(x, lowY, x, lowY + lowH, 0xFF707070.toInt(), 1f, 3f)
        }
        val xs = FloatArray(tr.size) { xOf((it + 0.5) * inp.rowSec) }
        val ys = FloatArray(tr.size) { yOfDb(tr[it].coerceIn(bot, top)) }
        pen.polyline(xs, ys, tr.size, TRACE, 1.6f)
        pen.frame(x0, lowY, x1 - x0, lowH, INK, 1.2f)
        pen.text("dB over gulv", 34f, lowY + lowH / 2, 17f, INK, 0, true)
        pen.text("sekunder", (x0 + x1) / 2, lowY + lowH + 50, 17f, INK, 0)

        // forklaring til kurven
        val label = String.format(DA, "%.3f–%.3f MHz (%s)", inp.traceLoHz / 1e6, inp.traceHiHz / 1e6, inp.traceName)
        val lw = pen.textWidth(label, 16f) + 62
        val lx = x1 - lw - 10; val ly = lowY + 10
        pen.rect(lx, ly, lw, 30f, 0xF2FFFFFF.toInt())
        pen.frame(lx, ly, lw, 30f, 0xFFBDBDBD.toInt(), 1f)
        pen.line(lx + 10, ly + 15, lx + 42, ly + 15, TRACE, 1.6f)
        pen.text(label, lx + 52, ly + 21, 16f, INK)
    }
}
