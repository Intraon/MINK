package dk.intraon.mink.radio

import java.io.DataInputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * Klient til rtl_tcp-protokollen, som "Rtl-sdr driver" (marto.rtl_tcp_andro) taler på localhost.
 * Læser IQ i blokke af [blockBytes] og kalder [onBlock] i læsetråden.
 */
class RtlTcpClient(
    private val host: String,
    private val port: Int,
    private val blockBytes: Int,
    private val onBlock: (ByteArray, Long) -> Unit,
    private val onState: (State, String) -> Unit,
) {
    enum class State { CONNECTING, RECEIVING, WAITING, FAILED }

    companion object {
        /** Gyldige gain-trin for R820T/R828D i dB. */
        val GAINS = doubleArrayOf(
            0.0, 0.9, 1.4, 2.7, 3.7, 7.7, 8.7, 12.5, 14.4, 15.7, 16.6, 19.7, 20.7, 22.9, 25.4, 28.0,
            29.7, 32.8, 33.8, 36.4, 37.2, 38.6, 40.2, 42.1, 43.4, 43.9, 44.5, 48.0, 49.6,
        )
        val TUNERS = mapOf(1 to "E4000", 2 to "FC0012", 3 to "FC0013", 4 to "FC2580", 5 to "R820T", 6 to "R828D")
        const val MAX_TRIES = 30
        /** Så længe venter [stop] højst på, at læsetråden bliver færdig med den blok, den er i gang med. */
        const val STOP_WAIT_MS = 500L
    }

    @Volatile private var socket: Socket? = null
    @Volatile private var out: OutputStream? = null
    @Volatile private var running = false
    private var thread: Thread? = null
    /**
     * Kommandoer, der kommer udefra (gain-skift), skrives fra denne tråd. Android afviser netværk på hovedtråden
     * (NetworkOnMainThreadException), og et tryk på − eller + kommer derfra: skrevet direkte nåede kommandoen
     * aldrig frem til driveren, og gain skiftede først ved næste Start.
     */
    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "rtl_tcp-cmd").apply { isDaemon = true } }
    @Volatile var tuner: String = "?"
        private set

    fun start(freqHz: Long, rate: Int, ppm: Int, gainDb: Double) {
        running = true
        thread = Thread({ run(freqHz, rate, ppm, gainDb) }, "rtl_tcp").apply { start() }
    }

    /**
     * Stopper forbindelsen og venter på læsetråden, så [onBlock] ikke kører og ikke bliver kaldt igen, når [stop]
     * er vendt tilbage. Den, der stopper, kan derfor lukke filer eller starte en ny forbindelse med samme
     * modtager uden at dele den med den gamle tråd. Kaldt fra læsetråden selv ventes der ikke.
     */
    fun stop() {
        running = false
        try { socket?.close() } catch (_: Exception) {}
        val t = thread
        t?.interrupt()
        writer.shutdown()
        if (t != null && t !== Thread.currentThread()) try { t.join(STOP_WAIT_MS) } catch (_: InterruptedException) {}
    }

    @Synchronized
    private fun cmd(c: Int, v: Int) {
        val o = out ?: return
        val b = ByteBuffer.allocate(5)
        b.put(c.toByte()); b.putInt(v)
        try { o.write(b.array()); o.flush() } catch (_: Exception) {}
    }

    fun setGain(db: Double) {
        cmd(0x03, 1)          // manuel gain
        cmd(0x08, 0)          // RTL AGC fra
        cmd(0x04, (db * 10).roundToInt())
    }

    private fun run(freqHz: Long, rate: Int, ppm: Int, startGain: Double) {
        var tries = 0
        var gainDb = startGain
        while (running) {
            try {
                onState(State.CONNECTING, "Forbinder til driveren")
                val s = Socket()
                // stop() skal kunne lukke socket'en, også mens den forbinder
                socket = s
                if (!running) break
                s.connect(InetSocketAddress(host, port), 3000)
                s.soTimeout = 5000
                s.receiveBufferSize = 1 shl 20
                out = s.getOutputStream()
                val inp = DataInputStream(s.getInputStream().buffered(1 shl 18))

                // rtl_tcp sender 12 bytes: "RTL0", tunertype, antal gain-trin
                val head = ByteArray(12)
                inp.readFully(head)
                val buf = ByteArray(blockBytes)
                var pre = 0
                if (head[0] == 'R'.code.toByte() && head[1] == 'T'.code.toByte() && head[2] == 'L'.code.toByte()) {
                    val t = ByteBuffer.wrap(head, 4, 4).int
                    tuner = TUNERS[t] ?: "type $t"
                } else {
                    // ingen header: de 12 bytes er samples
                    System.arraycopy(head, 0, buf, 0, 12); pre = 12
                    tuner = "ukendt"
                }

                cmd(0x02, rate)
                // er tuneren flyttet undervejs, er det dér, en ny forbindelse efter et udfald skal stå
                tunedHz = lastFreq ?: freqHz
                cmd(0x05, lastPpm ?: ppm)
                cmd(0x01, tunedHz.toInt())
                gainDb = lastGain ?: gainDb
                setGain(gainDb)
                onState(State.RECEIVING, "Modtager")
                tries = 0

                while (running) {
                    inp.readFully(buf, pre, blockBytes - pre)
                    pre = 0
                    // en blok, der blev læst færdig, mens der blev stoppet, afleveres ikke
                    if (!running) break
                    onBlock(buf, System.currentTimeMillis())
                }
            } catch (e: Exception) {
                out = null
                try { socket?.close() } catch (_: Exception) {}
                if (!running) break
                tries++
                if (tries > MAX_TRIES) {
                    onState(State.FAILED, "Driveren svarer ikke")
                    running = false
                    break
                }
                onState(State.WAITING, "Venter på driveren (${e.javaClass.simpleName})")
                try { Thread.sleep(1000) } catch (_: InterruptedException) { break }
            }
        }
        out = null
        try { socket?.close() } catch (_: Exception) {}
    }

    /** Senest satte gain, så en genforbindelse fortsætter, hvor den slap. */
    @Volatile private var lastGain: Double? = null
    @Volatile private var lastPpm: Int? = null
    @Volatile private var tunedHz = 0L
    @Volatile private var lastFreq: Long? = null

    /** Flytter tuneren til [hz] nu. En ny forbindelse efter et udfald starter også dér. Må kaldes fra enhver tråd. */
    fun changeFreq(hz: Long) {
        lastFreq = hz; tunedHz = hz
        try { writer.execute { cmd(0x01, hz.toInt()) } } catch (_: java.util.concurrent.RejectedExecutionException) {}
    }

    /** Skifter gain nu. Må kaldes fra enhver tråd, også hovedtråden. */
    /** Ny PPM-korrektion med det samme. Frekvensen sendes igen, så tuneren flytter sig, uanset hvilken driver der svarer. */
    fun changePpm(ppm: Int) {
        lastPpm = ppm
        try {
            writer.execute { cmd(0x05, ppm); if (tunedHz != 0L) cmd(0x01, tunedHz.toInt()) }
        } catch (_: java.util.concurrent.RejectedExecutionException) {}
    }

    fun changeGain(db: Double) {
        lastGain = db
        try { writer.execute { setGain(db) } } catch (_: java.util.concurrent.RejectedExecutionException) {}
    }
}
