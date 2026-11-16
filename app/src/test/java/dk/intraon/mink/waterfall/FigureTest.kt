package dk.intraon.mink.waterfall

import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Figurens beregninger. Selve tegningen ses med tools/FigurePreview.java. */
class FigureTest {
    private fun pack(db: Double) = (((db - Scope.MIN_DB) / (Scope.MAX_DB - Scope.MIN_DB)).coerceIn(0.0, 1.0) * 255).toInt().toByte()

    /** 30 s, 740 søjler a 500 Hz fra 869,380 MHz: støj, et udbrud på 0,4 s hvert 4. s i kanalen og et bredt signal i 1 s. */
    private fun sample(): Figure.Input {
        val rows = 750; val bins = 740; val lo = 869_380_000.0; val binHz = 500.0
        val rnd = Random(7)
        val db = ByteArray(rows * bins)
        for (r in 0 until rows) for (c in 0 until bins) {
            val hz = lo + (c + 0.5) * binHz; val sec = r * 0.04
            var v = rnd.nextGaussian() * 1.2
            val phase = (sec - 1.8) % 4.0
            if (sec >= 1.8 && phase < 0.4 && Math.abs(hz - 869_616_000.0) < 5_000) v = 31.0 + rnd.nextGaussian()
            if (sec >= 3.3 && (sec - 3.3) % 4.0 < 0.4 && Math.abs(hz - 869_470_000.0) < 4_000) v = 18.0 + rnd.nextGaussian()
            if (sec in 26.3..26.8 && Math.abs(hz - 869_618_000.0) < 31_250) v = 45.0
            db[r * bins + c] = pack(v)
        }
        return Figure.Input(rows, bins, db, lo, binHz, 0.04, 869_618_000.0, 62_500.0, 869_607_000.0, 869_625_000.0, "målet", "MINK · 16.11.2026 21:45:10 · de seneste 30 s · gain 20,7 dB")
    }

    @Test fun sporetFolgerUdbruddene() {
        val inp = sample()
        val tr = Figure.trace(inp)
        assertEquals(750, tr.size)
        assertTrue("stille mellem udbruddene", tr[20] < 4.0)
        assertTrue("udbruddet ses i sporet", tr[(2.0 / 0.04).toInt()] > 20.0)
        // udbrud ved 1,8 s og derefter hvert 4. s (7 stk.), plus det brede signal ved 26,3 s
        val on = Figure.onsets(tr, inp.rowSec).map { it * 0.04 }
        assertEquals(8, on.size)
        for (i in 0 until 7) assertEquals(1.8 + 4 * i, on[i], 0.05)
    }

    @Test fun etUdbrudDerErIGangVedStartTaellerIkke() {
        val tr = DoubleArray(100) { if (it < 10 || it in 50..59) 30.0 else 0.0 }
        assertEquals(listOf(50), Figure.onsets(tr, 0.04))
    }

}
