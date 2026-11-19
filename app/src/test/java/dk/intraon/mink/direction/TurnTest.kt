package dk.intraon.mink.direction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnTest {
    /** En drejning fra [from]° til [to]° med et udbrud for hver 9°, som når målet sender hvert fjerde sekund. */
    private fun sweep(from: Int, to: Int) = (from until to step 9).map { Bearing.norm(it.toDouble()) to -60.0 }

    @Test fun omgangenErFoerstFaerdigNaarDerErMaaltHeleVejenRundt() {
        // drejet fra nord over øst til sydvest: der mangler stadig fra vest til nord
        val part = Turn.of(sweep(0, 230))
        assertFalse(part.done)
        val (mid, width) = part.missing!!
        assertEquals("der mangler omkring nordvest, midten var $mid", 300.0, mid, 15.0)
        assertTrue("hullet var $width°", width in 90..130)

        val full = Turn.of(sweep(0, 360))
        assertTrue("målt i ${full.count} af ${full.of} retninger", full.done)
        assertNull(full.missing)
    }

    @Test fun etEnkeltUdbrudIEnRetningErIkkeNokTilAtDenErMaalt() {
        // hele vejen rundt, men kun ét udbrud i nærheden af øst
        val samples = sweep(0, 60) + listOf(90.0 to -60.0) + sweep(130, 360)
        val turn = Turn.of(samples)
        assertFalse(turn.done)
        assertEquals(90.0, turn.missing!!.first, 10.0)
    }
}
