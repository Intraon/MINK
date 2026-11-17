package dk.intraon.mink.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripFilesTest {
    @Test fun tidsstempletStaarIFilnavnet() {
        assertEquals("20261116_223740", TripFiles.stamp("mink_20261116_223740.jsonl.txt"))
        assertEquals("20261116_223740", TripFiles.stamp("mink_20261116_223740.iq.wav"))
        assertEquals("20261116_224039", TripFiles.stamp("MINK_pejling_20261116_224039.jpg"))
        assertNull(TripFiles.stamp(".nomedia"))
    }

    @Test fun billederHoererTilDenTurDeErTagetUnder() {
        val trip = "20261116_223740"; val next = "20261116_230110"
        assertTrue(TripFiles.inTrip("MINK_pejling_20261116_224039.jpg", trip, next))
        assertTrue("målebilledet fra Billede-knappen", TripFiles.inTrip("MINK_20261116_225901.png", trip, next))
        assertFalse("taget under den næste tur", TripFiles.inTrip("MINK_pejling_20261116_230500.jpg", trip, next))
        assertFalse("taget før turen", TripFiles.inTrip("MINK_20261116_220000.png", trip, next))
        assertTrue("den nyeste tur har ingen øvre grænse", TripFiles.inTrip("MINK_pejling_20261117_010000.jpg", next, null))
    }

    @Test fun naesteTurFindesBlandtAlle() {
        val all = listOf("20261116_230110", "20261116_223740", "20261115_152700")
        assertEquals("20261116_223740", TripFiles.nextAfter("20261115_152700", all))
        assertEquals("20261116_230110", TripFiles.nextAfter("20261116_223740", all))
        assertNull(TripFiles.nextAfter("20261116_230110", all))
    }

    @Test fun delingTilbyderKunDetTurenHarOgHuskerValget() {
        assertEquals(listOf(TripFiles.SHARE_LOG), TripFiles.shareLevels(hasWave = false, hasPics = false))
        assertEquals(listOf(TripFiles.SHARE_LOG, TripFiles.SHARE_WAVE, TripFiles.SHARE_ALL), TripFiles.shareLevels(hasWave = true, hasPics = true))
        val all = TripFiles.shareLevels(hasWave = true, hasPics = true)
        assertEquals("det hele er valgt, til brugeren vælger andet", TripFiles.SHARE_ALL, TripFiles.shareLevel(TripFiles.SHARE_ALL, all))
        assertEquals("uden billeder er bølgeformen det meste, der kan deles", TripFiles.SHARE_WAVE,
            TripFiles.shareLevel(TripFiles.SHARE_ALL, TripFiles.shareLevels(hasWave = true, hasPics = false)))
        assertEquals("et valg uden billeder bliver ikke til et valg med billeder", TripFiles.SHARE_LOG,
            TripFiles.shareLevel(TripFiles.SHARE_WAVE, TripFiles.shareLevels(hasWave = false, hasPics = true)))
    }
}
