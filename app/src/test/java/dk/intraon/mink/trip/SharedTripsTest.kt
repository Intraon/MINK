package dk.intraon.mink.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedTripsTest {
    private val morning = "20261118_091500"; private val noon = "20261118_130210"; private val afternoon = "20261118_145148"
    private val yesterday = "20261117_150200"
    private val trips = listOf(afternoon to 212_000L, noon to 98_000L, morning to 40_000L, yesterday to 300_000L)

    @Test fun enDelingBegynderMedDagensUdelteTure() {
        val shared = SharedTrips().after(mapOf(morning to 40_000L), trips.map { it.first })
        assertEquals("turen fra i går er udelt, men hører ikke til i dag", listOf(afternoon, noon), shared.suggested(trips, "20261118"))
        val all = shared.after(mapOf(afternoon to 212_000L, noon to 98_000L), trips.map { it.first })
        assertEquals("er alt fra i dag delt, er det den nyeste tur", listOf(afternoon), all.suggested(trips, "20261118"))
        assertEquals("en dag uden ture", listOf(afternoon), SharedTrips().suggested(trips, "20261119"))
    }

    @Test fun enTurDerErVoksetSidenDenBlevDeltErUdeltIgen() {
        val shared = SharedTrips.decode(SharedTrips().after(mapOf(afternoon to 212_000L), trips.map { it.first }).encode())
        assertTrue(shared.has(afternoon, 212_000L))
        assertFalse("turen kørte videre efter delingen", shared.has(afternoon, 260_000L))
        assertFalse(shared.has(noon, 98_000L))
    }

    @Test fun enTurHvisLogErSlettetGlemmes() {
        val shared = SharedTrips().after(mapOf(yesterday to 300_000L), listOf(yesterday)).after(mapOf(noon to 98_000L), listOf(noon, afternoon))
        assertTrue(shared.has(noon, 98_000L))
        assertFalse("loggen fra i går findes ikke længere", shared.has(yesterday, 300_000L))
    }
}
