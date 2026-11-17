package dk.intraon.mink.measure

/**
 * Køen af poster, der venter på at blive sendt til en webhook. Posterne sendes i pakker a højst [batch]. En pakke
 * beholder sit nummer og sit indhold, til den er afleveret: en pakke, der sendes igen, er præcis den samme, så en
 * modtager, der allerede har fået den, kan kende den på nummeret. Bliver køen længere end [max], falder de ældste
 * poster ud.
 *
 * Ren Kotlin uden Android. Alle funktioner må kaldes fra enhver tråd, men kun én tråd ad gangen må sende.
 */
class Outbox<T>(private val max: Int, private val batch: Int) {
    /** En pakke: dens nummer og de poster, den rummer. */
    class Packet<T>(val seq: Int, val items: List<T>)

    private val queue = ArrayDeque<T>()
    private var seq = 1
    /** Den pakke, der er forsøgt sendt og ikke afleveret endnu. */
    private var pending: Packet<T>? = null

    /** Antal poster, der venter. */
    val size: Int get() = synchronized(queue) { queue.size }

    /** Lægger en post i køen og returnerer køens længde. */
    fun add(item: T): Int = synchronized(queue) {
        queue.addLast(item)
        while (queue.size > max) queue.removeFirst()
        queue.size
    }

    /**
     * Sender pakker med [send], som returnerer, om pakken blev afleveret. Fortsætter, til køen er tom, en pakke
     * ikke blev afleveret, eller [more] siger stop; [more] spørges før hver pakke. Returnerer antallet af poster,
     * der blev afleveret.
     */
    fun drain(more: () -> Boolean = { true }, send: (Packet<T>) -> Boolean): Int {
        var delivered = 0
        while (more()) {
            val packet = synchronized(queue) {
                pending ?: if (queue.isEmpty()) null else Packet(seq, queue.take(batch)).also { pending = it }
            } ?: break
            if (!send(packet)) break
            synchronized(queue) {
                // fjern kun dem, der blev sendt: køen kan være skåret til forfra imens
                for (item in packet.items) if (queue.firstOrNull() === item) queue.removeFirst()
                seq++
                pending = null
            }
            delivered += packet.items.size
        }
        return delivered
    }
}
