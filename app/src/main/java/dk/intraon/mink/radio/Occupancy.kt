package dk.intraon.mink.radio

/**
 * Hvor stor en del af tiden et frekvensområde er optaget af de sendere, der høres: summen af den tid, mindst ét
 * udbrud var i gang, over det seneste minut. Det er tallet for, hvor meget en anden bruger af kanalen har at
 * kæmpe med. Udbrud, der overlapper i tid, tæller kun én gang. Ren Kotlin; metoderne er synkroniserede.
 */
class Occupancy(private val windowMs: Long = 60_000) {
    /** Andelen af tiden (0–1), området var optaget, og hvor mange sendere der stod for det. */
    class Share(val fraction: Double, val senders: Int)

    private class Burst(val start: Long, val end: Long, val freqHz: Double, val source: Int)

    private val bursts = ArrayList<Burst>()
    private var since: Long? = null

    /** Lytningen begynder (eller begynder forfra) til tiden [tMs]: det, der blev hørt før, tæller ikke med. */
    @Synchronized fun start(tMs: Long) { bursts.clear(); since = tMs }

    /** Et udbrud fra senderen [source] på [freqHz], der begyndte [tStartMs] og varede [durMs]. */
    @Synchronized fun add(tStartMs: Long, durMs: Int, freqHz: Double, source: Int) {
        bursts.add(Burst(tStartMs, tStartMs + durMs, freqHz, source))
        bursts.removeAll { it.end < tStartMs - 2 * windowMs }
    }

    /**
     * Optagetheden af [loHz]–[hiHz] set fra tiden [nowMs]. Der regnes over det seneste minut, eller den tid, der
     * er lyttet, hvis den er kortere. Null, før der er lyttet i [minMs]: et par sekunder siger ikke noget.
     */
    @Synchronized fun share(loHz: Double, hiHz: Double, nowMs: Long, minMs: Long = 20_000): Share? {
        val from = maxOf(since ?: return null, nowMs - windowMs)
        if (nowMs - from < minMs) return null
        val inside = bursts.filter { it.freqHz in loHz..hiHz && it.end > from && it.start < nowMs }.sortedBy { it.start }
        var busy = 0L; var until = from
        for (b in inside) {
            val a = maxOf(b.start, until); val e = minOf(b.end, nowMs)
            if (e > a) { busy += e - a; until = e }
        }
        return Share(busy.toDouble() / (nowMs - from), inside.map { it.source }.distinct().size)
    }
}
