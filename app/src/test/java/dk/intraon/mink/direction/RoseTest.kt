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
}
