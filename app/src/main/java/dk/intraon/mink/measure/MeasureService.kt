package dk.intraon.mink.measure

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import dk.intraon.mink.BuildConfig
import dk.intraon.mink.R
import dk.intraon.mink.core.Hub
import dk.intraon.mink.core.MarkKind
import dk.intraon.mink.core.Settings
import dk.intraon.mink.direction.Compass
import dk.intraon.mink.direction.Rose
import dk.intraon.mink.dsp.BurstDetector
import dk.intraon.mink.dsp.IqRing
import dk.intraon.mink.radio.GainControl
import dk.intraon.mink.radio.Occupancy
import dk.intraon.mink.radio.RtlTcpClient
import dk.intraon.mink.radio.SourceTracker
import dk.intraon.mink.trip.IqWav
import dk.intraon.mink.trip.JsonlLog
import dk.intraon.mink.ui.MainActivity
import dk.intraon.mink.waterfall.FigureExport
import dk.intraon.mink.waterfall.Scope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import org.json.JSONObject

/**
 * Måletjenesten: læser IQ fra driveren, finder bursts, logger dem med GPS-position og sender dem videre.
 * Kører som foreground service, så målingen fortsætter med skærmen slukket.
 */
class MeasureService : Service() {

    companion object {
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        const val ACTION_MARK = "mark"
        const val ACTION_GAIN_UP = "gain+"
        const val ACTION_GAIN_DOWN = "gain-"
        const val ACTION_RECONNECT = "reconnect"
        const val ACTION_TARGET = "target"
        const val ACTION_GAIN_SET = "gain="
        const val ACTION_BEARING = "bearing"
        const val ACTION_PLACE = "place"
        const val EXTRA_PHOTO = "photo"
        private const val EXTRA_KIND = "kind"
        const val ACTION_PPM = "ppm="
        const val ACTION_BAND = "band="
        private const val EXTRA_LO = "lo"
        private const val EXTRA_HI = "hi"
        /** Så længe efter at tuneren er flyttet, regnes det, der høres, ikke for en måling: tuneren falder først til ro. */
        private const val SETTLE_MS = 300L
        const val EXTRA_PPM = "ppm"
        private const val EXTRA_HZ = "hz"
        private const val EXTRA_DB = "db"
        private const val EXTRA_PROTECT = "protect"
        private const val TAG = "MINK"
        private const val CHANNEL = "maaling"
        private const val NOTIFICATION_ID = 1
        const val TICK_MS = 10_000L
        /** Et GPS-fix ældre end dette bruges ikke. */
        private const val FIX_MAX_AGE_MS = 10_000L

        fun send(ctx: Context, action: String) {
            val i = Intent(ctx, MeasureService::class.java).setAction(action)
            if (action == ACTION_START) ctx.startForegroundService(i) else if (Hub.running) ctx.startService(i)
        }

        /** Indstillingerne er gemt, mens der måles: gain og beskyttelsen mod mætning gælder med det samme. */
        fun sendGain(ctx: Context, db: Double, protect: Boolean) {
            if (!Hub.running) return
            ctx.startService(Intent(ctx, MeasureService::class.java).setAction(ACTION_GAIN_SET).putExtra(EXTRA_DB, db).putExtra(EXTRA_PROTECT, protect))
        }

        /** Sæt et mærke dér, hvor telefonen er. [kind] er en af typerne i MarkKind. */
        fun sendMark(ctx: Context, kind: String) {
            if (!Hub.running) return
            ctx.startService(Intent(ctx, MeasureService::class.java).setAction(ACTION_MARK).putExtra(EXTRA_KIND, kind))
        }

        /** Gem en pejling. [photo] er navnet på det foto, kameraskærmen gemmer i galleriet sammen med den. */
        fun sendBearing(ctx: Context, photo: String?) {
            if (!Hub.running) return
            ctx.startService(Intent(ctx, MeasureService::class.java).setAction(ACTION_BEARING).putExtra(EXTRA_PHOTO, photo))
        }

        /** Kameraskærmen forlades: gem stedets stærkeste retning som pejling, hvis stedet er en værd. */
        fun sendPlaceDone(ctx: Context) {
            if (!Hub.running) return
            ctx.startService(Intent(ctx, MeasureService::class.java).setAction(ACTION_PLACE))
        }

        /** Ny PPM-korrektion, mens der måles: gælder med det samme. */
        fun sendPpm(ctx: Context, ppm: Int) {
            if (!Hub.running) return
            ctx.startService(Intent(ctx, MeasureService::class.java).setAction(ACTION_PPM).putExtra(EXTRA_PPM, ppm))
        }

        /** Vælg mål mens der måles: en frekvens i Hz, eller null for intet mål. */
        /** Flyt det bånd, der lyttes på, til [loHz]–[hiHz]. Turens eget bånd er hjemme; alt andet er et kig til siden. */
        fun sendBand(ctx: Context, loHz: Double, hiHz: Double) {
            if (!Hub.running) return
            ctx.startService(Intent(ctx, MeasureService::class.java).setAction(ACTION_BAND).putExtra(EXTRA_LO, loHz).putExtra(EXTRA_HI, hiHz))
        }

        fun sendTarget(ctx: Context, hz: Double?) {
            if (!Hub.running) return
            ctx.startService(Intent(ctx, MeasureService::class.java).setAction(ACTION_TARGET).putExtra(EXTRA_HZ, hz ?: Double.NaN))
        }
    }

    private lateinit var settings: Settings
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var wake: PowerManager.WakeLock? = null
    private var client: RtlTcpClient? = null
    @Volatile private var detector: BurstDetector? = null
    /** En ny signalbredde venter, til intet signal er i gang. */
    @Volatile private var pendingBwHz: Double? = null
    /** Et bånd, der skal lyttes på fra næste blok, og hvornår båndet sidst blev flyttet. */
    @Volatile private var pendingBand: Pair<Double, Double>? = null
    @Volatile private var bandChangedAt = 0L
    private var bwChangedAt = 0L
    /** Blokke modtaget i alt, og hvor mange der var gået, da den nuværende detektor begyndte at tælle fra nul. */
    private var blocksSeen = 0L
    private var frameBase = 0L
    /** Den seneste ændring af PPM-korrektionen: hvor meget alt flyttede sig, og hvornår. */
    private var ppmShiftHz = 0.0
    private var ppmShiftAt = 0L
    private var gain: GainControl? = null
    private var log: JsonlLog? = null
    private var uploader: Uploader? = null
    private var ring: IqRing? = null
    private var wav: IqWav? = null
    private var iqDecim = 64
    private var session = ""
    private var tuneHz = 0.0
    private var tracker: SourceTracker? = null
    /** Hvor meget af tiden din kanal er optaget af det, der høres. */
    private val occupancy = Occupancy()
    /** Den gain, dongle'n faktisk står på (et skift venter, til der er stille). */
    @Volatile private var appliedGain = 0.0
    /** Den PPM-korrektion, donglen står på. */
    private var appliedPpm = 0
    private var compass: Compass? = null
    @Volatile private var pendingGain: GainControl.Change? = null
    /** Hvornår det nuværende mål blev valgt – et tick, der kun delvist dækker målet, kan ikke kaldes stille. */
    @Volatile private var targetSince = 0L
    private var burstsInTick = 0
    private var targetInTick = 0
    private var strongestTarget: Double? = null
    private var strongestAny: Double? = null
    private var quietTicks = 0
    // blokkenes tid regnes ud fra antal samples, så TCP-buffering ikke flytter et bursts starttid
    private var anchorWall = 0L
    private var anchorBlocks = 0L

    private val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
    private val tickRun = Runnable { tick() }

    private val locListener = object : LocationListener {
        override fun onLocationChanged(l: Location) { Hub.location = l; Hub.changed() }
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                // Efter hver startForegroundService skal startForeground kaldes (også hvis målingen allerede kører)
                try { goForeground() } catch (e: RuntimeException) { Log.w(TAG, "startForeground fejlede", e) }
                if (!Hub.running) startMeasuring()
            }
            ACTION_STOP -> stopMeasuring()
            ACTION_MARK -> mark(MarkKind.of(intent.getStringExtra(EXTRA_KIND)))
            ACTION_BEARING -> bearing(intent.getStringExtra(EXTRA_PHOTO))
            ACTION_PLACE -> placeDone()
            ACTION_GAIN_UP -> manualGain(+1)
            ACTION_GAIN_DOWN -> manualGain(-1)
            ACTION_GAIN_SET -> chooseGain(intent.getDoubleExtra(EXTRA_DB, Double.NaN), intent.getBooleanExtra(EXTRA_PROTECT, true))
            ACTION_TARGET -> retarget(intent.getDoubleExtra(EXTRA_HZ, Double.NaN).takeUnless { it.isNaN() })
            ACTION_PPM -> changePpm(intent.getIntExtra(EXTRA_PPM, settings.ppm))
            ACTION_BAND -> {
                val lo = intent.getDoubleExtra(EXTRA_LO, Double.NaN); val hi = intent.getDoubleExtra(EXTRA_HI, Double.NaN)
                if (Hub.running && hi > lo) pendingBand = lo to hi
            }
            ACTION_RECONNECT -> if (Hub.running) connect()
            else -> if (!Hub.running) stopSelf()
        }
        syncCompass()
        // Dræbes processen, kan målingen ikke genoptages uden brugeren (driveren skal startes fra en skærm)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (Hub.running) stopMeasuring()
        super.onDestroy()
    }

    // ---------- start / stop ----------

    private fun startMeasuring() {
        settings = Settings(this)
        val cfg = try {
            settings.detectorConfig()
        } catch (e: IllegalArgumentException) {
            Hub.conn = Hub.Conn.FAILED
            Hub.statusText = e.message ?: "Ugyldige indstillinger"
            Hub.changed()
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
            return
        }
        session = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        Hub.reset()
        Hub.running = true
        Hub.conn = Hub.Conn.CONNECTING
        Hub.statusText = "Forbinder til driveren"
        tuneHz = cfg.tuneHz
        burstsInTick = 0; targetInTick = 0; strongestTarget = null; strongestAny = null; quietTicks = 0; anchorWall = 0
        blocksSeen = 0; frameBase = 0; ppmShiftHz = 0.0; ppmShiftAt = 0
        pendingGain = null; pendingBand = null; bandChangedAt = 0
        occupancy.start(System.currentTimeMillis())
        Hub.bandLoHz = cfg.bandLoHz; Hub.bandHiHz = cfg.bandHiHz; Hub.homeLoHz = cfg.bandLoHz; Hub.homeHiHz = cfg.bandHiHz

        wake = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "mink:maaling").apply { acquire(12 * 3600_000L) }
        thread = HandlerThread("mink-tick").apply { start() }
        handler = Handler(thread!!.looper)

        JsonlLog.hideFromMedia(this, settings)
        log = JsonlLog(this, "${JsonlLog.PREFIX}$session.jsonl.txt")
        Hub.logName = log?.name
        Hub.logOk = log?.ok == true
        iqDecim = IqRing.decimFor(cfg.signalBwHz, cfg.sampleRate)
        if (settings.saveIq) {
            // ringen skal rumme det længste stykke, detektoren melder (chunkMs), plus lidt før og efter
            ring = IqRing(2 * cfg.n, (cfg.chunkMs / (1000.0 * cfg.n / cfg.sampleRate)).toInt() + 100)
            wav = IqWav(this, "${JsonlLog.PREFIX}$session${JsonlLog.IQ_SUFFIX}", cfg.sampleRate / iqDecim)
        } else {
            ring = null; wav = null
        }
        uploader = Uploader(settings.webhook, session).apply { start() }
        Hub.uploadOn = uploader?.enabled == true

        gain = GainControl(RtlTcpClient.GAINS, settings.startGain).apply { auto = settings.autoGain }
        Hub.gainDb = gain!!.db
        Hub.gainChosenDb = gain!!.chosenDb
        Hub.signalBwHz = cfg.signalBwHz
        appliedGain = gain!!.db
        appliedPpm = settings.ppm
        tracker = SourceTracker(maxOf(3_000.0, 0.8 * cfg.signalBwHz), settings.targetMhz?.times(1e6))
        Hub.targetHz = tracker!!.targetHz
        targetSince = System.currentTimeMillis()
        Hub.autoGain = settings.autoGain
        detector = listenOn(cfg)

        record(
            JSONObject().put("type", "meta").put("session", session).put("version", BuildConfig.VERSION_NAME)
                .put("device", Build.MODEL).put("ppm", settings.ppm).put("tune", cfg.tuneHz.toLong())
                .put("band_lo", cfg.bandLoHz.toLong()).put("band_hi", cfg.bandHiHz.toLong())
                .put("signal_bw", cfg.signalBwHz.toLong()).put("chan", (settings.channelMhz * 1e6).toLong()).put("chan_bw", (settings.channelKhz * 1e3).toLong()).put("target", settings.targetMhz?.let { (it * 1e6).toLong() } ?: JSONObject.NULL)
                .put("search_lo", detector!!.centerLoHz.toLong()).put("search_hi", detector!!.centerHiHz.toLong())
                .put("on_snr", cfg.onSnrDb).put("min_burst_ms", cfg.minBurstMs)
                .put("rate", cfg.sampleRate).put("nfft", cfg.n).put("tick_ms", TICK_MS)
                .put("iq_file", wav?.takeIf { it.ok }?.name ?: JSONObject.NULL).put("iq_rate", cfg.sampleRate / iqDecim)
        )
        connect()

        try {
            (getSystemService(LOCATION_SERVICE) as LocationManager)
                .requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, locListener, thread!!.looper)
        } catch (e: SecurityException) {
            Log.w(TAG, "ingen adgang til position", e)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "ingen GPS", e)
        }
        handler?.postDelayed(tickRun, TICK_MS)
        Hub.changed()
    }

    /** En detektor for [cfg], med vandfaldet stillet på det samme bånd. */
    private fun listenOn(cfg: BurstDetector.Config) = BurstDetector(cfg).apply {
        onBurst = { this@MeasureService.onBurst(it) }
        // vandfaldet viser det lyttede bånd med luft i hver side – det samme stykke, detektoren søger i
        val binHz = cfg.sampleRate.toDouble() / cfg.n
        val lo = maxOf(1, bandBinLo - (BurstDetector.EDGE_AIR_HZ / binHz).toInt())
        val hi = minOf(cfg.n - 2, bandBinHi + (BurstDetector.EDGE_AIR_HZ / binHz).toInt())
        Scope.configure(lo, hi - lo + 1, freqOfBin(lo - 0.5), binHz, cfg.n)
        spectrumSink = { re, im, floor -> Scope.feed(re, im, floor) }
    }

    /**
     * Flytter det bånd, der lyttes på, midt i en tur: brugeren har trukket vandfaldet til siden for at kigge på
     * nabofrekvenserne, eller er vendt hjem til turens eget bånd. Tuneren flyttes, og detektoren og vandfaldet bygges
     * om til det nye bånd. Sendere, der høres dér, gemmes som alle andre. Ligger målet uden for båndet, kan det ikke
     * høres, og [tick] skriver så ikke stille punkter.
     */
    private fun applyBand(loHz: Double, hiHz: Double) {
        val old = detector ?: return
        if (loHz == old.cfg.bandLoHz && hiHz == old.cfg.bandHiHz) return
        val cfg = old.cfg.copy(tuneHz = BurstDetector.tuneFor(loHz, hiHz, old.cfg.sampleRate), bandLoHz = loHz, bandHiHz = hiHz)
        val det = listenOn(cfg)
        detector = det
        // den nye detektor tæller blokke fra nul; bølgeform-ringen tæller videre, så udsnittene skal forskydes
        frameBase = blocksSeen
        tuneHz = cfg.tuneHz
        client?.changeFreq(cfg.tuneHz.toLong())
        bandChangedAt = System.currentTimeMillis()
        // i det nye bånd høres andre sendere: optagetheden begynder forfra
        occupancy.start(bandChangedAt)
        Hub.channelBusy = null; Hub.channelSenders = 0
        targetInTick = 0; strongestTarget = null
        Hub.bandLoHz = loHz; Hub.bandHiHz = hiHz
        // hjemme er alt som før; kun under et kig kan målet ligge uden for det, detektoren søger i
        Hub.targetHeard = !Hub.away || BandShift.hears(det.centerLoHz, det.centerHiHz, tracker?.targetHz)
        Hub.ppmSeq = 0
        record(
            JSONObject().put("type", "band").put("t", iso(bandChangedAt)).put("lo", loHz.toLong()).put("hi", hiHz.toLong())
                .put("tune", cfg.tuneHz.toLong()).put("search_lo", det.centerLoHz.toLong()).put("search_hi", det.centerHiHz.toLong())
        )
        Hub.changed()
    }

    private fun connect() {
        val cfg = detector?.cfg ?: return
        client?.stop()
        Hub.ppmSeq = Scope.seq   // en ny forbindelse kan have fået en ny korrektion
        anchorWall = 0
        client = RtlTcpClient("127.0.0.1", 1234, 2 * cfg.n, ::onBlock, ::onState).also {
            it.changeGain(gain?.db ?: settings.startGain)
            it.start(cfg.tuneHz.toLong(), cfg.sampleRate, settings.ppm, gain?.db ?: settings.startGain)
        }
    }

    private fun stopMeasuring() {
        if (!Hub.running) { stopSelf(); return }
        placeDone()
        Hub.running = false
        client?.stop(); client = null
        compass?.stop()
        try { (getSystemService(LOCATION_SERVICE) as LocationManager).removeUpdates(locListener) } catch (_: Exception) {}
        handler?.removeCallbacksAndMessages(null)
        thread?.quitSafely(); thread = null; handler = null
        log?.close()
        wav?.close(); wav = null; ring = null
        uploader?.stop()
        try { wake?.let { if (it.isHeld) it.release() } } catch (_: Exception) {}
        wake = null
        Hub.conn = Hub.Conn.STOPPED
        Hub.statusText = "Stoppet"
        Hub.changed()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ---------- måling ----------

    private fun onState(state: RtlTcpClient.State, text: String) {
        if (!Hub.running) return
        Hub.conn = when (state) {
            RtlTcpClient.State.CONNECTING -> Hub.Conn.CONNECTING
            RtlTcpClient.State.RECEIVING -> Hub.Conn.RECEIVING
            RtlTcpClient.State.WAITING -> Hub.Conn.WAITING
            RtlTcpClient.State.FAILED -> Hub.Conn.FAILED
        }
        Hub.statusText = text
        client?.tuner?.let { if (state == RtlTcpClient.State.RECEIVING) Hub.tuner = it }
        Hub.changed()
    }

    private fun onBlock(buf: ByteArray, wall: Long) {
        val det = detector ?: return
        var t = anchorWall + (anchorBlocks * det.frameMs).toLong()
        if (anchorWall == 0L || abs(wall - t) > 400) {
            anchorWall = wall; anchorBlocks = 0; t = wall
        }
        anchorBlocks++
        blocksSeen++
        ring?.push(buf)
        det.processBlock(buf, 0, t)
        // et gain-skift venter, til intet signal er i gang – ellers deler skiftet målingen i to
        val pg = pendingGain
        if (pg != null && !det.busy) { pendingGain = null; applyGain(pg) }
        val pb = pendingBwHz
        if (pb != null && !det.busy) { pendingBwHz = null; applySignalBw(pb) }
        // et kig til siden venter ikke på, at et signal bliver færdigt: brugeren har bedt om at se et andet sted nu
        pendingBand?.let { pendingBand = null; applyBand(it.first, it.second) }
    }

    /**
     * Signalbredden følger målet: er målet målt bredere eller smallere end det, detektoren står på, bygges detektoren
     * om til målets bredde. Båndet og vandfaldet er de samme, og bølgeformen gemmes med den rate, turen startede med.
     * Bredden gælder kun denne tur: næste tur starter på indstillingen igen, så et bredt mål én gang ikke gør appen
     * døv for smalle signaler bagefter.
     */
    private fun applySignalBw(bwHz: Double) {
        val old = detector ?: return
        val cfg = old.cfg.copy(signalBwHz = bwHz)
        detector = BurstDetector(cfg).apply { onBurst = old.onBurst; spectrumSink = old.spectrumSink }
        // den nye detektor tæller blokke fra nul; bølgeform-ringen tæller videre, så udsnittene skal forskydes
        frameBase = blocksSeen
        Hub.signalBwHz = bwHz
        bwChangedAt = System.currentTimeMillis()
        record(JSONObject().put("type", "signal_bw").put("t", iso(bwChangedAt)).put("from", old.cfg.signalBwHz.toLong()).put("to", bwHz.toLong()))
        Hub.changed()
    }

    private fun onBurst(b: BurstDetector.Burst) {
        val g = gain ?: return
        if (b.tStartMs - bandChangedAt < SETTLE_MS) return
        val tr = tracker ?: return
        val gainDb = appliedGain
        val level = b.peakDbfs - gainDb
        // et udbrud, der var i gang, da korrektionen blev ændret, er målt på den gamle plads: flyt det med de andre,
        // så det ikke bliver til en ny sender
        var freq = b.freqHz
        if (ppmShiftHz != 0.0 && b.tStartMs - ppmShiftAt < 600 && !tr.near(freq) && tr.near(freq + ppmShiftHz)) freq += ppmShiftHz
        val (src, isTarget) = tr.add(b.tStartMs, freq, b.peakSnr, level, b.bwHz)
        occupancy.add(b.tStartMs, b.durMs, freq, src)
        if (isTarget && pendingBwHz == null && b.tStartMs - bwChangedAt > 30_000) {
            val t = tr.snapshot().firstOrNull { it.target }
            val bw = t?.bwHz
            if (t != null && bw != null && t.bwN >= 5) pendingBwHz = BurstDetector.widthFor(bw, detector?.cfg?.signalBwHz ?: 0.0)
        }
        val loc = freshLocation()
        val o = JSONObject().put("type", "burst").put("t", iso(b.tStartMs))
        putLocation(o, loc, true)
        o.put("f", String.format(Locale.US, "%.4f", freq / 1e6)).put("src", src)
            .put("snr", r1(b.peakSnr)).put("msnr", r1(b.meanSnr)).put("dbfs", r1(b.peakDbfs)).put("lvl", r1(level))
            .put("gain", gainDb).put("floor", r1(b.floorDb)).put("dur", b.durMs).put("clip", r1(b.clipPct))
        b.bwHz?.let { o.put("bw", it.roundToInt()) }
        // med retningsantenne: hvilken vej telefonen (og dermed antennen) pegede, og ind i rosen for stedet
        var left: Rose.Stop? = null
        val held = Hub.heldFrameAt
        if (Hub.directional) Hub.freshHeading()?.let { hdg ->
            o.put("hdg", r1(hdg)).put("tilt", r1(Hub.tiltDeg))
            // et kompas, der ikke er til at stole på, mærkes i loggen og får ikke lov at farve rosen
            if (Hub.compassPoor) o.put("hdg_poor", true)
            else if (isTarget) left = Hub.rose.add(hdg, level, loc?.latitude, loc?.longitude, t = b.tStartMs)
        }
        if (isTarget) o.put("tgt", true)
        if (b.ongoing || b.part > 0) o.put("cont", true)
        saveClip(b, o)
        record(o)
        // målingen blev taget et nyt sted: det sted, brugeren forlod, bliver til en pejling
        left?.let { autoBearing(it, held) }
        burstsInTick++
        if (b.peakDbfs > (strongestAny ?: -999.0)) strongestAny = b.peakDbfs
        Hub.sources = tr.snapshot()
        Hub.targetHz = tr.targetHz
        if (isTarget) {
            // kun målet styrer tal, pil, bip, graf og kort
            detector?.let { d ->
                val half = d.cfg.signalBwHz / 2
                Hub.targetAtEdge = b.freqHz > d.centerHiHz - half || b.freqHz < d.centerLoHz + half
            }
            targetInTick++
            quietTicks = 0
            if (b.peakDbfs > (strongestTarget ?: -999.0)) strongestTarget = b.peakDbfs
            Hub.addBurst(Hub.Point(b.tStartMs, loc?.latitude, loc?.longitude, level, b.peakSnr, b.freqHz, b.durMs, gainDb))
            if (settings.beep) Beeper.beep(level, settings.beepVolume / 100.0)
            notifyStatus()
        } else {
            Hub.changed()
        }
        if (!b.ongoing || b.clipPct > 0.5) g.afterBurst(b.peakDbfs, b.clipPct)?.let { requestGain(it) }
    }

    /**
     * Gemmer bølgeformen: et smalt IQ-udsnit omkring burstets frekvens, fra 20 ms før til 10 ms efter (højst 1 s).
     * Kun første stykke af en lang udsendelse gemmes.
     */
    private fun saveClip(b: BurstDetector.Burst, o: JSONObject) {
        val r = ring ?: return
        val w = wav ?: return
        val det = detector ?: return
        if (b.part > 0) return
        val start = b.startFrame + frameBase
        val first = start - 10
        val last = minOf(b.lastFrame + frameBase + 5, first + (1000 / det.frameMs).toLong())
        val clip = r.clip(first, last, b.freqHz - tuneHz, det.cfg.sampleRate, iqDecim)
        val at = w.append(clip)
        if (at < 0) return
        o.put("iq", at).put("iqn", clip.size / 2).put("iqfc", b.freqHz.toLong())
            .put("iqpre", ((start - maxOf(first, 1)) * det.frameMs).roundToInt())
    }

    private fun tick() {
        if (!Hub.running) return
        try {
            val st = detector?.takeStats() ?: return
            val loc = freshLocation()
            Hub.floorDb = st.floorDb
            Hub.clipPct = st.clipPct
            // uden et valgt mål giver "stille" ingen mening: der er intet, der kan være stille
            // og heller ikke, når båndet er flyttet væk fra målet, eller først kom tilbage midt i perioden
            val now = System.currentTimeMillis()
            val hasTarget = tracker?.targetHz != null && now - targetSince >= TICK_MS - 500 && Hub.targetHeard && now - bandChangedAt >= TICK_MS - 500
            val o = JSONObject().put("type", "tick").put("t", iso(System.currentTimeMillis()))
            putLocation(o, loc, false)
            o.put("gain", gain?.db ?: 0.0).put("floor", st.floorDb?.let { r1(it) } ?: JSONObject.NULL)
                .put("bursts", burstsInTick).put("tb", if (hasTarget) targetInTick else JSONObject.NULL).put("wide", st.wideFrames)
                .put("maxsnr", st.maxSnr?.let { r1(it) } ?: JSONObject.NULL)
                .put("clip", r1(st.clipPct)).put("frames", st.frames)
            if (!Hub.targetHeard) o.put("away", true)
            // din kanals optagethed: kun når hele kanalen ligger i det bånd, der lyttes på
            val chLo = Hub.channelHz - Hub.channelBwHz / 2; val chHi = Hub.channelHz + Hub.channelBwHz / 2
            val share = if (Hub.channelHz > 0 && Hub.channelBwHz > 0 && chLo >= Hub.bandLoHz && chHi <= Hub.bandHiHz) occupancy.share(chLo, chHi, now) else null
            Hub.channelBusy = share?.fraction; Hub.channelSenders = share?.senders ?: 0
            if (share != null) o.put("busy", r1(share.fraction * 100))
            record(o)
            if (targetInTick == 0) {
                quietTicks++
                if (st.frames > 0 && hasTarget) {
                    Hub.silent.add(
                        Hub.Point(System.currentTimeMillis(), loc?.latitude, loc?.longitude, null, st.maxSnr, null, 0, gain?.db ?: 0.0)
                    )
                }
            }
            val strongest = strongestTarget ?: strongestAny
            burstsInTick = 0; targetInTick = 0; strongestTarget = null; strongestAny = null
            if (st.frames > 0) gain?.tick(strongest, quietTicks, st.clipPct)?.let { requestGain(it) }
            Hub.logOk = log?.ok == true
            Hub.changed()
        } catch (e: Throwable) {
            Log.w(TAG, "tick fejlede", e)
        } finally {
            handler?.postDelayed(tickRun, TICK_MS)
        }
    }

    /** Auto-gain: skift nu, hvis der er stille, ellers så snart det igangværende signal er slut. */
    private fun requestGain(c: GainControl.Change) {
        if (detector?.busy == true) pendingGain = GainControl.Change(pendingGain?.from ?: c.from, c.to, c.why) else applyGain(c)
    }

    private fun manualGain(step: Int) {
        val g = gain ?: return
        val c = g.manual(step)
        // valget huskes til næste tur; beskyttelsen mod mætning bliver ved med at virke
        settings.startGain = g.chosenDb
        Hub.gainChosenDb = g.chosenDb
        pendingGain = null
        if (c != null) applyGain(c) else Hub.changed()
    }

    /** Ny PPM-korrektion, mens der måles. Det, der allerede står i vandfaldet, ligger stadig på den gamle plads. */
    private fun changePpm(ppm: Int) {
        client?.changePpm(ppm)
        Hub.ppmSeq = Scope.seq
        // positiv PPM flytter alt opad: målet og de andre sendere følger med til deres nye plads
        val shift = (ppm - appliedPpm) * tuneHz / 1e6
        appliedPpm = ppm
        ppmShiftHz = shift; ppmShiftAt = System.currentTimeMillis()
        tracker?.let { tr ->
            tr.shift(shift)
            Hub.targetHz = tr.targetHz
            Hub.sources = tr.snapshot()
            tr.targetHz?.let { settings.targetMhz = Math.round(it / 100.0) / 10_000.0 }
        }
        record(JSONObject().put("type", "ppm").put("t", iso(System.currentTimeMillis())).put("to", ppm))
        Hub.changed()
    }

    /** Gain og beskyttelse valgt i indstillingerne, mens der måles. */
    private fun chooseGain(db: Double, protect: Boolean) {
        val g = gain ?: return
        g.auto = protect
        Hub.autoGain = protect
        val c = if (db.isNaN()) null else g.choose(db)
        Hub.gainChosenDb = g.chosenDb
        pendingGain = null
        if (c != null) applyGain(c) else Hub.changed()
    }

    private fun retarget(hz: Double?) {
        val tr = tracker ?: return
        tr.retarget(hz)
        targetSince = System.currentTimeMillis()
        targetInTick = 0; strongestTarget = null
        Hub.targetHz = tr.targetHz
        Hub.targetAtEdge = false
        // et mål valgt under et kig til siden gør det sted til turens bånd: dér skal der lyttes resten af turen
        val det = detector
        val inReach = det == null || BandShift.hears(det.centerLoHz, det.centerHiHz, tr.targetHz)
        if (Hub.away && tr.targetHz != null && inReach) { Hub.homeLoHz = Hub.bandLoHz; Hub.homeHiHz = Hub.bandHiHz }
        Hub.targetHeard = !Hub.away || inReach
        Hub.sources = tr.snapshot()
        val picked = tr.snapshot().firstOrNull { it.target }
        record(
            JSONObject().put("type", "target").put("t", iso(System.currentTimeMillis()))
                .put("f", (picked?.freqHz ?: hz)?.let { String.format(Locale.US, "%.4f", it / 1e6) } ?: JSONObject.NULL)
                .put("src", picked?.id ?: JSONObject.NULL)
        )
        Hub.clearTrack()
    }

    private fun applyGain(c: GainControl.Change) {
        client?.changeGain(c.to)
        appliedGain = c.to
        Hub.gainDb = c.to
        Hub.gainSteps.add(Hub.GainStep(System.currentTimeMillis(), c.to))
        record(JSONObject().put("type", "gain").put("t", iso(System.currentTimeMillis())).put("from", c.from).put("to", c.to).put("why", c.why))
        Hub.changed()
    }

    /** Kompasset kører, mens der måles med retningsantenne – så hver måling kan få sin retning med. */
    private fun syncCompass() {
        if (!::settings.isInitialized) return
        val want = Hub.running && settings.directional
        Hub.directional = settings.directional
        if (want) { if (compass == null) compass = Compass(this); compass?.start() } else compass?.stop()
    }

    /** Gemmer en pejling: stedet, retningen telefonen peger i, og målets niveau lige nu. */
    private fun bearing(photo: String?) {
        if (!Hub.running) return
        val now = System.currentTimeMillis()
        val deg = Hub.freshHeading(now) ?: return
        val loc = freshLocation()
        val last = Hub.lastBurst?.takeIf { now - it.t < 6_000 }
        val n = Hub.bearings.size + 1
        Hub.bearings.add(Hub.BearingLine(now, loc?.latitude, loc?.longitude, deg, last?.level, n))
        val o = JSONObject().put("type", "bearing").put("t", iso(now))
        putLocation(o, loc, false)
        o.put("hdg", r1(deg)).put("tilt", r1(Hub.tiltDeg)).put("lvl", last?.level?.let { r1(it) } ?: JSONObject.NULL)
            .put("snr", last?.snr?.let { r1(it) } ?: JSONObject.NULL).put("n", n)
            .put("cmp", if (Hub.compassPoor) "poor" else "ok").put("ut", Hub.magUt?.let { r1(it) } ?: JSONObject.NULL)
            // fladt = retningen er telefonens øverste kant; ellers er det dér, kameraet ser hen
            .put("flat", Hub.flat).put("photo", photo?.let { "${FigureExport.DIR}/$it" } ?: JSONObject.NULL)
        Hub.rose.best()?.let { o.put("best", r1(it)) }
        record(o)
        Hub.changed()
    }

    /** Stedet er færdigt (kameraskærmen forlades, eller målingen stoppes): gem dets pejling, hvis det er en værd. */
    private fun placeDone() {
        if (!Hub.running) return
        val held = Hub.heldFrameAt
        Hub.rose.finish()?.let { autoBearing(it, held) }
    }

    /**
     * Gemmer et steds stærkeste retning som pejling, uden at brugeren har trykket: stedet og retningen kommer fra
     * rosen, niveauet er stedets stærkeste måling. [held] er tidspunktet for den måling, kameraskærmen holdt et
     * billede af, da stedet blev afleveret; er det stedets stærkeste, får pejlingen et foto, som skærmen gemmer.
     */
    private fun autoBearing(stop: Rose.Stop, held: Long) {
        val now = System.currentTimeMillis()
        val n = Hub.bearings.size + 1
        val photo = if (held != 0L && held == stop.at) "MINK_pejling_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(stop.at)) + ".jpg" else null
        Hub.bearings.add(Hub.BearingLine(now, stop.lat, stop.lon, stop.best, stop.level, n))
        val o = JSONObject().put("type", "bearing").put("t", iso(now))
            .put("lat", (stop.lat * 1e6).roundToInt() / 1e6).put("lon", (stop.lon * 1e6).roundToInt() / 1e6)
            .put("hdg", r1(stop.best)).put("lvl", r1(stop.level)).put("best", r1(stop.best)).put("n", n).put("auto", true)
            .put("photo", photo?.let { "${FigureExport.DIR}/$it" } ?: JSONObject.NULL)
        record(o)
        Hub.autoBearing = Hub.AutoBearing(stop, n, photo)
        Hub.changed()
    }

    private fun mark(kind: String) {
        if (!Hub.running) return
        val loc = freshLocation()
        val n = Hub.marks.size + 1
        Hub.marks.add(Hub.Mark(System.currentTimeMillis(), loc?.latitude, loc?.longitude, n, kind))
        val o = JSONObject().put("type", "mark").put("t", iso(System.currentTimeMillis()))
        putLocation(o, loc, false)
        record(o.put("n", n).put("kind", kind))
        Hub.changed()
    }

    private fun record(o: JSONObject) {
        log?.write(o)
        uploader?.add(o)
    }

    // ---------- hjælpere ----------

    private fun iso(ms: Long): String = synchronized(isoFmt) { isoFmt.format(Date(ms)) }
    private fun r1(x: Double): Double = if (x.isNaN() || x.isInfinite()) 0.0 else (x * 10).roundToInt() / 10.0

    private fun freshLocation(): Location? {
        val l = Hub.location ?: return null
        val ageMs = (SystemClock.elapsedRealtimeNanos() - l.elapsedRealtimeNanos) / 1_000_000
        return if (ageMs <= FIX_MAX_AGE_MS) l else null
    }

    private fun putLocation(o: JSONObject, l: Location?, bearing: Boolean) {
        if (l == null) {
            o.put("lat", JSONObject.NULL).put("lon", JSONObject.NULL).put("acc", JSONObject.NULL).put("spd", JSONObject.NULL)
            if (bearing) o.put("brg", JSONObject.NULL)
            return
        }
        o.put("lat", (l.latitude * 1e6).roundToInt() / 1e6).put("lon", (l.longitude * 1e6).roundToInt() / 1e6)
            .put("acc", l.accuracy.roundToInt()).put("spd", r1(l.speed * 3.6))
        if (bearing) o.put("brg", if (l.hasBearing()) l.bearing.roundToInt() else JSONObject.NULL)
    }

    // ---------- notifikation ----------

    private fun pending(action: String, code: Int): PendingIntent = PendingIntent.getService(
        this, code, Intent(this, MeasureService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Måling", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
        }
        val last = Hub.lastBurst
        val snr = last?.snr
        val text = if (last == null || snr == null) Hub.statusText else {
            val d = Hub.trendDelta
            val arrow = if (d == null) "" else if (d >= 2) " ▲" else if (d <= -2) " ▼" else " ▬"
            String.format(Locale.US, "%+d dB%s · %.4f MHz", snr.roundToInt(), arrow, (last.freqHz ?: 0.0) / 1e6)
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val noIcon: Icon? = null
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_mink)
            .setContentTitle("MINK måler")
            .setContentText(text)
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(noIcon, "Mærke", pending(ACTION_MARK, 1)).build())
            .addAction(Notification.Action.Builder(noIcon, "Stop", pending(ACTION_STOP, 2)).build())
            .build()
    }

    private fun goForeground() {
        startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
    }

    private fun notifyStatus() {
        try {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
        } catch (_: RuntimeException) {}
    }
}
