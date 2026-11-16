package dk.intraon.mink.direction

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Dæmper kompassets sitren: retningen udjævnes over ca. [tauS] sekunder. Der regnes på retningen som en pil,
 * ikke som et tal, så 359° og 1° giver 0° og ikke 180°.
 */
class HeadingFilter(private val tauS: Double = 0.25) {
    private var x = 0.0
    private var y = 0.0
    private var t = Double.NaN

    /** En ny retning til tiden [tS] (sekunder). Returnerer den udjævnede retning. */
    fun add(deg: Double, tS: Double): Double {
        val vx = sin(Math.toRadians(deg)); val vy = cos(Math.toRadians(deg))
        if (t.isNaN() || tS < t || tS - t > 1.0) { x = vx; y = vy }
        else { val a = 1 - Math.exp(-(tS - t) / tauS); x += a * (vx - x); y += a * (vy - y) }
        t = tS
        return Bearing.norm(Math.toDegrees(atan2(x, y)))
    }
}
