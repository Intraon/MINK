package dk.intraon.mink.map

import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

/**
 * Læser en vektorflise (Mapbox Vector Tile): lag med veje, vand, bygninger og navne som punkter, linjer og flader i
 * flisens egne enheder. Kun det, [BaseMap] bruger, pakkes ud. Ren Kotlin.
 */
object VectorTile {
    const val POINT = 1
    const val LINE = 2
    const val AREA = 3

    /** Én ting i et lag. [parts] er dens linjer eller ringe som x, y, x, y … i flisens enheder (0 til lagets extent). */
    class Feature(val type: Int, val tags: Map<String, Any>, val parts: List<IntArray>)

    class Layer(val name: String, val extent: Int, val features: List<Feature>)

    /** Lagene i en flise. Flisen må være gzip-pakket, som den typisk er, når den hentes. */
    fun read(bytes: ByteArray): List<Layer> {
        val raw = if (bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte())
            GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() } else bytes
        val r = Reader(raw, 0, raw.size)
        val layers = ArrayList<Layer>()
        while (r.more()) {
            val key = r.varint().toInt()
            if (key ushr 3 == 3 && key and 7 == 2) layers.add(layer(r.sub())) else r.skip(key and 7)
        }
        return layers
    }

    private fun layer(r: Reader): Layer {
        var name = ""; var extent = 4096
        val keys = ArrayList<String>(); val values = ArrayList<Any>(); val raw = ArrayList<Reader>()
        while (r.more()) {
            val key = r.varint().toInt()
            when (key ushr 3) {
                1 -> name = r.string()
                2 -> raw.add(r.sub())
                3 -> keys.add(r.string())
                4 -> values.add(value(r.sub()))
                5 -> extent = r.varint().toInt()
                else -> r.skip(key and 7)
            }
        }
        return Layer(name, extent, raw.map { feature(it, keys, values) })
    }

    private fun value(r: Reader): Any {
        var v: Any = ""
        while (r.more()) {
            val key = r.varint().toInt()
            when (key ushr 3) {
                1 -> v = r.string()
                2 -> v = java.lang.Float.intBitsToFloat(r.fixed32()).toDouble()
                3 -> v = java.lang.Double.longBitsToDouble(r.fixed64())
                4, 5 -> v = r.varint()
                6 -> v = zigzag(r.varint())
                7 -> v = r.varint() != 0L
                else -> r.skip(key and 7)
            }
        }
        return v
    }

    private fun feature(r: Reader, keys: List<String>, values: List<Any>): Feature {
        var type = 0; val tags = HashMap<String, Any>(); var parts: List<IntArray> = emptyList()
        while (r.more()) {
            val key = r.varint().toInt()
            when (key ushr 3) {
                2 -> { val t = r.sub(); while (t.more()) { val k = t.varint().toInt(); val v = t.varint().toInt(); if (k < keys.size && v < values.size) tags[keys[k]] = values[v] } }
                3 -> type = r.varint().toInt()
                4 -> parts = geometry(r.sub())
                else -> r.skip(key and 7)
            }
        }
        return Feature(type, tags, parts)
    }

    /** Tegnekommandoerne: flyt til (ny del), linje til, og luk ringen. Koordinaterne er skridt fra det forrige punkt. */
    private fun geometry(r: Reader): List<IntArray> {
        val parts = ArrayList<IntArray>(); var cur = ArrayList<Int>(); var x = 0; var y = 0
        fun close() { if (cur.size >= 2) parts.add(cur.toIntArray()); cur = ArrayList() }
        while (r.more()) {
            val cmd = r.varint().toInt(); val n = cmd ushr 3
            when (cmd and 7) {
                1 -> repeat(n) { close(); x += zigzag(r.varint()).toInt(); y += zigzag(r.varint()).toInt(); cur.add(x); cur.add(y) }
                2 -> repeat(n) { x += zigzag(r.varint()).toInt(); y += zigzag(r.varint()).toInt(); cur.add(x); cur.add(y) }
                7 -> if (cur.size >= 2) { cur.add(cur[0]); cur.add(cur[1]) }
            }
        }
        close()
        return parts
    }

    private fun zigzag(v: Long): Long = (v ushr 1) xor -(v and 1)

    private class Reader(val b: ByteArray, var p: Int, val end: Int) {
        fun more() = p < end
        fun varint(): Long {
            var res = 0L; var shift = 0
            while (true) { val x = b[p++].toInt(); res = res or ((x and 0x7f).toLong() shl shift); if (x and 0x80 == 0) return res; shift += 7 }
        }
        fun fixed32(): Int { var v = 0; for (i in 0 until 4) v = v or ((b[p++].toInt() and 0xff) shl (8 * i)); return v }
        fun fixed64(): Long { var v = 0L; for (i in 0 until 8) v = v or ((b[p++].toLong() and 0xff) shl (8 * i)); return v }
        fun sub(): Reader { val n = varint().toInt(); val s = Reader(b, p, p + n); p += n; return s }
        fun string(): String { val n = varint().toInt(); val s = String(b, p, n, Charsets.UTF_8); p += n; return s }
        fun skip(wire: Int) {
            when (wire) { 0 -> varint(); 1 -> p += 8; 2 -> p += varint().toInt(); 5 -> p += 4; else -> throw IllegalArgumentException("ukendt felt i flisen") }
        }
    }
}
