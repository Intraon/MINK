package dk.intraon.mink.map

import dk.intraon.mink.core.Hub

/**
 * Turens spor på kortet: målingerne og de stille steder i den rækkefølge, de blev til, bundet sammen stykke for
 * stykke. Hvert stykke kender niveauet i begge ender, så det kan farves glidende fra den ene måling til den næste.
 * En ende uden niveau er et sted, hvor der blev lyttet uden at høre målet.
 *
 * Ren Kotlin uden Android.
 */
object Track {
    /** Ligger der længere tid end dette mellem to steder, bindes de ikke sammen: dér holdt målingen pause. */
    const val GAP_MS = 30_000L

    /** Et stykke af sporet. Et sted uden naboer er et stykke, hvor [from] og [to] er det samme sted. */
    class Piece(val from: Hub.Point, val to: Hub.Point) {
        /** Det højeste niveau i stykkets ender, eller null, hvis målet ikke blev hørt i nogen af dem. */
        val level: Double?
            get() {
                val a = from.level; val b = to.level
                return if (a == null) b else if (b == null) a else maxOf(a, b)
            }
    }

    /**
     * Binder stederne sammen i tidsrækkefølge; steder uden position springes over. Stykkerne kommer i den rækkefølge,
     * de skal tegnes: først dem uden niveau, så de svageste, og de stærkeste til sidst. Går turen samme vej flere
     * gange, er det dermed den stærkeste måling på stedet, der ses: kroppen, en bygning eller et udfald kan kun
     * trække en måling ned, så den stærkeste er den, der siger mest om afstanden.
     */
    fun pieces(points: List<Hub.Point>): List<Piece> {
        val placed = points.filter { it.lat != null && it.lon != null }.sortedBy { it.t }
        val res = ArrayList<Piece>()
        var joined = false      // om det foregående sted blev bundet til stedet før det
        for (i in placed.indices) {
            val here = placed[i]
            val next = placed.getOrNull(i + 1)
            if (next != null && next.t - here.t <= GAP_MS) { res.add(Piece(here, next)); joined = true }
            else { if (!joined) res.add(Piece(here, here)); joined = false }
        }
        return res.sortedBy { it.level ?: -Double.MAX_VALUE }
    }
}
