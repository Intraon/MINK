package dk.intraon.mink.map

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Henter vektorfliser til baggrundskortet. [index] er adressen på kildens oversigt (TileJSON), som fortæller, hvor
 * fliserne ligger lige nu; [agent] er det navn, appen giver sig til kende med. Kald fra en baggrundstråd.
 */
class TileSource(private val index: String, private val agent: String) {
    /** De fliser, der kunne hentes. Er kilden ikke sat, eller svarer den ikke, er listen tom. */
    fun fetch(tiles: List<BaseMap.Tile>): List<Pair<BaseMap.Tile, ByteArray>> {
        if (index.isBlank() || tiles.isEmpty()) return emptyList()
        val template = try { JSONObject(String(get(index) ?: return emptyList(), Charsets.UTF_8)).getJSONArray("tiles").getString(0) } catch (_: Exception) { return emptyList() }
        val res = ArrayList<Pair<BaseMap.Tile, ByteArray>>()
        var failed = 0
        for (t in tiles) {
            val bytes = get(template.replace("{z}", BaseMap.ZOOM.toString()).replace("{x}", t.x.toString()).replace("{y}", t.y.toString()))
            if (bytes != null) res.add(t to bytes) else if (++failed >= GIVE_UP) break     // uden net skal brugeren ikke vente på hver flise
        }
        return res
    }

    private fun get(url: String): ByteArray? {
        var c: HttpURLConnection? = null
        return try {
            c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 8000; c.readTimeout = 15000
            c.setRequestProperty("User-Agent", agent)
            if (c.responseCode == 200) c.inputStream.use { it.readBytes() } else null
        } catch (_: Exception) {
            null
        } finally {
            c?.disconnect()
        }
    }

    private companion object { const val GIVE_UP = 3 }
}
