package dk.intraon.mink.direction

/**
 * Hvor kompasrosen og teksterne står, når telefonen ligger fladt. Feltet er [w] × [h] punkter, og [d] er skærmens
 * tæthed. Øverst ligger feltet med retningen, nederst knappen Tag pejling; rosen fylder det, der er imellem.
 */
class FlatLayout(w: Float, h: Float, d: Float) {
    /** Rosens radius: så stor, som bredden og pladsen mellem top og bund tillader. */
    val radius: Float = minOf(w * 0.40f, (h - (TOP + ABOVE + BELOW + BUTTON) * d) / 2f).coerceAtLeast(MIN_RADIUS * d)
    val centerY: Float = (TOP + ABOVE) * d + radius
    /** Grundlinjen for teksten under rosen (den stærkeste retning). Den står over knappen, aldrig bag den. */
    val bestY: Float = centerY + radius + 26 * d
    /**
     * Grundlinjen for linjen om omgangen, under den stærkeste retning. Null på et felt, der er så lavt, at linjen
     * ville stå bag knappen: dér viser ringen om rosen omgangen alene.
     */
    val turnY: Float? = (bestY + 18 * d).takeIf { it + 5 * d <= h - BUTTON * d }
    /** Grundlinjen for antallet af gemte pejlinger: øverst til venstre, hvor rosen endnu er smal. */
    val countY: Float = (TOP + 14) * d

    companion object {
        /** Feltet med retningen øverst. */
        const val TOP = 58f
        /** Luft over rosen til pilen og mærket for den stærkeste retning. */
        const val ABOVE = 24f
        /** De to linjer under rosen med luft ned til knappen. */
        const val BELOW = 58f
        /** Så højt over feltets bund når knappen Tag pejling. */
        const val BUTTON = 62f
        const val MIN_RADIUS = 40f
    }
}
