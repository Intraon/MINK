package dk.intraon.mink.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mønstret fra den første rigtige test: tre sendere med 4 s mellemrum, og en dongle der driver 0,2 kHz/s. */
class SourceTrackerTest {
    private class Hit(val t: Long, val hz: Double, val snr: Double, val lvl: Double)

    private fun hits(seconds: Int): List<Hit> {
        val out = ArrayList<Hit>()
        for (k in 0 until seconds / 4) {
            val t = k * 4000L; val drift = t / 1000.0 * 200 - 5000      // −5 kHz ved start, +0,2 kHz/s
            out.add(Hit(t + 1840, 869_612_500.0 + drift, 12.0, -90.0))
            if (k % 3 != 1) out.add(Hit(t + 3165, 869_512_500.0 + drift, 6.0, -96.0))
            // den kraftigste sender kommer nogle gange i to stykker
            out.add(Hit(t + 3500, 869_412_500.0 + drift, 25.0, -70.0))
            if (k % 4 == 0) out.add(Hit(t + 3690, 869_412_500.0 + drift + 600, 27.0, -69.0))
        }
        return out
    }

    @Test
    fun udenValgtMaalErIngenSenderMaal() {
        val tr = SourceTracker(4_800.0, null)
        for (h in hits(60)) assertFalse(tr.add(h.t, h.hz, h.snr, h.lvl).second)
        assertEquals(3, tr.snapshot().size)
        assertTrue(tr.snapshot().none { it.target })
        assertNull(tr.targetHz)
    }

    @Test
    fun detValgteMaalFoelgesGennemDriftOgSkifterAldrigTilEnStaerkereSender() {
        // målet er den svageste af de to tydelige sendere; den anden er 20 dB kraftigere hele vejen
        val tr = SourceTracker(4_800.0, 869_612_500.0)
        var target = 0
        val ids = HashSet<Int>()
        for (h in hits(120)) { val (id, isTarget) = tr.add(h.t, h.hz, h.snr, h.lvl); if (isTarget) { target++; ids.add(id) } }
        val s = tr.snapshot()
        assertEquals("tre sendere, ikke flere", 3, s.size)
        assertEquals("alle målets 30 bursts", 30, target)
        assertEquals("og kun én sender har været mål", 1, ids.size)
        // efter 2 minutter er målet drevet 19 kHz – langt uden for de ±10 kHz – og følges stadig
        assertEquals(869_612_500.0 - 5000 + 116 * 200, s[0].freqHz, 500.0)
        assertEquals(s[0].freqHz, tr.targetHz!!, 1.0)
    }

    @Test
    fun etMaalDerIkkeErHoertVaelgesIkke() {
        val tr = SourceTracker(4_800.0, 869_537_500.0)      // en frekvens, ingen sender på
        for (h in hits(40)) assertFalse(tr.add(h.t, h.hz, h.snr, h.lvl).second)
        assertTrue(tr.snapshot().none { it.target })
        assertEquals(869_537_500.0, tr.targetHz!!, 0.0)
    }

    @Test
    fun maaletVaelgesOmOgFjernesIHaanden() {
        val tr = SourceTracker(4_800.0, null)
        for (h in hits(40)) tr.add(h.t, h.hz, h.snr, h.lvl)
        val mid = tr.snapshot().first { kotlin.math.abs(it.freqHz - 869_512_500.0) < 15_000 }
        tr.retarget(mid.freqHz + 3_000)                     // trykket lidt ved siden af
        assertEquals(mid.id, tr.snapshot().first { it.target }.id)
        assertEquals(mid.freqHz, tr.targetHz!!, 1.0)
        tr.retarget(null)
        assertTrue(tr.snapshot().none { it.target })
    }

    @Test
    fun senderensBreddeUdjaevnesOverMaalingerne() {
        val tr = SourceTracker(4_800.0, 869_612_500.0)
        tr.add(0, 869_612_500.0, 20.0, -70.0, 12_000.0)
        tr.add(4000, 869_612_600.0, 8.0, -85.0, null)          // for svag til, at bredden kunne måles
        tr.add(8000, 869_612_700.0, 20.0, -70.0, 10_000.0)
        val v = tr.snapshot().single()
        assertTrue(v.target)
        assertEquals(2, v.bwN)
        assertEquals(11_600.0, v.bwHz!!, 1.0)
    }

    @Test fun kilderOgMaalFoelgerMedNaarKorrektionenAendres() {
        val tr = SourceTracker(5_000.0, 869_644_500.0)
        tr.add(0, 869_644_500.0, 20.0, -70.0)
        tr.add(100, 869_538_400.0, 6.0, -85.0)
        // korrektionen flytter alt 5,5 kHz ned
        tr.shift(-5_500.0)
        assertEquals(869_639_000.0, tr.targetHz!!, 1.0)
        // næste måling af målet ligger på den nye plads og hører til samme kilde, som stadig er målet
        val (id, isTarget) = tr.add(4_000, 869_639_100.0, 21.0, -70.0)
        assertEquals(1, id)
        assertTrue(isTarget)
        assertEquals("ingen dubletter", 2, tr.snapshot().size)
    }

}
