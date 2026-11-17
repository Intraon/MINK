package dk.intraon.mink.direction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoseTest {
    @Test fun rosenFinderDenStaerkesteRetning() {
        val r = Rose()
        assertNull(r.best())
        // drej en omgang: stærkest omkring 240°, svagest modsat (kroppen skygger)
        for (d in 0 until 360 step 30) r.add(d + 5.0, -70.0 - 15 * (1 - Math.cos(Math.toRadians(d - 240.0))) / 2)
        assertEquals(12, r.filled())
        assertEquals(245.0, r.best()!!, 6.0)
        val (lo, hi) = r.range()!!
        assertEquals(-70.0, hi, 0.01)
        assertEquals(-85.0, lo, 0.01)
    }

    @Test fun rosenBegynderForfraNaarManFlytterSig() {
        val r = Rose()
        r.add(100.0, -70.0, 56.0, 10.0)
        r.add(130.0, -72.0, 56.00005, 10.0)      // 5,5 m: samme sted
        assertEquals(2, r.filled())
        r.add(200.0, -60.0, 56.0005, 10.0)       // 55 m væk
        assertEquals(1, r.filled())
        assertEquals(56.0005, r.lat, 1e-9)
    }

    /** En omgang rundt på samme sted: en måling for hver 10°, stærkest mod [toward], kroppen skygger modsat. */
    private fun turn(r: Rose, toward: Double, lat: Double, t0: Long, from: Int = 0, until: Int = 360) {
        for (d in from until until step 10) {
            r.add(d + 5.0, -62.0 - 22 * (1 - Math.cos(Math.toRadians(d + 5.0 - toward))) / 2, lat, 9.0, t = t0 + d * 400L)
        }
    }

    @Test fun stedetsStaerkesteRetningAfleveresNaarManGaarVidere() {
        val r = Rose()
        turn(r, 185.0, 56.0, 1_000L)
        val left = r.add(190.0, -70.0, 56.0005, 9.0, t = 200_000L)!!     // 55 m væk
        assertEquals(185.0, left.best, 6.0)
        assertEquals(-62.0, left.level, 0.01)
        assertEquals(1_000L + 180 * 400L, left.at)
        assertEquals(56.0, left.lat, 1e-9)
        assertEquals(36, left.rose.filled())
        assertEquals("rosen er begyndt forfra på det nye sted", 1, r.filled())
    }

    @Test fun etStedUdenEnHalvOmgangGiverIngenPejling() {
        // man går med telefonen fremme: nogle få retninger pr. sted
        val r = Rose()
        turn(r, 185.0, 56.0, 0L, from = 150, until = 230)
        assertNull(r.finish())
        assertNull(r.add(190.0, -70.0, 56.0005, 9.0))
    }

    @Test fun etStedAfleveresKunEnGang() {
        val r = Rose()
        turn(r, 185.0, 56.0, 0L)
        assertEquals(185.0, r.finish()!!.best, 6.0)
        assertNull(r.finish())
        assertNull("heller ikke, når man går videre", r.add(190.0, -70.0, 56.0005, 9.0))
    }
}
