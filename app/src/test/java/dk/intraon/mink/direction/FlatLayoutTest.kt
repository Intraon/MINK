package dk.intraon.mink.direction

import org.junit.Assert.assertTrue
import org.junit.Test

class FlatLayoutTest {
    @Test fun tekstenUnderRosenStaarAldrigBagKnappen() {
        val d = 2.75f
        // et lavt felt (lille telefon med vandfald og tal over), et almindeligt og et højt
        for (hDp in listOf(260f, 380f, 640f)) {
            val h = hDp * d
            val l = FlatLayout(393 * d, h, d)
            assertTrue("teksten går fri af knappen ved $hDp", l.bestY + 5 * d <= h - FlatLayout.BUTTON * d)
            assertTrue("rosen går fri af feltet øverst ved $hDp", l.centerY - l.radius >= (FlatLayout.TOP + 18) * d)
            assertTrue("antallet står over rosen ved $hDp", l.countY < l.centerY - l.radius)
        }
    }
}
