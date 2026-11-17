package dk.intraon.mink.measure

import dk.intraon.mink.BuildConfig
import dk.intraon.mink.core.Hub
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

/**
 * Sender målinger i små pakker til en webhook (fx Home Assistant) hvert 10. sekund. Ved fejl bliver de liggende i
 * køen ([Outbox], op til [MAX_QUEUE]) og sendes senere. Kun én tråd sender. Loggen på telefonen er turens egentlige
 * optegnelse; webhooken er en kopi undervejs, og det, der ikke er afleveret, når appen lukkes, findes kun i loggen.
 */
class Uploader(private val url: String, private val session: String) {
    private val box = Outbox<JSONObject>(MAX_QUEUE, BATCH)
    @Volatile private var running = false
    private var thread: Thread? = null

    val enabled get() = url.isNotBlank()

    fun add(o: JSONObject) {
        if (!enabled) return
        Hub.queued = box.add(o)
    }

    fun start() {
        if (!enabled) return
        running = true
        thread = Thread({ loop() }, "uploader").apply { isDaemon = true; start() }
    }

    /**
     * Stopper. Sende-tråden sender resten af køen, før den slutter: til køen er tom, en pakke ikke kan afleveres,
     * eller der er gået [FLUSH_MS].
     */
    fun stop() {
        running = false
        thread?.interrupt()
    }

    private fun loop() {
        while (running) {
            try { Thread.sleep(PERIOD_MS) } catch (_: InterruptedException) {}
            if (!running) break
            // højst ti pakker pr. runde, så et langt efterslæb ikke holder forbindelsen optaget i ét stræk
            var rounds = 0
            box.drain({ rounds++ < 10 }, ::send)
        }
        val deadline = System.nanoTime() + FLUSH_MS * 1_000_000
        box.drain({ System.nanoTime() < deadline }, ::send)
    }

    private fun send(p: Outbox.Packet<JSONObject>): Boolean {
        val body = JSONObject()
            .put("session", session)
            .put("seq", p.seq)
            .put("version", BuildConfig.VERSION_NAME)
            .put("records", JSONArray(p.items))
            .toString()
        val code = post(url, body)
        val ok = code in 200..299
        if (ok) { Hub.sent += p.items.size; Hub.uploadError = null }
        else Hub.uploadError = if (code > 0) "HTTP $code" else lastError
        // pakken ligger stadig i køen, til den er meldt afleveret
        Hub.queued = (box.size - if (ok) p.items.size else 0).coerceAtLeast(0)
        Hub.changed()
        return ok
    }

    companion object {
        const val MAX_QUEUE = 5000
        const val BATCH = 30
        const val PERIOD_MS = 10_000L
        /** Så længe bruges der højst på at sende resten af køen, når der stoppes. */
        const val FLUSH_MS = 15_000L
        @Volatile var lastError: String = "fejl"

        /** POST'er JSON; returnerer HTTP-koden eller −1 ved netværksfejl (årsagen i [lastError]). */
        fun post(url: String, body: String): Int {
            var c: HttpURLConnection? = null
            return try {
                c = URL(url).openConnection() as HttpURLConnection
                c.requestMethod = "POST"
                c.connectTimeout = 8000
                c.readTimeout = 8000
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toByteArray()) }
                c.responseCode
            } catch (e: Exception) {
                lastError = e.javaClass.simpleName
                -1
            } finally {
                // også efter en fejl: ellers bliver forbindelsen hængende, til den ryddes op af sig selv
                c?.disconnect()
            }
        }
    }
}
