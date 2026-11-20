package dk.intraon.mink.direction

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Krydspejling: det sted, pejlinger fra flere steder peger på tilsammen, og hvor usikkert det er.
 *
 * Stedet ([lat], [lon]) er det punkt, der ligger tættest på alle pejlingernes linjer, når en pejling vejer tungere,
 * jo tættere på den er taget: en fejl på ti grader flytter linjen 900 m på fem kilometers afstand og 90 m på 500 m.
 * Området om stedet er en ellipse med halvakserne [majorM] og [minorM] meter og den lange akse i retningen
 * [axisDeg]. Den er lang, hvor pejlingerne krydser hinanden i en spids vinkel. [used] pejlinger er regnet med, og
 * [left] er udeladt, fordi de peger et helt andet sted hen end resten. Ren Kotlin.
 */
class Crossing(
    val lat: Double, val lon: Double, val used: Int, val left: Int,
    val majorM: Double, val minorM: Double, val axisDeg: Double,
) {
    /** En pejling: stedet, retningen, udsvinget i dB en omgang rundt (null = ikke målt) og om stedet havde flere retninger. */
    class Sight(val lat: Double, val lon: Double, val deg: Double, val swing: Double? = null, val several: Boolean = false)

    /** Ellipsens omrids som (bredde, længde), [steps] punkter rundt. */
    fun outline(steps: Int = 48): List<Pair<Double, Double>> {
        val a = Math.toRadians(axisDeg)
        return (0 until steps).map { k ->
            val t = 2 * Math.PI * k / steps
            val along = majorM * cos(t); val across = minorM * sin(t)
            val east = along * sin(a) + across * cos(a); val north = along * cos(a) - across * sin(a)
            (lat + north / M_PER_DEG) to (lon + east / (M_PER_DEG * cos(Math.toRadians(lat))))
        }
    }

    companion object {
        /** Så mange grader rammer en pejling typisk ved siden af. Kilen på kortet har samme bredde. */
        const val MISS_DEG = 10.0
        /** Et sted tæller kun med, når niveauet svingede mindst så mange dB en omgang rundt. */
        private const val CLEAR_DB = 10.0
        /** Pejlinger taget inden for så mange meter af hinanden er samme sted og tæller én gang. */
        private const val SAME_PLACE_M = 30.0
        /** To pejlinger skal krydse hinanden med mindst denne vinkel; ellers løber de ved siden af hinanden. */
        private const val MIN_ANGLE_DEG = 20.0
        /** En pejling, der peger mere end så mange grader ved siden af stedet, hører ikke til det. */
        private const val OFF_DEG = 30.0
        /** Tættere på end dette vejer en pejling ikke tungere: stedet selv er ikke kendt bedre end det. */
        private const val NEAR_M = 50.0
        /** Ellipsen tegnes to gange så stor som den typiske fejl, så senderen oftest ligger inden for den. */
        private const val SPREAD = 2.0
        private const val M_PER_DEG = 111_320.0

        private class Line(val x: Double, val y: Double, val deg: Double) {
            val ux = sin(Math.toRadians(deg)); val uy = cos(Math.toRadians(deg))
            /** Hvor mange grader pejlingen peger ved siden af punktet ([px], [py]): 0 = lige på, 180 = modsat vej. */
            fun off(px: Double, py: Double): Double {
                val dx = px - x; val dy = py - y
                return abs(Math.toDegrees(atan2(ux * dy - uy * dx, ux * dx + uy * dy)))
            }
        }

        /**
         * Krydspejlingen af [sights]. Steder med flere retninger og steder uden tydelig retning regnes ikke med, og
         * flere pejlinger fra samme sted tæller som den tydeligste af dem. Null, når der ikke er to steder tilbage,
         * når pejlingerne løber næsten parallelt, eller når de ikke mødes foran de steder, de er taget fra.
         */
        fun of(sights: List<Sight>): Crossing? {
            val clear = sights.filter { !it.several && (it.swing == null || it.swing >= CLEAR_DB) }.sortedByDescending { it.swing ?: 0.0 }
            val places = ArrayList<Sight>()
            for (s in clear) if (places.none { Bearing.meters(it.lat, it.lon, s.lat, s.lon) <= SAME_PLACE_M }) places.add(s)
            if (places.size < 2) return null
            val lat0 = places.sumOf { it.lat } / places.size; val lon0 = places.sumOf { it.lon } / places.size
            val east = M_PER_DEG * cos(Math.toRadians(lat0))
            var lines = places.map { Line((it.lon - lon0) * east, (it.lat - lat0) * M_PER_DEG, it.deg) }

            var at = solve(lines) ?: return null
            // en pejling, der peger et helt andet sted hen (genskin, eller en anden sender), tages ud, én ad gangen
            while (lines.size > 2) {
                val worst = lines.maxByOrNull { it.off(at.first, at.second) }!!
                if (worst.off(at.first, at.second) <= OFF_DEG) break
                lines = lines - worst
                at = solve(lines) ?: return null
            }
            if (lines.any { it.off(at.first, at.second) > OFF_DEG }) return null
            if (!crosses(lines)) return null

            // usikkerheden: hver pejling kender stedet på tværs af sin egen retning med afstanden gange den typiske fejl
            var sxx = 0.0; var sxy = 0.0; var syy = 0.0
            for (l in lines) {
                val w = weight(l, at)
                sxx += w * l.uy * l.uy; sxy -= w * l.uy * l.ux; syy += w * l.ux * l.ux
            }
            val det = sxx * syy - sxy * sxy
            if (det <= 0) return null
            val cxx = syy / det; val cxy = -sxy / det; val cyy = sxx / det
            // ellipsens akser er de retninger, usikkerheden er størst og mindst i
            val mean = (cxx + cyy) / 2; val half = hypot((cxx - cyy) / 2, cxy)
            val long = mean + half
            val axis = if (abs(cxy) > 1e-9 * long) Math.toDegrees(atan2(cxy, long - cxx)) else if (cxx >= cyy) 90.0 else 0.0
            return Crossing(
                lat0 + at.second / M_PER_DEG, lon0 + at.first / east, lines.size, places.size - lines.size,
                SPREAD * sqrt(long), SPREAD * sqrt((mean - half).coerceAtLeast(0.0)), Bearing.norm(axis) % 180,
            )
        }

        /** Vægten af en pejling set fra punktet [at]: én over kvadratet på den fejl i meter, den typisk har dér. */
        private fun weight(l: Line, at: Pair<Double, Double>): Double {
            val miss = hypot(at.first - l.x, at.second - l.y).coerceAtLeast(NEAR_M) * Math.toRadians(MISS_DEG)
            return 1 / (miss * miss)
        }

        /** Punktet tættest på linjerne. Vægtene afhænger af afstanden til punktet, så der regnes nogle gange. */
        private fun solve(lines: List<Line>): Pair<Double, Double>? {
            var at: Pair<Double, Double>? = null
            repeat(6) {
                var a = 0.0; var b = 0.0; var c = 0.0; var e = 0.0; var f = 0.0
                for (l in lines) {
                    val w = at?.let { p -> weight(l, p) } ?: 1.0
                    // linjens normal er (uy, −ux); afstanden fra punktet til linjen er normalen gange (punkt − sted)
                    val nx = l.uy; val ny = -l.ux; val k = nx * l.x + ny * l.y
                    a += w * nx * nx; b += w * nx * ny; c += w * ny * ny; e += w * nx * k; f += w * ny * k
                }
                val det = a * c - b * b
                if (abs(det) < 1e-12 * (a + c) * (a + c)) return null
                at = (c * e - b * f) / det to (a * f - b * e) / det
            }
            return at
        }

        /** Krydser mindst to af linjerne hinanden med en vinkel, der er til at regne på? */
        private fun crosses(lines: List<Line>): Boolean = lines.any { p ->
            lines.any { q -> abs(Bearing.diff(p.deg, q.deg)).let { minOf(it, 180 - it) } >= MIN_ANGLE_DEG }
        }
    }
}
