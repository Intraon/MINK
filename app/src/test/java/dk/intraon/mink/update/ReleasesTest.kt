package dk.intraon.mink.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleasesTest {
    private val latest = """
        {"tag_name":"v0.4.460900","name":"MINK 0.4.460900","draft":false,"prerelease":false,
         "html_url":"https://github.com/Intraon/MINK/releases/tag/v0.4.460900",
         "body":"Roligere kompas.\nFlad tilstand.",
         "assets":[
           {"name":"MINK.apk.sha256","size":75,"browser_download_url":"https://github.com/Intraon/MINK/releases/download/v0.4.460900/MINK.apk.sha256"},
           {"name":"MINK.apk","size":3141592,"browser_download_url":"https://github.com/Intraon/MINK/releases/download/v0.4.460900/MINK.apk"}
         ]}
    """.trimIndent()

    @Test fun laeserUdgivelsenOgFinderApken() {
        val r = Releases.parse(latest)!!
        assertEquals("0.4.460900", r.version)
        assertEquals("https://github.com/Intraon/MINK/releases/download/v0.4.460900/MINK.apk", r.apkUrl)
        assertEquals(3141592L, r.apkBytes)
        assertTrue(r.notes.startsWith("Roligere kompas"))
        assertEquals("https://github.com/Intraon/MINK/releases/tag/v0.4.460900", r.pageUrl)
    }

    @Test fun udgivelseUdenApkGiverIngenAdresse() {
        val r = Releases.parse("""{"tag_name":"v0.5.1","assets":[],"html_url":"x","body":""}""")!!
        assertNull(r.apkUrl)
    }

    @Test fun etRepoDerIkkeFindesErIkkeEnUdgivelse() {
        assertNull(Releases.parse("""{"message":"Not Found","status":"404"}"""))
        assertNull(Releases.parse("ikke json"))
        assertNull("kladder tilbydes ikke", Releases.parse("""{"tag_name":"v9.0.0","draft":true}"""))
        assertNull("forhåndsudgaver tilbydes ikke", Releases.parse("""{"tag_name":"v9.0.0","prerelease":true}"""))
    }

    @Test fun noterneVisesUdenMarkdownOgAdresser() {
        val fraGithub = "## What's Changed\n* Hold teksten fri af knappen by @nogen in https://github.com/Intraon/MINK/pull/2\n\n\n" +
            "**Full Changelog**: https://github.com/Intraon/MINK/commits/v0.4.461392"
        assertEquals("What's Changed\n• Hold teksten fri af knappen", Releases.readable(fraGithub))
        val fraUdgiv = "Ændringer:\n\n- Ret `ppm` med [ét tryk](https://example.org/x)\n- Vis **retningen**\n"
        assertEquals("Ændringer:\n\n• Ret ppm med ét tryk\n• Vis retningen", Releases.readable(fraUdgiv))
    }

    @Test fun versionerSammenlignesSomTal() {
        assertTrue(Releases.newer("0.4.460900", "0.4.460694"))
        assertTrue("0.10 er nyere end 0.9", Releases.newer("0.10.1", "0.9.999999"))
        assertTrue(Releases.newer("v1.0.0", "0.4.460694"))
        assertFalse("samme version", Releases.newer("0.4.460694", "0.4.460694"))
        assertFalse("ældre", Releases.newer("0.3.460641", "0.4.460694"))
        assertFalse("kan ikke læses: tilbyd ingenting", Releases.newer("nightly", "0.4.460694"))
    }


    @Test fun etTjekUdenSvarUdskyderDetNaesteMedEnTimeOgIkkeEtHalvtDoegn() {
        val hour = 3600_000L; val now = 100 * hour
        assertFalse("GitHub svarede for to timer siden", Releases.due(now, now - 2 * hour, 0))
        assertTrue("sidste svar er 13 timer gammelt", Releases.due(now, now - 13 * hour, 0))
        assertFalse("der blev prøvet uden held for ti minutter siden", Releases.due(now, now - 13 * hour, now - hour / 6))
        assertTrue("forsøget uden held er to timer gammelt", Releases.due(now, now - 13 * hour, now - 2 * hour))
        assertTrue("en ny installation har aldrig spurgt", Releases.due(now, 0, 0))
    }
}
