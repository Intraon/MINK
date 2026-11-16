package dk.intraon.mink.waterfall

/**
 * Tegnefladen, figuren tegnes på. Android-udgaven ligger i FigurePen.kt; testen har sin egen, så den samme
 * figur kan tegnes og ses uden en telefon. Farver er ARGB.
 */
interface Pen {
    fun rect(x: Float, y: Float, w: Float, h: Float, argb: Int)
    fun frame(x: Float, y: Float, w: Float, h: Float, argb: Int, width: Float)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float, argb: Int, width: Float, dash: Float = 0f)
    fun polyline(xs: FloatArray, ys: FloatArray, n: Int, argb: Int, width: Float)
    /** [align]: -1 = venstre, 0 = midt, 1 = højre om x. [vertical] drejer teksten, så den læses nedefra og op. */
    fun text(s: String, x: Float, y: Float, size: Float, argb: Int, align: Int = -1, vertical: Boolean = false)
    fun textWidth(s: String, size: Float): Float
    /** [px] er [w]×[h] punkter række for række; de strækkes ud over feltet. */
    fun image(px: IntArray, w: Int, h: Int, x: Float, y: Float, dw: Float, dh: Float)
}
