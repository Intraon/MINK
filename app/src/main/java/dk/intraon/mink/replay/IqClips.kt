package dk.intraon.mink.replay

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.LruCache
import dk.intraon.mink.waterfall.Scope
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.concurrent.Executors

/**
 * Udbruddenes ægte signal fra en turs bølgeform-fil, som små billeder til vandfaldet. Filen læses på en baggrundstråd,
 * ét udsnit ad gangen, når vandfaldet beder om det; [onReady] kaldes på hovedtråden, når et billede er klar, og
 * når et udsnit ikke kunne læses. [rate] er filens sample rate. [open] åbner filen og kaldes på baggrundstråden.
 */
class IqClips(private val rate: Int, private val open: () -> FileChannel?, private val onReady: () -> Unit) {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val cache = LruCache<Long, Bitmap>(KEEP)
    /** Udsnit, der er bedt om og ikke læst endnu, og dem, der ikke kunne læses. Kun hovedtråden rører dem. */
    private val asked = HashSet<Long>()
    private val failed = HashSet<Long>()
    private var channel: FileChannel? = null
    private var opened = false
    @Volatile private var closed = false
    private var got = false
    /**
     * Intet udsnit har kunnet læses, og mindst ét er prøvet: filen er væk eller kan ikke åbnes. Så tegner vandfaldet
     * felter i stedet. Kun hovedtråden læser og sætter den.
     */
    var unreadable = false
        private set

    /**
     * Billedet af udbruddets udsnit: [ClipSpectrum.N] søjler fra udsnittets laveste til højeste frekvens og én række
     * pr. 4 ms med den seneste øverst. Null, indtil udsnittet er læst, og hvis udbruddet ikke har et.
     */
    fun get(b: Replay.Burst): Bitmap? {
        cache.get(b.iq)?.let { return it }
        if (closed || b.iqn < ClipSpectrum.N || b.iq in failed || asked.size >= QUEUE || !asked.add(b.iq)) return null
        worker.execute {
            val bmp = if (closed) null else read(b)
            main.post {
                asked.remove(b.iq)
                if (closed) return@post
                if (bmp == null) failed.add(b.iq) else { cache.put(b.iq, bmp); got = true }
                unreadable = !got
                onReady()
            }
        }
        return null
    }

    private fun read(b: Replay.Burst): Bitmap? = try {
        if (!opened) { opened = true; channel = open() }
        val ch = channel
        val buf = ByteBuffer.allocate(b.iqn * 4).order(ByteOrder.LITTLE_ENDIAN)
        var pos = HEADER + b.iq * 4
        while (ch != null && buf.hasRemaining()) { val n = ch.read(buf, pos); if (n <= 0) break; pos += n }
        if (buf.hasRemaining()) null else {
            buf.flip()
            val iq = ShortArray(b.iqn * 2).also { buf.asShortBuffer().get(it) }
            val rows = ClipSpectrum.rows(iq, quiet = b.iqPreMs * rate / 1000)
            val px = IntArray(rows.size * ClipSpectrum.N)
            for (r in rows.indices) {
                val y = rows.size - 1 - r
                for (i in 0 until ClipSpectrum.N) px[y * ClipSpectrum.N + i] = Scope.color(((rows[r][i] - Scope.MIN_DB) / (Scope.MAX_DB - Scope.MIN_DB)).toFloat().coerceIn(0f, 1f))
            }
            Bitmap.createBitmap(px, ClipSpectrum.N, rows.size, Bitmap.Config.ARGB_8888)
        }
    } catch (_: Exception) {
        null
    }

    /** Vandfaldet er lukket: filen lukkes, når den igangværende læsning er færdig. */
    fun close() {
        closed = true
        worker.execute { try { channel?.close() } catch (_: Exception) {} }
        worker.shutdown()
    }

    companion object {
        /** WAV-filens hoved fylder 44 bytes før det første sample. */
        private const val HEADER = 44L
        /** Så mange billeder huskes; et billede fylder omkring 25 kB. */
        private const val KEEP = 400
        /** Flere udsnit end dette venter ikke på at blive læst: ruller man hurtigt forbi, springes de over. */
        private const val QUEUE = 60

        /**
         * Bølgeform-filen bag [uri]. Strømmen ejer filen og holder den åben, til kanalen lukkes: en strøm lavet af
         * det nøgne filnummer gør ikke, og så lukker Android filen, når den rydder op, mens der stadig læses.
         */
        fun of(ctx: Context, uri: Uri, rate: Int, onReady: () -> Unit) = IqClips(rate, {
            ctx.contentResolver.openFileDescriptor(uri, "r")?.let { ParcelFileDescriptor.AutoCloseInputStream(it).channel }
        }, onReady)
    }
}
