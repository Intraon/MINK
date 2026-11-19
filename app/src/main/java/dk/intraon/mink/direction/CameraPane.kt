package dk.intraon.mink.direction

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Size
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.TextView
import dk.intraon.mink.core.Hub
import dk.intraon.mink.measure.MeasureService
import dk.intraon.mink.ui.MainActivity
import dk.intraon.mink.ui.Ui
import dk.intraon.mink.waterfall.FigureExport
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Kameraskærmen til retningsantenne: man holder telefonen i samme retning som antennen, som når man tager et
 * billede. Oven på kamerabilledet ligger et farvebånd langs horisonten, hvor hver måling har farvet den retning,
 * antennen pegede i, da den blev taget. "Tag pejling" gemmer et foto med retning og styrke og en streg på kortet.
 *
 * Skarpheden bestemmes af antennen, ikke af kameraet: en lille Yagi ser omkring 60° ad gangen.
 */
class CameraPane(private val a: MainActivity) : FrameLayout(a) {
    private val d = resources.displayMetrics.density
    private val texture = TextureView(a)
    private val overlay = Overlay(a)
    private val compass = Compass(a, motion = true)
    private var calDialog: AlertDialog? = null
    /** Kameraets tilbagemeldinger kommer på hovedtråden, så de ikke kan krydse en ændring af billedets størrelse. */
    private val main = Handler(Looper.getMainLooper())
    private val watch = CameraWatch()
    /** Tæller op, hver gang kameraet startes eller stoppes; en tilbagemelding fra et ældre forsøg kendes på sit tal. */
    private var run = 0
    private var starting = false
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var preview = Size(1280, 720)
    /** Synsfeltet for hele kamerabilledet, når telefonen holdes på højkant: vandret og lodret, i grader. */
    private var hfov = 50.0
    private var vfov = 80.0
    /** Kamerabilledets størrelse på skærmen. Det er mindst lige så stort som feltet og beskæres i kanten. */
    private var texW = 0
    private var texH = 0
    private var error: String? = null
    /** Sat, når der ikke er et kamera at prøve igen med. */
    private var hopeless = false
    private var wanted = false
    /** Et kamerabillede fra et steds stærkeste måling: hvornår målingen blev taget, og hvor telefonen pegede. */
    private class Held(val at: Long, val frame: Bitmap, val hdg: Double, val tilt: Double)
    /** Højst to billeder: det fra stedets stærkeste måling indtil nu, og det, der blev holdt før. */
    private val held = ArrayList<Held>()
    /** Den pejling, appen selv har gemt, som skærmen senest har taget sig af. */
    private var handled: Hub.AutoBearing? = Hub.autoBearing
    /** Det, et foto af en tidligere måling skal vise i stedet for det, der måles lige nu. */
    private class Shot(val rose: Rose, val status: String)

    init {
        setBackgroundColor(0xFF000000.toInt())
        addView(texture, LayoutParams(MATCH_PARENT, MATCH_PARENT, Gravity.CENTER))
        addView(overlay, LayoutParams(MATCH_PARENT, MATCH_PARENT))
        val shoot = a.button("Tag pejling", 0xE6202124.toInt()) { takeBearing() }.apply { textSize = 15f; setPadding((16 * d).toInt(), 0, (16 * d).toInt(), 0) }
        addView(shoot, LayoutParams(WRAP_CONTENT, (48 * d).toInt(), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = (14 * d).toInt() })
        val cal = a.button("Kalibrér kompas", 0xE6202124.toInt()) { calibrate() }.apply { textSize = 12f; setPadding((10 * d).toInt(), 0, (10 * d).toInt(), 0) }
        addView(cal, LayoutParams(WRAP_CONTENT, (34 * d).toInt(), Gravity.TOP or Gravity.END).apply { topMargin = (12 * d).toInt(); rightMargin = (8 * d).toInt() })
        // målet vælges ellers i vandfaldet; herfra kan det vælges på listen over de sendere, der høres
        val pick = a.button("Mål", 0xE6202124.toInt()) { a.targetDialog() }.apply { textSize = 12f; setPadding((10 * d).toInt(), 0, (10 * d).toInt(), 0) }
        addView(pick, LayoutParams(WRAP_CONTENT, (34 * d).toInt(), Gravity.BOTTOM or Gravity.END).apply { bottomMargin = (21 * d).toInt(); rightMargin = (8 * d).toInt() })
        texture.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) { if (wanted) startCamera() }
            // Når feltet skifter størrelse, sætter Android billedets størrelse til feltets. Kameraet kan kun levere
            // sine egne størrelser, så dets størrelse sættes tilbage med det samme.
            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                if (starting || device != null) st.setDefaultBufferSize(preview.width, preview.height)
            }
            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean { stopCamera(); watch.stopped(); return true }
            override fun onSurfaceTextureUpdated(st: SurfaceTexture) { watch.frame(SystemClock.elapsedRealtime()) }
        }
    }

    /** Kameraskærmen vises: kompas og kamera startes. Mangler tilladelsen, spørges der om den. */
    fun open() {
        wanted = true
        compass.start()
        overlay.run()
        if (a.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            a.requestPermissions(arrayOf(Manifest.permission.CAMERA), MainActivity.REQ_CAMERA)
            return
        }
        if (texture.isAvailable) startCamera()
        // første gang i denne omgang: kompasset skal kalibreres, før retningerne er noget værd
        if (!Hub.compassCalibrated && compass.available) calibrate()
    }

    /**
     * Beder brugeren bevæge telefonen i 8-taller, så magnetometeret ser feltet fra alle sider. Følger med i bevægelsen
     * (gyroskopet) og i, om feltets styrke passer med Jordens på stedet. Lukker selv, når begge dele er i orden.
     */
    fun calibrate() {
        if (calDialog != null) return
        for (i in 0..2) Hub.turn[i] = 0.0
        val began = System.currentTimeMillis()
        val msg = TextView(a).apply { textSize = 15f; setTextColor(Ui.TEXT); setPadding((22 * d).toInt(), (14 * d).toInt(), (22 * d).toInt(), 0) }
        val dlg = AlertDialog.Builder(a)
            .setTitle("Kalibrér kompasset")
            .setView(msg)
            .setNegativeButton("Spring over") { _, _ -> Hub.compassCalibrated = true }
            .setOnDismissListener { calDialog = null }
            .create()
        calDialog = dlg
        val update = object : Runnable {
            override fun run() {
                if (calDialog !== dlg) return
                // uden gyroskop kan bevægelsen ikke måles: så gives der 10 sekunder
                val prog = if (compass.hasGyro) CompassCheck.progress(Hub.turn) else ((System.currentTimeMillis() - began) / 10_000.0).coerceIn(0.0, 1.0)
                val ut = Hub.magUt; val exp = Hub.magExpectedUt
                val fieldOk = CompassCheck.fieldOk(ut, exp)
                val lowAcc = Hub.magAccuracy in 0..1
                if (prog >= 1.0 && fieldOk && !lowAcc) {
                    Hub.compassCalibrated = true
                    Hub.rose.reset()      // det, der blev målt før kalibreringen, pegede forkert
                    dlg.dismiss()
                    a.toast("Kompasset er kalibreret")
                    return
                }
                val field = if (ut == null) "måles …" else "${ut.roundToInt()} µT" + (exp?.let { " (her ca. ${it.roundToInt()})" } ?: "") + if (fieldOk) " ✓" else " ✗"
                msg.text = "Bevæg telefonen i store 8-taller et par gange, så den når at vende alle veje.\n\n" +
                    "Gør det væk fra højttalere, magneter, bil og stål – og fra en antenne med magnetfod.\n\n" +
                    "Bevægelse: ${(prog * 100).roundToInt()} %\nMagnetfelt: $field" +
                    (if (prog >= 1.0 && !fieldOk) "\n\nFeltet passer stadig ikke. Noget magnetisk er for tæt på. Flyt dig, og fortsæt 8-tallerne." else "") +
                    (if (prog >= 1.0 && fieldOk && lowAcc) "\n\nTelefonen melder stadig lav præcision. Fortsæt lidt endnu." else "")
                msg.postDelayed(this, 200)
            }
        }
        dlg.show()
        update.run()
    }

    fun close() {
        // stedet er færdigt, når skærmen forlades: dets stærkeste retning gemmes som pejling
        if (wanted) MeasureService.sendPlaceDone(a)
        wanted = false
        calDialog?.dismiss()
        compass.stop()
        overlay.pause()
        stopCamera()
        watch.stopped()
        if (!hopeless) error = null
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        post { fitTexture() }
    }

    /** Kamerabilledet fylder feltet ud uden at blive trukket skævt: det, der ikke er plads til, skæres af i kanten. */
    private fun fitTexture() {
        val vw = width; val vh = height
        if (vw == 0 || vh == 0) return
        val aspect = preview.height.toFloat() / preview.width      // bredde/højde på højkant
        if (vw.toFloat() / vh > aspect) { texW = vw; texH = (vw / aspect).roundToInt() } else { texH = vh; texW = (vh * aspect).roundToInt() }
        val lp = texture.layoutParams as LayoutParams
        if (lp.width != texW || lp.height != texH) { lp.width = texW; lp.height = texH; lp.gravity = Gravity.CENTER; texture.layoutParams = lp }
    }

    @SuppressLint("MissingPermission")
    private fun startCamera() {
        if (device != null || starting || hopeless) return
        val mine = ++run
        starting = true
        watch.started(SystemClock.elapsedRealtime())
        try {
            val cm = a.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK }
                ?: cm.cameraIdList.firstOrNull()
            if (id == null) { starting = false; hopeless = true; watch.stopped(); error = "Telefonen har intet kamera"; return }
            val ch = cm.getCameraCharacteristics(id)
            val sizes = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.getOutputSizes(SurfaceTexture::class.java)?.toList().orEmpty()
            preview = sizes.filter { it.width <= 1920 && it.width * 9 == it.height * 16 }.maxByOrNull { it.width }
                ?: sizes.filter { it.width <= 1920 }.maxByOrNull { it.width * it.height }
                ?: Size(1280, 720)
            // synsfeltet ud fra sensorens størrelse og brændvidden; et 16:9-billede bruger hele sensorens bredde
            val phys = ch.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
            val f = ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()
            if (phys != null && f != null && f > 0) {
                val long = phys.width
                val short = min(phys.height, phys.width * preview.height / preview.width)
                hfov = Math.toDegrees(2 * atan(short / (2.0 * f)))
                vfov = Math.toDegrees(2 * atan(long / (2.0 * f)))
            }
            fitTexture()
            cm.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(cam: CameraDevice) {
                    // skærmen kan være forladt, mens kameraet var ved at åbne
                    if (mine != run || !wanted) { cam.close(); return }
                    starting = false; device = cam; startPreview(cam, mine)
                }
                override fun onDisconnected(cam: CameraDevice) { cam.close(); if (mine == run) fail("Kameraet kunne ikke åbnes") }
                override fun onError(cam: CameraDevice, code: Int) { cam.close(); if (mine == run) fail("Kameraet kunne ikke åbnes (fejl $code)") }
            }, main)
        } catch (_: SecurityException) {
            fail("MINK har ikke adgang til kameraet")
        } catch (_: Exception) {
            fail("Kameraet kunne ikke åbnes")
        }
    }

    private fun startPreview(cam: CameraDevice, mine: Int) {
        try {
            val st = texture.surfaceTexture
            if (st == null) { fail("Kameraet kunne ikke vise billedet"); return }
            st.setDefaultBufferSize(preview.width, preview.height)
            val surface = Surface(st)
            val req = cam.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply { addTarget(surface) }
            @Suppress("DEPRECATION")
            cam.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(s: CameraCaptureSession) {
                    if (mine != run) return
                    session = s
                    try { s.setRepeatingRequest(req.build(), null, main) } catch (_: Exception) { fail("Kameraet kunne ikke vise billedet") }
                }
                override fun onConfigureFailed(s: CameraCaptureSession) { if (mine == run) fail("Kameraet kunne ikke vise billedet") }
            }, main)
        } catch (_: Exception) {
            fail("Kameraet kunne ikke vise billedet")
        }
    }

    /**
     * Et forsøg på at få kameraet i gang mislykkedes: kameraet lukkes, og [watch] bestemmer, hvornår der prøves igen.
     * [message] vises først, når flere forsøg i træk er slået fejl.
     */
    private fun fail(message: String) {
        stopCamera()
        watch.failed(SystemClock.elapsedRealtime())
        error = if (watch.givenUp) message else null
    }

    private fun stopCamera() {
        run++
        try { session?.close() } catch (_: Exception) {}
        try { device?.close() } catch (_: Exception) {}
        session = null; device = null; starting = false
    }

    /** Gemmer pejlingen i loggen (og dermed på kortet) og et foto med retning og styrke i galleriet. */
    private fun takeBearing() {
        if (!Hub.running) { a.toast("Tryk Start først – en pejling skal have en måling med"); return }
        val hdg = Hub.freshHeading()
        if (hdg == null) { a.toast("Kompasset svarer ikke"); return }
        // en pejling med et kompas, der viser forkert, er værre end ingen: kalibrér først
        if (Hub.compassPoor) { calibrate(); return }
        val now = Date()
        val photo = "MINK_pejling_" + SimpleDateFormat("yyyyMMdd_HHmmss", Ui.DA).format(now) + ".jpg"
        MeasureService.sendBearing(a, photo)
        val raw = try { if (device != null && texW > 0) texture.getBitmap(texW, texH) else null } catch (_: Throwable) { null }
        val last = Hub.lastBurst?.takeIf { now.time - it.t < 6_000 }
        val line = "${hdg.roundToInt()}° ${Bearing.name(hdg)}" + (last?.level?.let { " · niveau ${Ui.f1(it)} dB" } ?: " · intet hørt")
        savePhoto(photo, raw, hdg, Hub.tiltDeg, now, null, line) { ok ->
            a.toast(if (ok) "Pejling ${hdg.roundToInt()}° gemt på kortet og som foto i galleriet" else "Pejling ${hdg.roundToInt()}° gemt på kortet (fotoet kunne ikke gemmes)")
        }
    }

    /**
     * Holder kamerabilledet fra stedets stærkeste måling indtil nu, så stedets pejling kan få et foto, når appen
     * selv gemmer den. Det forrige billede beholdes: stedet, det hører til, kan være forladt, uden at pejlingen
     * er gemt endnu.
     */
    private fun hold() {
        val at = Hub.rose.topAt
        if (at == 0L || at == Hub.heldFrameAt || !Hub.running || Hub.flat || device == null || texW <= 0) return
        val hdg = Hub.freshHeading() ?: return
        val frame = try { texture.getBitmap(texW, texH) } catch (_: Throwable) { null } ?: return
        while (held.size > 1) held.removeAt(0).frame.recycle()
        held.add(Held(at, frame, hdg, Hub.tiltDeg))
        Hub.heldFrameAt = at
    }

    /**
     * Appen har selv gemt en pejling for et sted, brugeren har forladt: fortæl det, og gem fotoet fra stedets
     * stærkeste måling, hvis det blev holdt. Kaldes, hver gang noget har ændret sig; gør kun noget én gang pr. pejling.
     */
    /** "248°, 123° og 330°": toppens midte og de andre retninger. */
    private fun degrees(l: Lobe): String {
        val all = (listOf(l.center) + l.others).map { "${it.roundToInt()}°" }
        return if (all.size == 1) all[0] else all.dropLast(1).joinToString(", ") + " og " + all.last()
    }

    /** "stærkest 248° VSV", eller "flere retninger: 248°, 123° og 330°", når signalet kommer lige stærkt fra flere sider. */
    private fun strongest(l: Lobe): String =
        if (l.several) "flere retninger: ${degrees(l)}" else "stærkest ${l.center.roundToInt()}° ${Bearing.name(l.center)}"

    fun autoSaved() {
        val ab = Hub.autoBearing ?: return
        if (ab === handled) return
        handled = ab
        val best = ab.stop.best
        // kom signalet lige stærkt fra flere sider, er det genskin, og så skal ingen af retningerne stå alene
        // er der målt hele vejen rundt, får brugeren at vide, at stedet er færdigt
        val round = if (ab.stop.rose.turn()?.done == true) "Omgangen er færdig. " else ""
        val what = round + if (ab.stop.lobe.several) "Stedet har flere retninger (${degrees(ab.stop.lobe)}), sandsynligvis genskin. Det"
        else "Stedets stærkeste retning ${best.roundToInt()}° ${Bearing.name(best)}"
        val shot = held.firstOrNull { it.at == ab.stop.at }
        if (ab.photo == null || shot == null || width == 0) { a.toast("$what er gemt som pejling på kortet"); return }
        held.remove(shot)
        val level = "niveau ${Ui.f1(ab.stop.lobe.top)} dB"
        savePhoto(ab.photo, shot.frame, shot.hdg, shot.tilt, Date(shot.at), Shot(ab.stop.rose, "taget i stedets stærkeste retning · $level"), "${strongest(ab.stop.lobe)} · $level") { ok ->
            a.toast(if (ok) "$what er gemt som pejling på kortet og som foto i galleriet" else "$what er gemt som pejling på kortet (fotoet kunne ikke gemmes)")
        }
    }

    /**
     * Tegner et pejlingsfoto og gemmer det i galleriet under [name]: kamerabilledet [raw] (null = intet kamerabillede),
     * det, skærmen viser oven på det, og en linje nederst med tidspunktet og [line], så fotoet kan stå alene.
     * [done] kaldes på hovedtråden med, om fotoet blev gemt. [raw] frigives.
     */
    private fun savePhoto(name: String, raw: Bitmap?, hdg: Double, tilt: Double, time: Date, shot: Shot?, line: String, done: (Boolean) -> Unit) {
        val vw = width; val vh = height
        Thread {
            var uri: Uri? = null
            try {
                val out = Bitmap.createBitmap(vw, vh, Bitmap.Config.ARGB_8888)
                val c = Canvas(out)
                c.drawColor(0xFF000000.toInt())
                if (raw != null) c.drawBitmap(raw, ((vw - raw.width) / 2).toFloat(), ((vh - raw.height) / 2).toFloat(), null)
                overlay.paint(c, vw, vh, hdg, tilt, time.time, shot)
                val p = Paint(Paint.ANTI_ALIAS_FLAG)
                p.color = 0xD9000000.toInt(); c.drawRect(0f, vh - 34 * d, vw.toFloat(), vh.toFloat(), p)
                p.color = 0xFFFFFFFF.toInt(); p.textSize = 14 * d
                c.drawText("MINK · " + SimpleDateFormat("d.M.yyyy HH:mm:ss", Ui.DA).format(time) + " · " + line, 10 * d, vh - 12 * d, p)
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, FigureExport.DIR)
                }
                val u = a.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (u != null && a.contentResolver.openOutputStream(u)?.use { out.compress(Bitmap.CompressFormat.JPEG, 92, it) } == true) uri = u
                out.recycle(); raw?.recycle()
            } catch (_: Throwable) {
                uri = null
            }
            a.runOnUiThread { done(uri != null) }
        }.start()
    }

    /** Det, der tegnes oven på kamerabilledet. Samme tegning bruges på skærmen og i det gemte foto. */
    private inner class Overlay(ctx: Context) : View(ctx) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val arc = RectF()
        private var running = false
        private val tick = object : Runnable {
            override fun run() { invalidate(); if (running) postDelayed(this, 50) }
        }

        fun run() { if (!running) { running = true; post(tick) } }
        fun pause() { running = false; removeCallbacks(tick) }

        override fun onDraw(c: Canvas) {
            // ligger telefonen fladt, er kameraet ikke til nogen nytte: det slukkes, og tændes igen på højkant
            val t = SystemClock.elapsedRealtime()
            if (Hub.flat) { if (device != null || starting) { stopCamera(); watch.stopped() } }
            else if (wanted && texture.isAvailable && a.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                if (watch.stalled(t)) fail("Kameraet kunne ikke vise billedet")
                else if (!hopeless && watch.mayStart(t)) startCamera()
            }
            autoSaved()
            hold()
            paint(c, width, height, Hub.freshHeading(), Hub.tiltDeg, System.currentTimeMillis())
        }

        /** Telefonen ligger fladt: en stor kompasrose i stedet for kamerabilledet. Telefonens top er op på skærmen. */
        private fun paintFlat(c: Canvas, w: Int, h: Int, hdg: Double, now: Long) {
            val cx = w / 2f
            c.drawColor(0xFF0E0F12.toInt())
            val lay = FlatLayout(w.toFloat(), h.toFloat(), d)
            val rr = lay.radius
            val cy = lay.centerY
            val rose = Hub.rose
            val range = rose.range()
            // rosen drejes, så verden står stille, mens telefonen drejer: nord peger mod nord
            c.save(); c.rotate((-hdg).toFloat(), cx, cy)
            p.style = Paint.Style.FILL; p.color = 0xFF1B1D22.toInt()
            c.drawCircle(cx, cy, rr, p)
            arc.set(cx - rr, cy - rr, cx + rr, cy + rr)
            for (i in 0 until rose.bins) {
                val lvl = rose.at(i) ?: continue
                p.color = Hub.colorFor(lvl, range)
                c.drawArc(arc, (rose.center(i) - rose.binDeg / 2.0 - 90).toFloat(), rose.binDeg.toFloat(), true, p)
            }
            p.style = Paint.Style.STROKE; p.color = 0x80FFFFFF.toInt(); p.strokeWidth = 1.5f * d
            c.drawCircle(cx, cy, rr, p)
            ring(c, rose.turn(), cx, cy, rr + 5 * d, 3 * d)
            p.style = Paint.Style.FILL; p.color = 0xFFFFFFFF.toInt(); p.textSize = 14 * d; p.textAlign = Paint.Align.CENTER
            for ((k, name) in listOf(0 to "N", 90 to "Ø", 180 to "S", 270 to "V")) {
                c.save(); c.rotate(k.toFloat(), cx, cy); c.drawText(name, cx, cy - rr + 18 * d, p); c.restore()
            }
            rose.best()?.let { best ->
                c.save(); c.rotate(best.toFloat(), cx, cy)
                c.drawText("▼", cx, cy - rr - 4 * d, p)
                c.restore()
            }
            c.restore()
            // pilen: dér peger telefonens top (og antennen)
            p.style = Paint.Style.STROKE; p.strokeWidth = 3 * d; p.color = 0xFFFFFFFF.toInt()
            c.drawLine(cx, cy, cx, cy - rr + 2 * d, p)
            p.style = Paint.Style.FILL
            val tip = Path().apply { moveTo(cx, cy - rr - 2 * d); lineTo(cx - 9 * d, cy - rr + 18 * d); lineTo(cx + 9 * d, cy - rr + 18 * d); close() }
            c.drawPath(tip, p)
            p.textSize = 13 * d
            val lobe = rose.lobe()
            c.drawText(if (lobe != null) "▼ ${strongest(lobe)}" else "drej langsomt rundt – ét udbrud pr. retning", cx, lay.bestY, p)
            val turnY = lay.turnY
            if (turnY != null) round(rose.turn())?.let { p.color = 0xB0FFFFFF.toInt(); c.drawText(it, cx, turnY, p); p.color = 0xFFFFFFFF.toInt() }
            val n = Hub.bearings.size
            p.textAlign = Paint.Align.LEFT; p.color = 0xB0FFFFFF.toInt(); p.textSize = 12 * d
            c.drawText(if (n == 0) "ingen pejlinger gemt" else "$n ${if (n == 1) "pejling" else "pejlinger"} gemt", 12 * d, lay.countY, p)
            header(c, w, hdg, now, if (Hub.compassPoor) "Kompasset viser forkert – tryk Kalibrér kompas" else null, "telefonens top")
        }

        /** Feltet øverst: retningen, målets styrke lige nu, og en eventuel advarsel under. */
        private fun header(c: Canvas, w: Int, hdg: Double, now: Long, warn: String?, what: String, fixed: String? = null) {
            p.style = Paint.Style.FILL; p.color = 0xB0000000.toInt()
            c.drawRect(0f, 0f, w.toFloat(), 58 * d, p)
            p.color = 0xFFFFFFFF.toInt(); p.textAlign = Paint.Align.LEFT; p.textSize = 22 * d
            c.drawText("${hdg.roundToInt()}° ${Bearing.name(hdg)}", 12 * d, 26 * d, p)
            val x = 12 * d + p.measureText("${hdg.roundToInt()}° ${Bearing.name(hdg)}  ")
            p.textSize = 12 * d; p.color = 0xB0FFFFFF.toInt()
            c.drawText(what, x, 26 * d, p)
            p.color = 0xFFFFFFFF.toInt()
            val last = Hub.lastBurst
            val status = fixed ?: when {
                !Hub.running -> "Tryk Start for at måle"
                Hub.targetHz == null -> "Vælg et mål: tryk Mål, eller i vandfaldet"
                last != null && now - last.t < 6_000 -> String.format(Ui.DA, "%+d dB over støjen · niveau %s dB", (last.snr ?: 0.0).roundToInt(), Ui.f1(last.level ?: 0.0))
                last != null -> "intet hørt i ${(now - last.t) / 1000} s"
                else -> "målet er ikke hørt endnu"
            }
            p.textSize = 14 * d
            c.drawText(status, 12 * d, 47 * d, p)
            if (warn != null) {
                p.color = Ui.YELLOW; c.drawRect(0f, 58 * d, w.toFloat(), 84 * d, p)
                p.color = 0xFF202124.toInt(); p.textSize = 13 * d; p.textAlign = Paint.Align.CENTER
                c.drawText(warn, w / 2f, 75 * d, p)
                p.textAlign = Paint.Align.LEFT
            }
        }

        /** Med [shot] tegnes et foto af en tidligere måling: dens rose og tekst, altid som kamerabillede og uden advarsler. */
        fun paint(c: Canvas, w: Int, h: Int, hdg: Double?, tilt: Double, now: Long, shot: Shot? = null) {
            val cx = w / 2f; val cy = h / 2f
            val err = error
            if (err != null) { center(c, err, cx, cy); return }
            if (hdg == null) { center(c, if (compass.available) "Venter på kompasset …" else "Telefonen har intet kompas", cx, cy); return }
            if (Hub.flat && shot == null) { paintFlat(c, w, h, hdg, now); return }
            val tw = if (texW > 0) texW.toFloat() else w.toFloat()
            val th = if (texH > 0) texH.toFloat() else h.toFloat()
            // horisonten flytter sig ned i billedet, når telefonen vipper op
            val warn = if (shot != null) null else when {
                Hub.compassPoor -> "Kompasset viser forkert – tryk Kalibrér kompas"
                kotlin.math.abs(tilt) > 35 -> "Hold telefonen på højkant – eller læg den helt fladt"
                else -> null
            }
            // feltet øverst (og advarslen under det) må ikke dække båndet og dets tal
            val panel = 58 * d + (if (warn != null) 26 * d else 0f)
            val hy = (cy + (Bearing.offsetPx(tilt.coerceIn(-60.0, 60.0), vfov, th) ?: 0f))
                .coerceIn(minOf(panel + 52 * d, 0.6f * h), maxOf(h - 250 * d, 0.6f * h))

            // farvebåndet: hver retning, der er målt i, farvet efter styrken dér
            val rose = shot?.rose ?: Hub.rose
            val range = rose.range()
            p.style = Paint.Style.FILL
            for (i in 0 until rose.bins) {
                val lvl = rose.at(i) ?: continue
                val l = Bearing.offsetPx(Bearing.diff(rose.center(i) - rose.binDeg / 2.0, hdg), hfov, tw) ?: continue
                val r = Bearing.offsetPx(Bearing.diff(rose.center(i) + rose.binDeg / 2.0, hdg), hfov, tw) ?: continue
                p.color = (Hub.colorFor(lvl, range) and 0x00FFFFFF) or 0xA0000000.toInt()
                c.drawRect(cx + l, hy - 24 * d, cx + r, hy + 24 * d, p)
            }
            // horisontlinje med et mærke for hver 10° og tal for hver 30°
            p.color = 0xCCFFFFFF.toInt(); p.strokeWidth = 1.5f * d
            c.drawLine(0f, hy, w.toFloat(), hy, p)
            p.textSize = 12 * d; p.textAlign = Paint.Align.CENTER
            for (k in 0 until 36) {
                val deg = k * 10.0
                val off = Bearing.offsetPx(Bearing.diff(deg, hdg), hfov, tw) ?: continue
                val x = cx + off
                if (x < 0 || x > w) continue
                val big = k % 3 == 0
                c.drawLine(x, hy - (if (big) 10 else 5) * d, x, hy + (if (big) 10 else 5) * d, p)
                if (big) c.drawText(if (k % 9 == 0) Bearing.name(deg) else "${deg.toInt()}°", x, hy - 30 * d, p)
            }
            // den stærkeste retning: et mærke i billedet, eller en pil mod den side, den ligger til
            rose.best()?.let { best ->
                val dlt = Bearing.diff(best, hdg)
                val off = Bearing.offsetPx(dlt, hfov, tw)
                p.color = 0xFFFFFFFF.toInt(); p.textSize = 15 * d
                if (off != null && cx + off > 20 * d && cx + off < w - 20 * d) c.drawText("▲ stærkest", cx + off, hy + 44 * d, p)
                else {
                    p.textAlign = if (dlt < 0) Paint.Align.LEFT else Paint.Align.RIGHT
                    c.drawText(if (dlt < 0) "◀ stærkest ${best.roundToInt()}°" else "stærkest ${best.roundToInt()}° ▶", if (dlt < 0) 10 * d else w - 10 * d, hy + 44 * d, p)
                    p.textAlign = Paint.Align.CENTER
                }
            }
            // sigtekorn midt i billedet: dér peger antennen
            p.style = Paint.Style.STROKE; p.color = 0xFFFFFFFF.toInt(); p.strokeWidth = 2 * d
            c.drawCircle(cx, cy, 16 * d, p)
            c.drawLine(cx - 30 * d, cy, cx - 16 * d, cy, p); c.drawLine(cx + 16 * d, cy, cx + 30 * d, cy, p)
            c.drawLine(cx, cy - 30 * d, cx, cy - 16 * d, p); c.drawLine(cx, cy + 16 * d, cx, cy + 30 * d, p)

            header(c, w, hdg, now, warn, "kameraet", shot?.status)

            // nederst til venstre: hele rosen set oppefra, nord op
            val rx = 58 * d; val ry = h - 122 * d; val rr = 44 * d
            p.style = Paint.Style.FILL; p.color = 0xB0000000.toInt()
            c.drawCircle(rx, ry, rr + 6 * d, p)
            arc.set(rx - rr, ry - rr, rx + rr, ry + rr)
            for (i in 0 until rose.bins) {
                val lvl = rose.at(i) ?: continue
                p.color = Hub.colorFor(lvl, range)
                c.drawArc(arc, (rose.center(i) - rose.binDeg / 2.0 - 90).toFloat(), rose.binDeg.toFloat(), true, p)
            }
            p.style = Paint.Style.STROKE; p.color = 0x80FFFFFF.toInt(); p.strokeWidth = 1 * d
            c.drawCircle(rx, ry, rr, p)
            ring(c, rose.turn(), rx, ry, rr + 3 * d, 2.5f * d)
            round(rose.turn())?.let {
                p.style = Paint.Style.FILL; p.color = 0xFFFFFFFF.toInt(); p.textSize = 13 * d; p.textAlign = Paint.Align.LEFT
                c.drawText(it, rx + rr + 14 * d, ry + 5 * d, p)
            }
            // nålen: dér peger telefonen
            p.color = 0xFFFFFFFF.toInt(); p.strokeWidth = 2.5f * d
            c.drawLine(rx, ry, rx + (rr * sin(Math.toRadians(hdg))).toFloat(), ry - (rr * cos(Math.toRadians(hdg))).toFloat(), p)
            p.style = Paint.Style.FILL; p.textSize = 11 * d; p.textAlign = Paint.Align.CENTER
            c.drawText("N", rx, ry - rr - 9 * d, p)
            val n = Hub.bearings.size
            p.textAlign = Paint.Align.RIGHT; p.textSize = 13 * d
            c.drawText(if (n == 0) "ingen pejlinger gemt" else "$n ${if (n == 1) "pejling" else "pejlinger"} gemt", w - 12 * d, h - 78 * d, p)
            p.textAlign = Paint.Align.LEFT
        }

        /**
         * Ringen om rosen: et stykke for hver retning, der er målt nok i. Den lukker sig, mens der drejes, og er
         * hel, når omgangen er færdig.
         */
        private fun ring(c: Canvas, turn: Turn?, x: Float, y: Float, r: Float, width: Float) {
            turn ?: return
            p.style = Paint.Style.STROKE; p.strokeWidth = width; p.color = if (turn.done) Ui.GREEN else Ui.ACCENT
            arc.set(x - r, y - r, x + r, y + r)
            val step = 360f / turn.of
            for (k in 0 until turn.of) if (turn.has(k)) c.drawArc(arc, k * step - step / 2 - 90, step, false, p)
            p.style = Paint.Style.FILL
        }

        /** Teksten om omgangen: hvor der mangler at blive målt, eller at den er færdig. Null, før der er målt. */
        private fun round(turn: Turn?): String? {
            if (turn == null || turn.count == 0) return null
            val (mid, width) = turn.missing ?: return "omgangen er færdig"
            return "omgangen mangler $width° omkring ${Bearing.name(mid)}"
        }

        private fun center(c: Canvas, text: String, cx: Float, cy: Float) {
            p.style = Paint.Style.FILL; p.color = 0xFFFFFFFF.toInt(); p.textSize = 15 * d; p.textAlign = Paint.Align.CENTER
            c.drawText(text, cx, cy, p)
            p.textAlign = Paint.Align.LEFT
        }
    }
}
