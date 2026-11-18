package dk.intraon.mink.core

import android.content.Context
import dk.intraon.mink.BuildConfig
import dk.intraon.mink.dsp.BurstDetector
import dk.intraon.mink.radio.GainControl

/** Brugerens indstillinger. Standardværdierne kommer fra gradle.properties (mink.*), så intet er hardcodet her. */
class Settings(ctx: Context) {
    private val p = ctx.getSharedPreferences("mink", Context.MODE_PRIVATE)

    private fun str(key: String, def: String) = p.getString(key, def) ?: def
    private fun put(key: String, v: String) = p.edit().putString(key, v.trim()).apply()
    private fun num(key: String, def: String, fallback: Double) =
        parse(str(key, def)) ?: parse(def) ?: fallback

    var webhook: String
        get() = str("webhook", BuildConfig.DEF_WEBHOOK)
        set(v) = put("webhook", v)

    /** Båndet der lyttes i, MHz. */
    var bandLoMhz: Double
        get() = num("bandLo", BuildConfig.DEF_BAND_LO, 433.050)
        set(v) = put("bandLo", v.toString())
    var bandHiMhz: Double
        get() = num("bandHi", BuildConfig.DEF_BAND_HI, 434.790)
        set(v) = put("bandHi", v.toString())

    /** Brugerens egen kanal (fx MeshCore): midterfrekvens i MHz og bredde i kHz. 0 = ingen kanal markeret. */
    var channelMhz: Double
        get() = num("channel", BuildConfig.DEF_CHANNEL, 0.0)
        set(v) = put("channel", v.toString())
    var channelKhz: Double
        get() = num("channelKhz", BuildConfig.DEF_CHANNEL_KHZ, 0.0)
        set(v) = put("channelKhz", v.toString())

    /** Forventet bredde af signalet, kHz. */
    var signalKhz: Double
        get() = num("signalKhz", BuildConfig.DEF_SIGNAL_KHZ, 10.0)
        set(v) = put("signalKhz", v.toString())
    /** Målets frekvens i MHz, eller null = intet mål valgt. */
    var targetMhz: Double?
        get() = parse(str("target", BuildConfig.DEF_TARGET))
        set(v) = put("target", v?.toString() ?: "")
    var onSnrDb: Double
        get() = num("onSnr", "5", 5.0)
        set(v) = put("onSnr", v.toString())
    var minBurstMs: Int
        get() = p.getInt("minBurstMs", 40)
        set(v) = p.edit().putInt("minBurstMs", v).apply()

    /** Frekvensområder der ignoreres, fx "869.480-869.485; 869.600-869.602" (MHz). */
    var ignore: String
        get() = str("ignore", "")
        set(v) = put("ignore", v)

    var ppm: Int
        get() = p.getInt("ppm", BuildConfig.DEF_PPM.trim().toIntOrNull() ?: 0)
        set(v) = p.edit().putInt("ppm", v).apply()
    /** Det valgte gain. Appen skruer aldrig højere op end det (se GainControl). */
    var startGain: Double
        get() = p.getFloat("gain", 29.7f).toDouble()
        set(v) = p.edit().putFloat("gain", v.toFloat()).apply()
    /** Beskyttelse mod mætning: appen må skrue ned for gain, når modtageren mættes. */
    var autoGain: Boolean
        get() = p.getBoolean("protectGain", true)
        set(v) = p.edit().putBoolean("protectGain", v).apply()
    var beep: Boolean
        get() = p.getBoolean("beep", true)
        set(v) = p.edit().putBoolean("beep", v).apply()
    var beepVolume: Int
        get() = p.getInt("beepVol", 60)
        set(v) = p.edit().putInt("beepVol", v.coerceIn(0, 100)).apply()
    /** Gem et smalt IQ-udsnit (selve bølgeformen) af hver måling i en WAV-fil ved siden af loggen. */
    var saveIq: Boolean
        get() = p.getBoolean("saveIq", true)
        set(v) = p.edit().putBoolean("saveIq", v).apply()
    /** Brugeren har en retningsantenne (eller skygger med kroppen): retningen gemmes, og kameraskærmen vises. */
    var directional: Boolean
        get() = p.getBoolean("directional", false)
        set(v) = p.edit().putBoolean("directional", v).apply()
    /** .nomedia er lagt i appens mappe i Overførsler (kun brugt på Android 10). */
    var nomediaDone: Boolean
        get() = p.getBoolean("nomediaDone", false)
        set(v) = p.edit().putBoolean("nomediaDone", v).apply()
    /** Hvor meget der sidst blev taget med, da en tur blev delt (se TripFiles.SHARE_…), og om det blev pakket i en zip. */
    var shareLevel: Int
        get() = p.getInt("shareLevel", 2)
        set(v) = p.edit().putInt("shareLevel", v).apply()
    var shareZip: Boolean
        get() = p.getBoolean("shareZip", true)
        set(v) = p.edit().putBoolean("shareZip", v).apply()
    /** Om der sidst blev lagt en kortfil ved, da en tur blev delt. Fra som udgangspunkt: kortdata hentes fra nettet. */
    var shareMapPage: Boolean
        get() = p.getBoolean("shareMapPage", false)
        set(v) = p.edit().putBoolean("shareMapPage", v).apply()
    /** Se selv efter nye versioner på GitHub. */
    var autoUpdate: Boolean
        get() = p.getBoolean("autoUpdate", true)
        set(v) = p.edit().putBoolean("autoUpdate", v).apply()
    var updateCheckedAt: Long
        get() = p.getLong("updateCheckedAt", 0)
        set(v) = p.edit().putLong("updateCheckedAt", v).apply()
    /** En version, brugeren har bedt om ikke at få tilbudt igen. */
    var skippedVersion: String
        get() = p.getString("skippedVersion", "") ?: ""
        set(v) = p.edit().putString("skippedVersion", v).apply()
    /** Vejledningen er vist én gang. */
    var guideSeen: Boolean
        get() = p.getBoolean("guideSeen", false)
        set(v) = p.edit().putBoolean("guideSeen", v).apply()
    var keepScreenOn: Boolean
        get() = p.getBoolean("keepOn", true)
        set(v) = p.edit().putBoolean("keepOn", v).apply()
    var batteryHintShown: Boolean
        get() = p.getBoolean("batteryHint", false)
        set(v) = p.edit().putBoolean("batteryHint", v).apply()
    /** false indtil kortet har været flyttet eller der har været et GPS-fix. */
    val hasLastPos: Boolean get() = p.contains("lastLat")
    var lastLat: Double
        get() = p.getFloat("lastLat", 56.0f).toDouble()
        set(v) = p.edit().putFloat("lastLat", v.toFloat()).apply()
    var lastLon: Double
        get() = p.getFloat("lastLon", 10.5f).toDouble()
        set(v) = p.edit().putFloat("lastLon", v.toFloat()).apply()

    fun ignoreRanges(): List<Pair<Double, Double>> = ignore.split(';', '\n').mapNotNull { part ->
        val ab = part.trim().split('-', '–').mapNotNull { parse(it) }
        if (ab.size == 2 && ab[1] > ab[0]) ab[0] * 1e6 to ab[1] * 1e6 else null
    }

    /** Detektorens opsætning ud fra indstillingerne. Kaster IllegalArgumentException, hvis båndet ikke giver mening. */
    fun detectorConfig(): BurstDetector.Config {
        val lo = bandLoMhz * 1e6
        val hi = bandHiMhz * 1e6
        require(hi > lo) { "Båndets øvre grænse skal være højere end den nedre" }
        require(hi - lo <= 880_000) { "Båndet må højst være 880 kHz bredt" }
        val on = onSnrDb.coerceIn(2.0, 40.0)
        return BurstDetector.Config(
            tuneHz = BurstDetector.tuneFor(lo, hi),
            bandLoHz = lo,
            bandHiHz = hi,
            signalBwHz = signalKhz.coerceIn(1.5, 100.0) * 1000,
            onSnrDb = on,
            offSnrDb = on / 2,
            minBurstMs = minBurstMs.coerceIn(10, 5000),
            ignore = ignoreRanges(),
        )
    }

    companion object {
        /** Tal med punktum eller komma. */
        fun parse(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()
    }
}
