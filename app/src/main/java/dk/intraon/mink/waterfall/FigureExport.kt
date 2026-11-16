package dk.intraon.mink.waterfall

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.provider.MediaStore
import dk.intraon.mink.core.Hub
import dk.intraon.mink.ui.Ui
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.max

/** Laver billedet af de seneste ca. 30 sekunder og gemmer det som PNG i telefonens galleri (Pictures/MINK). */
object FigureExport {
    /** Kortere end det er der ikke noget at vise. */
    const val MIN_SECONDS = 3
    /** Mappen i galleriet. */
    const val DIR = "Pictures/MINK"

    /** Null, hvis der ikke er målt længe nok endnu, eller filen ikke kunne skrives. */
    fun save(ctx: Context): Uri? {
        val snap = Scope.snapshot() ?: return null
        if (snap.rows < MIN_SECONDS * Scope.ROWS_PER_SECOND) return null
        val now = Date()
        val bandLo = snap.loHz; val bandHi = snap.loHz + snap.bins * snap.binHz
        val tgt = Hub.targetHz
        val chan = Hub.channelHz; val chanBw = Hub.channelBwHz
        // kurven nederst følger målet, hvis der er valgt et; ellers brugerens kanal; ellers hele båndet
        val (lo, hi, name) = when {
            tgt != null -> { val half = max(1.5 * Hub.signalBwHz, 6_000.0); Triple(tgt - half, tgt + half, "målet") }
            chan > 0 && chanBw > 0 -> Triple(chan - chanBw / 2, chan + chanBw / 2, "din kanal")
            else -> Triple(bandLo, bandHi, "hele båndet")
        }
        val caption = "MINK · " + SimpleDateFormat("d.M.yyyy HH:mm:ss", Ui.DA).format(now) +
            " · de seneste ${Math.round(snap.rows / Scope.ROWS_PER_SECOND.toDouble())} s · gain ${Ui.f1(Hub.gainDb)} dB"
        val inp = Figure.Input(
            snap.rows, snap.bins, snap.db, snap.loHz, snap.binHz, 1.0 / Scope.ROWS_PER_SECOND,
            chan, chanBw, max(lo, bandLo), minOf(hi, bandHi), name, caption,
        )
        val bmp = Bitmap.createBitmap(Figure.W, Figure.H, Bitmap.Config.ARGB_8888)
        try {
            Figure.draw(CanvasPen(Canvas(bmp)), inp)
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "MINK_" + SimpleDateFormat("yyyyMMdd_HHmmss", Ui.DA).format(now) + ".png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, DIR)
            }
            val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            val ok = ctx.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: false
            return if (ok) uri else null
        } catch (_: Exception) {
            return null
        } finally {
            bmp.recycle()
        }
    }
}
