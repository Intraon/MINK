package dk.intraon.mink.trip

import java.util.Locale

/** Hvilke filer der hører til en tur. En tur kendes på sit starttidspunkt i filnavnet, fx "20261116_223740". */
object TripFiles {
    private val STAMP = Regex("""\d{8}_\d{6}""")

    /** Tidsstemplet i et filnavn ("mink_20261116_223740.jsonl.txt" → "20261116_223740"), eller null. */
    fun stamp(name: String): String? = STAMP.find(name)?.value

    /**
     * Hører billedet til den tur, der begyndte [session]? Billeder har deres eget tidsstempel i navnet; de hører til
     * turen, hvis de er taget efter dens start og før den næste tur begyndte ([next]; null = der er ingen senere tur).
     */
    fun inTrip(photoName: String, session: String, next: String?): Boolean {
        val s = stamp(photoName) ?: return false
        return s >= session && (next == null || s < next)
    }

    /** Starten på den første tur efter [session] blandt [all], eller null hvis [session] er den nyeste. */
    fun nextAfter(session: String, all: Collection<String>): String? = all.filter { it > session }.minOrNull()

    /** Det, en deling kan tage med. Valget gemmes i indstillingerne som summen af de valgte dele. */
    const val PART_LOG = 1
    const val PART_WAVE = 2
    const val PART_PICTURES = 4
    const val PART_MAP = 8
    const val PART_ALL = PART_LOG or PART_WAVE or PART_PICTURES or PART_MAP

    /**
     * Navnet på et kortbillede taget [now] ("20261117_130000"). Viser kortet én tidligere tur, står dens tidsstempel
     * ([shown]) først i navnet, så billedet hører til den tur og ikke til den, der er den nyeste, mens billedet tages.
     */
    fun mapPictureName(now: String, shown: String?): String =
        if (shown == null) "MINK_kort_$now.png" else "MINK_kort_${shown}_$now.png"

    /**
     * Navnet på et billede af vandfaldet for den tidligere tur [session], taget [now]. Turens tidsstempel står først,
     * så billedet hører til den tur, det viser.
     */
    fun replayPictureName(now: String, session: String): String = "MINK_vandfald_${session}_$now.png"

    /**
     * Navnet på det kort over hele turen [session], som laves, når turen deles. Der er ét pr. tur; deles turen
     * igen, erstattes det.
     */
    fun tripMapName(session: String): String = "MINK_kort_$session.png"

    /** Kortfilens navn: turens tid for én tur, ellers antallet af ture og den nyestes tid. */
    fun mapPageName(sessions: List<String>): String =
        if (sessions.size == 1) "MINK_kort_${sessions[0]}.html" else "MINK_kort_${sessions.size}_ture_${sessions.maxOrNull()}.html"

    /** "16.11.2026 kl. 22.37" ud af et filnavn med tidsstempel; navnet selv, hvis det ikke har et. */
    fun title(name: String): String {
        val m = Regex("""(\d{4})(\d{2})(\d{2})_(\d{2})(\d{2})""").find(name) ?: return name
        return "${m.groupValues[3]}.${m.groupValues[2]}.${m.groupValues[1]} kl. ${m.groupValues[4]}.${m.groupValues[5]}"
    }

    /** "16.11 kl. 22.37": turens navn, hvor der er lidt plads. */
    fun shortTitle(name: String): String {
        val m = Regex("""\d{4}(\d{2})(\d{2})_(\d{2})(\d{2})""").find(name) ?: return name
        return "${m.groupValues[2]}.${m.groupValues[1]} kl. ${m.groupValues[3]}.${m.groupValues[4]}"
    }

    /** "kl. 22.37": en tur fra i dag, nævnt blandt andre fra i dag. */
    fun clock(name: String): String {
        val m = Regex("""\d{8}_(\d{2})(\d{2})""").find(name) ?: return name
        return "kl. ${m.groupValues[1]}.${m.groupValues[2]}"
    }

    /** "48 kB" eller "3,2 MB". */
    fun size(bytes: Long): String =
        if (bytes < 1_000_000) "${maxOf(1, bytes / 1000)} kB" else String.format(Locale.forLanguageTag("da-DK"), "%.1f MB", bytes / 1e6)
}
