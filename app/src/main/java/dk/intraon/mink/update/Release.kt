package dk.intraon.mink.update

/** En udgivelse på GitHub: versionen, noterne og APK-filen i den. */
data class Release(val version: String, val notes: String, val apkUrl: String?, val apkBytes: Long, val pageUrl: String)
