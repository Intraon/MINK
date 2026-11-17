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

    /** Hvor meget en deling tager med, fra mindst til mest. Tallene gemmes i indstillingerne. */
    const val SHARE_LOG = 0
    const val SHARE_WAVE = 1
    const val SHARE_ALL = 2

    /**
     * De trin, en tur kan deles på: loggen alene, med bølgeform, hvis turen har én, og det hele. Det hele findes
     * altid, fordi kortet over turen laves, når den deles.
     */
    fun shareLevels(hasWave: Boolean): List<Int> = listOfNotNull(SHARE_LOG, SHARE_WAVE.takeIf { hasWave }, SHARE_ALL)

    /** Det trin, dialogen står på: det, der blev valgt sidst, eller det højeste under det, som turen har filer til. */
    fun shareLevel(remembered: Int, levels: List<Int>): Int = levels.lastOrNull { it <= remembered } ?: SHARE_LOG

    /**
     * Navnet på et kortbillede taget [now] ("20261117_130000"). Viser kortet én tidligere tur, står dens tidsstempel
     * ([shown]) først i navnet, så billedet hører til den tur og ikke til den, der er den nyeste, mens billedet tages.
     */
    fun mapPictureName(now: String, shown: String?): String =
        if (shown == null) "MINK_kort_$now.png" else "MINK_kort_${shown}_$now.png"

    /**
     * Navnet på det kort over hele turen [session], som laves, når turen deles. Der er ét pr. tur; deles turen
     * igen, erstattes det.
     */
    fun tripMapName(session: String): String = "MINK_kort_$session.png"

    /** "16.11.2026 kl. 22.37" ud af et filnavn med tidsstempel; navnet selv, hvis det ikke har et. */
    fun title(name: String): String {
        val m = Regex("""(\d{4})(\d{2})(\d{2})_(\d{2})(\d{2})""").find(name) ?: return name
        return "${m.groupValues[3]}.${m.groupValues[2]}.${m.groupValues[1]} kl. ${m.groupValues[4]}.${m.groupValues[5]}"
    }

    /** "48 kB" eller "3,2 MB". */
    fun size(bytes: Long): String =
        if (bytes < 1_000_000) "${maxOf(1, bytes / 1000)} kB" else String.format(Locale.forLanguageTag("da-DK"), "%.1f MB", bytes / 1e6)
}
