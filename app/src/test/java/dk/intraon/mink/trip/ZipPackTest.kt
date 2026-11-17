package dk.intraon.mink.trip

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class ZipPackTest {
    @Test fun enFilDerIkkeKanLaesesBliverMeldtOgRestenPakkes() {
        val out = ByteArrayOutputStream()
        val unread = ZipPack.write(out, listOf(
            ZipPack.Source("mink_20261117_120129.jsonl.txt") { ByteArrayInputStream("log".toByteArray()) },
            ZipPack.Source("mink_20261117_120129.iq.wav") { null },
            ZipPack.Source("MINK_20261117_120400.png") { throw SecurityException("ingen adgang") },
            ZipPack.Source("MINK_pejling_20261117_120500.jpg") { ByteArrayInputStream(ByteArray(5000) { 7 }) },
        ))
        assertEquals(listOf("mink_20261117_120129.iq.wav", "MINK_20261117_120400.png"), unread)
        val packed = ArrayList<String>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { z ->
            while (true) { val e = z.nextEntry ?: break; packed.add("${e.name}:${z.readBytes().size}") }
        }
        assertEquals(listOf("mink_20261117_120129.jsonl.txt:3", "MINK_pejling_20261117_120500.jpg:5000"), packed)
    }
}
