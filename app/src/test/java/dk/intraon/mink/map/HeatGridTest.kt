package dk.intraon.mink.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeatGridTest {
    @Test
    fun maalingerSamlesIFelterOgDetStaerkesteFindes() {
        val g = HeatGrid(200.0, 56.0)
        // to målinger ca. 30 m fra hinanden (samme felt), én 2 km væk, og et stille punkt et tredje sted
        g.add(56.03610, 10.47410, -70.0)
        g.add(56.03630, 10.47430, -60.0)
        g.add(56.05400, 10.47400, -90.0)
        g.add(56.07000, 10.47400, null)
        assertEquals(3, g.cells.size)
        val best = g.strongest()!!
        assertEquals(-65.0, best.second, 1e-9)
        val b = g.bounds(best.first)
        assertTrue("feltet rummer målingen", 56.0361 in b[0]..b[2] && 10.4741 in b[1]..b[3])
        assertEquals("200 m i nord-syd", 200.0, (b[2] - b[0]) * 110_540.0, 0.01)
        assertEquals(-90.0 to -65.0, g.range())
        assertEquals(1, g.cells.values.count { it.mean == null && it.quiet == 1 })
    }

    @Test
    fun udenMaalingerErDerIntetStaerkesteFelt() {
        val g = HeatGrid(100.0, 56.0)
        g.add(56.0, 9.0, null)
        assertNull(g.strongest())
        assertNull(g.range())
    }

    @Test
    fun feltstoerrelsenFoelgerZoom() {
        assertEquals(25.0, HeatGrid.sizeFor(3.0), 0.0)
        assertEquals(200.0, HeatGrid.sizeFor(130.0), 0.0)
        assertEquals(50_000.0, HeatGrid.sizeFor(9e9), 0.0)
    }
}
