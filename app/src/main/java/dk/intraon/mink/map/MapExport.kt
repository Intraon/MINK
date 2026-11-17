package dk.intraon.mink.map

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.view.View
import dk.intraon.mink.trip.JsonlLog
import dk.intraon.mink.ui.Ui
import dk.intraon.mink.waterfall.FigureExport
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.RectL
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.TilesOverlay
import java.io.ByteArrayOutputStream
import kotlin.math.floor
import kotlin.math.max

/** Laver kortbilleder og gemmer dem som PNG i telefonens galleri (Pictures/MINK). */
object MapExport {
    /** Størrelsen på et kortbillede af en hel tur, uden bjælken med teksten. */
    const val W = 1400
    const val H = 1000
    /** Så længe ventes der højst på kortfliser, der skal hentes. */
    const val WAIT_MS = 7_000L
    private const val POLL_MS = 300L
    /** Punkter, felter og tekst på et turbillede tegnes, som om skærmen havde denne tæthed. */
    private const val DENSITY = 2f

    /**
     * Tegner kortet, som det ses på skærmen: alt, hvad der ligger på det (punkter eller varmekort, mærker,
     * pejlinger og kildeangivelsen), farveforklaringen [legend] dér, hvor den står, og [caption] på en bjælke under
     * kortet. Skal kaldes på hovedtråden. Null, hvis kortet ikke har en størrelse endnu.
     */
    fun capture(map: MapView, legend: View, caption: String, density: Float): Bitmap? {
        val w = map.width; val h = map.height
        if (w <= 0 || h <= 0) return null
        val bar = (30 * density).toInt()
        val bmp = Bitmap.createBitmap(w, h + bar, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        map.draw(c)
        if (legend.visibility == View.VISIBLE && legend.width > 0) {
            c.save(); c.translate(legend.left.toFloat(), legend.top.toFloat()); legend.draw(c); c.restore()
        }
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Ui.BG; c.drawRect(0f, h.toFloat(), w.toFloat(), (h + bar).toFloat(), p)
        p.color = Ui.TEXT; p.textSize = 12 * density
        c.drawText(caption, 8 * density, h + bar / 2f + 4.5f * density, p)
        return bmp
    }

    /**
     * Tegner et kort over en hel tur uden om skærmen. Udsnittet lægges, så alle turens steder er med, og turen
     * vises som varmekort med mærker og pejlinger. Under kortet står [caption], farveforklaringen og kortets kilde.
     *
     * Kortfliserne hentes, mens der ventes; dem, der ikke er kommet efter [WAIT_MS], mangler på billedet. [done]
     * kaldes på hovedtråden med billedet, eller med null, hvis turen ingen steder har. Skal kaldes på hovedtråden.
     */
    fun tripPicture(ctx: Context, points: TripPoints, caption: String, done: (Bitmap?) -> Unit) {
        val pos = points.positions()
        if (pos.isEmpty()) { done(null); return }
        val south = pos.minOf { it.first }; val north = pos.maxOf { it.first }
        val west = pos.minOf { it.second }; val east = pos.maxOf { it.second }
        // luft om turen: en ottendedel af dens udstrækning, og mindst et par hundrede meter
        val padLat = max((north - south) / 8, 0.0015); val padLon = max((east - west) / 8, 0.003)
        val box = BoundingBox(north + padLat, east + padLon, south - padLat, west - padLon)
        // hele zoomtrin giver skarpe kortfliser; rundes der ned, er der stadig plads til det hele
        val fit = MapView.getTileSystem().getBoundingBoxZoom(box, W, H)
        val zoom = if (fit == Double.MIN_VALUE) 16.0 else floor(fit).coerceIn(3.0, 18.0)
        val pj = Projection(zoom, W, H, GeoPoint((north + south) / 2, (east + west) / 2), 0f, true, true, 0, 0)
        val view = RectL().also { pj.getMercatorViewPort(it) }
        val provider = MapTileProviderBasic(ctx.applicationContext, TileSourceFactory.MAPNIK)
        val tiles = TilesOverlay(provider, ctx.applicationContext)
        val overlay = PointsOverlay(DENSITY).apply { only = points; heat = true }
        val handler = Handler(Looper.getMainLooper())
        val started = SystemClock.elapsedRealtime()

        fun paint() {
            val bar = (52 * DENSITY).toInt()
            val bmp = Bitmap.createBitmap(W, H + bar, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            pj.save(c, true, false)
            tiles.drawTiles(c, pj, zoom, view)
            overlay.draw(c, pj)
            pj.restore(c, false)
            val source = provider.tileSource.copyrightNotice ?: "© OpenStreetMap contributors"
            provider.detach()
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = Ui.BG; c.drawRect(0f, H.toFloat(), W.toFloat(), (H + bar).toFloat(), p)
            p.color = Ui.TEXT; p.textSize = 13 * DENSITY
            c.drawText(caption, 8 * DENSITY, H + 21f * DENSITY, p)
            p.color = Ui.DIM; p.textSize = 11 * DENSITY
            c.drawText(overlay.heatLegend, 8 * DENSITY, H + 42f * DENSITY, p)
            c.drawText(source, W - 8 * DENSITY - p.measureText(source), H + 42f * DENSITY, p)
            done(bmp)
        }

        // Et kald uden lærred beder om de fliser, udsnittet skal bruge, og tæller, hvor mange der mangler.
        fun poll() {
            tiles.drawTiles(null, pj, zoom, view)
            val st = tiles.tileStates
            val missing = st.scaled + st.notFound
            if ((st.total > 0 && missing == 0) || SystemClock.elapsedRealtime() - started > WAIT_MS) paint()
            else handler.postDelayed({ poll() }, POLL_MS)
        }
        poll()
    }

    /**
     * Skriver billedet til galleriet under [name] og frigiver det (kald fra en baggrundstråd). Med [replace] slettes
     * et ældre billede med samme navn først. Null, hvis billedet ikke kunne skrives.
     */
    fun save(ctx: Context, bmp: Bitmap, name: String, replace: Boolean = false): JsonlLog.Entry? = try {
        val png = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        val images = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        if (replace) try { ctx.contentResolver.delete(images, "${MediaStore.Images.Media.DISPLAY_NAME}=?", arrayOf(name)) } catch (_: Exception) {}
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, FigureExport.DIR)
        }
        val uri: Uri? = ctx.contentResolver.insert(images, values)
        val ok = uri != null && (ctx.contentResolver.openOutputStream(uri)?.use { it.write(png); true } ?: false)
        if (ok && uri != null) JsonlLog.Entry(name, uri, png.size.toLong()) else null
    } catch (_: Exception) {
        null
    } finally {
        bmp.recycle()
    }
}
