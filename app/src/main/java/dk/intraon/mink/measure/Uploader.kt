package dk.intraon.mink.measure

import dk.intraon.mink.BuildConfig
import dk.intraon.mink.core.Hub
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

/**
 * Sender målinger i små pakker til en webhook (fx Home Assistant) hvert 10. sekund.
 * Ved fejl bliver de liggende i køen og sendes senere (op til [MAX_QUEUE]). Kun én tråd sender, og en pakke
 * beholder sit nummer, til den er afleveret – et gensendt kald giver derfor samme pakke igen, ikke en ny.
 */
class Uploader(private val url: String, private val session: String) {
    private val lock = Any()
    private val queue = ArrayDeque<JSONObject>()
    @Volatile private var running = false
    private var thread: Thread? = null
    private var seq = 1

    val enabled get() = url.isNotBlank()

    fun add(o: JSONObject) {
        if (!enabled) return
        synchronized(lock) {
            queue.addLast(o)
            while (queue.size > MAX_QUEUE) queue.removeFirst()
            Hub.queued = queue.size
        }
    }

    fun start() {
        if (!enabled) return
        running = true
        thread = Thread({ loop() }, "uploader").apply { isDaemon = true; start() }
    }

    /** Stopper; sende-tråden tømmer selv køen en sidste gang, før den slutter. */
    fun stop() {
        running = false
        thread?.interrupt()
    }

    private fun loop() {
        while (running) {
            try { Thread.sleep(PERIOD_MS) } catch (_: InterruptedException) {}
            var rounds = 0
            while (rounds++ < 10 && sendOnce()) { /* tøm efterslæb */ }
        }
    }

    private fun sendOnce(): Boolean {
        val items = synchronized(lock) { queue.take(BATCH) }
        if (items.isEmpty()) return false
        val body = JSONObject()
            .put("session", session)
            .put("seq", seq)
            .put("version", BuildConfig.VERSION_NAME)
            .put("records", JSONArray(items))
            .toString()
        val code = post(url, body)
        synchronized(lock) {
            if (code in 200..299) {
                // fjern kun dem, der faktisk blev sendt (køen kan være trimmet imens)
                for (it in items) if (queue.firstOrNull() === it) queue.removeFirst()
                seq++
                Hub.sent += items.size
                Hub.uploadError = null
            } else {
                Hub.uploadError = if (code > 0) "HTTP $code" else lastError
            }
            Hub.queued = queue.size
        }
        Hub.changed()
        return code in 200..299
    }

    companion object {
        const val MAX_QUEUE = 5000
        const val BATCH = 30
        const val PERIOD_MS = 10_000L
        @Volatile var lastError: String = "fejl"

        /** POST'er JSON; returnerer HTTP-koden eller −1 ved netværksfejl (årsagen i [lastError]). */
        fun post(url: String, body: String): Int = try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toByteArray()) }
            val code = c.responseCode
            c.disconnect()
            code
        } catch (e: Exception) {
            lastError = e.javaClass.simpleName
            -1
        }
    }
}
