package dk.intraon.mink.trip

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dk.intraon.mink.core.Settings
import java.io.File
import java.io.OutputStream
import org.json.JSONObject

/**
 * Skriver én JSON-linje pr. hændelse til Download/mink/ på telefonen.
 * Filen hedder .jsonl.txt: MediaStore sætter selv .txt på en text/plain-fil, og som tekstfil kan den deles med alt.
 */
class JsonlLog(ctx: Context, val name: String) {
    private var out: OutputStream? = null
    var uri: Uri? = null
        private set
    @Volatile var ok = false
        private set

    init {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, DIR)
            }
            uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            uri?.let { out = ctx.contentResolver.openOutputStream(it, "wa") }
            ok = out != null
        } catch (_: Exception) {
            out = null
        }
    }

    @Synchronized
    fun write(o: JSONObject) {
        val s = out ?: return
        try {
            s.write((o.toString() + "\n").toByteArray())
            s.flush()
        } catch (_: Exception) {
            ok = false
        }
    }

    @Synchronized
    fun close() {
        try { out?.close() } catch (_: Exception) {}
        out = null
    }

    data class Entry(val name: String, val uri: Uri, val bytes: Long)

    companion object {
        const val DIR = "Download/mink"
        const val PREFIX = "mink_"
        const val LOG_SUFFIX = ".jsonl"
        const val IQ_SUFFIX = ".iq.wav"

        /**
         * Lægger en tom .nomedia-fil i appens mappe i Overførsler. Den fortæller telefonen, at mappen ikke rummer
         * billeder, musik eller video, så bølgeform-filerne (WAV) ikke ender i musikafspilleren. Loggen og delingen
         * er ikke berørt, og billederne ligger i en anden mappe (Pictures/MINK), som stadig vises i galleriet.
         */
        fun hideFromMedia(ctx: Context, settings: Settings) {
            try {
                if (Build.VERSION.SDK_INT >= 30) {
                    // fra Android 11 må en app selv oprette filer i Overførsler med en almindelig sti
                    val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "mink")
                    val f = File(dir, ".nomedia")
                    if (!f.exists()) { dir.mkdirs(); f.createNewFile() }
                } else if (!settings.nomediaDone) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, ".nomedia")
                        put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                        put(MediaStore.Downloads.RELATIVE_PATH, DIR)
                    }
                    if (ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) != null) settings.nomediaDone = true
                }
            } catch (_: Exception) {
                // kan mappen ikke mærkes, virker alt andet stadig; filerne kan så blot ses af musikafspilleren
            }
        }

        /** Appens egne logfiler, nyeste først. */
        fun list(ctx: Context, suffix: String = LOG_SUFFIX): List<Entry> {
            val res = ArrayList<Entry>()
            try {
                val cols = arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME, MediaStore.Downloads.SIZE)
                ctx.contentResolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, cols,
                    "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?", arrayOf("$PREFIX%"),
                    "${MediaStore.Downloads.DISPLAY_NAME} DESC",
                )?.use { c ->
                    while (c.moveToNext()) {
                        val n = c.getString(1) ?: continue
                        if (!n.contains(suffix)) continue
                        val u = Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, c.getLong(0).toString())
                        res.add(Entry(n, u, c.getLong(2)))
                    }
                }
            } catch (_: Exception) {}
            return res
        }
    }
}
