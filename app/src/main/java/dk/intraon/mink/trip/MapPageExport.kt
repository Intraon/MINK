package dk.intraon.mink.trip

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.util.Base64
import dk.intraon.mink.BuildConfig
import dk.intraon.mink.map.BaseMap
import dk.intraon.mink.map.TileSource
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Laver kortfilen ([MapPage]) for de valgte ture og gemmer den i appens mappe i Overførsler. */
object MapPageExport {
    /** Den skrevne kortfil. [background] er falsk, hvis kortdata ikke kunne hentes, og filen derfor er uden baggrund. */
    class Made(val file: JsonlLog.Entry, val background: Boolean)

    private const val MAX_TILES = 90        // baggrundskortet dækker højst så mange fliser; dem med flest målinger først
    private const val PHOTO_PX = 1280       // fotoernes længste side i filen
    private const val PHOTO_QUALITY = 80

    /** Kald fra en baggrundstråd: der hentes kortdata, og filer læses og skrives. Null, hvis filen ikke kunne skrives. */
    fun make(ctx: Context, trips: List<JsonlLog.Entry>, pictures: List<JsonlLog.Entry>): Made? {
        val logs = trips.map { e ->
            MapPage.Log(e.name, try { ctx.contentResolver.openInputStream(e.uri)?.bufferedReader()?.use { it.readLines() } ?: emptyList() } catch (_: Exception) { emptyList() })
        }
        val agent = "MINK/${BuildConfig.VERSION_NAME}" + if (BuildConfig.REPO.isNotEmpty()) " (github.com/${BuildConfig.REPO})" else ""
        val tiles = TileSource(BuildConfig.MAP_TILES, agent).fetch(BaseMap.tiles(MapPage.positions(logs), MAX_TILES))
        val photos = HashMap<String, String>()
        for (name in MapPage.photoNames(logs)) {
            val uri = pictures.firstOrNull { it.name == name }?.uri ?: continue
            photos[name] = photo(ctx, uri) ?: continue
        }
        val template = try { ctx.assets.open("kort.html").bufferedReader().use { it.readText() } } catch (_: Exception) { return null }
        val made = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US).format(Date())
        val html = MapPage.html(template, MapPage.data(logs), if (tiles.isEmpty()) null else BaseMap.json(tiles), photos, made).toByteArray(Charsets.UTF_8)

        val name = TripFiles.mapPageName(trips.mapNotNull { TripFiles.stamp(it.name) })
        val dl = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        try { ctx.contentResolver.delete(dl, "${MediaStore.Downloads.DISPLAY_NAME}=?", arrayOf(name)) } catch (_: Exception) {}
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "text/html")
                put(MediaStore.Downloads.RELATIVE_PATH, JsonlLog.DIR)
            }
            val uri = ctx.contentResolver.insert(dl, values) ?: return null
            (ctx.contentResolver.openOutputStream(uri) ?: return null).use { it.write(html) }
            Made(JsonlLog.Entry(name, uri, html.size.toLong()), tiles.isNotEmpty())
        } catch (_: Exception) {
            null
        }
    }

    /** Fotoet skaleret ned, så filen kan sendes, som JPEG i base64. */
    private fun photo(ctx: Context, uri: Uri): String? = try {
        val size = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, size) }
        var sample = 1
        while (maxOf(size.outWidth, size.outHeight) / (sample * 2) >= PHOTO_PX) sample *= 2
        val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
        if (bmp == null) null else {
            val longest = maxOf(bmp.width, bmp.height)
            val fit = if (longest > PHOTO_PX) Bitmap.createScaledBitmap(bmp, bmp.width * PHOTO_PX / longest, bmp.height * PHOTO_PX / longest, true) else bmp
            val out = ByteArrayOutputStream()
            fit.compress(Bitmap.CompressFormat.JPEG, PHOTO_QUALITY, out)
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }
    } catch (_: Exception) {
        null
    }
}
