package dk.intraon.mink.map

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.atan
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sinh
import kotlin.math.tan

/**
 * Baggrundskortet i en kortfil: veje, jernbane, vand, skov, bygninger og navne fra vektorfliser, skåret ned til det,
 * kortfilen tegner, og lagt i filen som tal. Fliserne følger OpenMapTiles' inddeling i lag. Ren Kotlin.
 */
object BaseMap {
    /** Fliserne hentes i dette zoomtrin, hvor de har alle veje og bygninger med. */
    const val ZOOM = 14

    /** En flise i [ZOOM]: kolonne og række. */
    data class Tile(val x: Int, val y: Int)

    fun tileOf(lat: Double, lon: Double): Tile {
        val n = 1 shl ZOOM
        return Tile(floor((lon + 180) / 360 * n).toInt(), floor((1 - asinh(tan(Math.toRadians(lat))) / PI) / 2 * n).toInt())
    }

    /**
     * De fliser, turenes steder ligger i, og derefter deres naboer, så kortet også viser omgivelserne. Er der flere
     * end [max], beholdes dem med flest steder, og naboerne kommer sidst.
     */
    fun tiles(positions: List<Pair<Double, Double>>, max: Int): List<Tile> {
        val own = positions.groupingBy { tileOf(it.first, it.second) }.eachCount()
        val around = HashMap<Tile, Int>()
        for ((t, n) in own) for (dx in -1..1) for (dy in -1..1) { val nb = Tile(t.x + dx, t.y + dy); if (nb !in own) around[nb] = (around[nb] ?: 0) + n }
        return (own.entries.sortedByDescending { it.value } + around.entries.sortedByDescending { it.value }).take(max).map { it.key }
    }

    /**
     * Kortet som JSON. Hver linje og flade er en række heltal: første punkts bredde og længde i 1/100000 grad og
     * derefter skridtet til hvert følgende punkt, så filen ikke bliver større end nødvendigt.
     */
    fun json(tiles: List<Pair<Tile, ByteArray>>): String {
        val roads = JSONArray(); val names = JSONArray(); val rail = JSONArray(); val water = JSONArray(); val streams = JSONArray()
        val areas = JSONArray(); val houses = JSONArray(); val places = JSONArray()
        val seenPlaces = HashSet<String>()
        for ((tile, bytes) in tiles) {
            val layers = try { VectorTile.read(bytes) } catch (_: Exception) { continue }
            for (layer in layers) for (f in layer.features) {
                val cls = f.tags["class"] as? String ?: ""
                fun lines(): List<JSONArray> = f.parts.mapNotNull { coded(tile, layer.extent, it) }
                when (layer.name) {
                    "transportation" -> if (f.type == VectorTile.LINE) {
                        val kind = ROADS[cls]
                        if (cls == "rail" || cls == "transit") lines().forEach { rail.put(it) }
                        else if (kind != null) lines().forEach { roads.put(JSONArray().put(kind).put(it)) }
                    }
                    "transportation_name" -> {
                        val name = (f.tags["name"] as? String)?.takeIf { it.isNotBlank() } ?: continue
                        if (f.type == VectorTile.LINE) lines().forEach { names.put(JSONArray().put(name).put(ROADS[cls] ?: 2).put(it)) }
                    }
                    "water" -> if (f.type == VectorTile.AREA) lines().forEach { water.put(it) }
                    "waterway" -> if (f.type == VectorTile.LINE) lines().forEach { streams.put(it) }
                    "landcover" -> if (f.type == VectorTile.AREA && cls in GREEN) lines().forEach { areas.put(JSONArray().put(if (cls == "wood") 0 else 1).put(it)) }
                    "park" -> if (f.type == VectorTile.AREA) lines().forEach { areas.put(JSONArray().put(1).put(it)) }
                    "landuse" -> if (f.type == VectorTile.AREA) BUILT[cls]?.let { kind -> lines().forEach { areas.put(JSONArray().put(kind).put(it)) } }
                    "building" -> if (f.type == VectorTile.AREA) f.parts.firstOrNull()?.let { coded(tile, layer.extent, it) }?.let { houses.put(it) }
                    "place" -> {
                        val name = (f.tags["name"] as? String)?.takeIf { it.isNotBlank() } ?: continue
                        val rank = PLACES[cls] ?: continue
                        val p = f.parts.firstOrNull() ?: continue
                        if (!seenPlaces.add(name)) continue
                        val (lat, lon) = degrees(tile, layer.extent, p[0], p[1])
                        places.put(JSONArray().put(e5(lat)).put(e5(lon)).put(name).put(rank))
                    }
                }
            }
        }
        return JSONObject().put("veje", roads).put("navne", names).put("bane", rail).put("vand", water).put("aaer", streams)
            .put("flader", areas).put("huse", houses).put("steder", places).toString()
    }

    /** Vejenes vægt på kortet: 0 motorvej, 1 hovedvej, 2 almindelig vej, 3 mindre vej og markvej, 4 sti. */
    private val ROADS = mapOf(
        "motorway" to 0, "trunk" to 0, "primary" to 1, "secondary" to 1, "tertiary" to 2, "minor" to 2,
        "service" to 3, "track" to 3, "busway" to 3, "raceway" to 3, "path" to 4,
    )
    private val GREEN = setOf("wood", "grass", "wetland")
    /** Bebyggede områder som flader: 2 by, 3 erhverv. Skov er 0 og græs 1. */
    private val BUILT = mapOf("residential" to 2, "suburb" to 2, "neighbourhood" to 2, "industrial" to 3, "commercial" to 3, "retail" to 3)
    private val PLACES = mapOf("city" to 0, "town" to 0, "village" to 1, "suburb" to 1, "hamlet" to 2, "neighbourhood" to 2, "quarter" to 2)

    private fun e5(deg: Double) = (deg * 1e5).roundToInt()

    private fun degrees(tile: Tile, extent: Int, px: Int, py: Int): Pair<Double, Double> {
        val n = (1 shl ZOOM).toDouble()
        val lon = (tile.x + px.toDouble() / extent) / n * 360 - 180
        val lat = Math.toDegrees(atan(sinh(PI * (1 - 2 * (tile.y + py.toDouble() / extent) / n))))
        return lat to lon
    }

    /** Punkterne som første punkt plus skridt. Punkter, der falder sammen med det forrige, udelades. Null, hvis der kun er ét tilbage. */
    private fun coded(tile: Tile, extent: Int, xy: IntArray): JSONArray? {
        val out = JSONArray(); var lastLat = 0; var lastLon = 0; var count = 0
        for (i in 0 until xy.size / 2) {
            val (lat, lon) = degrees(tile, extent, xy[2 * i], xy[2 * i + 1])
            val a = e5(lat); val o = e5(lon)
            if (count == 0) { out.put(a).put(o) } else if (a == lastLat && o == lastLon) continue else { out.put(a - lastLat).put(o - lastLon) }
            lastLat = a; lastLon = o; count++
        }
        return if (count >= 2) out else null
    }
}
