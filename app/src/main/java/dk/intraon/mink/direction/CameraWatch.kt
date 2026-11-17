package dk.intraon.mink.direction

/**
 * Holder styr på, om kameraet kommer i gang. Et kamera, der ikke kunne åbnes eller vise billedet, prøves igen
 * efter [RETRY_MS], uden at brugeren skal forlade skærmen. Først efter [TRIES] forgæves forsøg i træk er der en
 * fejl at vise, og der prøves stadig. Et kamera, der er startet, men ikke har leveret et billede i [STALL_MS],
 * regnes for gået i stå.
 *
 * Ren Kotlin uden Android. Tiderne er millisekunder fra et ur, der kun går fremad.
 */
class CameraWatch {
    private var startedAt = -1L
    private var frameAt = -1L
    private var retryAt = 0L
    /** Forgæves forsøg i træk siden det seneste billede. */
    var failures = 0
        private set

    /** Et forsøg på at starte kameraet er sat i gang. */
    fun started(now: Long) { startedAt = now; frameAt = -1L }

    /** Kameraet har leveret et billede. */
    fun frame(now: Long) { frameAt = now; failures = 0 }

    /** Forsøget mislykkedes, eller kameraet gik i stå. */
    fun failed(now: Long) { startedAt = -1L; frameAt = -1L; failures++; retryAt = now + RETRY_MS }

    /** Kameraet er slukket med vilje: skærmen er forladt, eller telefonen ligger fladt. */
    fun stopped() { startedAt = -1L; frameAt = -1L; failures = 0; retryAt = 0L }

    /** Om et nyt forsøg må sættes i gang nu. */
    fun mayStart(now: Long) = startedAt < 0 && now >= retryAt

    /** Om kameraet er startet uden at levere billeder længe nok til, at det skal startes forfra. */
    fun stalled(now: Long) = startedAt >= 0 && now - maxOf(startedAt, frameAt) > STALL_MS

    /** Om der er mislykkedes så mange forsøg i træk, at brugeren skal have fejlen at vide. */
    val givenUp get() = failures >= TRIES

    companion object {
        const val RETRY_MS = 700L
        const val STALL_MS = 4_000L
        const val TRIES = 3
    }
}
