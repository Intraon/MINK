package dk.intraon.mink.direction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class CrossingTest {
    // en opdigtet sender og steder omkring den
    private val txLat = 55.0; private val txLon = 10.0

    /** Stedet [meters] fra senderen i retningen [fromTx], med en pejling, der rammer [miss] grader ved siden af senderen. */
    private fun sight(fromTx: Double, meters: Double, miss: Double, swing: Double? = 18.0, several: Boolean = false): Crossing.Sight {
        val (lat, lon) = Bearing.destination(txLat, txLon, fromTx, meters)
        val north = (txLat - lat) * 111_320.0; val east = (txLon - lon) * 111_320.0 * cos(Math.toRadians(lat))
        return Crossing.Sight(lat, lon, Bearing.norm(Math.toDegrees(atan2(east, north)) + miss), swing, several)
    }

    private fun Crossing.fromTx() = Bearing.meters(lat, lon, txLat, txLon)

    /** Ligger senderen inden for ellipsen? */
    private fun Crossing.holdsTx(): Boolean {
        val north = (txLat - lat) * 111_320.0; val east = (txLon - lon) * 111_320.0 * cos(Math.toRadians(lat))
        val a = Math.toRadians(axisDeg)
        val along = east * sin(a) + north * cos(a); val across = east * cos(a) - north * sin(a)
        return (along / majorM).let { it * it } + (across / minorM).let { it * it } <= 1.0
    }

    @Test fun pejlingerFraTreStederMedHverSinFejlGiverEtStedTaetPaaSenderen() {
        val c = Crossing.of(listOf(sight(40.0, 4_000.0, 8.0), sight(120.0, 3_000.0, -7.0), sight(260.0, 5_000.0, 5.0)))!!
        assertEquals(3, c.used)
        // otte grader ved siden af på fire kilometer er 560 m, så stedet kan ikke blive meget bedre end det
        assertTrue("stedet lå ${c.fromTx()} m fra senderen", c.fromTx() < 900)
        assertTrue("senderen ligger inden for området (${c.majorM} × ${c.minorM} m)", c.holdsTx())
        assertTrue("området er ikke større, end pejlingerne giver grund til: ${c.majorM} m", c.majorM < 2_500)
    }

    @Test fun pejlingerTaetPaaBestemmerStedetSelvOmDeFjerneRammerVedSiden() {
        // to fjerne pejlinger, der begge rammer ti grader ved siden af, og to nære, der rammer rigtigt
        val far = listOf(sight(60.0, 5_000.0, 10.0), sight(100.0, 5_000.0, 10.0))
        val alone = Crossing.of(far)!!
        val withNear = Crossing.of(far + sight(190.0, 400.0, 0.0) + sight(280.0, 600.0, 0.0))!!
        assertTrue("uden de nære lå stedet ${alone.fromTx()} m væk", alone.fromTx() > 600)
        assertTrue("med de nære lå stedet ${withNear.fromTx()} m væk", withNear.fromTx() < 100)
        assertTrue(withNear.majorM < alone.majorM / 3)
    }

    @Test fun genskinOgEnPejlingDerPegerEtAndetStedHenRegnesIkkeMed() {
        val good = listOf(sight(40.0, 4_000.0, 4.0), sight(120.0, 3_000.0, -5.0), sight(260.0, 5_000.0, 3.0))
        val c = Crossing.of(
            good + sight(200.0, 2_000.0, 95.0) +                    // peger på tværs: et genskin, der ligner en retning
                sight(330.0, 1_000.0, 60.0, several = true) +       // stedet havde flere retninger
                sight(10.0, 2_500.0, 40.0, swing = 6.0),            // stedet havde ingen tydelig retning
        )!!
        assertEquals(3, c.used)
        assertEquals("den, der pegede på tværs, er udeladt", 1, c.left)
        assertTrue("stedet lå ${c.fromTx()} m fra senderen", c.fromTx() < 600)
        assertTrue(c.holdsTx())
    }

    @Test fun pejlingerDerIkkeKrydserGiverIntetSted() {
        assertNull("kun ét sted", Crossing.of(listOf(sight(40.0, 4_000.0, 0.0), sight(40.0, 4_010.0, 3.0))))
        assertNull("næsten parallelle", Crossing.of(listOf(sight(40.0, 4_000.0, 0.0), sight(48.0, 4_000.0, 0.0))))
        // to pejlinger, der peger væk fra hinanden: linjerne mødes bag de steder, de er taget fra
        assertNull("mødes bag stederne", Crossing.of(listOf(sight(90.0, 2_000.0, 120.0), sight(270.0, 2_000.0, -120.0))))
    }

    @Test fun omridsetLiggerOmStedetOgErLangtDerHvorPejlingerneKrydserSpidst() {
        // to pejlinger fra øst med 25° imellem: de krydser spidst, så området er langt i øst-vestlig retning
        val c = Crossing.of(listOf(sight(78.0, 3_000.0, 0.0), sight(103.0, 3_000.0, 0.0)))!!
        assertTrue("aksen var ${c.axisDeg}°", c.axisDeg in 70.0..110.0)
        assertTrue(c.majorM > 3 * c.minorM)
        val far = c.outline().maxOf { Bearing.meters(it.first, it.second, c.lat, c.lon) }
        assertEquals(c.majorM, far, c.majorM * 0.02)
    }
}
