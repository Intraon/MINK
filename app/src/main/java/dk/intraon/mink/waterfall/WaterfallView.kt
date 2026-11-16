package dk.intraon.mink.waterfall

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import dk.intraon.mink.core.Hub
import dk.intraon.mink.ui.Ui
import kotlin.math.abs
import kotlin.math.max

/**
 * Vandfald som i en SDR-modtager: spektret øverst, og under det tiden nedad med de nyeste øverst.
 * Svagt = mørkeblåt, stærkt = hvidt, gult og rødt. Tryk på et signal for at gøre det til målet.
 */
class WaterfallView(ctx: Context, private val onTap: (Double) -> Unit) : View(ctx) {
    private val d = resources.displayMetrics.density
    private var bmp: Bitmap? = null
    private var bmpW = 0
    private var drawn = 0L
    private var head = 0
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bp = Paint().apply { isFilterBitmap = false }
    private var rowPx = IntArray(0)
    private val src = Rect()
    private val dst = RectF()
    private val path = Path()
    private var downX = 0f
    private var downY = 0f
    private val palette = IntArray(256) { Scope.color(it / 255f) }
    private val ticker = object : Runnable {
        override fun run() { invalidate(); postDelayed(this, 40) }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); post(ticker) }
    override fun onDetachedFromWindow() { removeCallbacks(ticker); super.onDetachedFromWindow() }

    override fun onDraw(c: Canvas) {
        val vw = width.toFloat(); val vh = height.toFloat()
        c.drawColor(0xFF03103A.toInt())
        val w = Scope.width
        p.style = Paint.Style.FILL; p.textSize = 14 * d; p.color = Ui.DIM
        if (w == 0) {
            val t = "Tryk Start for at se vandfaldet"
            c.drawText(t, (vw - p.measureText(t)) / 2, vh / 2, p)
            return
        }
        // nye rækker ind i billedet (nyeste får det laveste rækkenummer fra head)
        var b = bmp
        val seq = Scope.seq
        if (b == null || bmpW != w || seq < drawn) {
            b = Bitmap.createBitmap(w, Scope.ROWS, Bitmap.Config.ARGB_8888).also { it.eraseColor(0xFF03103A.toInt()) }
            bmp = b; bmpW = w; rowPx = IntArray(w); head = 0
            drawn = max(0L, seq - Scope.ROWS)
        }
        val data = Scope.data
        for (r in max(drawn, seq - Scope.ROWS) until seq) {
            val off = ((r % Scope.ROWS) * w).toInt()
            if (off + w > data.size) break
            for (i in 0 until w) rowPx[i] = palette[data[off + i].toInt() and 0xff]
            head = (head - 1 + Scope.ROWS) % Scope.ROWS
            b.setPixels(rowPx, 0, w, 0, head, w, 1)
        }
        drawn = seq

        val specH = 64 * d; val axisH = 18 * d
        val top = specH + axisH; val wfH = vh - top

        // vandfald: rækkerne head..ROWS-1 er de nyeste, 0..head-1 de ældste
        val n1 = Scope.ROWS - head
        val h1 = wfH * n1 / Scope.ROWS
        src.set(0, head, w, Scope.ROWS); dst.set(0f, top, vw, top + h1)
        c.drawBitmap(b, src, dst, bp)
        if (head > 0) {
            src.set(0, 0, w, head); dst.set(0f, top + h1, vw, vh)
            c.drawBitmap(b, src, dst, bp)
        }

        // spektrum: den nyeste række som kurve
        c.drawRect(0f, 0f, vw, specH, p.apply { color = 0xFF0A0C12.toInt() })
        if (seq > 0) {
            val off = (((seq - 1) % Scope.ROWS) * w).toInt()
            path.reset()
            for (i in 0 until w) {
                val v = (data[off + i].toInt() and 0xff) / 255f
                val x = (i + 0.5f) / w * vw; val y = specH - 2 * d - v * (specH - 6 * d)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            p.style = Paint.Style.STROKE; p.strokeWidth = 1.2f * d; p.color = Ui.ACCENT
            c.drawPath(path, p)
        }

        // frekvensakse
        val span = w * Scope.binHz
        val step = doubleArrayOf(10e3, 25e3, 50e3, 100e3, 200e3, 500e3).first { span / it <= 6 || it == 500e3 }
        p.style = Paint.Style.FILL; p.textSize = 11 * d
        c.drawRect(0f, specH, vw, top, p.apply { color = 0xFF0A0C12.toInt() })
        var f = Math.ceil(Scope.loHz / step) * step
        while (f < Scope.loHz + span) {
            val x = (Scope.columnOf(f) / w * vw).toFloat()
            p.color = 0x40FFFFFF
            c.drawRect(x, 0f, x + d, specH, p)
            p.color = Ui.DIM
            val label = String.format(Ui.DA, "%.3f", f / 1e6)
            c.drawText(label, (x - p.measureText(label) / 2).coerceIn(2 * d, vw - p.measureText(label) - 2 * d), top - 5 * d, p)
            f += step
        }

        // brugerens egen kanal: svagt grønt felt med kanter, så man ser, hvad der ligger inde i den
        if (Hub.channelHz > 0 && Hub.channelBwHz > 0) {
            val x0 = (Scope.columnOf(Hub.channelHz - Hub.channelBwHz / 2) / w * vw).toFloat()
            val x1 = (Scope.columnOf(Hub.channelHz + Hub.channelBwHz / 2) / w * vw).toFloat()
            if (x1 > 0 && x0 < vw) {
                p.style = Paint.Style.FILL
                p.color = 0x1834C759
                c.drawRect(x0, 0f, x1, vh, p)
                p.color = 0xA034C759.toInt()
                c.drawRect(x0, 0f, x0 + d, vh, p)
                c.drawRect(x1 - d, 0f, x1, vh, p)
                p.textSize = 10 * d
                val label = "din kanal"
                if (x1 - x0 > p.measureText(label) + 6 * d) c.drawText(label, x0 + 4 * d, top + 12 * d, p)
            }
        }
        // andre sendere: tynd hvid markering; målet: rød linje gennem det hele
        for (s in Hub.sources) if (!s.target) {
            val x = (Scope.columnOf(s.freqHz) / w * vw).toFloat()
            p.color = 0x90FFFFFF.toInt()
            c.drawRect(x - d / 2, specH - 10 * d, x + d / 2, specH, p)
        }
        val tgt = Hub.targetHz
        if (tgt != null) {
            val x = (Scope.columnOf(tgt) / w * vw).toFloat()
            p.color = 0x30FF3B30
            c.drawRect(x - 7 * d, 0f, x + 7 * d, vh, p)
            p.color = 0xFFFF3B30.toInt()
            c.drawRect(x - d / 2, 0f, x + d / 2, vh, p)
        } else {
            p.color = Ui.TEXT; p.textSize = 13 * d
            val t = "Tryk på et signal for at vælge det som mål"
            c.drawRect(0f, vh - 26 * d, vw, vh, p.apply { color = 0xB00E0F12.toInt() })
            p.color = Ui.TEXT
            c.drawText(t, (vw - p.measureText(t)) / 2, vh - 9 * d, p)
        }
        // tidsskala: ét mærke for hvert 5. sekund
        p.color = 0x90FFFFFF.toInt(); p.textSize = 10 * d
        val perSec = wfH / (Scope.ROWS.toFloat() / Scope.ROWS_PER_SECOND)
        var s = 5
        while (s * perSec < wfH) {
            c.drawText("${s} s", 3 * d, top + s * perSec + 4 * d, p)
            s += 5
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> { downX = e.x; downY = e.y }
            MotionEvent.ACTION_UP -> if (Scope.width > 0 && abs(e.x - downX) < 16 * d && abs(e.y - downY) < 16 * d) {
                performClick()
                onTap(Scope.hzOf(e.x / width * Scope.width.toDouble()))
            }
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }
}
