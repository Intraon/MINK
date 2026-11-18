package dk.intraon.mink.trip

/**
 * Hvilke ture der er delt. En tur huskes med den størrelse, dens log havde, da den blev delt: er loggen vokset
 * siden, mangler modtageren resten, og turen tæller som udelt. En tur kendes på tidsstemplet i sit filnavn.
 */
class SharedTrips(private val sizes: Map<String, Long> = emptyMap()) {
    /** Er turen [session] delt, sådan som dens log ser ud nu ([bytes])? */
    fun has(session: String, bytes: Long): Boolean = sizes[session]?.let { bytes <= it } ?: false

    /**
     * Listen, efter at turene i [shared] (tur → loggens størrelse) er delt. Ture, hvis log ikke længere findes
     * blandt [existing], glemmes, så listen ikke vokser for evigt.
     */
    fun after(shared: Map<String, Long>, existing: Collection<String>): SharedTrips =
        SharedTrips((sizes + shared).filterKeys { it in existing })

    /**
     * De ture, en deling begynder med: alle ture fra dagen [day] ("20261118"), som ikke er delt. Er der ingen, er
     * det den nyeste tur, så der altid er noget at dele. [trips] er tur og loggens størrelse, nyeste først.
     */
    fun suggested(trips: List<Pair<String, Long>>, day: String): List<String> {
        val unshared = trips.filter { (session, bytes) -> session.startsWith(day) && !has(session, bytes) }.map { it.first }
        return unshared.ifEmpty { listOfNotNull(trips.firstOrNull()?.first) }
    }

    /** Listen som én linje tekst til indstillingerne: "20261118_145148=212044;20261118_130210=98112". */
    fun encode(): String = sizes.entries.joinToString(";") { "${it.key}=${it.value}" }

    companion object {
        /** Læser en linje skrevet af [encode]. Dele, der ikke kan læses, springes over. */
        fun decode(text: String): SharedTrips = SharedTrips(text.split(';').mapNotNull { part ->
            val bytes = part.substringAfter('=', "").toLongOrNull() ?: return@mapNotNull null
            part.substringBefore('=') to bytes
        }.toMap())
    }
}
