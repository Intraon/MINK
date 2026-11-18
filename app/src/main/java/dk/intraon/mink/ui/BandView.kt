package dk.intraon.mink.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/** Tegner et [BandPicture]: båndet, din kanal og rammen om det, modtageren ser ad gangen, med tal under. */
class BandView(ctx: Context) : View(ctx) {
    private val d = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    private var picture: BandPicture? = null
    private var from = ""
    private var to = ""
    private var seen = ""

    /** Viser [picture] med teksterne [from] og [to] under båndets ender og [seen] under det, modtageren ser. */
    fun show(picture: BandPicture?, from: String, to: String, seen: String) {
        this.picture = picture; this.from = from; this.to = to; this.seen = seen
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val pic = picture ?: return
        val w = width.toFloat(); val top = 6 * d; val bottom = 34 * d; val round = 4 * d
        fun x(f: Float) = f * (w - 2 * d) + d
        p.style = Paint.Style.FILL
        p.color = if (pic.tooWide) 0xFF5A2326.toInt() else 0xFF1E4F7A.toInt()
        r.set(x(pic.bandFrom), top, x(pic.bandTo), bottom); c.drawRoundRect(r, round, round, p)
        if (pic.tooWide) { p.color = 0xFF1E4F7A.toInt(); r.set(x(pic.seenFrom), top - 3 * d, x(pic.seenTo), bottom + 3 * d); c.drawRoundRect(r, round, round, p) }
        pic.channel?.let { (a, b) -> p.color = 0x993DDC84.toInt(); c.drawRect(x(a), top, maxOf(x(b), x(a) + 2 * d), bottom, p) }
        p.style = Paint.Style.STROKE; p.strokeWidth = d
        p.color = if (pic.tooWide) Ui.TEXT else 0xFF5F6368.toInt()
        val edge = if (pic.tooWide) 3 * d else 0f
        r.set(x(pic.seenFrom), top - edge, x(pic.seenTo), bottom + edge); c.drawRoundRect(r, round, round, p)
        p.style = Paint.Style.FILL; p.textSize = 11 * d
        val y = 52 * d
        p.color = 0xFFC4C9CF.toInt(); p.textAlign = Paint.Align.LEFT; c.drawText(from, d, y, p)
        // tallet for båndets øvre ende står under enden, når der er plads til det mellem de to andre tekster
        val right = if (pic.tooWide) w - d else x(pic.bandTo)
        val room = right - p.measureText(to) / 2 > p.measureText(from) + 8 * d
        if (pic.tooWide) { p.textAlign = Paint.Align.RIGHT; c.drawText(to, w - d, y, p) }
        else {
            if (room) { p.textAlign = Paint.Align.CENTER; c.drawText(to, right, y, p) }
            p.color = Ui.DIM; p.textAlign = Paint.Align.RIGHT
            if (w - d - p.measureText(seen) > right + p.measureText(to) / 2 + 8 * d) c.drawText(seen, w - d, y, p)
        }
    }
}
