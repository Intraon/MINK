package dk.intraon.mink.radio

/**
 * Gain. Ren Kotlin uden Android, så reglerne kan testes direkte (GainControlTest).
 *
 * Brugeren vælger et gain, og appen skruer aldrig højere op end det. Så betyder tallet "dB over støjen", bippet og
 * vandfaldet det samme overalt på turen: bliver signalet svagere, ses og høres det. Skruede appen selv op, når
 * signalet blev svagt, ville et fjernt signal se lige så tydeligt ud som et nært.
 *
 * Beskyttelsen ([auto]) skruer kun ned, når modtageren mættes, og tilbage mod det valgte gain igen:
 * - Efter et burst: klipning eller meget kraftigt signal → 2 trin ned.
 * - I hvert tick: var det stærkeste signal i perioden svagt, eller har der længe været stille → 1 trin op,
 *   men aldrig over det valgte, og aldrig hvis modtageren blev mættet i perioden (fx af en kraftig sender i
 *   nærheden på en anden frekvens) – så ville mere gain kun gøre det værre.
 * − og + flytter det valgte gain. Beskyttelsen bliver ved med at virke.
 */
class GainControl(private val gains: DoubleArray, startDb: Double) {

    data class Change(val from: Double, val to: Double, val why: String)

    var idx: Int = nearest(startDb)
        private set
    private var chosenIdx = idx
    /** Beskyttelse mod mætning: skru ned tæt på senderen og tilbage igen. */
    var auto = true
    val db get() = gains[idx]
    /** Det gain, brugeren har valgt. Der skrues aldrig højere op end det. */
    val chosenDb get() = gains[chosenIdx]

    private fun nearest(db: Double) = gains.indices.minByOrNull { kotlin.math.abs(gains[it] - db) } ?: 0

    private fun move(to: Int, why: String): Change? {
        val t = to.coerceIn(0, gains.size - 1)
        if (t == idx) return null
        val c = Change(gains[idx], gains[t], why)
        idx = t
        return c
    }

    fun afterBurst(peakDbfs: Double, clipPct: Double): Change? {
        if (!auto) return null
        if (clipPct > 0.5) return move(idx - 2, "clip")
        if (peakDbfs > -10.0) return move(idx - 2, "kraftigt")
        return null
    }

    /**
     * [strongestDbfs] = det kraftigste burst siden sidste tick (null = ingen), [quietTicks] = antal ticks i træk
     * uden et burst, [clipPct] = andel mættede samples i perioden. Skruer kun op, hvis beskyttelsen har skruet ned.
     */
    fun tick(strongestDbfs: Double?, quietTicks: Int, clipPct: Double): Change? {
        if (!auto || clipPct > 0.5 || idx >= chosenIdx) return null
        if (strongestDbfs != null) return if (strongestDbfs < -45.0) move(idx + 1, "tilbage") else null
        return if (quietTicks >= 2) move(idx + 1, "tilbage") else null
    }

    /** Brugeren vælger et gain direkte (fra indstillingerne). */
    fun choose(db: Double): Change? {
        chosenIdx = nearest(db)
        return move(chosenIdx, "manuel")
    }

    /** Et trin op eller ned fra det gain, der står nu. Det bliver det nye valg. */
    fun manual(step: Int): Change? {
        chosenIdx = (idx + step).coerceIn(0, gains.size - 1)
        return move(chosenIdx, "manuel")
    }
}
