package dk.intraon.mink.update

import android.app.AlertDialog
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.widget.TextView
import dk.intraon.mink.BuildConfig
import dk.intraon.mink.core.Hub
import dk.intraon.mink.trip.TripFiles
import dk.intraon.mink.ui.MainActivity
import dk.intraon.mink.ui.Ui
import dk.intraon.mink.ui.dp
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Opdatering fra GitHub. Appen spørger GitHub, om der er udgivet en nyere version i projektets repo
 * ([BuildConfig.REPO]), og tilbyder at hente og installere den. Der sendes intet andet end selve forespørgslen;
 * tjekket kan slås fra under Indstillinger.
 *
 * Android installerer kun en APK oven i appen, hvis den er signeret med samme nøgle som den, der er installeret.
 * En fil fra et andet sted kan derfor ikke snige sig ind som en opdatering.
 */
class Updater(private val a: MainActivity) {
    companion object {
        const val ACTION_INSTALL_STATUS = "dk.intraon.mink.INSTALL_STATUS"
        /** Så ofte ser appen selv efter en ny version. */
        private const val EVERY_MS = 12 * 3600_000L
    }

    private var busy = false

    /** Ved start: se efter en ny version, hvis det er længe siden sidst, og der ikke måles. */
    fun autoCheck() {
        val s = a.settings
        if (!s.autoUpdate || Hub.running || BuildConfig.REPO.isEmpty()) return
        if (System.currentTimeMillis() - s.updateCheckedAt < EVERY_MS) return
        check(quiet = true)
    }

    /** Spørg GitHub nu. [quiet] = sig kun noget, hvis der er en ny version. */
    fun check(quiet: Boolean) {
        if (busy) return
        if (BuildConfig.REPO.isEmpty()) { if (!quiet) a.toast("Der er ikke sat et repo, opdateringer kan hentes fra"); return }
        busy = true
        Thread {
            val rel = try { fetch() } catch (_: Exception) { null }
            a.runOnUiThread {
                busy = false
                a.settings.updateCheckedAt = System.currentTimeMillis()
                when {
                    rel == null -> if (!quiet) a.toast("Kunne ikke hente oplysninger om udgivelser")
                    !Releases.newer(rel.version, BuildConfig.VERSION_NAME) -> if (!quiet) a.toast("Du har den nyeste version (${BuildConfig.VERSION_NAME})")
                    quiet && rel.version == a.settings.skippedVersion -> {}
                    else -> offer(rel)
                }
            }
        }.start()
    }

    private fun fetch(): Release? {
        val c = URL(Releases.latestUrl(BuildConfig.REPO)).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8000; c.readTimeout = 8000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("User-Agent", "MINK/${BuildConfig.VERSION_NAME}")
            if (c.responseCode != 200) return null
            return Releases.parse(c.inputStream.bufferedReader().use { it.readText() })
        } finally {
            c.disconnect()
        }
    }

    private fun offer(rel: Release) {
        val notes = rel.notes.trim().let { if (it.length > 700) it.take(700) + " …" else it }
        AlertDialog.Builder(a)
            .setTitle("MINK ${rel.version} er udgivet")
            .setMessage("Du har ${BuildConfig.VERSION_NAME}." + (if (notes.isNotEmpty()) "\n\n$notes" else ""))
            .setPositiveButton("Opdatér") { _, _ -> if (Hub.running) a.toast("Stop målingen først") else download(rel) }
            .setNegativeButton("Senere", null)
            .setNeutralButton("Spring denne over") { _, _ -> a.settings.skippedVersion = rel.version }
            .show()
    }

    /** Henter APK'en til appens midlertidige mappe og giver den til Androids installation. */
    private fun download(rel: Release) {
        val url = rel.apkUrl
        if (url == null) { openInBrowser(rel.pageUrl); return }
        val msg = TextView(a).apply { textSize = 15f; setTextColor(Ui.TEXT); setPadding(a.dp(22), a.dp(14), a.dp(22), 0); text = "Henter …" }
        var cancelled = false
        val dlg = AlertDialog.Builder(a).setTitle("Henter MINK ${rel.version}").setView(msg)
            .setNegativeButton("Annullér") { _, _ -> cancelled = true }.setCancelable(false).show()
        Thread {
            val file = File(a.cacheDir, "opdatering.apk")
            var ok = false
            try {
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = 10_000; c.readTimeout = 20_000
                c.setRequestProperty("User-Agent", "MINK/${BuildConfig.VERSION_NAME}")
                try {
                    if (c.responseCode == 200) {
                        val total = if (rel.apkBytes > 0) rel.apkBytes else c.contentLengthLong
                        c.inputStream.use { inp ->
                            file.outputStream().use { out ->
                                val buf = ByteArray(64 * 1024); var done = 0L; var shown = -1L
                                while (!cancelled) {
                                    val n = inp.read(buf); if (n < 0) break
                                    out.write(buf, 0, n); done += n
                                    val pct = if (total > 0) done * 100 / total else -1
                                    if (pct != shown) { shown = pct; a.runOnUiThread { msg.text = if (pct >= 0) "Henter … $pct %" else "Henter … ${TripFiles.size(done)}" } }
                                }
                                // en fil, der er kortere end GitHub oplyste, er ikke hentet færdig
                                ok = !cancelled && (rel.apkBytes <= 0 || done == rel.apkBytes)
                            }
                        }
                    }
                } finally {
                    c.disconnect()
                }
            } catch (_: Exception) {
                ok = false
            }
            a.runOnUiThread {
                dlg.dismiss()
                when {
                    cancelled -> file.delete()
                    !ok -> { file.delete(); a.toast("Opdateringen kunne ikke hentes – åbner udgivelsen i browseren"); openInBrowser(rel.pageUrl) }
                    else -> install(file, rel)
                }
            }
        }.start()
    }

    /** Afleverer APK'en til Android. Systemet spørger selv brugeren, før noget bliver installeret. */
    private fun install(file: File, rel: Release) {
        try {
            val pi = a.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply { setAppPackageName(a.packageName) }
            val id = pi.createSession(params)
            pi.openSession(id).use { s ->
                s.openWrite("MINK.apk", 0, file.length()).use { out ->
                    file.inputStream().use { it.copyTo(out) }
                    s.fsync(out)
                }
                val back = Intent(a, MainActivity::class.java).setAction(ACTION_INSTALL_STATUS)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                s.commit(PendingIntent.getActivity(a, 7, back, flags).intentSender)
            }
        } catch (_: Exception) {
            a.toast("Installationen kunne ikke startes – åbner udgivelsen i browseren")
            openInBrowser(rel.pageUrl)
        }
    }

    /**
     * Androids svar på installationen kommer tilbage til skærmen som en intent. Første svar er som regel
     * "brugeren skal bekræfte": så vises systemets egen dialog. Returnerer true, hvis intenten var et sådant svar.
     */
    fun onInstallStatus(i: Intent): Boolean {
        if (i.action != ACTION_INSTALL_STATUS) return false
        when (val status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                try { if (confirm != null) a.startActivity(confirm) } catch (_: Exception) { a.toast("Android kunne ikke vise installationen") }
            }
            PackageInstaller.STATUS_SUCCESS -> {}
            PackageInstaller.STATUS_FAILURE_ABORTED -> {}
            else -> a.toast("Opdateringen blev ikke installeret (${i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "fejl $status"})")
        }
        return true
    }

    fun openInBrowser(url: String) {
        try { a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: ActivityNotFoundException) { a.toast("Ingen browser fundet") }
    }
}
