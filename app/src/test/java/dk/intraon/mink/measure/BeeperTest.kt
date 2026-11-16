package dk.intraon.mink.measure

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Bippet skal følge signalet, ikke gain: man kører efter lyden og ser ikke på gain. */
class BeeperTest {
    @Test
    fun sammeSignalGiverSammeToneNaarGainSkrues() {
        // samme signal målt ved 29,7 og 25,4 dB gain: 4,3 dB lavere i dBFS, samme niveau
        val foer = Beeper.toneHz(-30.0 - 29.7)
        val efter = Beeper.toneHz(-34.3 - 25.4)
        assertEquals(foer, efter, 0.01)
    }

    @Test
    fun tonenStigerMedNiveauetOgStopperIEnderne() {
        assertEquals(300.0, Beeper.toneHz(-120.0), 0.01)
        assertEquals(300.0, Beeper.toneHz(-96.0), 0.01)
        assertEquals(1380.0, Beeper.toneHz(-60.0), 0.01)
        assertEquals(2400.0, Beeper.toneHz(-26.0), 0.01)
        assertEquals(2400.0, Beeper.toneHz(0.0), 0.01)
        assertTrue(Beeper.toneHz(-59.0) > Beeper.toneHz(-60.0))
    }
}
