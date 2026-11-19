package dk.intraon.mink.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OccupancyTest {
    private val lo = 869_586_750.0; private val hi = 869_649_250.0      // kanalen 869,618 MHz, 62,5 kHz bred

    @Test fun toSendereIKanalenOptagerDenEnFemtedelAfTiden() {
        val o = Occupancy()
        o.start(1_000_000L)
        assertNull("der er ikke lyttet længe nok", o.share(lo, hi, 1_005_000L))
        // to sendere i kanalen og én udenfor, hver med et udbrud på 386 ms hvert fjerde sekund, forskudt i takten
        for (k in 0 until 30) {
            val t = 1_000_000L + k * 4_000L
            o.add(t + 1_080, 386, 869_596_000.0, 1)
            o.add(t + 3_600, 386, 869_640_000.0, 2)
            o.add(t + 1_540, 386, 869_446_000.0, 3)
        }
        val share = o.share(lo, hi, 1_120_000L)!!
        assertEquals(2 * 386 / 4000.0, share.fraction, 0.005)
        assertEquals(2, share.senders)
    }

    @Test fun udbrudDerOverlapperITidTaellerKunEnGang() {
        val o = Occupancy()
        o.start(0L)
        for (k in 0 until 15) {
            o.add(1_000L + k * 4_000L, 400, 869_600_000.0, 1)
            o.add(1_200L + k * 4_000L, 400, 869_640_000.0, 2)      // begynder, mens den første sender
        }
        assertEquals(600 / 4000.0, o.share(lo, hi, 60_000L)!!.fraction, 0.005)
    }
}
