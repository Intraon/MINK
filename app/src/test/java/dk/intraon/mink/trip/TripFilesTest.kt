package dk.intraon.mink.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripFilesTest {
    @Test fun tidsstempletStaarIFilnavnet() {
        assertEquals("20261116_223740", TripFiles.stamp("mink_20261116_223740.jsonl.txt"))
        assertEquals("20261116_223740", TripFiles.stamp("mink_20261116_223740.iq.wav"))
        assertEquals("20261116_224039", TripFiles.stamp("MINK_pejling_20261116_224039.jpg"))
        assertNull(TripFiles.stamp(".nomedia"))
    }

    @Test fun billederHoererTilDenTurDeErTagetUnder() {
        val trip = "20261116_223740"; val next = "20261116_230110"
        assertTrue(TripFiles.inTrip("MINK_pejling_20261116_224039.jpg", trip, next))
        assertTrue("målebilledet fra Billede-knappen", TripFiles.inTrip("MINK_20261116_225901.png", trip, next))
        assertFalse("taget under den næste tur", TripFiles.inTrip("MINK_pejling_20261116_230500.jpg", trip, next))
        assertFalse("taget før turen", TripFiles.inTrip("MINK_20261116_220000.png", trip, next))
        assertTrue("den nyeste tur har ingen øvre grænse", TripFiles.inTrip("MINK_pejling_20261117_010000.jpg", next, null))
    }

    @Test fun naesteTurFindesBlandtAlle() {
        val all = listOf("20261116_230110", "20261116_223740", "20261115_152700")
        assertEquals("20261116_223740", TripFiles.nextAfter("20261115_152700", all))
        assertEquals("20261116_230110", TripFiles.nextAfter("20261116_223740", all))
        assertNull(TripFiles.nextAfter("20261116_230110", all))
    }

    @Test fun kortbilledetHoererTilDenTurKortetViser() {
        val old = "20261116_223740"; val newest = "20261117_120129"
        val ofOld = TripFiles.mapPictureName("20261117_130000", old)
        assertTrue("taget i dag, men af turen fra i går", TripFiles.inTrip(ofOld, old, "20261116_230110"))
        assertFalse(TripFiles.inTrip(ofOld, newest, null))
        assertTrue("uden en tidligere tur på kortet hører det til den nyeste", TripFiles.inTrip(TripFiles.mapPictureName("20261117_130000", null), newest, null))
        assertTrue("kortet, der laves ved delingen, hører til sin tur", TripFiles.inTrip(TripFiles.tripMapName(old), old, "20261116_230110"))
        val waterfall = TripFiles.replayPictureName("20261117_130000", old)
        assertTrue("et billede af turens vandfald, taget i dag", TripFiles.inTrip(waterfall, old, "20261116_230110"))
        assertFalse(TripFiles.inTrip(waterfall, newest, null))
    }

    @Test fun bladringGaarFraDenVisteTurOgsaaNaarEnNyTurErBegyndt() {
        val old = listOf("mink_20261118_145148.jsonl.txt", "mink_20261118_130210.jsonl.txt", "mink_20261117_150200.jsonl.txt")
        assertEquals(1, TripFiles.step(old, old[0], null, +1))
        // en måling er startet, mens turen fra 13.02 blev vist, så alle pladser er rykket én
        val now = listOf("mink_20261118_153000.jsonl.txt") + old
        assertEquals("ældre end 13.02 er turen fra i går", 3, TripFiles.step(now, old[1], "mink_20261118_153000", +1))
        assertEquals("nyere end 13.02 er 14.51", 1, TripFiles.step(now, old[1], "mink_20261118_153000", -1))
        assertEquals("den tur, der kører, springes over på vej mod den aktuelle", -1, TripFiles.step(now, old[0], "mink_20261118_153000", -1))
        assertEquals("fra den aktuelle tur er den første den nyeste, der ikke kører", 1, TripFiles.step(now, null, "mink_20261118_153000", +1))
        assertEquals("der er ingen ældre", now.size, TripFiles.step(now, old[2], "mink_20261118_153000", +1))
    }
}
