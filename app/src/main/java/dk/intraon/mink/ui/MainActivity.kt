package dk.intraon.mink.ui

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings as AndroidSettings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import dk.intraon.mink.map.MapExport
import dk.intraon.mink.core.Hub
import dk.intraon.mink.core.MarkKind
import dk.intraon.mink.core.Settings
import dk.intraon.mink.direction.CameraPane
import dk.intraon.mink.map.MapData
import dk.intraon.mink.map.PointsOverlay
import dk.intraon.mink.measure.MeasureService
import dk.intraon.mink.radio.Calibrate
import dk.intraon.mink.trip.JsonlLog
import dk.intraon.mink.trip.TripFiles
import dk.intraon.mink.trip.TripShare
import dk.intraon.mink.update.Updater
import dk.intraon.mink.waterfall.FigureExport
import dk.intraon.mink.waterfall.Scope
import dk.intraon.mink.waterfall.WaterfallView
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt
import java.text.SimpleDateFormat
import java.util.Date
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView

/** Skærmen "Kør": stort tal, trendpil, graf, kort og knapper. Selve målingen ligger i MeasureService. */
class MainActivity : Activity() {

    companion object {
        const val REQ_PERMS = 1
        const val REQ_CAMERA = 3
        const val REQ_DRIVER = 2
        private const val DRIVER_PACKAGE = "marto.rtl_tcp_andro"
    }

    lateinit var settings: Settings
        private set
    val updater by lazy { Updater(this) }
    private lateinit var root: FrameLayout
    private lateinit var figureBtn: Button
    /** Hvor mange Hz trafikken på kanalen ligger ved siden af (null = ukendt eller i orden), og hvornår det sidst blev set efter. */
    private var calOffsetHz: Double? = null
    private var calCheckedAt = 0L
    /** Den forskydning, der sidst blev rettet for, og hvornår – til at opdage, at driveren ikke tog rettelsen. */
    private var calFixedHz: Double? = null
    private var calFixedAt = 0L
    private lateinit var statusTv: TextView
    private lateinit var bigTv: TextView
    private lateinit var subTv: TextView
    private lateinit var arrowTv: TextView
    private lateinit var deltaTv: TextView
    private lateinit var trendTv: TextView
    private lateinit var detailTv: TextView
    private lateinit var sourcesTv: TextView
    private lateinit var warnTv: TextView
    private lateinit var legendTv: TextView
    private lateinit var graph: LevelGraphView
    private lateinit var map: MapView
    private lateinit var startBtn: Button
    private lateinit var followBtn: Button
    private lateinit var waterfall: WaterfallView
    private lateinit var mapBoxView: View
    private lateinit var tabWf: Button
    private lateinit var tabMap: Button
    private lateinit var tabCam: Button
    /** Det store tal, detaljerne og listen over sendere – skjules på kamerafanen, hvor kameraet skal have pladsen. */
    private lateinit var topBox: LinearLayout
    private lateinit var cameraPane: CameraPane
    /** Den fane, der vises: 0 = vandfald, 1 = kort, 2 = kamera. */
    private var tab = 0
    private lateinit var heatBtn: Button
    private lateinit var pointsLayer: PointsOverlay
    private var settingsView: View? = null
    private lateinit var tripTv: TextView
    private var follow = true
    /** Ture, der kan bladres i på kortet (nyeste først), og hvilken der vises: -1 = kun den aktuelle tur. */
    private var trips: List<JsonlLog.Entry> = emptyList()
    private var tripIndex = -1
    private var tripsShown = 0
    private var driverOk = true
    private var dongle: Boolean? = null
    private var hwChecked = 0L
    private var lastShown: Hub.Point? = null
    private val ui = Handler(Looper.getMainLooper())
    private val listener: () -> Unit = { render() }
    private val ticker = object : Runnable {
        override fun run() { render(); ui.postDelayed(this, 500) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = Settings(this)
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = File(filesDir, "osmdroid").apply { mkdirs() }
            osmdroidTileCache = File(cacheDir, "osmdroid-tiles").apply { mkdirs() }
        }
        @Suppress("DEPRECATION")
        run { window.statusBarColor = Ui.BG; window.navigationBarColor = Ui.BG }
        root = FrameLayout(this).apply { setBackgroundColor(Ui.BG); fitsSystemWindows = true }
        root.addView(buildMain(), FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        setContentView(root)
        if (!Hub.running) Hub.targetHz = settings.targetMhz?.times(1e6)
        applyChannel()
        applyDirectional()
        Hub.listen(listener)
        applyKeepScreenOn()
        if (!settings.guideSeen) { settings.guideSeen = true; showGuide() } else updater.autoCheck()
        updater.onInstallStatus(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        updater.onInstallStatus(intent)
    }

    /** Kort vejledning: vises første gang og bag ?-knappen. */
    private fun showGuide() = Guide.show(this)

    override fun onResume() {
        super.onResume()
        map.onResume()
        ui.post(ticker)
        if (tab == 2) cameraPane.open()
    }

    override fun onPause() {
        cameraPane.close()
        ui.removeCallbacks(ticker)
        map.onPause()
        (map.mapCenter as? GeoPoint)?.let { settings.lastLat = it.latitude; settings.lastLon = it.longitude }
        super.onPause()
    }

    override fun onDestroy() {
        Hub.unlisten(listener)
        map.onDetach()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (settingsView != null) closeSettings() else @Suppress("DEPRECATION") super.onBackPressed()
    }

    /** Brugerens egen kanal til vandfaldet og til mærket "i din kanal". */
    fun applyChannel() {
        Hub.channelHz = settings.channelMhz * 1e6
        Hub.channelBwHz = settings.channelKhz * 1e3
    }

    fun applyKeepScreenOn() {
        if (settings.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    // ---------- opbygning ----------

    private fun tv(sp: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        textSize = sp; setTextColor(color); if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    /** Et tegn med en lille forklaring under, til knapperne nederst. */
    private fun captioned(sign: String, caption: String): CharSequence = SpannableStringBuilder("$sign\n$caption").apply {
        setSpan(android.text.style.RelativeSizeSpan(0.55f), sign.length + 1, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        setSpan(ForegroundColorSpan(Ui.DIM), sign.length + 1, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    fun button(label: CharSequence, color: Int, onClick: (View) -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 18f; setTextColor(Ui.TEXT)
        minWidth = 0; minimumWidth = 0; setPadding(0, 0, 0, 0)
        background = GradientDrawable().apply { setColor(color); cornerRadius = dp(10).toFloat() }
        setOnClickListener { onClick(it) }
    }

    private fun buildMain(): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val pad = dp(12)

        statusTv = tv(12f, Ui.DIM).apply { setPadding(pad, dp(8), pad, dp(4)); setLineSpacing(dp(2).toFloat(), 1f) }
        col.addView(statusTv, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        // stort tal + trend
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(pad, 0, pad, 0) }
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        bigTv = tv(88f, Ui.TEXT, true).apply { text = "—"; gravity = Gravity.CENTER; includeFontPadding = false }
        subTv = tv(14f, Ui.DIM).apply { text = "dB over støjen"; gravity = Gravity.CENTER }
        left.addView(bigTv, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        left.addView(subTv, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        arrowTv = tv(56f, Ui.DIM, true).apply { text = "▬"; gravity = Gravity.CENTER; includeFontPadding = false }
        deltaTv = tv(22f, Ui.DIM, true).apply { gravity = Gravity.CENTER }
        trendTv = tv(14f, Ui.DIM).apply { gravity = Gravity.CENTER }
        right.addView(arrowTv); right.addView(deltaTv); right.addView(trendTv)
        row.addView(left, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.5f))
        row.addView(right, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        topBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        topBox.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        detailTv = tv(14f, Ui.DIM).apply { gravity = Gravity.CENTER; setPadding(pad, dp(4), pad, dp(4)) }
        topBox.addView(detailTv, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        // hvilke sendere der høres, og hvilken der er målet – tryk for at vælge en anden
        sourcesTv = tv(13f, Ui.ACCENT).apply {
            gravity = Gravity.CENTER; setPadding(pad, dp(6), pad, dp(10)); visibility = View.GONE
            setOnClickListener { targetDialog() }
        }
        topBox.addView(sourcesTv, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        col.addView(topBox, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        warnTv = tv(15f, 0xFF202124.toInt(), true).apply {
            setBackgroundColor(Ui.YELLOW); gravity = Gravity.CENTER; setPadding(pad, dp(8), pad, dp(8)); visibility = View.GONE
            setOnClickListener {
                if (!driverOk) openDriverStore()
                else if (Hub.running && Hub.conn != Hub.Conn.RECEIVING) launchDriver()
                else if (Hub.running && calOffsetHz != null) calibrate()
            }
        }
        col.addView(warnTv, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        graph = LevelGraphView(this)
        col.addView(graph, LinearLayout.LayoutParams(MATCH_PARENT, dp(110)))

        // kort
        val mapBox = FrameLayout(this)
        map = MapView(this).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(if (settings.hasLastPos) 14.0 else 6.0)
            controller.setCenter(GeoPoint(settings.lastLat, settings.lastLon))
            pointsLayer = PointsOverlay(resources.displayMetrics.density)
            overlays.add(pointsLayer)
            // OpenStreetMap kræver synlig kildeangivelse på kortet
            overlays.add(org.osmdroid.views.overlay.CopyrightOverlay(context).apply { setAlignRight(true); setTextSize(10) })
            setOnTouchListener { _, ev ->
                if (ev.action == MotionEvent.ACTION_MOVE && follow) { follow = false; updateFollow() }
                false
            }
        }
        mapBox.addView(map, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        followBtn = button("◉", Ui.BUTTON) { follow = true; updateFollow(); render() }
        mapBox.addView(followBtn, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP or Gravity.END).apply { setMargins(dp(8), dp(8), dp(8), dp(8)) })
        // skifter mellem punkter og varmekort
        heatBtn = button("▦", Ui.BUTTON) {
            pointsLayer.heat = !pointsLayer.heat
            heatBtn.setTextColor(if (pointsLayer.heat) Ui.ACCENT else Ui.TEXT)
            render()
        }
        mapBox.addView(heatBtn, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP or Gravity.END).apply { setMargins(dp(8), dp(64), dp(8), dp(8)) })
        // bladr i tidligere ture: ◀ ældre, ▶ nyere; tryk på navnet for at vælge flere ad gangen
        val tripBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        tripBar.addView(button("◀", Ui.BUTTON) { stepTrip(+1) }, LinearLayout.LayoutParams(dp(44), dp(44)))
        tripTv = tv(14f, Ui.TEXT, true).apply {
            text = "Aktuel tur"; gravity = Gravity.CENTER; setPadding(dp(10), 0, dp(10), 0)
            background = GradientDrawable().apply { setColor(0xE6202124.toInt()); cornerRadius = dp(10).toFloat() }
            setOnClickListener { sessionsDialog() }
        }
        tripBar.addView(tripTv, LinearLayout.LayoutParams(WRAP_CONTENT, dp(44)).apply { setMargins(dp(4), 0, dp(4), 0) })
        tripBar.addView(button("▶", Ui.BUTTON) { stepTrip(-1) }, LinearLayout.LayoutParams(dp(44), dp(44)))
        mapBox.addView(tripBar, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.START).apply { setMargins(dp(8), dp(8), dp(8), dp(8)) })
        legendTv = tv(12f, Ui.TEXT).apply { setBackgroundColor(0xCC0E0F12.toInt()); setPadding(dp(6), dp(3), dp(6), dp(3)) }
        mapBox.addView(legendTv, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.BOTTOM or Gravity.START).apply { setMargins(dp(6), dp(6), dp(6), dp(6)) })
        // faner: vandfald (se båndet og vælg mål) og kort (hvor er der målt)
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(6), dp(4), dp(6), dp(4)) }
        tabWf = button("Vandfald", Ui.BUTTON) { showTab(0) }.apply { textSize = 14f }
        tabMap = button("Kort", Ui.BUTTON) { showTab(1) }.apply { textSize = 14f }
        tabCam = button("Kamera", Ui.BUTTON) { showTab(2) }.apply { textSize = 14f }
        tabs.addView(tabWf, LinearLayout.LayoutParams(0, dp(36), 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
        tabs.addView(tabMap, LinearLayout.LayoutParams(0, dp(36), 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
        tabs.addView(tabCam, LinearLayout.LayoutParams(0, dp(36), 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
        tabs.addView(button("?", Ui.BUTTON) { showGuide() }.apply { textSize = 16f; setTextColor(Ui.ACCENT) },
            LinearLayout.LayoutParams(dp(44), dp(36)).apply { setMargins(dp(3), 0, dp(3), 0) })
        col.addView(tabs, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        val mainBox = FrameLayout(this)
        mapBoxView = mapBox
        waterfall = WaterfallView(this) { hz -> onWaterfallTap(hz) }
        mainBox.addView(mapBox, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        mainBox.addView(waterfall, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        cameraPane = CameraPane(this)
        mainBox.addView(cameraPane, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        // Billede: i vandfaldet de seneste 30 sekunder som en figur, på kortet kortet, som det ses
        figureBtn = button("Billede", 0xE6202124.toInt()) { if (tab == 1) saveMapPicture() else shareFigure() }.apply { textSize = 13f; setPadding(dp(12), 0, dp(12), 0) }
        mainBox.addView(figureBtn, FrameLayout.LayoutParams(WRAP_CONTENT, dp(36), Gravity.BOTTOM or Gravity.END).apply { setMargins(dp(8), dp(8), dp(8), dp(34)) })
        col.addView(mainBox, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        showTab(0)

        // knapper
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(6), dp(6), dp(6), dp(6)) }
        fun add(b: Button, weight: Float) = bar.addView(b, LinearLayout.LayoutParams(0, dp(60), weight).apply { setMargins(dp(3), 0, dp(3), 0) })
        startBtn = button("▶ Start", Ui.GREEN) { onStartStop() }.apply { setTextColor(0xFF0E0F12.toInt()); typeface = Typeface.DEFAULT_BOLD }
        add(startBtn, 2.2f)
        add(button(captioned("⚑", "Mærke"), Ui.BUTTON) { v ->
            if (Hub.running) { v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); markDialog() }
            else toast("Mærker kan sættes, mens der måles")
        }, 1f)
        add(button(captioned("−", "Gain"), Ui.BUTTON) { MeasureService.send(this, MeasureService.ACTION_GAIN_DOWN) }, 1f)
        add(button(captioned("+", "Gain"), Ui.BUTTON) { MeasureService.send(this, MeasureService.ACTION_GAIN_UP) }, 1f)
        // Del: seneste tur til delemenuen; langt tryk åbner listen over ture
        add(button(captioned("Del", "turen"), Ui.BUTTON) { exportLatest() }.apply {
            textSize = 16f; setTextColor(Ui.ACCENT)
            setOnLongClickListener { sessionsDialog(); true }
        }, 1.5f)
        add(button(captioned("⚙", "Indstil"), Ui.BUTTON) { openSettings() }, 1f)
        col.addView(bar, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        return col
    }

    private fun showTab(which: Int) {
        tab = which
        waterfall.visibility = if (which == 0) View.VISIBLE else View.GONE
        figureBtn.visibility = if (which == 0 || which == 1) View.VISIBLE else View.GONE
        mapBoxView.visibility = if (which == 1) View.VISIBLE else View.GONE
        cameraPane.visibility = if (which == 2) View.VISIBLE else View.GONE
        if (which == 2) cameraPane.open() else cameraPane.close()
        tabWf.setTextColor(if (which == 0) Ui.ACCENT else Ui.DIM)
        tabMap.setTextColor(if (which == 1) Ui.ACCENT else Ui.DIM)
        tabCam.setTextColor(if (which == 2) Ui.ACCENT else Ui.DIM)
    }

    /** Retningsantenne til eller fra: fanen Kamera følger indstillingen. */
    fun applyDirectional() {
        Hub.directional = settings.directional
        tabCam.visibility = if (settings.directional) View.VISIBLE else View.GONE
        if (!settings.directional && tab == 2) showTab(0)
    }

    /** Tryk i vandfaldet: det signal, der blev trykket på (eller lige ved), bliver målet. */
    private fun onWaterfallTap(tapHz: Double) {
        val near = Hub.sources.filter { kotlin.math.abs(it.freqHz - tapHz) <= 8_000 }.minByOrNull { kotlin.math.abs(it.freqHz - tapHz) }
        val hit = near?.freqHz ?: Scope.peakNear(tapHz, 8_000.0)
        // et tryk på et tomt sted må ikke flytte et mål, der høres – så er det et fejltryk
        if (hit == null && Hub.running && Hub.sources.any { it.target }) {
            toast("Intet signal dér – målet er uændret")
            return
        }
        setTarget(hit ?: tapHz)
    }

    private fun setTarget(hz: Double?) {
        settings.targetMhz = hz?.let { Math.round(it / 100.0) / 10_000.0 }
        if (Hub.running) MeasureService.sendTarget(this, hz) else Hub.targetHz = hz
        toast(if (hz == null) "Intet mål valgt" else "Mål: ${Ui.mhz(hz)} MHz")
        render()
    }

    private fun updateFollow() {
        followBtn.text = if (follow) "◉" else "◎"
        followBtn.setTextColor(if (follow) Ui.ACCENT else Ui.TEXT)
    }

    // ---------- start / stop ----------

    private fun onStartStop() {
        if (Hub.running) { MeasureService.send(this, MeasureService.ACTION_STOP); return }
        try { settings.detectorConfig() } catch (e: IllegalArgumentException) { toast(e.message ?: "Ugyldige indstillinger"); return }
        val need = ArrayList<String>()
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) need += Manifest.permission.ACCESS_FINE_LOCATION
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) need += Manifest.permission.POST_NOTIFICATIONS
        if (need.isNotEmpty()) requestPermissions(need.toTypedArray(), REQ_PERMS) else launchDriver()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_CAMERA) {
            if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) { if (tab == 2) cameraPane.open() }
            else { toast("Uden adgang til kameraet kan kameraskærmen ikke vises"); showTab(0) }
            return
        }
        if (requestCode != REQ_PERMS) return
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) launchDriver()
        else toast("MINK skal have adgang til position for at kunne gemme, hvor målingerne er taget")
    }

    /** Starter "Rtl-sdr driver", som åbner dongle'n og stiller rtl_tcp til rådighed på localhost. */
    private fun launchDriver() {
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(Intent(Intent.ACTION_VIEW, Uri.parse("iqsrc://-a 127.0.0.1 -p 1234 -s 1024000")), REQ_DRIVER)
        } catch (_: ActivityNotFoundException) {
            AlertDialog.Builder(this)
                .setTitle("Rtl-sdr driver mangler")
                .setMessage("MINK bruger appen \"Rtl-sdr driver\" til at tale med dongle'n. Installér den, og tryk Start igen.")
                .setPositiveButton("Hent den") { _, _ -> openDriverStore() }
                .setNegativeButton("Luk", null).show()
        }
    }

    /** Ét tryk til driveren i Play Butik (eller browseren, hvis Play Butik ikke findes på telefonen). */
    private fun openDriverStore() {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$DRIVER_PACKAGE"))) }
        catch (_: ActivityNotFoundException) {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$DRIVER_PACKAGE"))) }
            catch (_: ActivityNotFoundException) { toast("Hverken Play Butik eller en browser kunne åbnes") }
        }
    }

    /** Er driver-appen installeret, og sidder der en RTL-SDR i USB-porten? Tjekkes højst hvert 2. sekund. */
    private fun checkHardware(now: Long) {
        if (now - hwChecked < 2000) return
        hwChecked = now
        driverOk = try { packageManager.getPackageInfo(DRIVER_PACKAGE, 0); true } catch (_: Exception) { false }
        dongle = try {
            val devs = (getSystemService(USB_SERVICE) as UsbManager).deviceList.values
            when {
                devs.any { it.vendorId == 0x0bda && (it.productId == 0x2832 || it.productId == 0x2838) } -> true
                devs.isEmpty() -> false
                else -> null      // der sidder noget i porten, men ikke en af de to almindelige RTL-SDR-typer
            }
        } catch (_: Exception) { null }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_DRIVER) return
        if (resultCode == RESULT_OK) {
            MeasureService.send(this, if (Hub.running) MeasureService.ACTION_RECONNECT else MeasureService.ACTION_START)
            follow = true; updateFollow()
            batteryHint()
        } else {
            val why = data?.getStringExtra("detailed_exception_message")
            AlertDialog.Builder(this)
                .setTitle("Driveren kunne ikke starte")
                .setMessage("Er dongle'n sat i, og har du givet USB-adgang?" + (if (why.isNullOrBlank()) "" else "\n\n$why"))
                .setPositiveButton("Prøv igen") { _, _ -> launchDriver() }
                .setNegativeButton("Luk", null).show()
        }
    }

    /** Første gang: telefoner med hård batteristyring (fx Xiaomi) lukker ellers målingen, når skærmen slukker. */
    private fun batteryHint() {
        if (settings.batteryHintShown) return
        settings.batteryHintShown = true
        AlertDialog.Builder(this)
            .setTitle("Lad målingen køre i baggrunden")
            .setMessage("Nogle telefoner lukker apps, når skærmen slukker. Sæt batteribesparelse for MINK til \"Ingen begrænsninger\", så målingen fortsætter.")
            .setPositiveButton("Åbn indstillinger") { _, _ -> openAppSettings() }
            .setNegativeButton("Senere", null).show()
    }

    fun openAppSettings() {
        try { startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
        catch (_: ActivityNotFoundException) {}
    }

    // ---------- eksport ----------


    /** Mærke-knappen: hvad er det for et sted? Mærket sættes dér, hvor telefonen er, og vises på kortet. */
    private fun markDialog() {
        val kinds = arrayOf(MarkKind.POSSIBLE, MarkKind.FOUND, MarkKind.PLAIN)
        AlertDialog.Builder(this)
            .setTitle("Sæt et mærke her")
            .setItems(arrayOf("Mulig kilde", "Kilde fundet", "Almindeligt mærke")) { _, i -> MeasureService.sendMark(this, kinds[i]) }
            .setNegativeButton("Annullér", null)
            .show()
    }

    /** Gemmer figuren af de seneste 30 sekunder som PNG i galleriet. Deling sker fra galleriet, ikke herfra. */
    private fun shareFigure() {
        if (Scope.seq < FigureExport.MIN_SECONDS * Scope.ROWS_PER_SECOND) { toast("Tryk Start, og lad den måle et øjeblik først"); return }
        Thread {
            val uri = FigureExport.save(this)
            runOnUiThread {
                toast(if (uri == null) "Billedet kunne ikke gemmes" else "Billedet er gemt i galleriet (${FigureExport.DIR})")
            }
        }.start()
    }

    /**
     * Gemmer kortet, som det ses lige nu, som PNG i galleriet. Billedet følger med, når turen deles: den tur, kortet
     * viser, hvis det er én tidligere tur, og ellers den nyeste.
     */
    private fun saveMapPicture() {
        val now = Date()
        val target = Hub.targetHz?.let { " · mål ${Ui.mhz(it)} MHz" } ?: ""
        val caption = "MINK · ${SimpleDateFormat("d.M.yyyy HH:mm", Ui.DA).format(now)} · ${tripTv.text}$target"
        val bmp = MapExport.capture(map, legendTv, caption, resources.displayMetrics.density)
        if (bmp == null) { toast("Kortet er ikke klar endnu"); return }
        val shown = if (MapData.sessions == 1 && tripIndex in trips.indices) TripFiles.stamp(trips[tripIndex].name) else null
        Thread {
            val saved = MapExport.save(this, bmp, TripFiles.mapPictureName(SimpleDateFormat("yyyyMMdd_HHmmss", Ui.DA).format(now), shown))
            runOnUiThread {
                toast(if (saved == null) "Billedet kunne ikke gemmes" else "Kortet er gemt i galleriet (${FigureExport.DIR})")
            }
        }.start()
    }

    private fun exportLatest() {
        val logs = JsonlLog.list(this)
        if (logs.isEmpty()) { toast("Der er ingen ture at sende endnu"); return }
        TripShare.show(this, listOf(logs[0]), logs, offerOlder = true)
    }

    /** Vælg en eller flere tidligere ture: læg dem på kortet, eller del dem. */
    fun sessionsDialog() {
        val logs = JsonlLog.list(this)
        if (logs.isEmpty()) { toast("Der er ingen ture endnu"); return }
        val names = logs.map { e -> "${TripFiles.title(e.name)} · ${max(1, e.bytes / 1024)} KB" }.toTypedArray()
        val picked = BooleanArray(logs.size).also { it[0] = true }
        fun chosen() = logs.filterIndexed { i, _ -> picked[i] }.map { it.uri }
        AlertDialog.Builder(this)
            .setTitle("Tidligere ture")
            .setMultiChoiceItems(names, picked) { _, i, on -> picked[i] = on }
            .setPositiveButton("Vis på kort") { _, _ ->
                trips = logs
                tripIndex = if (picked.count { it } == 1) picked.indexOfFirst { it } else -1
                showOnMap(chosen())
            }
            .setNeutralButton("Del …") { _, _ ->
                if (chosen().isEmpty()) toast("Vælg mindst én tur")
                else TripShare.show(this, logs.filterIndexed { i, _ -> picked[i] }, logs, offerOlder = false)
            }
            .setNegativeButton("Luk", null).show()
    }

    /** Bladrer én tur ad gangen på kortet: +1 = ældre, -1 = nyere. Forbi den nyeste vises kun den aktuelle tur. */
    private fun stepTrip(dir: Int) {
        trips = JsonlLog.list(this)
        if (trips.isEmpty()) { toast("Der er ingen ture endnu"); return }
        // den tur, der kører lige nu, vises i forvejen som "Aktuel tur" og springes over i listen
        val live = Hub.logName?.takeIf { Hub.running }
        var i = tripIndex + dir
        while (i in trips.indices && live != null && trips[i].name.startsWith(live)) i += dir
        if (i >= trips.size) { toast("Det er den ældste tur"); return }
        tripIndex = if (i < 0) -1 else i
        showOnMap(if (tripIndex < 0) emptyList() else listOf(trips[tripIndex].uri))
    }

    /** Lægger de valgte ture ind på kortet (ingen valgt = fjern dem igen) og zoomer, så de kan ses. */
    private fun showOnMap(uris: List<Uri>) {
        if (settingsView != null) closeSettings()
        tripsShown = uris.size
        if (uris.isEmpty()) { tripIndex = -1; MapData.clear(); showTab(1); render(); return }
        Thread {
            val n = MapData.load(this, uris)
            runOnUiThread {
                if (n == 0) toast("De valgte ture har ingen målinger med position")
                val pts = MapData.bursts + MapData.silent
                val lats = pts.mapNotNull { it.lat }; val lons = pts.mapNotNull { it.lon }
                if (lats.isNotEmpty()) {
                    follow = false; updateFollow()
                    val box = BoundingBox(lats.max() + 0.002, lons.max() + 0.004, lats.min() - 0.002, lons.min() - 0.004)
                    map.zoomToBoundingBox(box, false, dp(24))
                }
                showTab(1)
                if (!pointsLayer.heat) heatBtn.performClick() else render()
            }
        }.start()
    }

    /** Vælg, hvilken af de hørte sendere tal, pil, bip og kort skal følge. */
    fun targetDialog() {
        val srcs = Hub.sources
        val items = srcs.map {
            "${Ui.mhz(it.freqHz)} MHz · ${String.format(Ui.DA, "%+d", it.snr.roundToInt())} dB · niveau ${Ui.f1(it.level)}" +
                (if (Hub.inChannel(it.freqHz)) " · i din kanal" else "") + if (it.target) "  ← mål" else ""
        } + "Intet mål"
        AlertDialog.Builder(this)
            .setTitle("Hvilken sender er målet?")
            .setItems(items.toTypedArray()) { _, i ->
                setTarget(srcs.getOrNull(i)?.freqHz)
            }
            .setNegativeButton("Luk", null).show()
    }

    // ---------- indstillinger ----------

    private fun openSettings() {
        if (settingsView != null) return
        settingsView = SettingsScreen(this, settings) { closeSettings() }.build()
        root.addView(settingsView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }

    private fun closeSettings() {
        settingsView?.let { root.removeView(it) }
        settingsView = null
        applyKeepScreenOn()
        render()
    }

    // ---------- visning ----------

    private fun render() {
        val now = System.currentTimeMillis()
        val running = Hub.running
        if (!running) checkHardware(now)

        startBtn.text = if (running) "■ Stop" else "▶ Start"
        (startBtn.background as? GradientDrawable)?.setColor(if (running) Ui.RED else Ui.GREEN)

        // statuslinje
        val dot = when (Hub.conn) {
            Hub.Conn.RECEIVING -> Ui.GREEN
            Hub.Conn.CONNECTING, Hub.Conn.WAITING -> Ui.YELLOW
            Hub.Conn.FAILED -> Ui.RED
            Hub.Conn.STOPPED -> Ui.DIM
        }
        val sb = SpannableStringBuilder("● ")
        sb.setSpan(ForegroundColorSpan(dot), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.append(Hub.statusText)
        if (running) {
            if (Hub.conn == Hub.Conn.RECEIVING) sb.append(" · ${Hub.tuner}")
            sb.append(" · gain ${Ui.f1(Hub.gainDb)} dB")
            if (Hub.gainDb < Hub.gainChosenDb - 0.05) sb.append(" (sænket fra ${Ui.f1(Hub.gainChosenDb)})")
        } else {
            sb.append(" · ${Ui.mhz(settings.bandLoMhz * 1e6)}–${Ui.mhz(settings.bandHiMhz * 1e6)} MHz")
        }
        sb.append("\n")
        val loc = Hub.location
        val fixAge = loc?.let { (android.os.SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos) / 1_000_000 }
        val gpsOk = fixAge != null && fixAge < 10_000
        if (gpsOk) sb.append("GPS ±${loc!!.accuracy.roundToInt()} m · ${(loc.speed * 3.6).roundToInt()} km/t")
        else sb.append(colored(if (running) "Ingen GPS" else "GPS venter", if (running) Ui.YELLOW else Ui.DIM))
        if (running || Hub.logName != null) {
            sb.append(" · ")
            if (Hub.logOk) sb.append("Log ✓") else sb.append(colored("Log fejler", Ui.RED))
            when {
                !Hub.uploadOn -> {}
                Hub.uploadError != null -> sb.append(" · ").append(colored("Upload kø ${Hub.queued} (${Hub.uploadError})", Ui.YELLOW))
                else -> sb.append(" · Upload ${Hub.sent} ✓")
            }
        }
        statusTv.text = sb

        // stort tal: friskheden måles mod den tid, der plejer at gå mellem målingerne
        val last = Hub.lastBurst
        val snr = last?.snr
        val expect = max(Hub.intervalMs ?: 4000L, 1000L)
        val age = if (last != null) now - last.t - last.durMs else Long.MAX_VALUE
        val fresh = age < max(expect * 3 / 2, 3000L)
        val stale = age < max(expect * 3, 6000L)
        if (last == null || snr == null || (!stale && running)) {
            bigTv.text = "—"; bigTv.setTextColor(Ui.DIM)
            subTv.text = when {
                running && Hub.targetHz == null -> "vælg et mål i vandfaldet"
                last != null && running -> "intet hørt i ${age / 1000} s"
                running -> "lytter …"
                else -> "dB over støjen"
            }
        } else {
            bigTv.text = String.format(Ui.DA, "%+d", snr.roundToInt())
            bigTv.setTextColor(if (fresh || !running) Ui.TEXT else Ui.DIM)
            subTv.text = "dB over støjen"
        }
        if (last != null && last !== lastShown) {
            lastShown = last
            bigTv.animate().scaleX(1.08f).scaleY(1.08f).setDuration(75)
                .withEndAction { bigTv.animate().scaleX(1f).scaleY(1f).setDuration(75) }
        }

        // trend
        val delta = Hub.trendDelta
        val limit = if (Hub.trendUncertain) 3.5 else 2.0
        if (delta == null || last == null || (!stale && running)) {
            arrowTv.text = "▬"; arrowTv.setTextColor(0xFF3C4043.toInt()); deltaTv.text = ""; trendTv.text = ""
        } else {
            val (sym, color, label) = when {
                delta >= limit -> Triple("▲", Ui.GREEN, "tættere")
                delta <= -limit -> Triple("▼", Ui.RED, "længere væk")
                else -> Triple("▬", Ui.DIM, "uændret")
            }
            arrowTv.text = sym; arrowTv.setTextColor(color)
            deltaTv.text = String.format(Ui.DA, "%+.1f", delta); deltaTv.setTextColor(color)
            trendTv.text = label
        }

        // detaljer
        detailTv.text = if (last?.level == null) {
            when {
                running -> "Finder smalle signaler i ${Ui.mhz(settings.bandLoMhz * 1e6)}–${Ui.mhz(settings.bandHiMhz * 1e6)} MHz"
                !driverOk -> "Mangler appen \"Rtl-sdr driver\""
                dongle == true -> "RTL-SDR fundet – tryk Start"
                dongle == null -> "USB-enhed fundet, men ikke genkendt som RTL-SDR – prøv Start"
                else -> "Sæt dongle'n i, og tryk Start"
            }
        } else buildString {
            append("niveau ${Ui.f1(last.level)} dB · ${Ui.mhz(last.freqHz ?: 0.0)} MHz · ${last.durMs} ms")
            if (running) append(" · ${max(0, age / 1000)} s siden")
            Hub.intervalMs?.let { append(" · hvert ${Ui.f1(it / 1000.0)} s") }
            Hub.sources.firstOrNull { it.target }?.bwHz?.let { append(" · ${(it / 1000).roundToInt()} kHz bred") }
        }

        // sendere og mål
        val srcs = Hub.sources
        val tgt = srcs.firstOrNull { it.target }
        val others = srcs.filter { !it.target }
        sourcesTv.visibility = if (running) View.VISIBLE else View.GONE
        sourcesTv.text = buildString {
            append("Mål: ")
            append(
                when {
                    tgt != null -> "${Ui.mhz(tgt.freqHz)} MHz" + if (Hub.inChannel(tgt.freqHz)) " (i din kanal)" else ""
                    Hub.targetHz != null -> "${Ui.mhz(Hub.targetHz ?: 0.0)} MHz, ikke hørt endnu"
                    else -> "ikke valgt – tryk på et signal i vandfaldet"
                }
            )
            if (others.isNotEmpty()) append(" · andre: " + others.joinToString(", ") { "${Ui.mhz(it.freqHz)} (${String.format(Ui.DA, "%+d", it.snr.roundToInt())})" })
            if (srcs.isNotEmpty()) append("  ▸ liste")
        }

        // ligger trafikken på kanalen ved siden af kanalen, viser donglen forkert frekvens
        if (running && now - calCheckedAt > 3000) {
            calCheckedAt = now
            calOffsetHz = Scope.snapshot()
                ?.let { Calibrate.offsetHz(it, Hub.ppmSeq, Hub.channelHz, Hub.channelBwHz) }
                ?.takeIf { kotlin.math.abs(it) >= Calibrate.WORTH_HZ }
        }
        if (!running) calOffsetHz = null
        val cal = calOffsetHz

        // advarsel
        val warn = when {
            !running && !driverOk -> "Rtl-sdr driver mangler – tryk her for at hente den i Play Butik"
            !running && Hub.conn == Hub.Conn.FAILED -> Hub.statusText
            !running -> null
            Hub.conn == Hub.Conn.FAILED -> "Driveren svarer ikke – tryk her for at starte den igen"
            Hub.conn == Hub.Conn.WAITING -> "Venter på driveren – tryk her, hvis dongle'n har været ude"
            cal != null -> "Donglen viser ${(kotlin.math.abs(cal) / 1000).roundToInt()} kHz for ${if (cal > 0) "højt" else "lavt"} – tryk her for at rette"
            Hub.clipPct > 0.5 -> if (Hub.autoGain) "Mætning – en kraftig sender er tæt på" else "Mætning – tryk − (virker med det samme)"
            Hub.targetAtEdge -> "Målet ligger på kanten af båndet – udvid båndet under ⚙"
            !gpsOk -> "Ingen GPS – målinger gemmes uden position"
            else -> null
        }
        warnTv.visibility = if (warn == null) View.GONE else View.VISIBLE
        if (warn != null) warnTv.text = warn

        // kort og graf
        val range = MapData.range()
        val trips = if (MapData.sessions > 0) "${MapData.sessions} tidligere ${if (MapData.sessions == 1) "tur" else "ture"} · " else ""
        legendTv.text = when {
            pointsLayer.heat -> trips + pointsLayer.heatLegend
            range == null -> "ingen målinger endnu"
            else -> "${trips}blå ${Ui.f1(range.first)} → rød ${Ui.f1(range.second)} dB · ${Hub.bursts.size + MapData.bursts.size} målinger · ${Hub.silent.size + MapData.silent.size} stille"
        }
        tripTv.text = when {
            tripsShown == 0 -> "Aktuel tur"
            tripsShown == 1 && tripIndex in this.trips.indices -> TripFiles.title(this.trips[tripIndex].name)
            else -> "$tripsShown ture"
        }
        if (follow && gpsOk) map.controller.setCenter(GeoPoint(loc!!.latitude, loc.longitude))
        map.invalidate()
        graph.invalidate()
    }

    /**
     * Er målet bredere end den signalbredde, detektoren er sat til? Fra ca. 1,3 × når signalets egne kanter ud i
     * naboområderne, og så skal vinduet ligge meget præcist for, at målingen ikke falder fra som "bred".
     */
    /** Retter donglens PPM-korrektion, så trafikken på kanalen lander på kanalen. Gælder med det samme. */
    private fun calibrate() {
        val off = calOffsetHz ?: return
        val now = System.currentTimeMillis()
        calOffsetHz = null; calCheckedAt = now
        // samme forskydning igen lige efter en rettelse: driveren tog den ikke, mens den kørte. Så forbindes der igen
        // (korrektionen sendes altid ved forbindelsen), i stedet for at lægge endnu en rettelse oveni.
        val prev = calFixedHz
        if (prev != null && now - calFixedAt < 120_000 && kotlin.math.abs(off - prev) < 3_000) {
            calFixedHz = null
            MeasureService.send(this, MeasureService.ACTION_RECONNECT)
            toast("Rettelsen slog ikke igennem – forbinder til donglen igen")
            return
        }
        val old = settings.ppm
        val new = Calibrate.ppm(old, off, Hub.channelHz)
        if (new == old) return
        calFixedHz = off; calFixedAt = now
        settings.ppm = new
        MeasureService.sendPpm(this, new)
        toast("Donglens frekvens er rettet (PPM $old → $new). Vandfaldet passer fra nu af.")
    }

    private fun colored(text: String, color: Int) = SpannableStringBuilder(text).apply {
        setSpan(ForegroundColorSpan(color), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}
