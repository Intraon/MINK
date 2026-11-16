package dk.intraon.mink.direction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadingFilterTest {
    @Test fun filtretDaemperSitrenOgKlarerNord() {
        val f = HeadingFilter(0.25)
        var out = 0.0
        // sitrer ±6° omkring nord, 50 gange i sekundet
        for (i in 0 until 100) out = f.add(if (i % 2 == 0) 354.0 else 6.0, i * 0.02)
        assertTrue("bliver ved nord, ikke 180°", Math.abs(Bearing.diff(out, 0.0)) < 1.5)
        // et rigtigt drej følger med inden for et sekund
        for (i in 100 until 150) out = f.add(90.0, i * 0.02)
        assertEquals(90.0, out, 3.0)
    }
}
