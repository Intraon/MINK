package dk.intraon.mink.trip

import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Pakker filer i én zip. Ren Kotlin: hver fil gives som et navn og en funktion, der åbner indholdet. */
object ZipPack {
    /** En fil til zippen: [name] er navnet i zippen, og [open] åbner indholdet eller giver null, hvis filen ikke kan læses. */
    class Source(val name: String, val open: () -> InputStream?)

    /**
     * Skriver [sources] til [out] som én zip og lukker [out]. Returnerer navnene på de filer, der ikke kom med, fordi
     * de ikke kunne åbnes eller læses til ende. Resten pakkes stadig, så én ulæselig fil ikke koster hele zippen.
     */
    fun write(out: OutputStream, sources: List<Source>): List<String> {
        val unread = ArrayList<String>()
        ZipOutputStream(out.buffered()).use { z ->
            for (s in sources) {
                val inp = try { s.open() } catch (_: Exception) { null }
                if (inp == null) { unread.add(s.name); continue }
                z.putNextEntry(ZipEntry(s.name))
                try { inp.use { it.copyTo(z) } } catch (_: Exception) { unread.add(s.name) }
                z.closeEntry()
            }
        }
        return unread
    }
}
