package dk.intraon.mink.trip

import android.app.Activity
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import dk.intraon.mink.waterfall.FigureExport

/** Del logfiler eller et billede via Androids delemenu. */
object Export {
    /** Åbner Androids delemenu med filerne. Ingen fortrykt tekst: modtageren og budskabet er brugerens. */
    fun share(a: Activity, uris: List<Uri>, mime: String = "text/plain", title: String = "Del målinger") {
        if (uris.isEmpty()) return
        val i = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        i.type = if (uris.size > 1) "*/*" else mime
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val clip = ClipData.newRawUri("MINK", uris[0])
        for (u in uris.drop(1)) clip.addItem(ClipData.Item(u))
        i.clipData = clip
        a.startActivity(Intent.createChooser(i, title))
    }

    /** Appens egne billeder i galleriet (Pictures/MINK): målebilleder og pejlingsfotos. */
    fun pictures(ctx: Context): List<JsonlLog.Entry> {
        val res = ArrayList<JsonlLog.Entry>()
        try {
            val cols = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.SIZE)
            ctx.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cols,
                "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?", arrayOf("${FigureExport.DIR}%"), null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val n = c.getString(1) ?: continue
                    res.add(JsonlLog.Entry(n, Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(0).toString()), c.getLong(2)))
                }
            }
        } catch (_: Exception) {}
        return res
    }

    /** En skrevet zip-fil og navnene på de filer, der ikke kunne læses og derfor mangler i den. */
    class Zipped(val uri: Uri, val unread: List<String>)

    /**
     * Pakker filerne i én zip-fil i appens mappe i Overførsler (kald fra en baggrundstråd). En ældre zip med samme
     * navn erstattes. Null, hvis zip-filen ikke kunne skrives.
     */
    fun zip(ctx: Context, name: String, files: List<JsonlLog.Entry>): Zipped? {
        val dl = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        try { ctx.contentResolver.delete(dl, "${MediaStore.Downloads.DISPLAY_NAME}=?", arrayOf(name)) } catch (_: Exception) {}
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                put(MediaStore.Downloads.RELATIVE_PATH, JsonlLog.DIR)
            }
            val uri = ctx.contentResolver.insert(dl, values) ?: return null
            val out = ctx.contentResolver.openOutputStream(uri) ?: return null
            Zipped(uri, ZipPack.write(out, files.map { f -> ZipPack.Source(f.name) { ctx.contentResolver.openInputStream(f.uri) } }))
        } catch (_: Exception) {
            null
        }
    }
}
