package dk.intraon.mink.ui

import java.util.Locale

/** Reglerne for det bånd, der tastes i indstillingerne. Ren Kotlin. */
object SettingsCheck {
    /**
     * Det bredeste bånd, der kan lyttes på, i kHz. Modtageren sampler 1,024 MHz ad gangen, og kanterne af det
     * udsnit er dæmpede, så 880 kHz er det, der kan bruges.
     */
    const val MAX_BAND_KHZ = 880.0

    /** Båndets bredde i kHz. Regnes i hele Hz, så 869,178 til 870,058 er præcis 880 og ikke en anelse over. */
    fun widthKhz(loMhz: Double, hiMhz: Double): Double = Math.round((hiMhz - loMhz) * 1e6) / 1000.0

    /** Hvad der er galt med båndet [loMhz]–[hiMhz], sagt til brugeren, eller null hvis det kan bruges. */
    fun band(loMhz: Double?, hiMhz: Double?): String? = when {
        loMhz == null || hiMhz == null || hiMhz <= loMhz -> "Båndets øvre grænse skal være højere end den nedre."
        widthKhz(loMhz, hiMhz) > MAX_BAND_KHZ ->
            "Båndet er ${khz(widthKhz(loMhz, hiMhz))} kHz bredt. Modtageren ser højst ${khz(MAX_BAND_KHZ)} kHz ad gangen."
        loMhz < 24 || hiMhz > 1766 -> "Modtageren dækker ca. 24 til 1766 MHz."
        else -> null
    }

    /** Det bredeste bånd med [centerMhz] i midten, i hele kHz. */
    fun widestAround(centerMhz: Double): Pair<Double, Double> {
        val lo = Math.round((centerMhz - MAX_BAND_KHZ / 2000) * 1000) / 1000.0
        return lo to Math.round((lo + MAX_BAND_KHZ / 1000) * 1000) / 1000.0
    }

    /** "880" eller "62,5": et antal kHz uden overflødige decimaler. */
    fun khz(v: Double): String = if (v == Math.rint(v)) v.toLong().toString() else String.format(Locale.forLanguageTag("da"), "%.1f", v)
}
