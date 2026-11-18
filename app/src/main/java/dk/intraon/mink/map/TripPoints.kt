package dk.intraon.mink.map

import dk.intraon.mink.core.Hub

/** Det, kortet viser for en eller flere ture: målinger, steder uden signal, mærker og pejlinger. */
class TripPoints(
    val bursts: List<Hub.Point>,
    val silent: List<Hub.Point>,
    val marks: List<Hub.Mark>,
    val bearings: List<Hub.BearingLine>,
) {
    /** Laveste og højeste niveau blandt målingerne, eller null uden målinger. */
    fun range(): Pair<Double, Double>? {
        val levels = bursts.mapNotNull { it.level }
        return if (levels.isEmpty()) null else levels.min() to levels.max()
    }

    /**
     * Målets frekvens i Hz: den midterste af målingernes frekvenser. En enkelt måling kan ligge skævt, når støj
     * trækker i toppen, så den midterste siger mere om senderen end den første. Null uden målinger med frekvens.
     */
    fun targetHz(): Double? = bursts.mapNotNull { it.freqHz }.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }

    /** Alle steder med position som (bredde, længde): det, et kortudsnit skal rumme for at vise hele turen. */
    fun positions(): List<Pair<Double, Double>> {
        val res = ArrayList<Pair<Double, Double>>()
        for (p in bursts + silent) { res.add((p.lat ?: continue) to (p.lon ?: continue)) }
        for (m in marks) { res.add((m.lat ?: continue) to (m.lon ?: continue)) }
        for (b in bearings) { res.add((b.lat ?: continue) to (b.lon ?: continue)) }
        return res
    }
}
