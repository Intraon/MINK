package dk.intraon.mink.dsp

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Finder smalbåndede signaler i rå 8-bit IQ fra rtl_tcp – uden at kende kanal, varighed eller gentagelsestid.
 *
 * Hver blok på [Config.n] samples FFT'es (rektangulært vindue, så en chirp fylder sit spektrum jævnt ud), og de
 * seneste [SMOOTH] blokke midles. I det lyttede bånd glider et vindue på [Config.signalBwHz]; et signal er der,
 * hvor vinduet ligger [Config.onSnrDb] over støjgulvet OG er smalt: begge naboområder ved siden af vinduet skal
 * være mørke. Bredbåndet trafik (LoRa-chirps fra MeshCore, Meshtastic, LoRaWAN) lyser naboerne op og tæller ikke.
 * Et signal skal også blive på sin frekvens; en langsom chirp, der driver væk, bliver for kort til at tælle.
 *
 * Et signals midte må ligge op til [EDGE_AIR_HZ] uden for båndet – det samme stykke, vandfaldet viser. Ellers
 * klippes et signal i stykker, når en dongle, der varmer op, flytter det hen over båndets kant.
 *
 * Flere signaler på hver sin frekvens følges samtidig (op til [MAX_TRACKS]), så to sendere, der overlapper i tid,
 * hver får deres egen måling med rigtig varighed.
 *
 * Et "burst" er en sammenhængende periode med signal. Varer det længere end [Config.chunkMs] (fx en konstant
 * bærebølge), meldes det i stykker med [Burst.ongoing], så en konstant sender også giver løbende målinger.
 *
 * Ren Kotlin uden Android, så den kan testes på JVM.
 */
class BurstDetector(val cfg: Config) {

    data class Config(
        /** Frekvensen tuneren står på (bør ligge uden for båndet, se [tuneFor]). */
        val tuneHz: Double,
        val bandLoHz: Double,
        val bandHiHz: Double,
        /** Forventet bredde af det signal, der ledes efter. */
        val signalBwHz: Double = 10_000.0,
        /** dB over støjgulvet for at starte et burst … */
        val onSnrDb: Double = 5.0,
        /** … og for at blive i det (hysterese). */
        val offSnrDb: Double = 2.5,
        /** Signalet skal ligge så mange dB over det kraftigste naboområde. */
        val narrowDb: Double = 6.0,
        val minBurstMs: Int = 40,
        /** Så lang pause afslutter et burst. */
        val gapMs: Int = 12,
        /** Så stor en del af burstet skal der faktisk være signal (chirps, der kun strejfer, falder fra). */
        val minFill: Double = 0.7,
        val chunkMs: Int = 2000,
        /** Frekvensområder, der aldrig tæller (fx en kendt bærebølge eller en intern spur). */
        val ignore: List<Pair<Double, Double>> = emptyList(),
        val sampleRate: Int = 1_024_000,
        val n: Int = 2048,
    )

    data class Burst(
        val tStartMs: Long,
        val durMs: Int,
        val freqHz: Double,
        /** Højeste (signal+støj)/støj i vinduet, dB. */
        val peakSnr: Double,
        val meanSnr: Double,
        /** Vinduets samlede effekt i dBFS (0 = fuldt udstyret bærebølge). */
        val peakDbfs: Double,
        /** Støjgulv pr. FFT-bin, dBFS. */
        val floorDb: Double,
        val clipPct: Double,
        val frames: Int,
        /** true = signalet fortsætter; dette er et delstykke af en lang udsendelse. */
        val ongoing: Boolean,
        /** Første og sidste blok med signal (1-baseret, samme tælling som IqRing). */
        val startFrame: Long,
        val lastFrame: Long,
        /** 0 = første stykke af en udsendelse, 1, 2 … = fortsættelser. */
        val part: Int,
        /**
         * Signalets målte bredde i Hz: det område omkring vinduet, der rummer de midterste 90 % af effekten over
         * støjen (median over burstets blokke). null, hvis signalet er for svagt til, at det kan måles.
         */
        val bwHz: Double? = null,
    )

    /** Løbende tal mellem to aflæsninger (bruges til de faste "tick"-punkter). */
    data class Stats(
        val frames: Int,
        val floorDb: Double?,
        val clipPct: Double,
        /** Blokke med kraftig, men bredbåndet energi i båndet (typisk LoRa). */
        val wideFrames: Int,
        /** Højeste vindues-SNR i perioden, også under tærsklen. */
        val maxSnr: Double?,
    )

    companion object {
        const val SMOOTH = 4            // blokke der midles (8 ms ved 1,024 MS/s)
        const val FLOOR_EVERY = 8       // støjgulvet genberegnes hver 8. blok
        const val MAX_TRACKS = 6        // så mange signaler kan følges på én gang
        /** Så langt uden for båndet må et signals midte ligge. Vandfaldet viser det samme stykke. */
        const val EDGE_AIR_HZ = 10_000.0
        /** Bredden måles kun i blokke, hvor vinduet ligger mindst så meget over støjen (13 dB), og i mindst så mange. */
        private const val BW_MIN_SNR = 20.0
        private const val BW_MIN_FRAMES = 5
        private const val LN2 = 0.6931471805599453

        /**
         * Den signalbredde, detektoren bør stå på, når målet måles til [measuredHz], og den står på [setHz].
         * Null = lad den stå: forskellen er for lille til at betyde noget.
         */
        fun widthFor(measuredHz: Double, setHz: Double): Double? {
            if (setHz <= 0 || measuredHz <= 0) return null
            val ratio = measuredHz / setHz
            if (ratio in 0.6..1.3) return null
            return (Math.round(measuredHz / 500.0) * 500.0).coerceIn(1_500.0, 100_000.0).takeIf { it != setHz }
        }

        /** Læg tuneren lige over båndet, så DC-spidsen i midten ikke rammer det, der lyttes efter. */
        fun tuneFor(bandLoHz: Double, bandHiHz: Double, sampleRate: Int = 1_024_000): Double {
            val width = bandHiHz - bandLoHz
            val usable = 0.44 * sampleRate
            return if (width + 75_000 <= usable) bandHiHz + 75_000 else (bandLoHz + bandHiHz) / 2
        }
    }

    var onBurst: ((Burst) -> Unit)? = null

    /**
     * Får hver bloks rå FFT (real- og imaginærdel, uskaleret, bin 0 = tunerfrekvensen) og støjgulvet pr. bin
     * (skaleret med 1/n²) – til vandfaldet.
     */
    var spectrumSink: ((DoubleArray, DoubleArray, Double) -> Unit)? = null

    /** true, mens mindst ét signal er i gang (gain bør ikke skiftes imens). */
    @Volatile var busy = false
        private set

    private val n = cfg.n
    private val fft = Fft(n)
    private val binHz = cfg.sampleRate.toDouble() / n
    val frameMs = 1000.0 * n / cfg.sampleRate
    private val norm = 1.0 / (n.toDouble() * n)
    private val re = DoubleArray(n)
    private val im = DoubleArray(n)
    private val ring = Array(SMOOTH) { DoubleArray(n) }
    private var ringPos = 0
    private val acc = DoubleArray(n)
    private val pre = DoubleArray(n + 1)

    private fun bin(hz: Double) = ((hz - cfg.tuneHz) / binHz).roundToInt() + n / 2

    /** Frekvensen midt i FFT-bin [m] (må gerne være et kommatal). */
    fun freqOfBin(m: Double) = cfg.tuneHz + (m - n / 2) * binHz

    private val uLo = max(1, n / 2 - (0.44 * n).toInt())
    private val uHi = min(n - 2, n / 2 + (0.44 * n).toInt())
    private val dcHalf = (3_000 / binHz).roundToInt()
    private val w = max(3, (cfg.signalBwHz / binHz).roundToInt())
    private val g = max(1, w / 2)
    private val valid = BooleanArray(n)
    private val sLo: Int
    private val sHi: Int
    /** Første og sidste FFT-bin i det lyttede bånd. */
    val bandBinLo: Int
    val bandBinHi: Int
    /** Laveste og højeste frekvens, et signals midte kan have og stadig blive fundet. */
    val centerLoHz: Double
    val centerHiHz: Double
    private val floorBins: IntArray
    private val floorTmp: DoubleArray
    private var floor = 1e-10
    private var frameNo = 0L
    private val onLin = 10.0.pow(cfg.onSnrDb / 10)
    private val offLin = 10.0.pow(cfg.offSnrDb / 10)
    private val narrowLin = 10.0.pow(cfg.narrowDb / 10)
    private val gapFrames = max(1, (cfg.gapMs / frameMs).roundToInt())

    init {
        val bLo = max(bin(cfg.bandLoHz), uLo)
        val bHi = min(bin(cfg.bandHiHz), uHi)
        require(bHi >= bLo) { "Båndet ligger uden for det, tuneren kan se" }
        bandBinLo = bLo; bandBinHi = bHi
        // vinduets midte må ligge EDGE_AIR_HZ uden for båndet (g = et halvt vindue)
        val air = (EDGE_AIR_HZ / binHz).roundToInt()
        var lo = bLo - g - air
        var hi = bHi - w + 1 + g + air
        if (hi < lo) { lo = (bLo + bHi) / 2 - w / 2; hi = lo }
        sLo = max(lo, uLo); sHi = min(hi, uHi - w + 1)
        centerLoHz = freqOfBin(sLo + (w - 1) / 2.0); centerHiHz = freqOfBin(sHi + (w - 1) / 2.0)
        val ign = cfg.ignore.map { bin(it.first) to bin(it.second) }
        for (s in sLo..sHi) {
            val e = s + w - 1
            val dc = s <= n / 2 + dcHalf && e >= n / 2 - dcHalf
            valid[s] = !dc && ign.none { s <= it.second && e >= it.first }
        }
        floorBins = (uLo..uHi).filter { kotlin.math.abs(it - n / 2) > dcHalf }.toIntArray()
        floorTmp = DoubleArray(floorBins.size)
    }

    /** Et signal, der er i gang lige nu. [s] er vinduets startbin, da det begyndte. */
    private class Track(val s: Int, bwBins: Int) {
        /** Hvor mange blokke, der målte hver bredde (i bins). */
        val bw = IntArray(bwBins + 1)
        var bwN = 0
        var start = 0L
        var startFrame = 0L
        var lastFrame = 0L
        var off = 0
        var frames = 0
        var clip = 0
        var samples = 0
        var peak = 0.0
        var peakSum = 0.0
        var sumSnr = 0.0
        var floorSum = 0.0
        var freqNum = 0.0
        var freqDen = 0.0
        var part = 0

        fun startChunk(tMs: Long, frame: Long) {
            start = tMs; startFrame = frame; lastFrame = frame
            frames = 0; clip = 0; samples = 0; peak = 0.0; peakSum = 0.0
            sumSnr = 0.0; floorSum = 0.0; freqNum = 0.0; freqDen = 0.0
            bw.fill(0); bwN = 0
        }
    }

    private val tracks = ArrayList<Track>()

    // løbende stats
    private var sFrames = 0
    private var sFloorSum = 0.0
    private var sClip = 0
    private var sSamples = 0
    private var sWide = 0
    private var sMax = 0.0

    @Synchronized
    fun takeStats(): Stats {
        val s = Stats(
            frames = sFrames,
            floorDb = if (sFrames > 0) db(sFloorSum / sFrames) else null,
            clipPct = if (sSamples > 0) 100.0 * sClip / sSamples else 0.0,
            wideFrames = sWide,
            maxSnr = if (sFrames > 0 && sMax > 0) db(sMax) else null,
        )
        sFrames = 0; sFloorSum = 0.0; sClip = 0; sSamples = 0; sWide = 0; sMax = 0.0
        return s
    }

    private fun db(x: Double) = 10.0 * log10(x + 1e-30)
    private fun mean(a: Int, b: Int) = (pre[b] - pre[a]) / (b - a)

    /** Middeleffekten i naboområdet [a, b) – eller −1, hvis det ikke kan bruges (uden for spektret eller på DC). */
    private fun flank(a0: Int, b0: Int): Double {
        val a = max(a0, uLo); val b = min(b0, uHi + 1)
        if (b - a < g) return -1.0
        if (a <= n / 2 + dcHalf && b - 1 >= n / 2 - dcHalf) return -1.0
        return mean(a, b)
    }

    /** Er vinduet, der starter i bin [s], smalt – altså klart kraftigere end begge naboområder? */
    private fun narrow(s: Int, pin: Double): Boolean {
        val fl = max(flank(s - g - w, s - g), flank(s + w + g, s + 2 * w + g))
        return pin - floor >= narrowLin * max(fl - floor, 0.0)
    }

    /** Behandler én blok på 2·n bytes (I,Q,I,Q…) fra [off] i [buf]. [tMs] er blokkens starttid. */
    @Synchronized
    fun processBlock(buf: ByteArray, off: Int, tMs: Long) {
        var clip = 0
        for (k in 0 until n) {
            val i = buf[off + 2 * k].toInt() and 0xff
            val q = buf[off + 2 * k + 1].toInt() and 0xff
            if (i == 0 || i == 255 || q == 0 || q == 255) clip++
            re[k] = (i - 127.4) / 128.0
            im[k] = (q - 127.4) / 128.0
        }
        fft.transform(re, im)
        val cur = ring[ringPos]
        ringPos = (ringPos + 1) % SMOOTH
        for (m in 0 until n) {
            val k = (m + n / 2) % n          // fftshift: tunerfrekvensen ligger i n/2
            cur[m] = (re[k] * re[k] + im[k] * im[k]) * norm
        }
        if (frameNo % FLOOR_EVERY == 0L) {
            for (j in floorBins.indices) floorTmp[j] = cur[floorBins[j]]
            floorTmp.sort()
            // periodogrammets bins er eksponentialfordelte: middel = median / ln 2
            floor = max(floorTmp[floorTmp.size / 2] / LN2, 1e-12)
        }
        spectrumSink?.invoke(re, im, floor)
        frameNo++
        pre[uLo] = 0.0
        for (m in uLo..uHi) {
            var a = 0.0
            for (r in 0 until SMOOTH) a += ring[r][m]
            a /= SMOOTH
            acc[m] = a
            pre[m + 1] = pre[m] + a
        }

        // 1) signaler, der er i gang: led kun lige omkring deres egen frekvens
        val offThr = floor * offLin
        val it = tracks.iterator()
        while (it.hasNext()) {
            val tr = it.next()
            var best = -1
            var bestPin = 0.0
            for (s in max(sLo, tr.s - g)..min(sHi, tr.s + g)) {
                if (!valid[s]) continue
                val pin = mean(s, s + w)
                if (pin < offThr || pin <= bestPin) continue
                if (narrow(s, pin)) { best = s; bestPin = pin }
            }
            if (best >= 0) {
                hit(tr, best, bestPin, clip)
                if ((frameNo - tr.startFrame + 1) * frameMs >= cfg.chunkMs) {
                    emit(tr, true)
                    tr.part++
                    tr.startChunk(tMs + frameMs.toLong(), frameNo + 1)
                }
            } else if (++tr.off >= gapFrames) {
                if (tr.frames * frameMs >= cfg.minBurstMs) emit(tr, false)
                it.remove()
            }
        }

        // 2) nyt signal: det kraftigste smalle vindue, der ikke ligger oven i et, der allerede følges
        var best = -1
        var bestPin = 0.0
        var anyMax = 0.0
        var wide = false
        val onThr = floor * onLin
        scan@ for (s in sLo..sHi) {
            if (!valid[s]) continue
            for (tr in tracks) if (kotlin.math.abs(s - tr.s) < w + g) continue@scan
            val pin = mean(s, s + w)
            if (pin > anyMax) anyMax = pin
            if (pin < onThr || pin <= bestPin) continue
            if (narrow(s, pin)) { best = s; bestPin = pin } else wide = true
        }
        if (best >= 0 && tracks.size < MAX_TRACKS) {
            val tr = Track(best, 3 * w + 2 * g)
            tr.startChunk(tMs, frameNo)
            tracks.add(tr)
            hit(tr, best, bestPin, clip)
        }

        sFrames++; sFloorSum += floor; sClip += clip; sSamples += n
        if (best < 0 && wide) sWide++
        if (anyMax / floor > sMax) sMax = anyMax / floor
        busy = tracks.isNotEmpty()
    }

    /** Én blok med signal lægges til [tr]. */
    private fun hit(tr: Track, s: Int, pin: Double, clip: Int) {
        tr.off = 0
        tr.lastFrame = frameNo
        tr.frames++; tr.clip += clip; tr.samples += n
        val snr = pin / floor
        tr.sumSnr += snr; tr.floorSum += floor
        if (snr > tr.peak) tr.peak = snr
        if (pin * w > tr.peakSum) tr.peakSum = pin * w
        var num = 0.0; var den = 0.0
        for (m in s until s + w) { num += m * acc[m]; den += acc[m] }
        tr.freqNum += num / den * pin; tr.freqDen += pin
        if (snr >= BW_MIN_SNR) measureWidth(tr, s)
    }

    /**
     * Signalets bredde i denne blok: i vinduet og naboområderne på begge sider findes det stykke, der rummer de
     * midterste 90 % af effekten over støjen. Kun bins klart over støjen tæller med, så støjen ikke gør det bredere.
     */
    private fun measureWidth(tr: Track, s: Int) {
        val a = max(uLo, s - w - g)
        val b = min(uHi, s + 2 * w + g - 1)
        val thr = 2 * floor
        var tot = 0.0
        for (m in a..b) if (acc[m] > thr && kotlin.math.abs(m - n / 2) > dcHalf) tot += acc[m] - floor
        if (tot <= 0.0) return
        var cum = 0.0
        var first = -1
        var last = b
        for (m in a..b) {
            if (acc[m] > thr && kotlin.math.abs(m - n / 2) > dcHalf) cum += acc[m] - floor
            if (first < 0 && cum >= 0.05 * tot) first = m
            if (cum >= 0.95 * tot) { last = m; break }
        }
        if (first < 0) return
        tr.bw[min(last - first + 1, tr.bw.size - 1)]++
        tr.bwN++
    }

    /** Medianen af de målte bredder, eller null hvis for få blokke kunne måles. */
    private fun widthOf(tr: Track): Double? {
        if (tr.bwN < BW_MIN_FRAMES) return null
        var seen = 0
        for (k in tr.bw.indices) {
            seen += tr.bw[k]
            if (2 * seen >= tr.bwN) return k * binHz
        }
        return null
    }

    private fun emit(tr: Track, ongoing: Boolean) {
        if (tr.frames == 0) return
        if (tr.frames < cfg.minFill * (tr.lastFrame - tr.startFrame + 1)) return
        val centerBin = tr.freqNum / tr.freqDen
        onBurst?.invoke(
            Burst(
                tStartMs = tr.start,
                durMs = ((tr.lastFrame - tr.startFrame + 1) * frameMs).roundToInt(),
                freqHz = cfg.tuneHz + (centerBin - n / 2) * binHz,
                peakSnr = db(tr.peak),
                meanSnr = db(tr.sumSnr / tr.frames),
                peakDbfs = db(tr.peakSum),
                floorDb = db(tr.floorSum / tr.frames),
                clipPct = if (tr.samples > 0) 100.0 * tr.clip / tr.samples else 0.0,
                frames = tr.frames,
                ongoing = ongoing,
                startFrame = tr.startFrame,
                lastFrame = tr.lastFrame,
                part = tr.part,
                bwHz = widthOf(tr),
            )
        )
    }
}
