package dk.intraon.mink.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import dk.intraon.mink.core.Hub
import kotlin.math.max

/** Niveau over tid: en prik pr. måling, grå streger i bunden hvor der var stille. Tryk skifter 5 min / hele turen. */
class LevelGraphView(ctx: Context) : View(ctx) {
    var full = false
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = resources.displayMetrics.density
    private val dash = DashPathEffect(floatArrayOf(4 * d, 4 * d), 0f)

    init {
        setOnClickListener { full = !full; invalidate() }
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        c.drawColor(Ui.PANEL)
        val bursts = Hub.bursts
        val silent = Hub.silent
        val end = if (Hub.running) System.currentTimeMillis()
        else max(bursts.lastOrNull()?.t ?: 0L, silent.lastOrNull()?.t ?: 0L) + 1000
        val start = if (full) Hub.sessionStart else end - 300_000
        val span = max(1L, end - start).toFloat()
        fun x(t: Long) = (t - start) / span * w

        p.style = Paint.Style.FILL
        p.textSize = 11 * d
        p.color = Ui.DIM
        c.drawText(if (full) "hele turen" else "sidste 5 min", 6 * d, 13 * d, p)

        // stille perioder
        p.color = 0xFF5F6368.toInt()
        for (s in silent) if (s.t >= start) c.drawRect(x(s.t) - d, h - 5 * d, x(s.t) + d, h - d, p)

        // gain-skift
        p.pathEffect = dash; p.strokeWidth = d; p.style = Paint.Style.STROKE
        for (g in Hub.gainSteps) if (g.t >= start) {
            p.color = Ui.YELLOW; p.style = Paint.Style.STROKE
            c.drawLine(x(g.t), 16 * d, x(g.t), h - 6 * d, p)
            p.style = Paint.Style.FILL; p.pathEffect = null
            c.drawText(Ui.f1(g.to), x(g.t) + 2 * d, 26 * d, p)
            p.pathEffect = dash
        }
        p.pathEffect = null

        // mærker
        p.style = Paint.Style.FILL; p.color = Ui.TEXT
        for (m in Hub.marks) if (m.t >= start) c.drawText("⚑${m.n}", x(m.t) - 4 * d, h - 8 * d, p)

        val pts = bursts.filter { it.t >= start && it.level != null }
        if (pts.isEmpty()) return
        var lo = pts.minOf { it.level!! } - 3
        var hi = pts.maxOf { it.level!! } + 3
        if (hi - lo < 12) { val mid = (hi + lo) / 2; lo = mid - 6; hi = mid + 6 }
        val top = 18 * d; val bottom = h - 10 * d
        fun y(l: Double) = (bottom - (l - lo) / (hi - lo) * (bottom - top)).toFloat()

        p.color = Ui.DIM
        c.drawText(Ui.f1(hi), w - 44 * d, 13 * d, p)
        c.drawText(Ui.f1(lo), w - 44 * d, h - 8 * d, p)

        p.style = Paint.Style.STROKE; p.strokeWidth = d; p.color = 0xFF5F6368.toInt()
        val path = Path()
        pts.forEachIndexed { i, b -> if (i == 0) path.moveTo(x(b.t), y(b.level!!)) else path.lineTo(x(b.t), y(b.level!!)) }
        c.drawPath(path, p)
        p.style = Paint.Style.FILL
        val range = Hub.levelRange()
        for (b in pts) {
            p.color = Hub.colorFor(b.level!!, range)
            c.drawCircle(x(b.t), y(b.level), 3 * d, p)
        }
    }
}
