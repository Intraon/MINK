package dk.intraon.mink.map

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import dk.intraon.mink.core.Hub
import dk.intraon.mink.direction.Bearing
import dk.intraon.mink.ui.Ui
import kotlin.math.max
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/**
 * Målingerne på kortet. To visninger:
 * - punkter: en farvet prik pr. måling, små grå hvor der var stille
 * - varmekort ([heat]): målingerne samlet i felter, farvet efter feltets gennemsnitlige niveau; det stærkeste
 *   felt får en hvid ramme. Grå felter = lyttet, intet hørt.
 * Begge viser den tur, der kører, sammen med de tidligere ture, der er lagt ind (MapData).
 */
/** Så lang tegnes en pejling på kortet (meter). */
private const val BEARING_M = 3_000.0

class PointsOverlay(private val d: Float) : Overlay() {
    var heat = false
    /** Tekst til forklaringen under kortet; sættes hver gang varmekortet tegnes. */
    @Volatile var heatLegend = ""
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pt = Point()
    private val pt2 = Point()
    private val gp = GeoPoint(0.0, 0.0)
    private var grid: HeatGrid? = null
    private var gridStamp = -1L

    override fun draw(c: Canvas, mv: MapView, shadow: Boolean) {
        if (shadow) return
        if (heat) drawHeat(c, mv) else drawPoints(c, mv)
        drawMarksAndMe(c, mv)
    }

    private fun px(mv: MapView, lat: Double, lon: Double, into: Point = pt) {
        gp.setCoords(lat, lon); mv.projection.toPixels(gp, into)
    }

    private fun drawPoints(c: Canvas, mv: MapView) {
        p.style = Paint.Style.FILL
        p.color = 0xCC6B7078.toInt()
        for (list in listOf(MapData.silent, Hub.silent)) for (s in list) {
            px(mv, s.lat ?: continue, s.lon ?: continue)
            c.drawCircle(pt.x.toFloat(), pt.y.toFloat(), 3 * d, p)
        }
        val range = MapData.range()
        for (list in listOf(MapData.bursts, Hub.bursts)) for (b in list) {
            px(mv, b.lat ?: continue, b.lon ?: continue)
            p.style = Paint.Style.FILL; p.color = Hub.colorFor(b.level ?: continue, range)
            c.drawCircle(pt.x.toFloat(), pt.y.toFloat(), 7 * d, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = d; p.color = 0xFF202124.toInt()
            c.drawCircle(pt.x.toFloat(), pt.y.toFloat(), 7 * d, p)
        }
    }

    private fun drawHeat(c: Canvas, mv: MapView) {
        // meter pr. pixel midt på kortet: 0,1 breddegrad er 11,12 km
        val center = mv.mapCenter
        px(mv, center.latitude, center.longitude); px(mv, center.latitude + 0.1, center.longitude, pt2)
        val mpp = 11_120.0 / max(1, kotlin.math.abs(pt2.y - pt.y))
        val size = HeatGrid.sizeFor(mpp * 26 * d)

        val stamp = MapData.bursts.size * 7L + MapData.silent.size * 5L + Hub.bursts.size * 3L + Hub.silent.size + MapData.sessions * 1_000_003L
        var g = grid
        if (g == null || g.sizeM != size || stamp != gridStamp) {
            val first = MapData.bursts.firstOrNull { it.lat != null } ?: Hub.bursts.firstOrNull { it.lat != null }
                ?: MapData.silent.firstOrNull { it.lat != null } ?: Hub.silent.firstOrNull { it.lat != null }
            g = HeatGrid(size, kotlin.math.round(first?.lat ?: center.latitude))
            for (list in listOf(MapData.bursts, Hub.bursts, MapData.silent, Hub.silent)) for (q in list) {
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
            p.color = if (m == null) 0x70808891 else (Hub.colorFor(m, range) and 0x00FFFFFF) or 0xB0000000.toInt()
            c.drawRect(pt.x.toFloat(), pt2.y.toFloat(), pt2.x.toFloat(), pt.y.toFloat(), p)
        }
        if (best != null) {
            val b = g.bounds(best.first)
            px(mv, b[0], b[1]); px(mv, b[2], b[3], pt2)
            p.style = Paint.Style.STROKE; p.strokeWidth = 3 * d; p.color = 0xFFFFFFFF.toInt()
            c.drawRect(pt.x.toFloat(), pt2.y.toFloat(), pt2.x.toFloat(), pt.y.toFloat(), p)
            p.style = Paint.Style.FILL; p.textSize = 14 * d
            val label = "stærkest ${Ui.f1(best.second)} dB"
            val tx = (pt.x + pt2.x) / 2f - p.measureText(label) / 2; val ty = pt2.y - 6 * d
            p.color = 0xD00E0F12.toInt()
            c.drawRect(tx - 4 * d, ty - 15 * d, tx + p.measureText(label) + 4 * d, ty + 4 * d, p)
            p.color = 0xFFFFFFFF.toInt()
            c.drawText(label, tx, ty, p)
        }
        val sizeTxt = if (size >= 1000) "${(size / 1000).toInt()} km" else "${size.toInt()} m"
        heatLegend = if (range == null) "ingen målinger med position endnu"
        else "felter à $sizeTxt · blå ${Ui.f1(range.first)} → rød ${Ui.f1(range.second)} dB · gråt = intet hørt"
    }

    /** Gemte pejlinger: en streg på [BEARING_M] meter fra stedet i den retning, antennen pegede. */
    private fun drawBearings(c: Canvas, mv: MapView) {
        for (list in listOf(MapData.bearings, Hub.bearings)) for (b in list) {
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

    private fun drawMarksAndMe(c: Canvas, mv: MapView) {
        drawBearings(c, mv)
        p.textSize = 13 * d
        for (list in listOf(MapData.marks, Hub.marks)) for (m in list) {
            px(mv, m.lat ?: continue, m.lon ?: continue)
            p.style = Paint.Style.FILL; p.color = 0xFFFFFFFF.toInt()
            c.drawCircle(pt.x.toFloat(), pt.y.toFloat(), 10 * d, p)
            p.color = 0xFF202124.toInt()
            val label = "⚑${m.n}"
            c.drawText(label, pt.x - p.measureText(label) / 2, pt.y + 5 * d, p)
        }
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
