package dk.intraon.mink.ui

/**
 * Tegningen af båndet i indstillingerne: hvor båndet, din kanal og det, modtageren ser ad gangen, ligger i forhold
 * til hinanden. Alle mål er brøkdele af tegningens bredde (0 = venstre kant, 1 = højre). Ren Kotlin.
 */
class BandPicture(
    val bandFrom: Float, val bandTo: Float,
    val seenFrom: Float, val seenTo: Float,
    /** Den del af din kanal, der ligger inden for tegningen, eller null. */
    val channel: Pair<Float, Float>?,
    val tooWide: Boolean,
) {
    companion object {
        /**
         * Tegningen for båndet [loMhz]–[hiMhz] og kanalen [channelMhz] med bredden [channelKhz] (0 = ingen kanal).
         * Kan båndet være i det, modtageren ser, fylder det sete hele bredden med båndet til venstre i det. Er
         * båndet for bredt, fylder båndet hele bredden, og det sete ligger omkring kanalen, eller midt i båndet
         * uden en kanal. Null, hvis båndet ikke har en bredde.
         */
        fun of(loMhz: Double?, hiMhz: Double?, channelMhz: Double, channelKhz: Double): BandPicture? {
            if (loMhz == null || hiMhz == null || hiMhz <= loMhz) return null
            val width = SettingsCheck.widthKhz(loMhz, hiMhz)
            val tooWide = width > SettingsCheck.MAX_BAND_KHZ
            val span = if (tooWide) width else SettingsCheck.MAX_BAND_KHZ
            fun at(mhz: Double) = ((mhz - loMhz) * 1000 / span).toFloat()
            val chan = if (channelMhz <= 0 || channelKhz <= 0) null else {
                val from = at(channelMhz - channelKhz / 2000); val to = at(channelMhz + channelKhz / 2000)
                if (to <= 0f || from >= 1f) null else from.coerceAtLeast(0f) to to.coerceAtMost(1f)
            }
            if (!tooWide) return BandPicture(0f, (width / span).toFloat(), 0f, 1f, chan, false)
            val center = if (channelMhz > loMhz && channelMhz < hiMhz) channelMhz else (loMhz + hiMhz) / 2
            val half = SettingsCheck.MAX_BAND_KHZ / 2000
            val from = (center - half).coerceIn(loMhz, hiMhz - 2 * half)
            return BandPicture(0f, 1f, at(from), at(from + 2 * half), chan, true)
        }
    }
}
