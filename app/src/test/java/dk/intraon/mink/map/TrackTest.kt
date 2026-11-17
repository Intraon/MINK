package dk.intraon.mink.map

import dk.intraon.mink.core.Hub
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackTest {
    /** En måling hvert fjerde sekund, 5 m længere mod nord for hver; null = lyttet uden at høre målet. */
    private fun walk(t0: Long, lat0: Double, levels: List<Double?>) = levels.mapIndexed { i, l ->
        Hub.Point(t0 + i * 4_000L, lat0 + i * 0.000045, 9.0, l, null, null, 0, 0.0)
    }

    @Test
    fun sporetBindesIkkeSammenHenOverEnPause() {
        // to stykker vej med 40 sekunders pause imellem, og ét sted for sig selv ti minutter senere
        val first = walk(0L, 56.0, listOf(-70.0, -68.0, -66.0))
        val second = walk(8_000L + 40_000L, 56.01, listOf(-60.0, -61.0))
        val alone = walk(600_000L, 56.02, listOf(-75.0))
        val pieces = Track.pieces(second + alone + first)

        val lines = pieces.filter { it.from !== it.to }
        assertEquals(3, lines.size)
        assertTrue(lines.none { it.from in first && it.to in second })
        assertEquals(listOf(alone[0]), pieces.filter { it.from === it.to }.map { it.from })
    }

    @Test
    fun denStaerkesteMaalingLiggerOeverstHvorTurenGaarSammeVejToGange() {
        // ud med ryggen til senderen, hjem med ansigtet mod den, og et stille stykke midt på
        val out = walk(0L, 56.0, listOf(-72.0, -74.0, null, null, -73.0))
        val home = walk(100_000L, 56.0, listOf(-61.0, -58.0, -60.0))
        val levels = Track.pieces(out + home).map { it.level }

        assertEquals(null, levels.first())
        assertEquals(-58.0, levels.last()!!, 0.0)
        assertEquals(levels.filterNotNull().sorted(), levels.filterNotNull())
    }
}
