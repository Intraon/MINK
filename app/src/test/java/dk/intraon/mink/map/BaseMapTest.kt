package dk.intraon.mink.map

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.sinh

class BaseMapTest {
    @Test fun enVejEnderSammeStedPaaKortetSomIFlisen() {
        val tile = BaseMap.tileOf(55.0, 10.0)
        // fra flisens midte til midten af dens østkant
        val road = TestTile.Thing(VectorTile.LINE, mapOf("class" to "minor"), listOf(2048 to 2048, 4096 to 2048))
        val map = JSONObject(BaseMap.json(listOf(tile to TestTile.tile("transportation", listOf(road)))))

        val n = (1 shl BaseMap.ZOOM).toDouble()
        val lat = Math.toDegrees(atan(sinh(PI * (1 - 2 * (tile.y + 0.5) / n))))
        val line = map.getJSONArray("veje").getJSONArray(0).getJSONArray(1)
        assertEquals(2, map.getJSONArray("veje").getJSONArray(0).getInt(0))
        assertEquals(lat, line.getInt(0) / 1e5, 2e-5)
        assertEquals((tile.x + 0.5) / n * 360 - 180, line.getInt(1) / 1e5, 2e-5)
        assertEquals((tile.x + 1.0) / n * 360 - 180, (line.getInt(1) + line.getInt(3)) / 1e5, 2e-5)
    }

    @Test fun fliserneMedFlestMaalingerKommerFoerstOgNaboerneSidst() {
        val here = List(5) { 55.0 to 10.0 }; val there = List(2) { 55.0 to 10.1 }; val far = listOf(55.0 to 10.2)

        assertEquals(listOf(BaseMap.tileOf(55.0, 10.0), BaseMap.tileOf(55.0, 10.1)), BaseMap.tiles(far + there + here, 2))
        // er der plads, kommer naboerne med efter de fliser, der har målinger
        assertEquals(listOf(BaseMap.tileOf(55.0, 10.0)), BaseMap.tiles(here, 9).take(1))
        assertEquals(9, BaseMap.tiles(here, 9).size)
    }
}
