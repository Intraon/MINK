package dk.intraon.mink.ui

import android.content.Context
import java.util.Locale
import kotlin.math.roundToInt

/** Farver og småhjælpere til skærmene. Mørkt tema til bilbrug. */
object Ui {
    const val BG = 0xFF0E0F12.toInt()
    const val PANEL = 0xFF16181D.toInt()
    const val TEXT = 0xFFE8EAED.toInt()
    const val DIM = 0xFF9AA0A6.toInt()
    const val ACCENT = 0xFF4FC3F7.toInt()
    const val GREEN = 0xFF3DDC84.toInt()
    const val RED = 0xFFFF5A5F.toInt()
    const val YELLOW = 0xFFFFC94D.toInt()
    const val BUTTON = 0xFF2A2D34.toInt()

    val DA: Locale = Locale.forLanguageTag("da-DK")
    fun f1(x: Double): String = String.format(DA, "%.1f", x)
    fun mhz(hz: Double): String = String.format(DA, "%.4f", hz / 1e6)
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()
