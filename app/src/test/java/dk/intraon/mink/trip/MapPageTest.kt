package dk.intraon.mink.trip

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MapPageTest {
    private fun time(minute: Int, second: Int) = String.format("2026-11-14T%02d:%02d:%02d.000+01:00", 12 + minute / 60, minute % 60, second)

    /** En måling af målet hvert fjerde sekund fra [minute], [steps] i alt, med så mange meter mod nord mellem hver. */
    private fun log(name: String, minute: Int, steps: Int, metersPerStep: Double, level: (Int) -> Double = { -70.0 }): MapPage.Log {
        val lines = ArrayList<String>()
        lines.add("""{"type":"meta","session":"$name"}""")
        for (i in 0 until steps) {
            val lat = 55.0 + i * metersPerStep / 111320.0
            lines.add("""{"type":"burst","t":"${time(minute + i * 4 / 60, i * 4 % 60)}","lat":$lat,"lon":10.0,"spd":${metersPerStep * 0.9},"f":"869.6000","src":1,"tgt":true,"snr":15,"lvl":${level(i)},"gain":29.7,"dur":350}""")
        }
        return MapPage.Log("mink_$name.jsonl.txt", lines)
    }

    @Test fun etStopLigeEfterEnGaaturHoererTilTurenOgEtSenereStopStaarForSig() {
        val walk = log("20261114_120000", 0, 60, 5.0)          // 12:00 til 12:04, 300 m
        val stop = log("20261114_120600", 6, 20, 0.0)          // to minutter efter, stillestående
        val later = log("20261114_140000", 120, 20, 0.0)       // to timer efter, stillestående

        val posts = JSONArray(MapPage.data(listOf(later, stop, walk)))

        assertEquals(2, posts.length())
        val kinds = (0 until 2).map { posts.getJSONObject(it).getString("kind") }.toSet()
        assertEquals(setOf("stille", "til fods"), kinds)
        val trip = (0 until 2).map { posts.getJSONObject(it) }.first { it.getString("kind") == "til fods" }
        assertEquals(80, trip.getInt("n"))
        assertTrue(trip.getString("label"), trip.getString("label").startsWith("14.11. 12:00–12:0"))
    }

    @Test fun enPejlingPegerModMidtenAfDenBredeTopOgIkkeModDenEneStaerkesteRetning() {
        // en bred top, der hælder mod vest: kraftigst i 195°, men næsten lige så kraftig helt ud til 285°
        val top = mapOf(165 to -64.0, 195 to -61.0, 225 to -62.0, 255 to -63.0, 285 to -64.0)
        val samples = (0 until 12).map { it * 30 + 15 }.flatMap { deg -> List(2) { deg.toDouble() to (top[deg] ?: -78.0) } }

        val lobe = MapPage.lobe(samples)!!

        assertTrue("midten var ${lobe.center}", lobe.center in 212.0..230.0)
        assertEquals(17.0, lobe.swing, 0.01)
    }

    @Test fun etHopFraEtFremmedSignalKommerIkkeMedPaaKortet() {
        val still = log("20261114_120000", 0, 20, 0.0) { i -> if (i == 10) -40.0 else -85.0 }

        val post = JSONArray(MapPage.data(listOf(still))).getJSONObject(0)

        assertEquals(19, post.getInt("n"))
        assertEquals(-85.0, post.getDouble("max"), 0.01)
    }

    @Test fun erDerSkruetPaaGainIHaandenBrugesKunMaalingerneVedDetSaedvanligeGain() {
        val still = log("20261114_120000", 0, 20, 0.0)
        // midt i målingen skrues der ned i hånden, og tre målinger ved det lave gain ser 15 dB kraftigere ud, end de er
        val lines = still.lines.toMutableList()
        lines.add("""{"type":"gain","t":"${time(2, 0)}","from":29.7,"to":8.7,"why":"manuel"}""")
        for (i in 0 until 3) lines.add("""{"type":"burst","t":"${time(2, 10 + i * 4)}","lat":55.0,"lon":10.0,"spd":0,"f":"869.6000","src":1,"tgt":true,"snr":15,"lvl":-55.0,"gain":8.7,"dur":350}""")

        val post = JSONArray(MapPage.data(listOf(MapPage.Log(still.name, lines)))).getJSONObject(0)

        assertEquals(20, post.getInt("n"))
        assertEquals(-70.0, post.getDouble("max"), 0.01)
    }

    @Test fun kortfilenHarFotoerneISigOgHenterIntetNaarDenAabnes() {
        val template = File("src/main/assets/kort.html").readText()

        val html = MapPage.html(template, MapPage.data(listOf(log("20261114_120000", 0, 20, 5.0))), null, mapOf("MINK_pejling_x.jpg" to "QUJDRA=="), "14.11.2026 13:00")

        assertTrue(html.contains("\"MINK_pejling_x.jpg\":\"QUJDRA==\""))
        assertFalse(html.contains("/*DATA*/") || html.contains("/*BASE*/") || html.contains("/*FOTO*/") || html.contains("{{LAVET}}"))
        assertFalse("filen må ikke pege ud på nettet", Regex("https?://|//[a-z]").containsMatchIn(html.replace(Regex("//[^\n]*\n"), "\n")))
    }
}
