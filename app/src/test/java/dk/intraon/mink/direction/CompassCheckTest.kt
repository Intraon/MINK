package dk.intraon.mink.direction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompassCheckTest {
    @Test fun kompassetErKunGodtNaarFeltetLignerJordens() {
        assertTrue(CompassCheck.fieldOk(49.0, 50.5))
        assertTrue("ukendt måling tæller som i orden", CompassCheck.fieldOk(null, 50.5))
        assertTrue("en højttalermagnet tæt på", !CompassCheck.fieldOk(120.0, 50.5))
        assertTrue("uden kendt sted: Jordens felt er 25–65 µT", CompassCheck.fieldOk(45.0, null) && !CompassCheck.fieldOk(90.0, null))
        assertTrue("lav præcision meldt af telefonen", CompassCheck.poor(1, 50.0, 50.5))
        assertTrue("middel præcision og rigtigt felt er godt nok", !CompassCheck.poor(2, 50.0, 50.5))
        assertTrue("ikke meldt endnu, men feltet er forkert", CompassCheck.poor(-1, 15.0, 50.5))
    }

    @Test fun ottetallerneSkalDrejeTelefonenOmAlleTreAkser() {
        assertEquals(0.0, CompassCheck.progress(doubleArrayOf(0.0, 0.0, 0.0)), 1e-9)
        // masser af drejning om én akse (man snurrer bare rundt) er kun en tredjedel
        assertEquals(1.0 / 3, CompassCheck.progress(doubleArrayOf(50.0, 0.0, 0.0)), 1e-9)
        assertEquals(1.0, CompassCheck.progress(doubleArrayOf(7.0, 6.5, 9.0)), 1e-9)
    }
}
