package dk.intraon.mink.map

import dk.intraon.mink.core.Hub
import dk.intraon.mink.core.MarkKind
import dk.intraon.mink.direction.Bearing
import dk.intraon.mink.trip.MapPage
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapDataTest {
    @Test fun maerketsTypeLaesesOgEtMaerkeUdenTypeErAlmindeligt() {
        val log = listOf(
            """{"type":"mark","t":"2026-11-17T12:03:00.000+01:00","lat":55.0001,"lon":9.0001,"n":1}""",
            """{"type":"mark","t":"2026-11-17T12:04:00.000+01:00","lat":55.0002,"lon":9.0002,"n":2,"kind":"possible"}""",
            """{"type":"mark","t":"2026-11-17T12:05:00.000+01:00","lat":55.0003,"lon":9.0003,"n":3,"kind":"found"}""",
            """{"type":"mark","t":"2026-11-17T12:06:00.000+01:00","lat":55.0004,"lon":9.0004,"n":4,"kind":"noget nyt"}""",
            """{"type":"mark","t":"2026-11-17T12:07:00.000+01:00","lat":null,"lon":null,"n":5,"kind":"found"}""",
        )
        val marks = MapData.parse(listOf(log)).marks
        assertEquals("et mærke uden position kan ikke stå på kortet", listOf(1, 2, 3, 4), marks.map { it.n })
        assertEquals(listOf(MarkKind.PLAIN, MarkKind.POSSIBLE, MarkKind.FOUND, MarkKind.PLAIN), marks.map { it.kind })
    }

    @Test fun kortetIAppenOgKortfilenAnslaarSammeStedOgsaaNaarLoggenSkrevEnAndenRetning() {
        // en opdigtet sender og to steder tre kilometer fra den; på hvert sted en hel omgang med toppen mod senderen
        val txLat = 55.0; val txLon = 10.0
        val lines = ArrayList<String>()
        lines.add("""{"type":"meta","session":"20261117_120000"}""")
        fun clock(sec: Int) = "2026-11-17T12:%02d:%02d.000+01:00".format(sec / 60, sec % 60)
        for ((k, fromTx) in listOf(40.0, 130.0).withIndex()) {
            val (lat, lon) = Bearing.destination(txLat, txLon, fromTx, 3_000.0)
            val toTx = Bearing.norm(fromTx + 180)
            for (i in 0 until 40) {
                val hdg = i * 9.0
                val lvl = -60.0 - minOf(kotlin.math.abs(Bearing.diff(hdg, toTx)), 90.0) / 90 * 20
                lines.add("""{"type":"burst","t":"${clock(k * 600 + i * 4)}","lat":$lat,"lon":$lon,"spd":0,"f":"869.6000","src":1,"tgt":true,"snr":15,"lvl":$lvl,"gain":29.7,"dur":350,"hdg":$hdg}""")
            }
            // loggen har den ene kraftigste måling som retning, 25° ved siden af toppens midte, og intet udsving
            lines.add("""{"type":"bearing","t":"${clock(k * 600 + 170)}","lat":$lat,"lon":$lon,"hdg":${Bearing.norm(toTx + 25)},"best":${Bearing.norm(toTx + 25)},"lvl":-60.0,"n":${k + 1},"auto":true,"photo":null}""")
        }

        val inApp = MapData.crossing(MapData.parse(listOf(lines)).bearings)!!
        val inFile = JSONObject(MapPage.crossing(MapPage.data(listOf(MapPage.Log("mink_20261117_120000.jsonl.txt", lines)))))

        // kortfilen har retningerne med én decimal, så de to steder kan ligge et par meter fra hinanden
        val apart = Bearing.meters(inApp.lat, inApp.lon, inFile.getDouble("lat"), inFile.getDouble("lon"))
        assertTrue("de to kort lå $apart m fra hinanden", apart < 5)
        val miss = Bearing.meters(inApp.lat, inApp.lon, txLat, txLon)
        assertTrue("stedet lå $miss m fra senderen: retningen skal være toppens midte, ikke den, loggen skrev", miss < 200)
    }

    @Test fun maaletsFrekvensErDenMidtersteOgIkkeDenFoersteMaaling() {
        fun burst(sec: Int, mhz: String) =
            """{"type":"burst","t":"2026-11-17T12:03:${"%02d".format(sec)}.000+01:00","lat":55.0001,"lon":9.0001,"f":"$mhz","src":1,"tgt":true,"snr":20.0,"lvl":-60.0,"gain":29.7,"dur":380}"""
        val log = listOf(burst(0, "869.6625"), burst(4, "869.6399"), burst(8, "869.6401"), burst(12, "869.6397"), burst(16, "869.6400"))
        assertEquals("den første måling lå 23 kHz ved siden af", 869_640_000.0, MapData.parse(listOf(log)).targetHz()!!, 500.0)
    }
}
