package dk.intraon.mink.direction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BearingTest {
    /** Rotationsmatrix for en telefon holdt lodret som et kamera, hvor bagsiden peger mod [azDeg] og vipper [tiltDeg] op. */
    private fun upright(azDeg: Double, tiltDeg: Double = 0.0): FloatArray {
        val a = Math.toRadians(azDeg); val t = Math.toRadians(tiltDeg)
        // kameraets retning i verden (øst, nord, op); telefonens Z-akse peger modsat
        val cx = Math.sin(a) * Math.cos(t); val cy = Math.cos(a) * Math.cos(t); val cz = Math.sin(t)
        // telefonens X-akse (mod højre set forfra) ligger vandret, vinkelret på kameraets retning
        val xx = Math.cos(a); val xy = -Math.sin(a); val xz = 0.0
        // Y = Z × X, med Z = −kameraretningen
        val zx = -cx; val zy = -cy; val zz = -cz
        val yx = zy * xz - zz * xy; val yy = zz * xx - zx * xz; val yz = zx * xy - zy * xx
        return floatArrayOf(xx.toFloat(), yx.toFloat(), zx.toFloat(), xy.toFloat(), yy.toFloat(), zy.toFloat(), xz.toFloat(), yz.toFloat(), zz.toFloat())
    }

    @Test fun kameraetsRetningUdFraRotationsmatricen() {
        for (az in listOf(0.0, 90.0, 180.0, 243.0, 359.0)) {
            val (a, t) = Bearing.fromRotation(upright(az))
            assertEquals(0.0, Bearing.diff(a, az), 0.01)
            assertEquals(0.0, t, 0.01)
        }
        val (a, t) = Bearing.fromRotation(upright(120.0, 25.0))
        assertEquals(120.0, a, 0.01)
        assertEquals("vippet 25° op", 25.0, t, 0.01)
    }

    @Test fun forskelOgNormering() {
        assertEquals(20.0, Bearing.diff(10.0, 350.0), 1e-9)
        assertEquals(-20.0, Bearing.diff(350.0, 10.0), 1e-9)
        assertEquals(350.0, Bearing.norm(-10.0), 1e-9)
        assertEquals("NØ", Bearing.name(44.0))
        assertEquals("N", Bearing.name(350.0))
        assertEquals("V", Bearing.name(270.0))
    }

    @Test fun retningIBilledet() {
        // 60° synsfelt, 1000 punkter bredt: kanten af billedet ligger 30° fra midten
        assertEquals(500f, Bearing.offsetPx(30.0, 60.0, 1000f)!!, 0.5f)
        assertEquals(-500f, Bearing.offsetPx(-30.0, 60.0, 1000f)!!, 0.5f)
        assertEquals(0f, Bearing.offsetPx(0.0, 60.0, 1000f)!!, 0.01f)
        assertNull("bag kameraet", Bearing.offsetPx(170.0, 60.0, 1000f))
    }

    /** Rotationsmatrix for en telefon, der ligger fladt med skærmen op og toppen mod [azDeg]. */
    private fun flatOnTable(azDeg: Double): FloatArray {
        val a = Math.toRadians(azDeg)
        // Y (toppen) peger mod azDeg, Z (ud af skærmen) op, X = Y × Z
        val yx = Math.sin(a); val yy = Math.cos(a)
        val xx = yy; val xy = -yx
        return floatArrayOf(xx.toFloat(), yx.toFloat(), 0f, xy.toFloat(), yy.toFloat(), 0f, 0f, 0f, 1f)
    }

    @Test fun fladtPaaBordetGaelderTelefonensTop() {
        for (az in listOf(0.0, 120.0, 268.0)) {
            val p = Bearing.pointing(flatOnTable(az), false)
            assertTrue(p.flat)
            assertEquals(0.0, Bearing.diff(p.deg, az), 0.01)
            assertEquals(-90.0, p.tilt, 0.01)
        }
    }

    @Test fun topOgKameraErEnigeIOvergangenOgSkifterMedTraeghed() {
        // telefonen vippet bagover: kameraet ser 50° ned, toppen peger samme vej frem
        val r = upright(75.0, -50.0)
        val asCamera = Bearing.pointing(r, false)
        val asFlat = Bearing.pointing(r, true)
        assertTrue("50° ned er ikke nok til at skifte til fladt", !asCamera.flat)
        assertTrue("men den bliver ved med at være flad, til den er rejst mere", asFlat.flat)
        assertEquals(0.0, Bearing.diff(asCamera.deg, asFlat.deg), 0.01)
    }

}
