package dk.intraon.mink.direction

/**
 * Om telefonens kompas er til at stole på. Et kompas, der ikke er kalibreret, eller som har en magnet i nærheden
 * (højttaler, antennefod, cover, bil), kan vise mange grader forkert uden selv at sige til. To ting afslører det:
 * sensoren melder lav præcision, eller det målte magnetfelt er meget stærkere eller svagere end Jordens på stedet.
 */
object CompassCheck {
    /** Jordens felt ligger mellem ca. 25 og 65 µT; i Danmark omkring 50. */
    private const val LOW_UT = 25.0
    private const val HIGH_UT = 65.0

    /** Er det målte felt [ut] troværdigt? Kendes det forventede felt for stedet, må det afvige 30 %. Ukendt måling tæller som i orden. */
    fun fieldOk(ut: Double?, expectedUt: Double?): Boolean {
        if (ut == null) return true
        if (expectedUt != null && expectedUt > 0) return kotlin.math.abs(ut - expectedUt) <= 0.3 * expectedUt
        return ut in LOW_UT..HIGH_UT
    }

    /** [accuracy] er sensorens egen melding: 0 = upålidelig, 1 = lav, 2 = middel, 3 = høj, −1 = ikke meldt endnu. */
    fun poor(accuracy: Int, ut: Double?, expectedUt: Double?): Boolean = accuracy in 0..1 || !fieldOk(ut, expectedUt)

    /**
     * Hvor langt man er med 8-tallerne, 0..1: telefonen skal have drejet en hel omgang om hver af sine tre akser
     * ([turnRad] = samlet drejning pr. akse i radianer). Så har magnetometeret set feltet fra alle sider.
     */
    fun progress(turnRad: DoubleArray): Double {
        val full = 2 * Math.PI
        return (turnRad.sumOf { kotlin.math.min(it, full) } / (turnRad.size * full)).coerceIn(0.0, 1.0)
    }
}
