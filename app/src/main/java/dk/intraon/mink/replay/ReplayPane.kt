package dk.intraon.mink.replay

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
    private var replay: Replay? = null
    private var clips: IqClips? = null
    private var trip = ""

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
        val note = TextView(a).apply {
            textSize = 11f; setTextColor(Ui.DIM); setPadding(a.dp(10), a.dp(3), a.dp(10), a.dp(4))
            text = "Genskabt fra turens log. Udbruddene er de målte; støjen imellem dem blev ikke gemt."
        }
        col.addView(note, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        addView(col, LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }

    /** Åbner turen [r] med navnet [name]. [wave] er dens bølgeform-fil, eller null, hvis den ikke findes. */
    fun open(name: String, r: Replay, wave: Uri?) {
        clips?.close()
        trip = name; replay = r
        clips = wave?.let { IqClips.of(a, it, r.iqRate) { view.invalidate() } }
        view.show(r, clips)
        name()
    }

    fun close() {
        clips?.close(); clips = null; replay = null
        view.clear()
        onPlace(null)
    }

    private fun name() { zoomBtn.text = view.zoomName() }

    private fun moved(time: Long) {
        val r = replay ?: return
        val level = view.levelAt(time)?.let { "niveau ${Ui.f1(it)} dB" } ?: "målet høres ikke"
        title.text = "$trip\n${clock.format(Date(time))} · $level"
        onPlace(r.fixAt(time))
    }
}
