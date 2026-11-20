package dk.intraon.mink.replay

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import dk.intraon.mink.ui.MainActivity
import dk.intraon.mink.ui.Ui
import dk.intraon.mink.ui.dp
import java.text.SimpleDateFormat
import java.util.Date

/**
 * En tidligere tur som vandfald: en linje med turens navn, klokkeslættet og målets niveau dér, hvor vandfaldet står,
 * knapper til afstand og luk, og selve vandfaldet ([ReplayView]). [onPlace] får stedet, turen var på det tidspunkt
 * (null, hvis der ikke er et), så kortet kan vise det; [onClose] kaldes, når brugeren lukker turen.
 */
class ReplayPane(private val a: MainActivity, private val onPlace: (Replay.Fix?) -> Unit, private val onClose: () -> Unit) : FrameLayout(a) {
    private val view = ReplayView(a) { time -> moved(time) }
    private val title = TextView(a).apply { textSize = 13f; setTextColor(Ui.TEXT); gravity = Gravity.CENTER_VERTICAL; setPadding(a.dp(10), 0, a.dp(6), 0); maxLines = 2 }
    private val zoomBtn: Button = a.button("normal", Ui.BUTTON) { view.zoom(); name() }.apply { textSize = 13f; setPadding(a.dp(10), 0, a.dp(10), 0) }
    private val clock = SimpleDateFormat("HH:mm:ss", Ui.DA)
    private val note = TextView(a).apply { textSize = 11f; setTextColor(Ui.DIM); setPadding(a.dp(10), a.dp(3), a.dp(10), a.dp(4)) }
    private var replay: Replay? = null
    private var clips: IqClips? = null
    private var trip = ""
    private var time = 0L

    /** Om en tur er åben. */
    val active get() = replay != null

    init {
        setBackgroundColor(Ui.BG)
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        val bar = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, a.dp(4), a.dp(4), a.dp(4)) }
        bar.addView(title, LinearLayout.LayoutParams(0, a.dp(40), 1f))
        bar.addView(zoomBtn, LinearLayout.LayoutParams(WRAP_CONTENT, a.dp(40)).apply { rightMargin = a.dp(4) })
        bar.addView(a.button("✕", Ui.BUTTON) { onClose() }, LinearLayout.LayoutParams(a.dp(44), a.dp(40)))
        col.addView(bar, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        col.addView(view, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        col.addView(note, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        addView(col, LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }

    /** Åbner turen [r] med navnet [name]. [wave] er dens bølgeform-fil, eller null, hvis den ikke findes. */
    fun open(name: String, r: Replay, wave: Uri?) {
        clips?.close()
        trip = name; replay = r
        clips = wave?.let { IqClips.of(a, it, r.iqRate) { view.invalidate(); name() } }
        view.show(r, clips)
        name()
    }

    fun close() {
        clips?.close(); clips = null; replay = null
        view.clear()
        onPlace(null)
    }

    private fun name() { zoomBtn.text = view.zoomName(); note.text = "Genskabt fra turens log. ${drawn()}" }

    /**
     * Hvad udbruddene er tegnet af lige nu: det målte signal fra turens bølgeform, eller et felt farvet efter styrken,
     * når der ses på overblik, når turen ingen bølgeform har på telefonen, eller når den ikke kunne læses.
     */
    private fun drawn(): String {
        val c = clips
        return when {
            c == null -> "Turens bølgeform ligger ikke på telefonen, så hvert udbrud er et felt farvet efter styrken."
            c.unreadable -> "Turens bølgeform kunne ikke læses, så hvert udbrud er et felt farvet efter styrken."
            !view.detailed -> "I overblik er hvert udbrud et felt farvet efter styrken. Vælg normal eller tæt på for at se signalet."
            else -> "Udbruddene er det målte signal fra turens bølgeform. Støjen imellem dem blev ikke gemt."
        }
    }

    /**
     * Vandfaldet, som det ses lige nu, med en bjælke under: turen, klokkeslættet i øverste kant og målets frekvens,
     * og at vandfaldet er genskabt, med afstanden. Null, hvis ingen tur er åben, eller vandfaldet ikke har en størrelse.
     */
    fun picture(): Bitmap? {
        val r = replay ?: return null
        val w = view.width; val h = view.height
        if (w <= 0 || h <= 0) return null
        val dens = resources.displayMetrics.density
        val bar = (48 * dens).toInt()
        val bmp = Bitmap.createBitmap(w, h + bar, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        view.draw(c)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = Ui.BG; c.drawRect(0f, h.toFloat(), w.toFloat(), (h + bar).toFloat(), p)
        val target = r.target.map { it.freqHz }.sorted().let { if (it.isEmpty()) "" else " · mål ${Ui.mhz(it[it.size / 2])} MHz" }
        // en linje, der er for lang til billedet, skrives mindre i stedet for at blive skåret af
        fun line(text: String, size: Float, color: Int, y: Float) {
            p.color = color; p.textSize = size * dens
            val room = w - 16 * dens
            if (p.measureText(text) > room) p.textSize *= room / p.measureText(text)
            c.drawText(text, 8 * dens, y, p)
        }
        line("MINK · tur $trip · ${clock.format(Date(time))}$target", 12f, Ui.TEXT, h + 19 * dens)
        line("Genskabt fra turens log (${view.zoomName()}). ${drawn()}", 11f, Ui.DIM, h + 38 * dens)
        return bmp
    }

    private fun moved(time: Long) {
        this.time = time
        val r = replay ?: return
        val level = view.levelAt(time)?.let { "niveau ${Ui.f1(it)} dB" } ?: "målet høres ikke"
        title.text = "$trip\n${clock.format(Date(time))} · $level"
        onPlace(r.fixAt(time))
    }
}
