package dk.intraon.mink.ui

import android.text.InputType
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
import kotlin.math.abs

/** Indstillinger som fuldskærms-view oven på hovedskærmen. Ændringer gælder fra næste Start. */
class SettingsScreen(private val a: MainActivity, private val s: Settings, private val onClose: () -> Unit) {

    private val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL; setPadding(a.dp(16), a.dp(12), a.dp(16), a.dp(24)) }

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
        // gælder med det samme; kontakten står under Gem-knappen
        toggle("Se selv efter nye versioner", a.settings.autoUpdate).setOnCheckedChangeListener { _, on -> a.settings.autoUpdate = on }
        note("Når du åbner appen, spørger den GitHub, om der er udgivet en nyere version. Det sker højst to gange i døgnet og aldrig mens der måles. Får den ikke svar, prøver den igen tidligst en time senere. Der sendes ikke andet end forespørgslen.")
        action("Søg efter opdatering nu", Ui.BUTTON) { a.updater.check(quiet = false) }
        if (BuildConfig.REPO.isNotEmpty()) action("Kildekode på GitHub", Ui.BUTTON) { a.updater.openInBrowser(Releases.pageUrl(BuildConfig.REPO)) }
        action("intraon.dk", Ui.BUTTON) { a.updater.openInBrowser("https://intraon.dk") }
    }

    private fun heading(text: String) = col.addView(TextView(a).apply {
        this.text = text; textSize = 18f; setTextColor(Ui.ACCENT); setPadding(0, a.dp(18), 0, a.dp(4))
    })

    private fun note(text: String) = col.addView(TextView(a).apply {
        this.text = text; textSize = 13f; setTextColor(Ui.DIM); setPadding(0, a.dp(2), 0, a.dp(6))
    })

    private fun edit(label: String, value: String, type: Int): EditText {
        col.addView(TextView(a).apply { text = label; textSize = 14f; setTextColor(Ui.TEXT); setPadding(0, a.dp(8), 0, 0) })
        val e = EditText(a).apply {
            setText(value); inputType = type; textSize = 18f; setTextColor(Ui.TEXT); setSingleLine(true)
        }
        col.addView(e, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        return e
    }

    private fun toggle(label: String, on: Boolean): Switch {
        val sw = Switch(a).apply { text = label; isChecked = on; textSize = 16f; setTextColor(Ui.TEXT); setPadding(0, a.dp(12), 0, a.dp(12)) }
        col.addView(sw, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        return sw
    }

    private fun action(label: String, color: Int, onClick: () -> Unit) {
        col.addView(
            a.button(label, color) { onClick() }.apply { textSize = 16f },
            LinearLayout.LayoutParams(MATCH_PARENT, a.dp(56)).apply { setMargins(0, a.dp(8), 0, 0) },
        )
    }

    fun build(): View {
        val dec = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        val int = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
        val txt = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS

        col.addView(TextView(a).apply { text = "Indstillinger"; textSize = 24f; setTextColor(Ui.TEXT) })
        note("Ændringer gælder fra næste Start. Gain og \"Skru ned ved mætning\" gælder med det samme.")

        heading("Din kanal")
        val ch = edit("Kanalens frekvens (MHz, 0 = ingen)", s.channelMhz.toString(), dec)
        val chBw = edit("Kanalens bredde (kHz)", s.channelKhz.toString(), dec)
        note("Din egen LoRa-kanal, fx MeshCore EU/UK narrow: 869,618 MHz og 62,5 kHz. Den vises som et grønt felt i vandfaldet, så du kan se, hvilke signaler der ligger inde i kanalen. Flytter du kanalen uden for båndet nedenfor, lægges båndet omkring den.")

        heading("Det der ledes efter")
        val lo = edit("Bånd fra (MHz)", s.bandLoMhz.toString(), dec)
        val hi = edit("Bånd til (MHz)", s.bandHiMhz.toString(), dec)
        val bw = edit("Signalets bredde (kHz)", s.signalKhz.toString(), dec)
        note("Bredden følger selv målet: når målet er målt nogle gange, stiller appen sig ind på dets bredde resten af turen. Tallet her er det, hver tur starter med.")
        val tgt = edit("Mål-frekvens (MHz, tom = intet mål)", s.targetMhz?.toString() ?: "", dec)
        note("Målet vælges nemmest ved at trykke på signalet i vandfaldet. Kun målet styrer tal, pil, bip og kort; andre sendere gemmes stadig i loggen. Målet skifter aldrig af sig selv.")
        note("Appen finder smalle signaler hvor som helst i båndet (højst 880 kHz). Bredbåndet trafik som LoRa tæller ikke.")
        val on = edit("Tærskel (dB over støjen)", s.onSnrDb.toString(), dec)
        val minMs = edit("Korteste signal (ms)", s.minBurstMs.toString(), InputType.TYPE_CLASS_NUMBER)
        val ign = edit("Ignorér frekvenser (MHz, fx 869.480-869.485; …)", s.ignore, txt)

        heading("Modtager")
        val ppm = edit("PPM-korrektion", s.ppm.toString(), int)
        note("Donglens frekvensfejl. Du behøver sjældent røre den: når appen ser trafik på din kanal, tilbyder den selv at rette den. Én ppm flytter alt ca. 0,9 kHz ved 869 MHz. En ændring her gælder med det samme.")
        val gain = edit("Gain (dB)", s.startGain.toString(), dec)
        val auto = toggle("Skru ned ved mætning", s.autoGain)
        note("Appen skruer aldrig højere op end dette gain, så tal og vandfald betyder det samme overalt på turen. Helt tæt på senderen skruer den ned og tilbage igen; bippet følger signalet og ændrer sig ikke af det. − og + på hovedskærmen flytter valget med det samme.")

        heading("Retningsantenne")
        val dir = toggle("Jeg har en retningsantenne", s.directional)
        note("Så gemmer appen, hvilken vej telefonen peger, på hver måling, og fanen Kamera kommer frem. Hold telefonen i samme retning som antennen, som når du tager et billede. Det virker også med en almindelig antenne holdt ind mod brystet: kroppen skygger bagud.")

        heading("Lyd og skærm")
        val beep = toggle("Bip ved hver måling", s.beep)
        col.addView(TextView(a).apply { text = "Bip-lydstyrke"; textSize = 14f; setTextColor(Ui.TEXT) })
        val vol = SeekBar(a).apply { max = 100; progress = s.beepVolume }
        col.addView(vol, LinearLayout.LayoutParams(MATCH_PARENT, a.dp(48)))
        val keep = toggle("Hold skærmen tændt", s.keepScreenOn)

        heading("Bølgeform")
        val iq = toggle("Gem IQ-udsnit af hver måling", s.saveIq)
        note("Et smalt udsnit af de rå samples omkring hvert signal gemmes i en WAV-fil ved siden af loggen, så signalet kan demoduleres bagefter. Ca. 30 KB pr. måling på 0,4 s. Lange udsendelser gemmes kun det første sekund.")

        heading("Upload (valgfrit)")
        note("Appen kan sende målingerne til din egen webhook undervejs, fx i Home Assistant. Tomt felt = intet sendes nogen steder; loggen ligger kun på telefonen.")
        val hook = edit("Webhook-URL (tom = ingen upload)", s.webhook, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        action("Test webhook", Ui.BUTTON) {
            val url = hook.text.toString().trim()
            if (url.isBlank()) a.toast("Der er ingen webhook at teste") else Thread {
                val code = Uploader.post(url, """{"type":"test"}""")
                a.runOnUiThread { a.toast(if (code > 0) "Webhook svarede HTTP $code" else "Ingen forbindelse (${Uploader.lastError})") }
            }.start()
        }

        heading("Måling i baggrunden")
        action("Batteri-indstillinger for appen", Ui.BUTTON) { a.openAppSettings() }

        action("Gem", Ui.GREEN) {
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
                l == null || h == null || h <= l -> a.toast("Båndets øvre grænse skal være højere end den nedre")
                h - l > 0.88 -> a.toast("Båndet må højst være 880 kHz bredt")
                l < 24 || h > 1766 -> a.toast("Dongle'n dækker ca. 24–1766 MHz")
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
        action("Annullér", Ui.BUTTON) { onClose() }

        about()

        return ScrollView(a).apply {
            setBackgroundColor(Ui.BG); isClickable = true; isFillViewport = true
            addView(col, MATCH_PARENT, WRAP_CONTENT)
        }
    }
}
