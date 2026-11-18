package dk.intraon.mink.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsCheckTest {
    @Test fun etForBredtBaandAfvisesMedSinBreddeITal() {
        assertEquals("Båndet er 2000 kHz bredt. Modtageren ser højst 880 kHz ad gangen.", SettingsCheck.band(868.0, 870.0))
        assertNull(SettingsCheck.band(869.380, 869.750))
        val (lo, hi) = SettingsCheck.widestAround(869.618)
        assertEquals(869.178, lo, 1e-9); assertEquals(870.058, hi, 1e-9)
        assertNull("det bredeste bånd omkring kanalen er selv lovligt", SettingsCheck.band(lo, hi))
    }

    @Test fun tegningenViserHvorMegetEtForBredtBaandRagerUd() {
        val ok = BandPicture.of(869.380, 869.750, 869.618, 62.5)!!
        assertEquals("370 af 880 kHz", 370f / 880f, ok.bandTo, 1e-4f)
        assertTrue("kanalen ligger inde i båndet", ok.channel!!.first > ok.bandFrom && ok.channel!!.second < ok.bandTo)
        val wide = BandPicture.of(868.0, 870.0, 869.618, 62.5)!!
        assertTrue(wide.tooWide)
        assertEquals("det sete er 880 af de 2000 kHz", 0.44f, wide.seenTo - wide.seenFrom, 1e-4f)
        assertTrue("og ligger omkring kanalen", wide.seenFrom < wide.channel!!.first && wide.seenTo > wide.channel!!.second)
        assertNull(BandPicture.of(870.0, 868.0, 0.0, 0.0))
    }
}
