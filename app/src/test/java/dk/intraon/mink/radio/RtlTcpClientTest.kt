package dk.intraon.mink.radio

import java.io.DataInputStream
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.util.Collections
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Klienten mod en lille rtl_tcp-server på localhost: header, kommandoer og blokke. */
class RtlTcpClientTest {
    @Test
    fun laeserHeaderSenderKommandoerOgLevererBlokke() {
        val server = ServerSocket(0)
        val cmds = Collections.synchronizedList(ArrayList<Pair<Int, Int>>())
        Thread {
            val s = server.accept()
            Thread {
                try {
                    val inp = DataInputStream(s.getInputStream())
                    while (true) { val c = inp.readUnsignedByte(); cmds.add(c to inp.readInt()) }
                } catch (_: Exception) {}
            }.start()
            val out = s.getOutputStream()
            out.write(ByteBuffer.allocate(12).put("RTL0".toByteArray()).putInt(5).putInt(29).array())
            for (b in 1..3) out.write(ByteArray(4096) { b.toByte() })
            out.flush()
        }.start()

        val blocks = LinkedBlockingQueue<Int>()
        val states = Collections.synchronizedList(ArrayList<RtlTcpClient.State>())
        val c = RtlTcpClient("127.0.0.1", server.localPort, 4096, { buf, _ -> blocks.add(buf[4095].toInt()) }, { st, _ -> states.add(st) })
        c.start(869_725_000L, 1_024_000, -65, 20.7)
        for (b in 1..3) assertEquals(b, blocks.poll(5, TimeUnit.SECONDS))
        assertEquals("R820T", c.tuner)
        c.changeGain(16.6)
        val deadline = System.currentTimeMillis() + 5000
        while (cmds.size < 9 && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertEquals(
            listOf(0x02 to 1_024_000, 0x05 to -65, 0x01 to 869_725_000, 0x03 to 1, 0x08 to 0, 0x04 to 207, 0x03 to 1, 0x08 to 0, 0x04 to 166),
            cmds.toList(),
        )
        assertTrue(states.contains(RtlTcpClient.State.RECEIVING))
        c.stop()
        server.close()
    }
}
