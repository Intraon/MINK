package dk.intraon.mink.core

import android.graphics.Color
import android.location.Location
import android.os.Handler
import android.os.Looper
import dk.intraon.mink.direction.CompassCheck
import dk.intraon.mink.direction.Rose
import dk.intraon.mink.radio.SourceTracker
import dk.intraon.mink.waterfall.Scope
import java.util.concurrent.CopyOnWriteArrayList

/** Fælles tilstand mellem tjenesten (måler) og skærmen (viser). */
object Hub {
    enum class Conn { STOPPED, CONNECTING, RECEIVING, WAITING, FAILED }

    data class Point(
        val t: Long,
        val lat: Double?,
        val lon: Double?,
        val level: Double?,   // gain-kompenseret niveau (dBFS − gain); null = intet hørt i perioden
        val snr: Double?,
        val freqHz: Double?,
        val durMs: Int,
        val gain: Double,
    )

    /** Et mærke, brugeren har sat. [kind] er en af typerne i [MarkKind]. */
    data class Mark(val t: Long, val lat: Double?, val lon: Double?, val n: Int, val kind: String = MarkKind.PLAIN)
    data class GainStep(val t: Long, val to: Double)
    /**
     * En gemt pejling: herfra, i den retning, med det niveau (null = intet hørt, da den blev taget). For et sted,
     * der er målt en omgang rundt, er [swing] forskellen i dB mellem den stærkeste og den svageste retning, og
     * [others] er andre retninger, der var næsten lige så stærke (genskin). Null og tom for en pejling uden omgang.
     */
    data class BearingLine(
        val t: Long, val lat: Double?, val lon: Double?, val deg: Double, val level: Double?, val n: Int,
        val swing: Double? = null, val others: List<Double> = emptyList(),
    )

    @Volatile var running = false
    @Volatile var conn = Conn.STOPPED
    @Volatile var statusText = "Stoppet"
    @Volatile var tuner = "?"
    @Volatile var gainDb = 20.7
    /** Det gain, brugeren har valgt. Står [gainDb] lavere, har beskyttelsen mod mætning skruet ned. */
    @Volatile var gainChosenDb = 20.7
    /** Beskyttelse mod mætning slået til. */
    @Volatile var autoGain = true
    /** Målet ligger så tæt på kanten af det, detektoren søger i, at det er ved at falde ud. */
    @Volatile var targetAtEdge = false
    /**
     * Det bånd, der lyttes på nu, og turens eget bånd (Hz). De er ens, indtil brugeren trækker vandfaldet til siden
     * for at kigge på nabofrekvenserne. 0 = der måles ikke.
     */
    @Volatile var bandLoHz = 0.0
    @Volatile var bandHiHz = 0.0
    @Volatile var homeLoHz = 0.0
    @Volatile var homeHiHz = 0.0
    /** Der kigges ved siden af turens bånd. */
    val away: Boolean get() = bandLoHz != homeLoHz || bandHiHz != homeHiHz
    /** Målet ligger i det bånd, der lyttes på. False, mens der kigges et sted, hvor målet ikke kan høres. */
    @Volatile var targetHeard = true
    /** Den signalbredde, detektoren er sat til (Hz). */
    @Volatile var signalBwHz = 0.0
    @Volatile var floorDb: Double? = null
    @Volatile var clipPct = 0.0
    @Volatile var location: Location? = null
    @Volatile var logName: String? = null
    @Volatile var logOk = true
    @Volatile var sent = 0
    @Volatile var queued = 0
    @Volatile var uploadError: String? = null
    @Volatile var uploadOn = false
    @Volatile var lastBurst: Point? = null
    @Volatile var trendDelta: Double? = null
    /** Gain blev skiftet mellem de bursts, trenden bygger på – så skal der mere til, før pilen slår ud. */
    @Volatile var trendUncertain = false
    /** Typisk tid mellem bursts, målt på de seneste (null = ukendt endnu). */
    @Volatile var intervalMs: Long? = null
    @Volatile var sessionStart = 0L
    /** De sendere, der høres lige nu (målet først). */
    @Volatile var sources: List<SourceTracker.View> = emptyList()
    /** Den frekvens, brugeren har valgt som mål (følger målets drift). null = intet mål valgt. */
    @Volatile var targetHz: Double? = null
    /** Brugerens egen kanal (midte og bredde i Hz); 0 = ingen. Sættes fra indstillingerne. */
    @Volatile var channelHz = 0.0
    @Volatile var channelBwHz = 0.0
    /** [Scope.seq], da PPM-korrektionen sidst blev ændret: ældre rækker i vandfaldet ligger forskudt. */
    @Volatile var ppmSeq = 0L
    fun inChannel(hz: Double): Boolean = channelHz > 0 && channelBwHz > 0 && kotlin.math.abs(hz - channelHz) <= channelBwHz / 2

    /** Retningsantenne slået til: telefonens retning gemmes på hver måling, og kameraskærmen er med. */
    @Volatile var directional = false
    /** Hvor telefonens bagside peger (grader fra sand nord) og hvor meget den vipper op; null = intet kompas endnu. */
    @Volatile var headingDeg: Double? = null
    @Volatile var tiltDeg = 0.0
    /** Telefonen ligger fladt med kameraet mod jorden: retningen er dér, dens øverste kant peger. */
    @Volatile var flat = false
    @Volatile var headingAt = 0L
    /** Magnetometerets egen melding om præcision (−1 = ikke meldt), det målte felt og det forventede på stedet (µT). */
    @Volatile var magAccuracy = -1
    @Volatile var magUt: Double? = null
    @Volatile var magExpectedUt: Double? = null
    /** Kompasset er ikke til at stole på lige nu (se [CompassCheck]). */
    val compassPoor: Boolean get() = CompassCheck.poor(magAccuracy, magUt, magExpectedUt)
    /** Brugeren har lavet 8-tallerne (eller sprunget dem over) siden appen blev åbnet. */
    @Volatile var compassCalibrated = false
    /** Hvor meget telefonen har drejet om hver akse (radianer), siden kalibreringen begyndte. */
    val turn = DoubleArray(3)
    /** Målets styrke pr. retning fra det sted, man står. */
    val rose = Rose()
    val bearings = CopyOnWriteArrayList<BearingLine>()
    /** En pejling, appen selv har gemt for et sted: stedet, pejlingens nummer og fotoets navn (null = intet foto). */
    class AutoBearing(val stop: Rose.Stop, val n: Int, val photo: String?)
    /** Den seneste pejling, appen selv har gemt. Kameraskærmen gemmer fotoet til den. */
    @Volatile var autoBearing: AutoBearing? = null
    /** Tidspunktet for den måling, kameraskærmen holder et kamerabillede af; 0 = intet. */
    @Volatile var heldFrameAt = 0L

    /** Retningen, hvis kompasset har meldt sig inden for det seneste sekund. */
    fun freshHeading(now: Long = System.currentTimeMillis()): Double? = headingDeg?.takeIf { now - headingAt < 1000 }

    val bursts = CopyOnWriteArrayList<Point>()
    /** Faste tick-punkter uden burst (hvor der var stille). */
    val silent = CopyOnWriteArrayList<Point>()
    val marks = CopyOnWriteArrayList<Mark>()
    val gainSteps = CopyOnWriteArrayList<GainStep>()

    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun listen(l: () -> Unit) { listeners.add(l) }
    fun unlisten(l: () -> Unit) { listeners.remove(l) }
    fun changed() { main.post { listeners.forEach { it() } } }

    fun reset() {
        bursts.clear(); silent.clear(); marks.clear(); gainSteps.clear(); bearings.clear(); rose.reset()
        autoBearing = null; heldFrameAt = 0L
        lastBurst = null; trendDelta = null; trendUncertain = false; floorDb = null; intervalMs = null
        sent = 0; queued = 0; uploadError = null; clipPct = 0.0; logOk = true
        sessionStart = System.currentTimeMillis()
        sources = emptyList()
        targetAtEdge = false
        bandLoHz = 0.0; bandHiHz = 0.0; homeLoHz = 0.0; homeHiHz = 0.0; targetHeard = true
        ppmSeq = 0
    }

    /** Nyt mål: graf, kort og trend starter forfra, så to sendere ikke blandes. Mærker bliver stående. */
    fun clearTrack() {
        bursts.clear(); silent.clear(); gainSteps.clear(); rose.reset()
        lastBurst = null; trendDelta = null; trendUncertain = false; intervalMs = null
        sessionStart = System.currentTimeMillis()
        changed()
    }

    /** Tilføj et burst; beregn ændringen i forhold til medianen af de tre forrige og den typiske tid mellem bursts. */
    fun addBurst(p: Point) {
        val prevPts = bursts.takeLast(3)
        val prev = prevPts.mapNotNull { it.level }.sorted()
        trendDelta = if (prev.isNotEmpty() && p.level != null) p.level - prev[prev.size / 2] else null
        trendUncertain = prevPts.any { it.gain != p.gain }
        bursts.add(p)
        lastBurst = p
        val ts = bursts.takeLast(6).map { it.t }
        val gaps = ts.zipWithNext { a, b -> b - a }.filter { it >= 300 }.sorted()
        intervalMs = if (gaps.size >= 2) gaps[gaps.size / 2] else null
        changed()
    }

    /** Sessionens niveau-skala (min, max). */
    fun levelRange(): Pair<Double, Double>? {
        var lo = Double.MAX_VALUE; var hi = -Double.MAX_VALUE
        for (b in bursts) { val l = b.level ?: continue; if (l < lo) lo = l; if (l > hi) hi = l }
        return if (hi >= lo) lo to hi else null
    }

    /** Blå (svagest) → grøn → gul → rød (stærkest) på sessionens skala. */
    fun colorFor(level: Double, range: Pair<Double, Double>?): Int {
        val (lo, hi) = range ?: return Color.rgb(0x4F, 0xC3, 0xF7)
        val x = if (hi - lo < 1.0) 0.5 else ((level - lo) / (hi - lo)).coerceIn(0.0, 1.0)
        return Color.HSVToColor(floatArrayOf((240 * (1 - x)).toFloat(), 0.85f, 1f))
    }
}
