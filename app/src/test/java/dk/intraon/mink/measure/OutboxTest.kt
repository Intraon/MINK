package dk.intraon.mink.measure

import org.junit.Assert.assertEquals
import org.junit.Test

class OutboxTest {
    @Test
    fun heleKoeenSendesOgsaaNaarDenErLang() {
        // en tur uden net: 1000 poster venter, når der stoppes
        val box = Outbox<String>(5000, 30)
        repeat(1000) { box.add("post $it") }
        val seqs = ArrayList<Int>()
        val got = ArrayList<String>()
        val delivered = box.drain { p -> seqs.add(p.seq); got.addAll(p.items); true }
        assertEquals(1000, delivered)
        assertEquals(0, box.size)
        assertEquals((0 until 1000).map { "post $it" }, got)
        assertEquals((1..34).toList(), seqs)
    }

    @Test
    fun enPakkeDerIkkeBlevAfleveretSendesIgenMedSammeNummerOgIndhold() {
        val box = Outbox<String>(5000, 30)
        repeat(40) { box.add("post $it") }
        val tries = ArrayList<Pair<Int, List<String>>>()
        var allowed = 1
        val send = { p: Outbox.Packet<String> -> tries.add(p.seq to p.items); if (allowed > 0) { allowed--; true } else false }

        // pakke 1 afleveres; så er nettet væk, og pakke 2 med de sidste ti poster kommer ikke frem
        assertEquals(30, box.drain(send = send))
        // en ny post kommer til, mens der ventes; den hører ikke til pakke 2
        box.add("post 40")
        assertEquals(0, box.drain(send = send))
        assertEquals(11, box.size)
        allowed = 5
        assertEquals(11, box.drain(send = send))

        val ten = (30 until 40).map { "post $it" }
        assertEquals(listOf(1, 2, 2, 2, 3), tries.map { it.first })
        assertEquals(listOf(ten, ten, ten, listOf("post 40")), tries.drop(1).map { it.second })
    }
}
