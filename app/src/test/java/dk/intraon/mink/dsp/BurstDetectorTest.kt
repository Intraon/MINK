package dk.intraon.mink.dsp

import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Detektoren mod syntetisk 8-bit IQ: støj, smalle FSK-bursts, LoRa-chirps med rigtig symboltid og en bærebølge. */
class BurstDetectorTest {
    private val rate = 1_024_000
    private val n = 2048
    private val sigma = 3.0
    private val cfg = BurstDetector.Config(
        tuneHz = BurstDetector.tuneFor(869_400_000.0, 869_650_000.0),
        bandLoHz = 869_400_000.0, bandHiHz = 869_650_000.0, signalBwHz = 6_000.0,
    )

    private fun interface Gen { fun add(i: Long, iq: DoubleArray) }

    /** Amplitude der giver ca. [snrDb] signal/støj i et 6 kHz-vindue. */
    private fun amp(snrDb: Double) = sqrt(10.0.pow(snrDb / 10) * 24 * sigma * sigma / n)

    /** 2-FSK ±1,2 kHz, 1200 baud, [onS] sekunder hvert [periodS]. */
    private fun fsk(a: Double, hz: Double, onS: Double, periodS: Double, t0: Double = 0.3): Gen {
        val rnd = Random(1); var ph = 0.0; var bit = 1; val spb = rate / 1200
        return Gen { i, iq ->
            val t = i.toDouble() / rate - t0
            if (t >= 0 && t - floor(t / periodS) * periodS < onS) {
                if (i % spb == 0L) bit = if (rnd.nextBoolean()) 1 else -1
                ph += 2 * PI * (hz - cfg.tuneHz + bit * 1200.0) / rate
                iq[0] += a * cos(ph); iq[1] += a * sin(ph)
            }
        }
    }

    /**
     * Bred FSK som den, der blev målt i felten 15.11.2026: 4800 symboler/s, fire niveauer (±1,6 og ±4,8 kHz)
     * med bløde overgange – ca. 12 kHz bred.
     */
    private fun wideFsk(a: Double, hz: Double, onS: Double, periodS: Double, t0: Double = 0.3): Gen {
        val rnd = Random(5); var ph = 0.0; var dev = 0.0; var want = 0.0; val sps = rate / 4800.0; var next = 0.0
        return Gen { i, iq ->
            val t = i.toDouble() / rate - t0
            if (t >= 0 && t - floor(t / periodS) * periodS < onS) {
                if (i >= next) { want = (rnd.nextInt(4) * 2 - 3) * 1600.0; next = if (next == 0.0) i + sps else next + sps }
                dev += (want - dev) * 0.02
                ph += 2 * PI * (hz - cfg.tuneHz + dev) / rate
                iq[0] += a * cos(ph); iq[1] += a * sin(ph)
            } else if (t >= 0) next = 0.0
        }
    }

    /** LoRa: 8 upchirps og derefter tilfældige symboler, uafbrudt. */
    private fun lora(a: Double, fc: Double, bw: Double, sf: Int): Gen {
        val ts = (1 shl sf) / bw; var ph = 0.0; val rnd = Random(7); var sym = -1L; var shift = 0.0
        return Gen { i, iq ->
            val t = i.toDouble() / rate
            val k = floor(t / ts).toLong()
            if (k != sym) { sym = k; shift = if (k < 8) 0.0 else rnd.nextInt(1 shl sf).toDouble() / (1 shl sf) }
            val fi = -bw / 2 + ((shift + (t / ts - k)) % 1.0) * bw
            ph += 2 * PI * (fc - cfg.tuneHz + fi) / rate
            iq[0] += a * cos(ph); iq[1] += a * sin(ph)
        }
    }

    private fun run(seconds: Double, vararg gens: Gen, c: BurstDetector.Config = cfg): List<BurstDetector.Burst> {
        val det = BurstDetector(c)
        val out = ArrayList<BurstDetector.Burst>()
        det.onBurst = { out.add(it) }
        val rnd = Random(42); val buf = ByteArray(2 * n); val iq = DoubleArray(2); var i = 0L
        repeat((seconds * rate / n).toInt()) {
            for (k in 0 until n) {
                iq[0] = rnd.nextGaussian() * sigma; iq[1] = rnd.nextGaussian() * sigma
                for (g in gens) g.add(i, iq)
                buf[2 * k] = (127.4 + iq[0]).roundToInt().coerceIn(0, 255).toByte()
                buf[2 * k + 1] = (127.4 + iq[1]).roundToInt().coerceIn(0, 255).toByte()
                i++
            }
            det.processBlock(buf, 0, (i - n) * 1000 / rate)
        }
        return out
    }

    @Test
    fun renStoejGiverIngenBursts() {
        assertEquals(0, run(8.0).size)
    }

    @Test
    fun svageOgStaerkeBurstsFindesMedRigtigFrekvensOgVarighed() {
        for ((snr, hz) in listOf(6.0 to 869_412_500.0, 20.0 to 869_612_500.0, 35.0 to 869_537_500.0)) {
            val b = run(0.3 + 3 * 1.5, fsk(amp(snr), hz, 0.4, 1.5))
            assertEquals("antal ved $snr dB", 3, b.size)
            for (x in b) {
                assertTrue("frekvens ${x.freqHz}", abs(x.freqHz - hz) < 1500)
                assertTrue("varighed ${x.durMs}", abs(x.durMs - 400) <= 20)
                assertTrue("snr ${x.peakSnr} ved $snr", abs(x.peakSnr - snr) < 3.5)
            }
        }
    }

    @Test
    fun tidsmoensteretErLigegyldigt() {
        // korte bursts tæt på hinanden og ét langt: ingen fast varighed eller gentagelsestid er forudsat
        assertEquals(6, run(0.3 + 6 * 0.5, fsk(amp(15.0), 869_500_000.0, 0.1, 0.5)).size)
        val long = run(0.3 + 5.0, fsk(amp(15.0), 869_500_000.0, 4.5, 100.0))
        assertTrue("en lang udsendelse meldes i stykker", long.size >= 2 && long.first().ongoing && !long.last().ongoing)
    }

    @Test
    fun toSendereDerOverlapperFaarHverSinMaaling() {
        // som i den første rigtige test: en svag sender på én kanal, og en anden, der starter, før den første er færdig
        val weak = fsk(amp(8.0), 869_512_500.0, 0.355, 100.0, t0 = 0.300)
        val other = fsk(amp(13.0), 869_412_500.0, 0.395, 100.0, t0 = 0.540)
        val b = run(1.2, weak, other).sortedBy { it.tStartMs }
        assertEquals(2, b.size)
        assertTrue("første: ${b[0].freqHz} ${b[0].durMs}", abs(b[0].freqHz - 869_512_500.0) < 1500 && abs(b[0].durMs - 355) <= 20)
        assertTrue("anden: ${b[1].freqHz} ${b[1].durMs}", abs(b[1].freqHz - 869_412_500.0) < 1500 && abs(b[1].durMs - 395) <= 20)
        assertTrue("de overlapper i tid", b[1].tStartMs < b[0].tStartMs + b[0].durMs)
    }

    @Test
    fun loraTaellerIkke() {
        assertEquals("MeshCore SF8/62,5 kHz", 0, run(4.0, lora(20.0, 869_618_000.0, 62_500.0, 8)).size)
        assertEquals("MeshCore SF10/62,5 kHz", 0, run(4.0, lora(20.0, 869_618_000.0, 62_500.0, 10)).size)
        assertEquals("Meshtastic SF11/250 kHz", 0, run(4.0, lora(20.0, 869_525_000.0, 250_000.0, 11)).size)
        assertEquals("LoRaWAN SF9/125 kHz", 0, run(4.0, lora(20.0, 869_525_000.0, 125_000.0, 9)).size)
    }

    @Test
    fun ignoreredeFrekvenserTaellerIkke() {
        val det = BurstDetector(cfg.copy(ignore = listOf(869_495_000.0 to 869_505_000.0)))
        var hits = 0
        det.onBurst = { hits++ }
        val rnd = Random(3); val buf = ByteArray(2 * n); val iq = DoubleArray(2); var i = 0L
        val g = fsk(amp(20.0), 869_500_000.0, 0.4, 1.0)
        repeat((3.0 * rate / n).toInt()) {
            for (k in 0 until n) {
                iq[0] = rnd.nextGaussian() * sigma; iq[1] = rnd.nextGaussian() * sigma
                g.add(i, iq)
                buf[2 * k] = (127.4 + iq[0]).roundToInt().coerceIn(0, 255).toByte()
                buf[2 * k + 1] = (127.4 + iq[1]).roundToInt().coerceIn(0, 255).toByte()
                i++
            }
            det.processBlock(buf, 0, (i - n) * 1000 / rate)
        }
        assertEquals(0, hits)
    }

    @Test
    fun signalLigeUdenForBaandetFindesHelt() {
        // en dongle, der driver, kan flytte et signal få kHz ud over båndets kant: et signals midte må ligge
        // 10 kHz uden for båndet, uden at målingen bliver klippet i stykker
        for (hz in listOf(869_650_000.0 + 3_000, 869_650_000.0 + 9_000, 869_400_000.0 - 9_000)) {
            val b = run(0.3 + 3 * 1.5, wideFsk(amp(22.0), hz, 0.38, 1.5))
            assertEquals("antal ved $hz", 3, b.size)
            for (x in b) {
                assertTrue("varighed ${x.durMs} ved $hz", abs(x.durMs - 380) <= 20)
                assertTrue("frekvens ${x.freqHz} ved $hz", abs(x.freqHz - hz) < 1500)
            }
        }
        // længere ude end det stykke, vandfaldet viser, findes intet
        assertEquals(0, run(0.3 + 3 * 1.5, wideFsk(amp(22.0), 869_650_000.0 + 16_000, 0.38, 1.5)).size)
    }

    @Test
    fun signaletsBreddeMaales() {
        val wide = run(0.3 + 3 * 1.5, wideFsk(amp(25.0), 869_520_000.0, 0.38, 1.5))
        assertEquals(3, wide.size)
        for (x in wide) assertTrue("bred FSK: ${x.bwHz}", x.bwHz.let { it != null && it in 8_500.0..12_500.0 })
        val slim = run(0.3 + 3 * 1.5, fsk(amp(25.0), 869_520_000.0, 0.4, 1.5))
        assertEquals(3, slim.size)
        for (x in slim) assertTrue("smal FSK: ${x.bwHz}", x.bwHz.let { it != null && it in 3_000.0..5_000.0 })
        // et svagt signal kan ikke måles – hellere intet tal end et forkert
        for (x in run(0.3 + 3 * 1.5, wideFsk(amp(8.0), 869_520_000.0, 0.38, 1.5))) assertEquals(null, x.bwHz)
    }

    @Test
    fun enSignalbreddeDerPasserTilEtBredtSignalSortererStadigLoRaFra() {
        val c = cfg.copy(signalBwHz = 12_500.0)
        val b = run(0.3 + 3 * 1.5, wideFsk(amp(22.0), 869_612_500.0, 0.38, 1.5), c = c)
        assertEquals(3, b.size)
        for (x in b) assertTrue("${x.freqHz} ${x.durMs}", abs(x.freqHz - 869_612_500.0) < 1500 && abs(x.durMs - 380) <= 20)
        assertEquals("MeshCore SF8/62,5 kHz", 0, run(4.0, lora(20.0, 869_618_000.0, 62_500.0, 8), c = c).size)
        assertEquals("MeshCore SF10/62,5 kHz", 0, run(4.0, lora(20.0, 869_618_000.0, 62_500.0, 10), c = c).size)
        assertEquals("LoRaWAN SF9/125 kHz", 0, run(4.0, lora(20.0, 869_525_000.0, 125_000.0, 9), c = c).size)
        assertEquals("ren støj", 0, run(8.0, c = c).size)
    }

    @Test fun signalbreddenFoelgerMaalet() {
        assertEquals(10_000.0, BurstDetector.widthFor(10_200.0, 6_000.0)!!, 0.0)
        assertNull("lille forskel: lad den stå", BurstDetector.widthFor(7_000.0, 6_000.0))
        assertEquals(3_000.0, BurstDetector.widthFor(3_100.0, 10_000.0)!!, 0.0)
        assertEquals(100_000.0, BurstDetector.widthFor(180_000.0, 10_000.0)!!, 0.0)
    }
}
