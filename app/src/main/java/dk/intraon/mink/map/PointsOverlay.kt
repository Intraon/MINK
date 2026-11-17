package dk.intraon.mink.map

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.Shader
import dk.intraon.mink.core.Hub
import dk.intraon.mink.core.MarkKind
import dk.intraon.mink.direction.Bearing
import dk.intraon.mink.ui.Ui
import kotlin.math.max
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay

/** Så lang tegnes en pejling på kortet (meter). */
private const val BEARING_M = 3_000.0
/** Farven på spor og felter, hvor der blev lyttet uden at høre målet. */
private const val QUIET = 0xFF808891.toInt()

/**
 * Målingerne på kortet. To visninger:
 * - spor: turen som en linje, farvet glidende efter niveauet fra måling til måling og grå, hvor der var stille.
 *   Den stærkeste måling får en hvid ring med sit niveau.
 * - varmekort ([heat]): målingerne samlet i felter, farvet efter feltets gennemsnitlige niveau; det stærkeste
 *   felt får en hvid ramme. Grå felter = lyttet, intet hørt.
 * Begge viser den tur, der kører, sammen med de tidligere ture, der er lagt ind (MapData).
 */

class PointsOverlay(private val d: Float) : Overlay() {
    var heat = false
    /**
     * Sat: laget tegner kun disse punkter og ikke telefonens egen position. Bruges til et kortbillede af én tur.
     * Ellers tegnes de indlæste ture sammen med den tur, der kører.
     */
    var only: TripPoints? = null
    /** Tekst til forklaringen under kortet; sættes hver gang varmekortet tegnes. */
    @Volatile var heatLegend = ""
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pt = Point()
    private val pt2 = Point()
    private val gp = GeoPoint(0.0, 0.0)
    private var grid: HeatGrid? = null
    private var gridStamp = -1L
    private var pieces: List<Track.Piece> = emptyList()
    private var piecesStamp = -1L

    override fun draw(c: Canvas, mv: Projection) {
        if (heat) drawHeat(c, mv) else drawTrack(c, mv)
        drawMarksAndMe(c, mv)
    }

    private fun px(mv: Projection, lat: Double, lon: Double, into: Point = pt) {
        gp.setCoords(lat, lon); mv.toPixels(gp, into)
    }

    private fun bursts() = only?.let { listOf(it.bursts) } ?: listOf(MapData.bursts, Hub.bursts)
    private fun silent() = only?.let { listOf(it.silent) } ?: listOf(MapData.silent, Hub.silent)
    private fun marks() = only?.let { listOf(it.marks) } ?: listOf(MapData.marks, Hub.marks)
    private fun bearings() = only?.let { listOf(it.bearings) } ?: listOf(MapData.bearings, Hub.bearings)

    /** Ændrer sig, når der er kommet målinger til eller andre ture er lagt ind. */
    private fun stamp() = bursts().sumOf { it.size } * 7L + silent().sumOf { it.size } * 5L + MapData.sessions * 1_000_003L

    private fun drawTrack(c: Canvas, mv: Projection) {
        val stamp = stamp()
        if (stamp != piecesStamp) { pieces = Track.pieces((bursts() + silent()).flatten()); piecesStamp = stamp }
        val o = only
        val range = if (o != null) o.range() else MapData.range()
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND

        // en mørk kant under hele sporet, så farverne står klart på kortet, også hvor sporet krydser sig selv
        p.strokeWidth = 9 * d; p.color = 0xFF202124.toInt()
        for (q in pieces) line(c, mv, q)
        p.strokeWidth = 6 * d
        for (q in pieces) {
            val from = q.from.level?.let { Hub.colorFor(it, range) } ?: QUIET
            val to = q.to.level?.let { Hub.colorFor(it, range) } ?: QUIET
            px(mv, q.from.lat ?: continue, q.from.lon ?: continue); px(mv, q.to.lat ?: continue, q.to.lon ?: continue, pt2)
            // et kort stykke ser ens ud med og uden farveovergang
            val visible = kotlin.math.abs(pt2.x - pt.x) + kotlin.math.abs(pt2.y - pt.y) > 6 * d
            if (from != to && visible) p.shader = LinearGradient(pt.x.toFloat(), pt.y.toFloat(), pt2.x.toFloat(), pt2.y.toFloat(), from, to, Shader.TileMode.CLAMP)
            else p.color = if ((q.to.level ?: -Double.MAX_VALUE) > (q.from.level ?: -Double.MAX_VALUE)) to else from
            line(c, mv, q)
            p.shader = null
        }
        p.strokeCap = Paint.Cap.BUTT

        val best = bursts().flatten().filter { it.lat != null && it.lon != null && it.level != null }.maxByOrNull { it.level!! } ?: return
        px(mv, best.lat!!, best.lon!!)
        val x = pt.x.toFloat(); val y = pt.y.toFloat()
        p.style = Paint.Style.STROKE; p.strokeWidth = 3 * d; p.color = 0xFFFFFFFF.toInt()
        c.drawCircle(x, y, 9 * d, p)
        label(c, "stærkest ${Ui.f1(best.level!!)} dB", x, y - 15 * d)
    }

    /** Tegner et stykke af sporet med den pensel, der er sat; et sted uden naboer bliver en prik. */
    private fun line(c: Canvas, mv: Projection, q: Track.Piece) {
        px(mv, q.from.lat ?: return, q.from.lon ?: return)
        if (q.from === q.to) { c.drawPoint(pt.x.toFloat(), pt.y.toFloat(), p); return }
        px(mv, q.to.lat ?: return, q.to.lon ?: return, pt2)
        c.drawLine(pt.x.toFloat(), pt.y.toFloat(), pt2.x.toFloat(), pt2.y.toFloat(), p)
    }

    /** Hvid tekst på mørk bund, midt over [x] med grundlinjen i [y]. */
    private fun label(c: Canvas, text: String, x: Float, y: Float) {
        p.style = Paint.Style.FILL; p.textSize = 14 * d
        val w = p.measureText(text); val tx = x - w / 2
        p.color = 0xD00E0F12.toInt()
        c.drawRect(tx - 4 * d, y - 15 * d, tx + w + 4 * d, y + 4 * d, p)
        p.color = 0xFFFFFFFF.toInt()
        c.drawText(text, tx, y, p)
    }

    private fun drawHeat(c: Canvas, mv: Projection) {
        // meter pr. pixel midt på kortet: 0,1 breddegrad er 11,12 km
        val center = mv.currentCenter
        px(mv, center.latitude, center.longitude); px(mv, center.latitude + 0.1, center.longitude, pt2)
        val mpp = 11_120.0 / max(1, kotlin.math.abs(pt2.y - pt.y))
        val size = HeatGrid.sizeFor(mpp * 26 * d)

        val stamp = stamp()
        var g = grid
        if (g == null || g.sizeM != size || stamp != gridStamp) {
            val first = (bursts() + silent()).firstNotNullOfOrNull { list -> list.firstOrNull { it.lat != null } }
            g = HeatGrid(size, kotlin.math.round(first?.lat ?: center.latitude))
            for (list in bursts() + silent()) for (q in list) {
                g.add(q.lat ?: continue, q.lon ?: continue, q.level)
            }
            grid = g; gridStamp = stamp
        }

        val range = g.range()
        val best = g.strongest()
        p.style = Paint.Style.FILL
        for ((key, cell) in g.cells) {
            val b = g.bounds(key)
            px(mv, b[0], b[1]); px(mv, b[2], b[3], pt2)
            if (pt2.x < 0 || pt.x > mv.width || pt.y < 0 || pt2.y > mv.height) continue
            val m = cell.mean
            p.color = if (m == null) (QUIET and 0x00FFFFFF) or 0x70000000 else (Hub.colorFor(m, range) and 0x00FFFFFF) or 0xB0000000.toInt()
            c.drawRect(pt.x.toFloat(), pt2.y.toFloat(), pt2.x.toFloat(), pt.y.toFloat(), p)
        }
        if (best != null) {
            val b = g.bounds(best.first)
            px(mv, b[0], b[1]); px(mv, b[2], b[3], pt2)
            p.style = Paint.Style.STROKE; p.strokeWidth = 3 * d; p.color = 0xFFFFFFFF.toInt()
            c.drawRect(pt.x.toFloat(), pt2.y.toFloat(), pt2.x.toFloat(), pt.y.toFloat(), p)
            label(c, "stærkest ${Ui.f1(best.second)} dB", (pt.x + pt2.x) / 2f, pt2.y - 6 * d)
        }
        val sizeTxt = if (size >= 1000) "${(size / 1000).toInt()} km" else "${size.toInt()} m"
        heatLegend = if (range == null) "ingen målinger med position endnu"
        else "felter à $sizeTxt · blå ${Ui.f1(range.first)} → rød ${Ui.f1(range.second)} dB · gråt = intet hørt"
    }

    /** Gemte pejlinger: en streg på [BEARING_M] meter fra stedet i den retning, antennen pegede. */
    private fun drawBearings(c: Canvas, mv: Projection) {
        for (list in bearings()) for (b in list) {
            val lat = b.lat ?: continue; val lon = b.lon ?: continue
            px(mv, lat, lon)
            val x0 = pt.x.toFloat(); val y0 = pt.y.toFloat()
            val (lat2, lon2) = Bearing.destination(lat, lon, b.deg, BEARING_M)
            px(mv, lat2, lon2)
            p.style = Paint.Style.STROKE; p.strokeWidth = 2.5f * d; p.color = 0xFFFFC94D.toInt()
            c.drawLine(x0, y0, pt.x.toFloat(), pt.y.toFloat(), p)
            p.style = Paint.Style.FILL
            c.drawCircle(x0, y0, 4 * d, p)
        }
    }

    /**
     * Et mærke: et almindeligt er en hvid skive med flag og nummer, en mulig kilde en gul skive med spørgsmålstegn,
     * og en fundet kilde en rød skive med hvid ring og prik. De to sidste har deres betydning skrevet ved siden af.
     */
    private fun drawMark(c: Canvas, x: Float, y: Float, m: Hub.Mark) {
        p.style = Paint.Style.FILL
        val label: String?
        when (m.kind) {
            MarkKind.FOUND -> {
                p.color = 0xFF202124.toInt(); c.drawCircle(x, y, 14.5f * d, p)
                p.color = Ui.RED; c.drawCircle(x, y, 13 * d, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = 2.5f * d; p.color = 0xFFFFFFFF.toInt(); c.drawCircle(x, y, 7.5f * d, p)
                p.style = Paint.Style.FILL; c.drawCircle(x, y, 2.8f * d, p)
                label = "kilde fundet"
            }
            MarkKind.POSSIBLE -> {
                p.color = 0xFF202124.toInt(); c.drawCircle(x, y, 13.5f * d, p)
                p.color = Ui.YELLOW; c.drawCircle(x, y, 12 * d, p)
                p.color = 0xFF202124.toInt(); p.textSize = 16 * d; p.isFakeBoldText = true
                c.drawText("?", x - p.measureText("?") / 2, y + 5.5f * d, p); p.isFakeBoldText = false
                label = "mulig kilde"
            }
            else -> {
                p.color = 0xFFFFFFFF.toInt(); c.drawCircle(x, y, 10 * d, p)
                p.color = 0xFF202124.toInt(); p.textSize = 13 * d
                val flag = "⚑${m.n}"
                c.drawText(flag, x - p.measureText(flag) / 2, y + 5 * d, p)
                label = null
            }
        }
        if (label != null) {
            p.textSize = 12 * d
            val tx = x + 18 * d; val w = p.measureText(label)
            p.color = 0xD00E0F12.toInt(); c.drawRect(tx - 4 * d, y - 10 * d, tx + w + 4 * d, y + 8 * d, p)
            p.color = 0xFFFFFFFF.toInt(); c.drawText(label, tx, y + 4 * d, p)
        }
    }

    private fun drawMarksAndMe(c: Canvas, mv: Projection) {
        drawBearings(c, mv)
        for (list in marks()) for (m in list) {
            px(mv, m.lat ?: continue, m.lon ?: continue)
            drawMark(c, pt.x.toFloat(), pt.y.toFloat(), m)
        }
        if (only != null) return
        val me = Hub.location ?: return
        px(mv, me.latitude, me.longitude)
        val x = pt.x.toFloat(); val y = pt.y.toFloat()
        if (me.hasBearing() && me.speed * 3.6 > 5) {
            c.save(); c.rotate(me.bearing, x, y)
            val wedge = Path().apply { moveTo(x, y - 22 * d); lineTo(x - 8 * d, y - 8 * d); lineTo(x + 8 * d, y - 8 * d); close() }
            p.style = Paint.Style.FILL; p.color = Ui.ACCENT
            c.drawPath(wedge, p); c.restore()
        }
        p.style = Paint.Style.FILL; p.color = 0xFFFFFFFF.toInt()
        c.drawCircle(x, y, 7 * d, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 3 * d; p.color = Ui.ACCENT
        c.drawCircle(x, y, 8 * d, p)
    }
}
