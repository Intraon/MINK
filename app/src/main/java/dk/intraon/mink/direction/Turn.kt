package dk.intraon.mink.direction

/**
 * Hvor langt en omgang rundt på ét sted er nået: hvilke retninger der er målt nok i til at tælle med i toppen
 * ([Lobe.levels]), og hvor der mangler. Omgangen er færdig, når der er målt hele vejen rundt: så bliver retningen
 * ikke bedre af at blive stående. Ren Kotlin.
 */
class Turn(private val measured: BooleanArray) {
    /** Så mange retninger er der målt i, ud af [of]. */
    val count: Int = measured.count { it }
    val of: Int get() = measured.size
    val done: Boolean get() = count == of

    /** Er der målt i retning nummer [k]? Retningen ligger i [k] · [Lobe.STEP] grader. */
    fun has(k: Int): Boolean = measured[k]

    /**
     * Det største sammenhængende stykke uden målinger: dets midte i grader, og hvor mange grader det spænder over.
     * Det er dér, der skal drejes hen. Null, når omgangen er færdig.
     */
    val missing: Pair<Double, Int>? = run {
        val n = measured.size
        if (count == n) return@run null
        if (count == 0) return@run 0.0 to 360
        var bestStart = 0; var bestLen = 0
        for (start in 0 until n) {
            // et stykke begynder lige efter en målt retning
            if (measured[start] || !measured[(start - 1 + n) % n]) continue
            var len = 0
            while (!measured[(start + len) % n]) len++
            if (len > bestLen) { bestLen = len; bestStart = start }
        }
        Bearing.norm((bestStart + (bestLen - 1) / 2.0) * Lobe.STEP) to bestLen * Lobe.STEP
    }

    companion object {
        /** Omgangen ud fra stedets målinger: (retning i grader, niveau i dB). */
        fun of(samples: List<Pair<Double, Double>>): Turn = Turn(BooleanArray(360 / Lobe.STEP).also { m ->
            val level = Lobe.levels(samples)
            for (k in m.indices) m[k] = !level[k].isNaN()
        })
    }
}
