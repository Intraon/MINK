package dk.intraon.mink.replay

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelsTest {
    @Test fun udbrudPaaSammeKanalFaarEtMaerkeMedKanalensMidtersteFrekvens() {
        // tre kanaler med 25 kHz imellem; udbruddene ligger et par kHz fra hinanden inden for hver kanal
        val freqs = listOf(869_446_300.0, 869_421_500.0, 869_445_100.0, 869_471_900.0, 869_422_800.0, 869_447_000.0, 869_420_900.0, 869_446_600.0)

        assertEquals(listOf(869_421_500.0, 869_446_300.0, 869_471_900.0), Channels.of(freqs))
    }

    @Test fun maerkerDerVilleDaekkeHinandenStaarIHverSinRaekke() {
        // kanaler med 27 punkter imellem og mærker på 40 punkter: hvert andet mærke må en række ned
        assertEquals(listOf(0, 1, 0, 1), Channels.rows(listOf(100f, 127f, 154f, 181f), 40f))
        assertEquals("langt fra hinanden står de alle øverst", listOf(0, 0), Channels.rows(listOf(100f, 200f), 40f))
        assertEquals("tre oven i hinanden: det tredje er der ikke plads til", listOf(0, 1, -1), Channels.rows(listOf(100f, 105f, 110f), 40f))
    }
}
