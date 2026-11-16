package dk.intraon.mink.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GainControlTest {
    private val gains = doubleArrayOf(0.0, 7.7, 12.5, 16.6, 20.7, 25.4, 29.7, 33.8, 37.2, 40.2, 43.9, 49.6)

    @Test
    fun derSkruesAldrigOverDetValgteGain() {
        // et svagt eller manglende signal må ikke få appen til at skrue op: så ser et fjernt signal lige så
        // tydeligt ud som et nært
        val g = GainControl(gains, 29.7)
        assertNull(g.tick(-60.0, 0, 0.0))
        assertNull(g.tick(null, 9, 0.0))
        assertNull(g.afterBurst(-60.0, 0.0))
        assertEquals(29.7, g.db, 0.0)
    }

    @Test
    fun klipningOgKraftigtSignalSaenkerToTrin() {
        val g = GainControl(gains, 20.7)
        assertEquals(12.5, g.afterBurst(-20.0, 2.0)!!.to, 0.0)
        assertEquals(0.0, g.afterBurst(-5.0, 0.0)!!.to, 0.0)
        assertEquals("det valgte gain er uændret", 20.7, g.chosenDb, 0.0)
    }

    @Test
    fun efterMaetningSkruesDerTilbageTilDetValgteMenIkkeHoejere() {
        val g = GainControl(gains, 29.7)
        assertEquals(20.7, g.afterBurst(-5.0, 0.0)!!.to, 0.0)      // tæt på senderen: to trin ned
        assertNull(g.tick(-36.0, 0, 0.0))                          // stadig kraftigt → bliv
        assertNull(g.tick(-50.0, 0, 3.5))                          // modtageren blev mættet i perioden → skru ikke op
        assertEquals(25.4, g.tick(-50.0, 0, 0.0)!!.to, 0.0)        // svagt igen → et trin tilbage
        assertNull(g.tick(null, 1, 0.0))
        assertEquals(29.7, g.tick(null, 2, 0.0)!!.to, 0.0)         // stille → tilbage til det valgte
        assertNull(g.tick(null, 9, 0.0))                           // … og ikke højere
        assertNull(g.tick(-70.0, 0, 0.0))
    }

    @Test
    fun manueltTrinFlytterValgetOgBeholderBeskyttelsen() {
        val g = GainControl(gains, 20.7)
        assertEquals(25.4, g.manual(+1)!!.to, 0.0)
        assertEquals(25.4, g.chosenDb, 0.0)
        assertNull(g.tick(-60.0, 9, 0.0))                          // der skrues ikke højere op end det nye valg
        assertEquals(16.6, g.afterBurst(-5.0, 5.0)!!.to, 0.0)      // beskyttelsen virker stadig
        assertEquals(20.7, g.tick(null, 2, 0.0)!!.to, 0.0)
        assertEquals(25.4, g.tick(null, 2, 0.0)!!.to, 0.0)
        assertNull(g.tick(null, 2, 0.0))
    }

    @Test
    fun udenBeskyttelseStaarGainFast() {
        val g = GainControl(gains, 29.7).apply { auto = false }
        assertNull(g.afterBurst(-5.0, 5.0))
        assertNull(g.tick(-60.0, 9, 0.0))
        assertEquals(25.4, g.manual(-1)!!.to, 0.0)
    }

    @Test
    fun gainValgtIIndstillingerneGaelderMedDetSamme() {
        val g = GainControl(gains, 20.7)
        assertEquals(29.7, g.choose(30.0)!!.to, 0.0)
        assertEquals(29.7, g.chosenDb, 0.0)
        assertNull(g.choose(29.7))                                 // samme gain igen: intet skift
        assertNull(g.tick(null, 9, 0.0))
    }
}
