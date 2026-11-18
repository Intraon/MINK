package dk.intraon.mink.measure

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BandShiftTest {
    private val lo = 869_380_000.0; private val hi = 869_750_000.0

    @Test fun etTraekFlytterBaandetIHeleTrinOgFinderHjemIgen() {
        val away = BandShift.moved(lo, hi, -481_700.0, lo, hi)!!
        assertEquals("båndet står på hele 5 kHz", 868_900_000.0, away.first, 0.1)
        assertEquals("og er lige så bredt som før", hi - lo, away.second - away.first, 0.1)
        assertEquals("et træk næsten hele vejen tilbage ender hjemme", lo to hi, BandShift.moved(away.first, away.second, 470_000.0, lo, hi))
        assertNull("et kort træk hjemmefra flytter ikke noget", BandShift.moved(lo, hi, 9_000.0, lo, hi))
    }

    @Test fun maaletHoeresKunNaarDetLiggerIDetBaandDerLyttesPaa() {
        assertTrue(BandShift.hears(lo, hi, 869_639_000.0))
        assertFalse("båndet er trukket ned under målet", BandShift.hears(868_900_000.0, 869_270_000.0, 869_639_000.0))
        assertTrue("uden et mål er der intet at gå glip af", BandShift.hears(868_900_000.0, 869_270_000.0, null))
    }
}
