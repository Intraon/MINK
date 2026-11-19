package dk.intraon.mink.direction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LobeTest {
    /** En omgang rundt med en måling for hver 10°: niveauet i hver retning, fra 0° og frem. */
    private fun turn(vararg levels: Double) = levels.mapIndexed { k, l -> k * 10.0 to l }

    @Test fun retningenErMidtenAfDenBredeTopOgIkkeDenEneStaerkesteMaaling() {
        // en bred top fra 190° til 270° med den kraftigste enkelte måling i kanten ved 220°, og en afviger i 60°
        val samples = turn(
            -69.0, -70.0, -74.0, -74.0, -77.0, -78.0, -60.0, -80.0, -79.0, -76.0, -76.0, -75.0, -75.0, -74.0, -73.0, -73.0, -72.0, -72.0,
            -71.0, -68.0, -66.0, -65.5, -63.5, -65.0, -65.0, -66.0, -66.0, -67.0, -69.0, -70.0, -70.0, -70.0, -70.0, -68.0, -68.0, -69.0,
        )
        val lobe = Lobe.of(samples)!!
        assertEquals("midten var ${lobe.center}", 245.0, lobe.center, 15.0)
        assertFalse("en enkelt afviger i 60° er ikke en retning", lobe.several)
        assertTrue("udsvinget var ${lobe.swing}", lobe.swing in 12.0..16.0)
    }

    @Test fun kommerSignaletLigeStaerktFraFlereSiderErDetFlereRetninger() {
        // mellem fire ståltårne: en top mod øst, en næsten lige så stærk mod vest og en mod nordvest
        val samples = turn(
            -66.0, -70.0, -72.0, -72.0, -70.0, -67.0, -64.0, -61.0, -58.0, -58.0, -58.0, -58.0, -58.0, -56.0, -60.0, -61.0, -60.0, -61.0,
            -61.0, -63.0, -64.0, -66.0, -72.0, -70.0, -61.0, -60.0, -60.0, -62.0, -67.0, -69.0, -69.0, -64.0, -60.0, -59.0, -60.0, -63.0,
        )
        val lobe = Lobe.of(samples)!!
        assertTrue(lobe.several)
        assertEquals("den stærkeste top ligger mod øst", 120.0, lobe.center, 20.0)
        assertEquals("toppen mod vest og den mod nordvest", 2, lobe.others.size)
        assertEquals(250.0, lobe.others[0], 12.0)
        assertEquals(330.0, lobe.others[1], 12.0)
    }

    @Test fun enPejlingKraeverEnHalvOmgang() {
        assertNull(Lobe.of((150 until 230 step 10).map { it.toDouble() to -60.0 }))
    }
}
