package dk.intraon.mink.waterfall

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/** [Pen] oven på et Android-lærred. */
class CanvasPen(private val c: Canvas) : Pen {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ip = Paint().apply { isFilterBitmap = true }
    private val path = Path()

    private fun fill(argb: Int) { p.style = Paint.Style.FILL; p.color = argb; p.pathEffect = null }
    private fun stroke(argb: Int, width: Float, dash: Float = 0f) {
        p.style = Paint.Style.STROKE; p.color = argb; p.strokeWidth = width
        p.pathEffect = if (dash > 0) DashPathEffect(floatArrayOf(dash, dash), 0f) else null
    }

    override fun rect(x: Float, y: Float, w: Float, h: Float, argb: Int) { fill(argb); c.drawRect(x, y, x + w, y + h, p) }
    override fun frame(x: Float, y: Float, w: Float, h: Float, argb: Int, width: Float) { stroke(argb, width); c.drawRect(x, y, x + w, y + h, p) }
    override fun line(x1: Float, y1: Float, x2: Float, y2: Float, argb: Int, width: Float, dash: Float) {
        stroke(argb, width, dash)
        // en stiplet linje skal tegnes som en sti; drawLine ser bort fra stiplingen på nogle telefoner
        path.reset(); path.moveTo(x1, y1); path.lineTo(x2, y2)
        c.drawPath(path, p)
    }
    override fun polyline(xs: FloatArray, ys: FloatArray, n: Int, argb: Int, width: Float) {
        if (n < 2) return
        stroke(argb, width)
        path.reset(); path.moveTo(xs[0], ys[0])
        for (i in 1 until n) path.lineTo(xs[i], ys[i])
        c.drawPath(path, p)
    }
    override fun text(s: String, x: Float, y: Float, size: Float, argb: Int, align: Int, vertical: Boolean) {
        fill(argb); p.textSize = size
        p.textAlign = when { align < 0 -> Paint.Align.LEFT; align > 0 -> Paint.Align.RIGHT; else -> Paint.Align.CENTER }
        if (vertical) {
            c.save(); c.rotate(-90f, x, y); c.drawText(s, x, y + size * 0.35f, p); c.restore()
        } else c.drawText(s, x, y, p)
        p.textAlign = Paint.Align.LEFT
    }
    override fun textWidth(s: String, size: Float): Float { p.textSize = size; return p.measureText(s) }
    override fun image(px: IntArray, w: Int, h: Int, x: Float, y: Float, dw: Float, dh: Float) {
        val b = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        c.drawBitmap(b, null, RectF(x, y, x + dw, y + dh), ip)
        b.recycle()
    }
}
