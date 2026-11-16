package dk.intraon.mink.direction

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Regning med retninger: hvor telefonen peger, hvor en retning ligger i kamerabilledet, og linjer på kortet. */
object Bearing {
    /** En retning i grader 0..360 med uret fra nord. */
    fun norm(deg: Double): Double = ((deg % 360) + 360) % 360

    /** Forskellen fra [from] til [to] i grader, −180..180 (positiv = med uret). */
    fun diff(to: Double, from: Double): Double = norm(to - from + 180) - 180

    /**
     * Hvor bagkameraet peger, ud fra telefonens rotationsmatrix (3×3 række for række, fra telefonens akser til
     * verdens: X mod øst, Y mod nord, Z op – som SensorManager.getRotationMatrixFromVector giver den).
     * Kameraet ser ud ad telefonens bagside, altså modsat telefonens Z-akse.
     * Returnerer retningen (grader fra magnetisk nord) og hældningen (grader over vandret).
     */
    fun fromRotation(r: FloatArray): Pair<Double, Double> {
        val e = -r[2].toDouble(); val n = -r[5].toDouble(); val u = -r[8].toDouble()
        val flat = sqrt(e * e + n * n)
        val az = if (flat < 1e-6) 0.0 else norm(Math.toDegrees(atan2(e, n)))
        return az to Math.toDegrees(asin(u.coerceIn(-1.0, 1.0)))
    }

    /** Hvor telefonen peger: retningen, kameraets hældning, og om telefonen ligger fladt. */
    class Pointing(val deg: Double, val tilt: Double, val flat: Boolean)

    /**
     * Retningen, der gælder: holdes telefonen som et kamera, er det dér, kameraet ser hen. Ligger den fladt med
     * kameraet mod jorden, er det dér, telefonens øverste kant peger – nogle kompasser er mest præcise sådan.
     * De to er enige i overgangen (en telefon, der vippes bagover, har top og kamera i samme retning), og
     * [wasFlat] giver lidt træghed, så den ikke skifter frem og tilbage ved grænsen.
     */
    fun pointing(r: FloatArray, wasFlat: Boolean): Pointing {
        val (camAz, tilt) = fromRotation(r)
        val flat = if (wasFlat) tilt < -40 else tilt < -55
        if (!flat) return Pointing(camAz, tilt, false)
        // telefonens Y-akse (mod øverste kant) i verden er matricens anden søjle
        val e = r[1].toDouble(); val n = r[4].toDouble()
        val az = if (sqrt(e * e + n * n) < 1e-6) camAz else norm(Math.toDegrees(atan2(e, n)))
        return Pointing(az, tilt, true)
    }

    /**
     * Hvor mange punkter fra billedets midte en retning ligger, når den er [deltaDeg] fra kameraets retning,
     * billedet er [sizePx] bredt (eller højt) og dækker [fovDeg]. Null = bag kameraet eller helt ude i siden.
     */
    fun offsetPx(deltaDeg: Double, fovDeg: Double, sizePx: Float): Float? {
        if (kotlin.math.abs(deltaDeg) > 80) return null
        return (tan(Math.toRadians(deltaDeg)) / tan(Math.toRadians(fovDeg / 2)) * sizePx / 2).toFloat()
    }

    /** Punktet [meters] fra ([lat], [lon]) i retningen [deg]. Godt nok til streger på få kilometer. */
    fun destination(lat: Double, lon: Double, deg: Double, meters: Double): Pair<Double, Double> {
        val dn = meters * cos(Math.toRadians(deg)); val de = meters * sin(Math.toRadians(deg))
        return (lat + dn / 111_320.0) to (lon + de / (111_320.0 * cos(Math.toRadians(lat))))
    }

    /** Afstanden i meter mellem to punkter tæt på hinanden. */
    fun meters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dn = (lat2 - lat1) * 111_320.0
        val de = (lon2 - lon1) * 111_320.0 * cos(Math.toRadians((lat1 + lat2) / 2))
        return sqrt(dn * dn + de * de)
    }

    /** "N", "NØ", "Ø" … for en retning. */
    fun name(deg: Double): String = arrayOf("N", "NØ", "Ø", "SØ", "S", "SV", "V", "NV")[(norm(deg + 22.5) / 45).toInt() % 8]
}
