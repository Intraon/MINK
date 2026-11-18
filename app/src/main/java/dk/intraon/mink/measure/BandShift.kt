package dk.intraon.mink.measure

import kotlin.math.abs

/**
 * Båndet, der lyttes på, når brugeren trækker vandfaldet til siden for at kigge på nabofrekvenserne. Båndet beholder
 * sin bredde og flytter sig; turens eget bånd er "hjemme". Ren Kotlin.
 */
object BandShift {
    /** Båndet flytter sig i hele trin, så frekvensaksen står på pæne tal. */
    const val STEP_HZ = 5_000.0
    /** Ender et træk så tæt på turens eget bånd, er det hjemme igen. */
    const val HOME_WITHIN_HZ = 15_000.0
    private const val MIN_HZ = 24e6
    private const val MAX_HZ = 1766e6

    /**
     * Båndet [loHz]–[hiHz] flyttet [byHz] (positiv = opad). Flytningen sker i hele [STEP_HZ] og kun inden for det,
     * modtageren dækker. Lander båndet inden for [HOME_WITHIN_HZ] af [homeLoHz], er svaret turens eget bånd.
     * Null, hvis trækket er for kort til at flytte noget.
     */
    fun moved(loHz: Double, hiHz: Double, byHz: Double, homeLoHz: Double, homeHiHz: Double): Pair<Double, Double>? {
        val width = hiHz - loHz
        val lo = (Math.round((loHz + byHz) / STEP_HZ) * STEP_HZ).coerceIn(MIN_HZ, MAX_HZ - width)
        if (abs(lo - homeLoHz) <= HOME_WITHIN_HZ && abs(width - (homeHiHz - homeLoHz)) < 1.0) {
            return if (loHz == homeLoHz) null else homeLoHz to homeHiHz
        }
        return if (lo == loHz) null else lo to lo + width
    }

    /** Kan målet [targetHz] høres, når der lyttes på [loHz]–[hiHz]? Uden et mål er der intet at gå glip af. */
    fun hears(loHz: Double, hiHz: Double, targetHz: Double?): Boolean = targetHz == null || targetHz in loHz..hiHz
}
