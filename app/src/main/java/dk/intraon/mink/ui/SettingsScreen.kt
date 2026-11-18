package dk.intraon.mink.ui

import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import dk.intraon.mink.BuildConfig
import dk.intraon.mink.R
import dk.intraon.mink.core.Hub
import dk.intraon.mink.core.Settings
import dk.intraon.mink.dsp.BurstDetector
import dk.intraon.mink.measure.MeasureService
import dk.intraon.mink.measure.Uploader
import dk.intraon.mink.radio.RtlTcpClient
import dk.intraon.mink.trip.JsonlLog
import dk.intraon.mink.update.Releases
import dk.intraon.mink.waterfall.FigureExport
import java.util.Locale
import kotlin.math.abs

/**
 * Indstillinger som fuldskærms-view oven på hovedskærmen, ordnet efter hvor ofte de bruges: kanal og bånd, målet,
 * modtageren, udstyr, det der gemmes, og til sidst finjusteringen, som er foldet sammen. Et mærke ved overskriften
 * eller feltet siger, om en ændring gælder med det samme eller fra næste Start. Gem ligger fast i bunden.
 */
class SettingsScreen(private val a: MainActivity, private val s: Settings, private val onClose: () -> Unit) {

    private val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(a.dp(16), a.dp(12), a.dp(16), a.dp(24)) }
    /** Dér, det næste felt lægges: selve siden, en spalte i en række med to felter, eller den foldede finjustering. */
    private var box: LinearLayout = col

    /** Hvem der står bag, hvilken version det er, og hvor opdateringer og kildekode kommer fra. */
    private fun about() {
        heading("Om MINK")
        val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, a.dp(6), 0, a.dp(6)) }
        row.addView(ImageView(a).apply { setImageResource(R.drawable.intraon); contentDescription = "Intraon" }, LinearLayout.LayoutParams(a.dp(48), a.dp(48)))
        row.addView(TextView(a).apply {
            textSize = 14f; setTextColor(Ui.TEXT); setPadding(a.dp(12), 0, 0, 0)
            text = "MINK · Mesh-INterferens-Kortlægger\nVersion ${BuildConfig.VERSION_NAME}\nLavet af Intraon · intraon.dk"
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        col.addView(row, MATCH_PARENT, WRAP_CONTENT)
        note("Åben kildekode under MIT-licensen. Kortet er © OpenStreetMap-bidragyderne. Tuner: ${Hub.tuner}.\nLog: ${JsonlLog.DIR}/${JsonlLog.PREFIX}<tid>.jsonl.txt (+ .iq.wav) · billeder: ${FigureExport.DIR}")
        // gælder med det samme, uden Gem
        toggle("Se selv efter nye versioner", a.settings.autoUpdate).setOnCheckedChangeListener { _, on -> a.settings.autoUpdate = on }
        note("Når du åbner appen, spørger den GitHub, om der er udgivet en nyere version. Det sker højst to gange i døgnet og aldrig mens der måles. Får den ikke svar, prøver den igen tidligst en time senere. Der sendes ikke andet end forespørgslen.")
        action("Søg efter opdatering nu", Ui.BUTTON) { a.updater.check(quiet = false) }
        action("Batteri-indstillinger for appen", Ui.BUTTON) { a.openAppSettings() }
        if (BuildConfig.REPO.isNotEmpty()) action("Kildekode på GitHub", Ui.BUTTON) { a.updater.openInBrowser(Releases.pageUrl(BuildConfig.REPO)) }
        action("intraon.dk", Ui.BUTTON) { a.updater.openInBrowser("https://intraon.dk") }
    }

    private val START = "fra næste Start"
    private val NOW = "med det samme"

    /** Et lille mærke, der siger, hvornår en ændring gælder: [NOW] er grønt, [START] gråt. */
    private fun mark(applies: String) = TextView(a).apply {
        text = applies; textSize = 11f; setPadding(a.dp(8), a.dp(1), a.dp(8), a.dp(1))
        setTextColor(if (applies == NOW) 0xFF9BE7C4.toInt() else 0xFFC4C9CF.toInt())
        background = GradientDrawable().apply { cornerRadius = a.dp(10).toFloat(); setStroke(a.dp(1), if (applies == NOW) Ui.GREEN else 0xFF5F6368.toInt()) }
    }

    /** En tekst til venstre og, hvis [applies] er sat, mærket for hvornår ændringen gælder til højre. */
    private fun labelled(label: TextView, applies: String?) {
        if (applies == null) { box.addView(label); return }
        val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(label, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        row.addView(mark(applies))
        box.addView(row, MATCH_PARENT, WRAP_CONTENT)
    }

    private fun heading(text: String, applies: String? = null) =
        labelled(TextView(a).apply { this.text = text; textSize = 18f; setTextColor(Ui.ACCENT); setPadding(0, a.dp(18), 0, a.dp(4)) }, applies)

    private fun note(text: String) = TextView(a).apply {
        this.text = text; textSize = 13f; setTextColor(Ui.DIM); setPadding(0, a.dp(2), 0, a.dp(6))
    }.also { box.addView(it) }

    private fun edit(label: String, value: String, type: Int, applies: String? = null): EditText {
        labelled(TextView(a).apply { text = label; textSize = 14f; setTextColor(Ui.TEXT); setPadding(0, a.dp(8), 0, 0) }, applies)
        val e = EditText(a).apply {
            setText(value); inputType = type; textSize = 18f; setTextColor(Ui.TEXT); setSingleLine(true)
        }
        box.addView(e, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        return e
    }

    /** To felter ved siden af hinanden: [left] og [right] lægger hver sit felt i sin spalte. */
    private fun <A, B> pair(left: () -> A, right: () -> B): Pair<A, B> {
        val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
        val l = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        val r = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        row.addView(l, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = a.dp(8) })
        row.addView(r, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginStart = a.dp(8) })
        val outer = box
        outer.addView(row, MATCH_PARENT, WRAP_CONTENT)
        box = l; val first = left()
        box = r; val second = right()
        box = outer
        return first to second
    }

    private fun toggle(label: String, on: Boolean): Switch {
        val sw = Switch(a).apply { text = label; isChecked = on; textSize = 16f; setTextColor(Ui.TEXT); setPadding(0, a.dp(12), 0, a.dp(12)) }
        box.addView(sw, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        return sw
    }

    private fun action(label: String, color: Int, onClick: () -> Unit): View {
        val b = a.button(label, color) { onClick() }.apply { textSize = 16f }
        box.addView(b, LinearLayout.LayoutParams(MATCH_PARENT, a.dp(56)).apply { setMargins(0, a.dp(8), 0, 0) })
        return b
    }

    fun build(): View {
        val dec = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        val int = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
        val txt = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS

        col.addView(TextView(a).apply { text = "Indstillinger"; textSize = 24f; setTextColor(Ui.TEXT) })

        heading("Kanal og bånd", START)
        val (ch, chBw) = pair({ edit("Kanal (MHz)", s.channelMhz.toString(), dec) }, { edit("Bredde (kHz)", s.channelKhz.toString(), dec) })
        note("Din egen LoRa-kanal, fx MeshCore EU/UK narrow: 869,618 MHz og 62,5 kHz. Den vises som et grønt felt i vandfaldet. Skriv 0, hvis du ikke har en. Flytter du kanalen uden for båndet, lægges båndet omkring den.")
        val (lo, hi) = pair({ edit("Bånd fra (MHz)", s.bandLoMhz.toString(), dec) }, { edit("Bånd til (MHz)", s.bandHiMhz.toString(), dec) })
        val bandView = BandView(a)
        col.addView(bandView, LinearLayout.LayoutParams(MATCH_PARENT, a.dp(60)).apply { topMargin = a.dp(10) })
        val bandNote = note("")
        val widest = action("Brug 880 kHz omkring din kanal", Ui.BUTTON) {}

        heading("Målet", START)
        val tgt = edit("Målets frekvens (MHz)", s.targetMhz?.toString() ?: "", dec)
        note("Den sender, som tal, pil, bip og kort følger. Den vælges nemmest ved at trykke på signalet i vandfaldet. Et tomt felt betyder, at der ikke er valgt et mål. Andre sendere gemmes stadig i loggen, og målet skifter aldrig af sig selv.")
        val bw = edit("Signalets bredde (kHz)", s.signalKhz.toString(), dec)
        note("Det, hver tur starter med. Når målet er målt nogle gange, stiller appen sig selv ind på dets bredde resten af turen.")

        heading("Modtager")
        val gain = edit("Gain (dB)", s.startGain.toString(), dec, NOW)
        note("Appen skruer aldrig højere op end dette, så tal og vandfald betyder det samme på hele turen. − og + på hovedskærmen flytter det samme valg.")
        val auto = toggle("Skru ned ved mætning", s.autoGain)
        note("Helt tæt på senderen skruer appen ned og tilbage igen. Bippet følger signalet og ændrer sig ikke af det. Gælder med det samme.")
        val ppm = edit("Frekvensrettelse (ppm)", s.ppm.toString(), int, NOW)
        note("Modtagerens frekvensfejl. Du behøver sjældent røre den: når appen ser trafik på din kanal, tilbyder den selv at rette den. Én ppm flytter alt ca. 0,9 kHz ved 869 MHz.")

        heading("Udstyr, lyd og skærm")
        val dir = toggle("Jeg har en retningsantenne", s.directional)
        note("Så kommer fanen Kamera frem, og appen gemmer, hvilken vej telefonen peger, på hver måling. Hold telefonen i samme retning som antennen. Det virker også med en almindelig antenne holdt ind mod brystet: kroppen skygger bagud.")
        val beep = toggle("Bip ved hver måling", s.beep)
        col.addView(TextView(a).apply { text = "Bip-lydstyrke"; textSize = 14f; setTextColor(Ui.TEXT) })
        val vol = SeekBar(a).apply { max = 100; progress = s.beepVolume }
        col.addView(vol, LinearLayout.LayoutParams(MATCH_PARENT, a.dp(48)))
        val keep = toggle("Hold skærmen tændt", s.keepScreenOn)

        heading("Det, der gemmes og sendes", START)
        val iq = toggle("Gem bølgeformen af hver måling", s.saveIq)
        note("Et smalt udsnit af det modtagne omkring hvert signal gemmes i en WAV-fil ved siden af loggen, så signalet kan undersøges bagefter. Ca. 30 kB pr. måling på 0,4 s. Lange udsendelser gemmes kun det første sekund.")
        val hook = edit("Webhook-adresse (tom = intet sendes)", s.webhook, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        note("Appen kan sende målingerne til din egen server undervejs, fx Home Assistant. Uden en adresse ligger loggen kun på telefonen.")
        action("Test webhook", Ui.BUTTON) {
            val url = hook.text.toString().trim()
            if (url.isBlank()) a.toast("Der er ingen webhook at teste") else Thread {
                val code = Uploader.post(url, """{"type":"test"}""")
                a.runOnUiThread { a.toast(if (code > 0) "Webhook svarede HTTP $code" else "Ingen forbindelse (${Uploader.lastError})") }
            }.start()
        }

        // Finjustering: felter, der sjældent røres, ligger foldet sammen bag én række
        heading("Finjustering", START)
        val fine = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        val fold = a.button("Vis tærskel, korteste signal og frekvenser, der ignoreres", Ui.PANEL) {}.apply { textSize = 15f }
        fold.setOnClickListener {
            val open = fine.visibility != View.VISIBLE
            fine.visibility = if (open) View.VISIBLE else View.GONE
            fold.text = if (open) "Skjul finjusteringen" else "Vis tærskel, korteste signal og frekvenser, der ignoreres"
        }
        col.addView(fold, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = a.dp(4) })
        col.addView(fine, MATCH_PARENT, WRAP_CONTENT)
        box = fine
        val on = edit("Tærskel (dB over støjen)", s.onSnrDb.toString(), dec)
        note("Så meget skal et signal ligge over støjen for at tælle. Lavere finder svagere signaler, men også mere støj.")
        val minMs = edit("Korteste signal (ms)", s.minBurstMs.toString(), InputType.TYPE_CLASS_NUMBER)
        note("Kortere udbrud end dette regnes som støj.")
        val ign = edit("Frekvenser, der ignoreres (MHz)", s.ignore, txt)
        note("Fx 869.480-869.485; flere adskilles med semikolon. Bredbåndet trafik som LoRa tæller i forvejen ikke.")
        box = col

        about()

        val save = a.button("Gem", Ui.GREEN) {}.apply { textSize = 16f }
        // båndet tjekkes, mens der skrives: tegningen og teksten under felterne følger med, og Gem er grå, til det passer
        fun showBand() {
            val l = Settings.parse(lo.text.toString()); val h = Settings.parse(hi.text.toString())
            val c = Settings.parse(ch.text.toString()) ?: 0.0; val cw = Settings.parse(chBw.text.toString()) ?: 0.0
            val problem = SettingsCheck.band(l, h)
            val picture = BandPicture.of(l, h, c, cw)
            bandView.visibility = if (picture == null) View.GONE else View.VISIBLE
            bandView.show(picture, l?.let { String.format(Ui.DA, "%.3f", it) } ?: "", h?.let { String.format(Ui.DA, "%.3f", it) } ?: "", "modtageren ser ${SettingsCheck.khz(SettingsCheck.MAX_BAND_KHZ)} kHz")
            bandNote.text = problem ?: "Båndet er ${SettingsCheck.khz(SettingsCheck.widthKhz(l!!, h!!))} kHz bredt. Appen finder smalle signaler hvor som helst i det. Du kan kigge ved siden af båndet ved at trække til siden i vandfaldet."
            bandNote.setTextColor(if (problem == null) Ui.DIM else 0xFFFF8A8E.toInt())
            widest.visibility = if (picture?.tooWide == true) View.VISIBLE else View.GONE
            (widest as TextView).text = if (c > 0) "Brug 880 kHz omkring din kanal" else "Brug 880 kHz midt i båndet"
            save.isEnabled = problem == null; save.alpha = if (problem == null) 1f else 0.4f
        }
        widest.setOnClickListener {
            val l = Settings.parse(lo.text.toString()); val h = Settings.parse(hi.text.toString())
            val c = Settings.parse(ch.text.toString()) ?: 0.0
            val (from, to) = SettingsCheck.widestAround(if (c > 0) c else if (l != null && h != null) (l + h) / 2 else return@setOnClickListener)
            lo.setText(String.format(Locale.US, "%.3f", from)); hi.setText(String.format(Locale.US, "%.3f", to))
        }
        val watch = object : TextWatcher {
            override fun afterTextChanged(e: Editable?) = showBand()
            override fun beforeTextChanged(t: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(t: CharSequence?, start: Int, before: Int, count: Int) {}
        }
        for (e in listOf(lo, hi, ch, chBw)) e.addTextChangedListener(watch)
        showBand()

        save.setOnClickListener {
            val l = Settings.parse(lo.text.toString())
            val h = Settings.parse(hi.text.toString())
            val w = Settings.parse(bw.text.toString())
            val o = Settings.parse(on.text.toString())
            val g = Settings.parse(gain.text.toString())
            val c = Settings.parse(ch.text.toString()) ?: 0.0
            val cw = Settings.parse(chBw.text.toString()) ?: 0.0
            // ligger kanalen uden for det indtastede bånd, flyttes båndet, så kanalen er med (370 kHz: tuneren kan stå uden for)
            val moved = c > 0 && l != null && h != null && (c - cw / 2000 < l || c + cw / 2000 > h)
            val l2 = if (moved) Math.round((c - 0.185) * 1000) / 1000.0 else l
            val h2 = if (moved) Math.round((c + 0.185) * 1000) / 1000.0 else h
            when {
                l == null || h == null || SettingsCheck.band(l, h) != null -> a.toast(SettingsCheck.band(l, h) ?: "")
                c != 0.0 && (c < 24 || c > 1766) -> a.toast("Kanalens frekvens skal ligge mellem 24 og 1766 MHz (eller være 0)")
                c != 0.0 && (cw < 1 || cw > 500) -> a.toast("Kanalens bredde skal være mellem 1 og 500 kHz")
                w == null || w < 1.5 || w > 100 -> a.toast("Signalets bredde skal være mellem 1,5 og 100 kHz")
                o == null || o < 2 || o > 40 -> a.toast("Tærsklen skal være mellem 2 og 40 dB")
                else -> {
                    s.bandLoMhz = l2!!; s.bandHiMhz = h2!!; s.signalKhz = w; s.onSnrDb = o
                    s.channelMhz = c; s.channelKhz = cw; a.applyChannel()
                    s.targetMhz = Settings.parse(tgt.text.toString())
                    if (!Hub.running) Hub.targetHz = s.targetMhz?.times(1e6)
                    s.minBurstMs = minMs.text.toString().trim().toIntOrNull()?.coerceIn(10, 5000) ?: 40
                    s.ignore = ign.text.toString()
                    val oldPpm = s.ppm
                    s.ppm = ppm.text.toString().trim().toIntOrNull()?.coerceIn(-200, 200) ?: s.ppm
                    if (s.ppm != oldPpm) {
                        if (Hub.running) MeasureService.sendPpm(a, s.ppm)
                        else {
                            // det gemte mål står dér, hvor signalet lå med den gamle korrektion: flyt det med
                            s.targetMhz = s.targetMhz?.let { Math.round(it * (1 + (s.ppm - oldPpm) / 1e6) * 10_000.0) / 10_000.0 }
                            Hub.targetHz = s.targetMhz?.times(1e6)
                        }
                    }
                    if (g != null) s.startGain = RtlTcpClient.GAINS.minByOrNull { abs(it - g) } ?: 29.7
                    s.autoGain = auto.isChecked; s.beep = beep.isChecked; s.beepVolume = vol.progress
                    s.keepScreenOn = keep.isChecked; s.saveIq = iq.isChecked; s.webhook = hook.text.toString()
                    s.directional = dir.isChecked; a.applyDirectional()
                    MeasureService.sendGain(a, s.startGain, s.autoGain)
                    val tune = BurstDetector.tuneFor(l2 * 1e6, h2 * 1e6)
                    a.toast((if (moved) "Båndet er flyttet til ${l2}–${h2} MHz, så kanalen er med. " else "Gemt. ") + "Tuneren stilles på ${Ui.mhz(tune)} MHz ved næste Start")
                    onClose()
                }
            }
        }

        // Gem og Annullér ligger fast under siden, så de kan nås uden at rulle
        val bar = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(Ui.PANEL); setPadding(a.dp(16), a.dp(10), a.dp(16), a.dp(12)) }
        bar.addView(a.button("Annullér", Ui.BUTTON) { onClose() }.apply { textSize = 16f }, LinearLayout.LayoutParams(0, a.dp(56), 1f).apply { marginEnd = a.dp(4) })
        bar.addView(save, LinearLayout.LayoutParams(0, a.dp(56), 1f).apply { marginStart = a.dp(4) })
        return LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Ui.BG); isClickable = true
            addView(ScrollView(a).apply { isFillViewport = true; addView(col, MATCH_PARENT, WRAP_CONTENT) }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            addView(bar, MATCH_PARENT, WRAP_CONTENT)
        }
    }
}
