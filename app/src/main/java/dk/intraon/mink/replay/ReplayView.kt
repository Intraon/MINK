package dk.intraon.mink.replay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.widget.OverScroller
import dk.intraon.mink.core.Hub
import dk.intraon.mink.ui.Ui
import dk.intraon.mink.waterfall.Scope
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Vandfaldet over en tidligere tur, genskabt fra loggen ([Replay]): hele båndet på tværs og tiden nedad med det
 * seneste øverst, som i det levende vandfald. Hvert udbrud står på sin frekvens med sin bredde og varighed. Tæt på
 * tegnes udbruddets ægte signal fra bølgeform-filen ([IqClips]); længere ude et felt farvet efter styrken.
 * Øverst ligger målets niveau over hele turen: et tryk dér springer til det tidspunkt. Vandfaldet trækkes med
 * fingeren. [onTime] får tidspunktet i vandfaldets øverste kant, hver gang det flytter sig.
 */
class ReplayView(ctx: Context, private val onTime: (Long) -> Unit) : View(ctx) {
    private val d = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bp = Paint().apply { isFilterBitmap = false }
    private val rect = RectF()
    private val clock = SimpleDateFormat("HH:mm:ss", Ui.DA)
    private val scroller = OverScroller(ctx)
    private var tracker: VelocityTracker? = null
    private var replay: Replay? = null
    private var clips: IqClips? = null
    private var range: Pair<Double, Double>? = null
    private var fresh = false
    private var scrubbing = false
    private var downY = 0f
    private var downT = 0L

    /** Tidspunktet i vandfaldets øverste kant. */
    var t = 0L
        private set
    /** Så mange millisekunder dækker én pixel i højden. */
    var msPerPx = STEPS[1]
        private set

    private val curveH get() = 58 * d
    private val axisH get() = 18 * d
    private val top get() = curveH + axisH

    /** Viser turen [r] fra begyndelsen. [c] er dens bølgeform, eller null, hvis den ikke findes. */
    fun show(r: Replay, c: IqClips?) {
        replay = r; clips = c
        range = r.target.takeIf { it.isNotEmpty() }?.let { list -> list.minOf { it.level } to list.maxOf { it.level } }
        msPerPx = STEPS[1]; fresh = true
        scroller.forceFinished(true)
        if (height > 0) toStart()
        invalidate()
    }

    fun clear() { replay = null; clips = null; invalidate() }

    /** Skifter mellem tre afstande: tæt på (et udbrud fylder en finger), som det levende vandfald, og overblik. */
    fun zoom() {
        msPerPx = STEPS[(STEPS.indexOfFirst { it == msPerPx } + 1) % STEPS.size]
        scroller.forceFinished(true)
        moveTo(t)
    }

    /** Er der zoomet så langt ind, at udbruddets eget signal kan ses? */
    val detailed: Boolean get() = msPerPx <= STEPS[1]

    /** Hvad den aktuelle afstand viser, til knappen. */
    fun zoomName(): String = when (msPerPx) { STEPS[0] -> "tæt på"; STEPS[1] -> "normal"; else -> "overblik" }

    /** Målets niveau ved tidspunktet [at]: den nærmeste måling inden for et udbruds afstand. */
    fun levelAt(at: Long): Double? = replay?.target?.minByOrNull { abs(it.t - at) }?.takeIf { abs(it.t - at) < 4_500 }?.level

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (fresh && h > 0) toStart()
    }

    /** Turens begyndelse nederst i billedet. */
    private fun toStart() {
        val r = replay ?: return
        fresh = false
        moveTo(r.startMs + ((height - top) * msPerPx).toLong())
    }

    private fun moveTo(time: Long) {
        val r = replay ?: return
        t = time.coerceIn(r.startMs, max(r.startMs, r.endMs + 500))
        onTime(t)
        invalidate()
    }

    private fun xOf(hz: Double, r: Replay) = ((hz - r.bandLoHz) / (r.bandHiHz - r.bandLoHz) * width).toFloat()
    private fun yOf(time: Long) = top + ((t - time) / msPerPx).toFloat()

    override fun onDraw(c: Canvas) {
        c.drawColor(Ui.BG)
        val r = replay ?: return
        if (r.bandHiHz <= r.bandLoHz || width == 0) return
        val w = width.toFloat(); val h = height.toFloat()
        drawCurve(c, r, w)
        drawAxis(c, r)

        c.save(); c.clipRect(0f, top, w, h)
        c.drawColor(Scope.color(0.03f))
        val chan = r.chanHz; val chanBw = r.chanBwHz
        if (chan != null && chanBw != null) {
            p.style = Paint.Style.FILL; p.color = 0x2230D158
            c.drawRect(xOf(chan - chanBw / 2, r), top, xOf(chan + chanBw / 2, r), h, p)
        }
        val detail = detailed && clips != null
        val seen = ArrayList<Double>()
        for (b in r.between(t - ((h - top) * msPerPx).toLong() - 1_000, t + 1_000)) {
            seen.add(b.freqHz)
            val bmp = if (detail) clips?.get(b) else null
            if (bmp != null) {
                val from = b.t - b.iqPreMs
                rect.set(xOf(b.iqFcHz - r.iqRate / 2.0, r), yOf(from + b.iqn * 1000L / r.iqRate), xOf(b.iqFcHz + r.iqRate / 2.0, r), yOf(from))
                c.drawBitmap(bmp, null, rect, bp)
            } else {
                val y1 = yOf(b.t); val y0 = minOf(yOf(b.t + b.durMs), y1 - 2 * d)
                p.style = Paint.Style.FILL
                p.color = Scope.color(((b.snr - Scope.MIN_DB) / (Scope.MAX_DB - Scope.MIN_DB)).toFloat().coerceIn(0.25f, 1f))
                c.drawRect(xOf(b.freqHz - b.bwHz / 2, r), y0, max(xOf(b.freqHz + b.bwHz / 2, r), xOf(b.freqHz - b.bwHz / 2, r) + 2 * d), y1, p)
            }
        }
        val strip = drawChannels(c, r, seen, w)
        // klokkeslæt langs venstre kant
        val step = when (msPerPx) { STEPS[0] -> 1_000L; STEPS[1] -> 5_000L; else -> 60_000L }
        p.textSize = 11 * d; p.textAlign = Paint.Align.LEFT
        var mark = t / step * step
        while (yOf(mark) < h) {
            val y = yOf(mark)
            if (y > top + max(12 * d, strip + 13 * d)) {
                p.color = 0x50FFFFFF; c.drawRect(0f, y, 10 * d, y + d, p)
                p.color = 0xD0FFFFFF.toInt(); c.drawText(clock.format(Date(mark)), 4 * d, y - 3 * d, p)
            }
            mark -= step
        }
        c.restore()
    }

    /**
     * Skriver frekvensen over hver kanal, der har udbrud i billedet, på et mørkt bånd øverst i vandfaldet: aksen har
     * kun et tal for hver 100 kHz, og kanalerne ligger tættere end det. Mærker, der ville dække hinanden, står i to
     * rækker ([Channels.rows]). Returnerer båndets højde, 0 uden udbrud.
     */
    private fun drawChannels(c: Canvas, r: Replay, freqs: List<Double>, w: Float): Float {
        val chans = Channels.of(freqs)
        p.textSize = 10.5f * d; p.textAlign = Paint.Align.CENTER
        val width = p.measureText("869,000") + 6 * d
        val xs = chans.map { xOf(it, r).coerceIn(width / 2, w - width / 2) }
        val rows = Channels.rows(xs, width)
        val used = (rows.maxOrNull() ?: -1) + 1
        if (used == 0) return 0f
        val rowH = 15 * d; val height = used * rowH + 4 * d
        p.style = Paint.Style.FILL; p.color = 0xD00E0F12.toInt()
        c.drawRect(0f, top, w, top + height, p)
        for (i in chans.indices) {
            if (rows[i] < 0) continue
            p.color = 0xFFFFFFFF.toInt()
            c.drawText(String.format(Ui.DA, "%.3f", chans[i] / 1e6), xs[i], top + rows[i] * rowH + 12 * d, p)
            // en lille streg ned mod kanalen, så mærket i øverste række ikke forveksles med naboens
            p.color = 0x90FFFFFF.toInt()
            c.drawRect(xOf(chans[i], r) - d / 2, top + (rows[i] + 1) * rowH, xOf(chans[i], r) + d / 2, top + height, p)
        }
        p.textAlign = Paint.Align.LEFT
        return height
    }

    /** Målets niveau over hele turen, med en streg dér, hvor vandfaldet står. */
    private fun drawCurve(c: Canvas, r: Replay, w: Float) {
        p.style = Paint.Style.FILL; p.color = Ui.PANEL
        c.drawRect(0f, 0f, w, curveH, p)
        val span = max(1L, r.endMs - r.startMs).toDouble()
        val rg = range
        if (rg != null) {
            val lo = rg.first; val hi = max(rg.second, lo + 1)
            for (b in r.target) {
                p.color = Hub.colorFor(b.level, rg)
                c.drawCircle(((b.t - r.startMs) / span * w).toFloat(), (curveH - 6 * d - (b.level - lo) / (hi - lo) * (curveH - 14 * d)).toFloat(), 1.6f * d, p)
            }
        }
        val x = ((t - r.startMs) / span * w).toFloat().coerceIn(0f, w)
        p.color = 0xFFFFFFFF.toInt(); c.drawRect(x - d, 0f, x + d, curveH, p)
    }

    private fun drawAxis(c: Canvas, r: Replay) {
        p.style = Paint.Style.FILL; p.textSize = 11 * d; p.textAlign = Paint.Align.CENTER
        var hz = Math.ceil(r.bandLoHz / 100_000) * 100_000
        while (hz < r.bandHiHz) {
            val x = xOf(hz, r)
            p.color = 0x50FFFFFF; c.drawRect(x, curveH + axisH - 4 * d, x + d, curveH + axisH, p)
            p.color = Ui.DIM; c.drawText(String.format(Ui.DA, "%.3f", hz / 1e6), x, curveH + axisH - 6 * d, p)
            hz += 100_000
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val r = replay ?: return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scroller.forceFinished(true)
                scrubbing = e.y < top
                downY = e.y; downT = t
                tracker?.recycle(); tracker = VelocityTracker.obtain().also { it.addMovement(e) }
                parent?.requestDisallowInterceptTouchEvent(true)
                if (scrubbing) scrub(e.x, r)
            }
            MotionEvent.ACTION_MOVE -> {
                tracker?.addMovement(e)
                // trækkes vandfaldet nedad, kommer det senere frem foroven
                if (scrubbing) scrub(e.x, r) else moveTo(downT + ((e.y - downY) * msPerPx).toLong())
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val tr = tracker
                if (!scrubbing && tr != null && e.actionMasked == MotionEvent.ACTION_UP) {
                    tr.computeCurrentVelocity(1000)
                    val max = ((r.endMs + 500 - r.startMs) / msPerPx).toInt()
                    scroller.fling(0, ((t - r.startMs) / msPerPx).roundToInt(), 0, tr.yVelocity.roundToInt(), 0, 0, 0, max)
                    postInvalidateOnAnimation()
                }
                tr?.recycle(); tracker = null
            }
        }
        return true
    }

    private fun scrub(x: Float, r: Replay) = moveTo(r.startMs + ((x / width).coerceIn(0f, 1f) * (r.endMs - r.startMs)).toLong())

    override fun computeScroll() {
        val r = replay ?: return
        if (scroller.computeScrollOffset()) moveTo(r.startMs + (scroller.currY * msPerPx).toLong())
    }

    companion object {
        /** Millisekunder pr. pixel: tæt på, som det levende vandfald, og overblik. */
        private val STEPS = doubleArrayOf(4.0, 20.0, 200.0)
    }
}
