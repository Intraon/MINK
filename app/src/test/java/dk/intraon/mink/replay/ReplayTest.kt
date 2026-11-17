package dk.intraon.mink.replay

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayTest {
    private val meta = """{"type":"meta","session":"20261114_221500","band_lo":869380000,"band_hi":869750000,"chan":869618000,"chan_bw":62500,"iq_file":"mink_20261114_221500.iq.wav","iq_rate":16000}"""

    private fun burst(sec: Int, mhz: String, lvl: Double, tgt: Boolean, iq: Long, lat: Double? = 56.0) =
        """{"type":"burst","t":"2026-11-14T22:15:%02d.000+01:00","lat":%s,"lon":%s,"f":"%s","src":%d,"snr":30.1,"lvl":%.1f,"dur":390,"bw":10000%s,"iq":%d,"iqn":6240,"iqfc":%d,"iqpre":20}"""
            .format(java.util.Locale.US, sec, lat?.toString() ?: "null", if (lat == null) "null" else "9.0", mhz, if (tgt) 1 else 2, lvl, if (tgt) ""","tgt":true""" else "", iq, (mhz.toDouble() * 1e6).roundToInt())

    @Test fun turenLaesesTilbageMedAlleSendereOgDeresUdsnit() {
        // målet hvert fjerde sekund og en anden sender forskudt to sekunder
        val lines = listOf(meta) + (0 until 5).flatMap { listOf(burst(4 * it, "869.6375", -60.0 - it, true, it * 12_480L), burst(4 * it + 2, "869.4375", -78.0, false, it * 12_480L + 6_240)) }
        val r = Replay.parse(lines)!!
        assertEquals(10, r.bursts.size)
        assertEquals(5, r.target.size)
        assertEquals(-60.0, r.strongest!!, 0.01)
        assertEquals(18_000L, r.endMs - r.startMs)
        assertEquals(869_380_000.0, r.bandLoHz, 1.0)
        assertEquals("mink_20261114_221500.iq.wav", r.iqFile)

        // vinduet 7,9-10,1 s efter start: målet, der begyndte ved 8,0 s, og den anden sender, der begyndte ved 10,0 s
        val seen = r.between(r.startMs + 7_900, r.startMs + 10_100)
        assertEquals(listOf(869_637_500.0, 869_437_500.0), seen.map { it.freqHz })
        assertEquals(2 * 12_480L, seen[0].iq)
        // et udbrud, der er begyndt før vinduet, men rækker ind i det, er med
        assertEquals(1, r.between(r.startMs + 8_200, r.startMs + 8_300).size)
    }

    @Test fun stedetForEtTidspunktErDetNaermesteOgIkkeEtForGammelt() {
        val lines = listOf(meta, burst(0, "869.6375", -60.0, true, 0, lat = 56.0), burst(4, "869.6375", -61.0, true, 6_240, lat = null),
            burst(8, "869.6375", -62.0, true, 12_480, lat = 56.001), burst(50, "869.6375", -63.0, true, 18_720, lat = null))
        val r = Replay.parse(lines)!!
        assertEquals(56.0, r.fixAt(r.startMs + 3_000)!!.lat, 1e-9)
        assertEquals(56.001, r.fixAt(r.startMs + 5_000)!!.lat, 1e-9)
        assertNull("42 sekunder efter den seneste position", r.fixAt(r.startMs + 50_000))
    }

    @Test fun enToneIUdsnittetLiggerPaaSinFrekvens() {
        // 16 kHz: en tone 2 kHz over udsnittets midte, begyndt efter 32 ms stilhed med lidt støj
        val rnd = java.util.Random(1)
        val n = 64 * 40
        val iq = ShortArray(2 * n) { (rnd.nextGaussian() * 20).toInt().toShort() }
        for (i in 64 * 8 until n) {
            iq[2 * i] = (iq[2 * i] + 4000 * cos(2 * PI * 2000 * i / 16000)).toInt().toShort()
            iq[2 * i + 1] = (iq[2 * i + 1] + 4000 * sin(2 * PI * 2000 * i / 16000)).toInt().toShort()
        }
        val rows = ClipSpectrum.rows(iq, quiet = 64 * 8)
        assertEquals(40, rows.size)
        val row = rows[20]
        assertEquals("2 kHz over midten er otte felter a 250 Hz til højre for felt 32", 40, row.indices.maxByOrNull { row[it] })
        assertTrue("tonen står langt over støjen", row[40] > 30)
        assertTrue("før tonen er der kun støj omkring gulvet", rows[3].max() < 12 && rows[3].average() > -6 && rows[3].average() < 3)
    }
}
