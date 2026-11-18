package dk.intraon.mink.trip

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import dk.intraon.mink.waterfall.FigureExport

/** Del logfiler eller et billede via Androids delemenu. */
object Export {
    /** Den modtager, der venter på besked om et valg i delemenuen, og den skærm, den er meldt til hos. */
    private var waiting: Pair<Activity, BroadcastReceiver>? = null

    /**
     * Åbner Androids delemenu med filerne. Ingen fortrykt tekst: modtageren og budskabet er brugerens. [picked]
     * kaldes på hovedtråden, når brugeren har valgt en app i menuen; Android fortæller ikke, om der derefter blev sendt.
     */
    fun share(a: Activity, uris: List<Uri>, mime: String = "text/plain", title: String = "Del målinger", picked: (() -> Unit)? = null) {
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
        val sender = picked?.let { whenPicked(a, it) }
        a.startActivity(if (sender == null) Intent.createChooser(i, title) else Intent.createChooser(i, title, sender))
        // kan appen ikke få besked om valget, regnes filerne som delt, når menuen åbner
        if (sender == null) picked?.invoke()
    }

    /**
     * Det, delemenuen skal sende, når brugeren vælger en app: en besked til denne app alene, som kalder [picked] én
     * gang. Kun det seneste valg ventes der på. Null, hvis Android ikke vil tage imod den.
     */
    private fun whenPicked(a: Activity, picked: () -> Unit): IntentSender? = try {
        forget(a)
        val action = a.packageName + ".DELT"
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { forget(a); picked() }
        }
        if (Build.VERSION.SDK_INT >= 33) a.registerReceiver(receiver, IntentFilter(action), Context.RECEIVER_NOT_EXPORTED)
        else a.registerReceiver(receiver, IntentFilter(action))
        waiting = a to receiver
        PendingIntent.getBroadcast(a, 0, Intent(action).setPackage(a.packageName), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE).intentSender
    } catch (_: Exception) {
        forget(a); null
    }

    /** Holder op med at vente på et valg i delemenuen. Kaldes, når skærmen [a] lukkes. */
    fun forget(a: Activity) {
        val (owner, receiver) = waiting ?: return
        if (owner !== a) return
        waiting = null
        try { a.unregisterReceiver(receiver) } catch (_: Exception) {}
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
