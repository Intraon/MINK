package dk.intraon.mink.trip

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * IQ-udsnittene fra en tur, lagt efter hinanden i én WAV-fil i Download/mink/ (stereo, 16 bit:
 * venstre = I, højre = Q). Hvert burst i loggen peger på sit udsnit med startposition og længde.
 * Headeren skrives om efter hvert udsnit, så filen er gyldig, selv om appen bliver lukket midt i en tur.
 *
 * Filen er en WAV, men den er ikke musik: den meldes til telefonen som en almindelig datafil, og mappen får en
 * .nomedia-fil ([JsonlLog.hideFromMedia]), så den ikke dukker op som et nummer i telefonens musikafspiller.
 */
class IqWav(ctx: Context, val name: String, private val rate: Int) {
    private var pfd: ParcelFileDescriptor? = null
    private var ch: FileChannel? = null
    private var frames = 0L
    var uri: Uri? = null
        private set
    @Volatile var ok = false
        private set

    init {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                put(MediaStore.Downloads.RELATIVE_PATH, JsonlLog.DIR)
            }
            uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            pfd = uri?.let { ctx.contentResolver.openFileDescriptor(it, "rw") }
            ch = pfd?.let { FileOutputStream(it.fileDescriptor).channel }
            if (ch != null) { header(); ok = true }
        } catch (_: Exception) {
            ok = false
        }
    }

    private fun header() {
        val data = frames * 4
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray()).putInt((36 + data).toInt()).put("WAVE".toByteArray())
        h.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(2).putInt(rate).putInt(rate * 4).putShort(4).putShort(16)
        h.put("data".toByteArray()).putInt(data.toInt())
        h.flip()
        ch?.write(h, 0)
    }

    /** Tilføjer et udsnit (I,Q,I,Q…) og returnerer dets startposition i filen, målt i samples – eller −1 ved fejl. */
    @Synchronized
    fun append(iq: ShortArray): Long {
        val c = ch ?: return -1
        if (iq.isEmpty()) return -1
        return try {
            val start = frames
            val bb = ByteBuffer.allocate(iq.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            bb.asShortBuffer().put(iq)
            c.write(bb, 44 + start * 4)
            frames += iq.size / 2
            header()
            start
        } catch (_: Exception) {
            ok = false
            -1
        }
    }

    @Synchronized
    fun close() {
        try { ch?.close() } catch (_: Exception) {}
        try { pfd?.close() } catch (_: Exception) {}
        ch = null; pfd = null
    }
}
