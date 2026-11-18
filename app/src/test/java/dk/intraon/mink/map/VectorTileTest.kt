package dk.intraon.mink.map

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class VectorTileTest {
    @Test fun enVejIEnPakketFliseLaesesMedKlasseOgPunkter() {
        val road = TestTile.Thing(VectorTile.LINE, mapOf("class" to "minor"), listOf(10 to 20, 15 to 20, 15 to 13))
        val layers = VectorTile.read(TestTile.tile("transportation", listOf(road), gzip = true))

        assertEquals("transportation", layers.single().name)
        val f = layers.single().features.single()
        assertEquals("minor", f.tags["class"])
        assertArrayEquals(intArrayOf(10, 20, 15, 20, 15, 13), f.parts.single())
    }
}
