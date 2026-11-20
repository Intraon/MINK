package dk.intraon.mink.replay

/**
 * Kanalerne i et udsnit af vandfaldet: hvilke frekvenser udbruddene samler sig om, og hvor deres mærker kan stå,
 * uden at de dækker hinanden. Ren Kotlin.
 */
object Channels {
    /**
     * Kanalerne blandt udbruddenes frekvenser [freqs] (Hz), stigende. Udbrud, der ligger mindre end [joinHz] fra
     * naboen, hører til samme kanal, og kanalens frekvens er den midterste af dem: en sender ligger ikke helt
     * stille fra udbrud til udbrud.
     */
    fun of(freqs: List<Double>, joinHz: Double = 8_000.0): List<Double> {
        if (freqs.isEmpty()) return emptyList()
        val sorted = freqs.sorted()
        val out = ArrayList<Double>()
        var from = 0
        for (i in 1..sorted.size) {
            if (i < sorted.size && sorted[i] - sorted[i - 1] < joinHz) continue
            out.add(sorted[(from + i - 1) / 2])
            from = i
        }
        return out
    }

    /**
     * Rækken (0 = øverst) for hvert mærke, når mærkerne har midten i [centers] (stigende, i punkter) og er [width]
     * brede: et mærke flytter en række ned, hvis det ellers ville dække det forrige i sin række. −1 = der er ikke
     * plads i nogen af de [rows] rækker.
     */
    fun rows(centers: List<Float>, width: Float, rows: Int = 2): List<Int> {
        val end = FloatArray(rows) { Float.NEGATIVE_INFINITY }
        return centers.map { c ->
            val row = (0 until rows).firstOrNull { c - width / 2 >= end[it] } ?: return@map -1
            end[row] = c + width / 2
            row
        }
    }
}
