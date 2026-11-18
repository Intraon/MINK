package dk.intraon.mink.map

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/** Bygger små vektorfliser til testene, i samme format som de hentede. */
object TestTile {
    class Thing(val type: Int, val tags: Map<String, String>, val points: List<Pair<Int, Int>>)

    /** En flise med ét lag. Hver ting er én linje eller flade gennem sine punkter (flisens enheder, 0 til 4096). */
    fun tile(layer: String, things: List<Thing>, gzip: Boolean = false): ByteArray {
        val keys = things.flatMap { it.tags.keys }.distinct(); val values = things.flatMap { it.tags.values }.distinct()
        val body = ByteArrayOutputStream()
        body.write(field(1, layer.toByteArray()))
        for (t in things) {
            val tags = ByteArrayOutputStream(); for ((k, v) in t.tags) { tags.write(varint(keys.indexOf(k).toLong())); tags.write(varint(values.indexOf(v).toLong())) }
            val geo = ByteArrayOutputStream(); var x = 0; var y = 0
            t.points.forEachIndexed { i, (px, py) ->
                if (i == 0) geo.write(varint((1 shl 3 or 1).toLong())) else if (i == 1) geo.write(varint(((t.points.size - 1) shl 3 or 2).toLong()))
                geo.write(varint(zigzag(px - x))); geo.write(varint(zigzag(py - y))); x = px; y = py
            }
            if (t.type == VectorTile.AREA) geo.write(varint((1 shl 3 or 7).toLong()))
            body.write(field(2, field(2, tags.toByteArray()) + key(3, 0) + varint(t.type.toLong()) + field(4, geo.toByteArray())))
        }
        for (k in keys) body.write(field(3, k.toByteArray()))
        for (v in values) body.write(field(4, field(1, v.toByteArray())))
        body.write(key(5, 0)); body.write(varint(4096))
        val raw = field(3, body.toByteArray())
        if (!gzip) return raw
        return ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(raw) } }.toByteArray()
    }

    private fun zigzag(v: Int): Long = ((v shl 1) xor (v shr 31)).toLong() and 0xffffffffL
    private fun key(field: Int, wire: Int) = varint((field shl 3 or wire).toLong())
    private fun field(n: Int, payload: ByteArray) = key(n, 2) + varint(payload.size.toLong()) + payload
    private fun varint(value: Long): ByteArray {
        val o = ByteArrayOutputStream(); var v = value
        while (true) { if (v and 0x7fL.inv() == 0L) { o.write(v.toInt()); break }; o.write((v and 0x7f).toInt() or 0x80); v = v ushr 7 }
        return o.toByteArray()
    }
}
