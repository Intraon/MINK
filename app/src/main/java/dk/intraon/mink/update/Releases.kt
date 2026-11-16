package dk.intraon.mink.update

import org.json.JSONObject

/** Læser GitHubs svar om den nyeste udgivelse og afgør, om den er nyere end den installerede version. Ren Kotlin. */
object Releases {
    /** Adressen, hvor GitHub fortæller om den nyeste udgivelse i [repo] ("ejer/navn"). */
    fun latestUrl(repo: String) = "https://api.github.com/repos/$repo/releases/latest"
    fun pageUrl(repo: String) = "https://github.com/$repo"

    /**
     * Svaret fra [latestUrl]. Versionen er udgivelsens mærke uden "v" foran; APK'en er den vedhæftede fil, der
     * hedder [apkName]. Null, hvis svaret ikke er en udgivelse (fx fordi repoet ikke findes endnu).
     */
    fun parse(json: String, apkName: String = "MINK.apk"): Release? = try {
        val o = JSONObject(json)
        val tag = o.optString("tag_name")
        if (tag.isEmpty() || o.optBoolean("draft") || o.optBoolean("prerelease")) null
        else {
            var url: String? = null; var bytes = 0L
            val assets = o.optJSONArray("assets")
            if (assets != null) for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name") == apkName) { url = a.optString("browser_download_url").ifEmpty { null }; bytes = a.optLong("size") }
            }
            Release(tag.removePrefix("v"), o.optString("body"), url, bytes, o.optString("html_url"))
        }
    } catch (_: Exception) {
        null
    }

    /**
     * Er [candidate] en nyere version end [current]? Versioner er tal adskilt af punktum ("0.4.460694") og
     * sammenlignes led for led som tal. Kan en af dem ikke læses, er svaret nej – så tilbydes ingen opdatering.
     */
    fun newer(candidate: String, current: String): Boolean {
        fun parts(v: String): List<Long>? {
            val nums = v.removePrefix("v").split('.').map { it.toLongOrNull() }
            return if (nums.any { it == null }) null else nums.filterNotNull()
        }
        val a = parts(candidate) ?: return false
        val b = parts(current) ?: return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
