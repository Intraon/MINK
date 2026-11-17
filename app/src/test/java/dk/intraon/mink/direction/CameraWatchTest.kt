package dk.intraon.mink.direction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraWatchTest {
    @Test
    fun etKameraDerIkkeKunneViseBilledetProevesIgenAfSigSelv() {
        // første gang skærmen åbnes, mislykkes det; andet forsøg lykkes
        val w = CameraWatch()
        assertTrue(w.mayStart(0))
        w.started(0); w.failed(300)
        assertFalse("fejlen vises ikke efter ét forsøg", w.givenUp)
        assertFalse("der holdes en kort pause", w.mayStart(400))
        assertTrue(w.mayStart(300 + CameraWatch.RETRY_MS))
        w.started(1_000); w.frame(1_400)
        assertEquals(0, w.failures)
        assertFalse(w.mayStart(5_000))
    }

    @Test
    fun fejlenVisesFoerstEfterFlereForsoegOgDerProevesStadig() {
        val w = CameraWatch()
        var now = 0L
        repeat(CameraWatch.TRIES) { w.started(now); now += 200; w.failed(now); now += CameraWatch.RETRY_MS }
        assertTrue(w.givenUp)
        assertTrue(w.mayStart(now))
    }

    @Test
    fun etKameraUdenBillederRegnesForGaaetIStaa() {
        val w = CameraWatch()
        w.started(0)
        assertFalse(w.stalled(CameraWatch.STALL_MS))
        assertTrue("startet, men aldrig et billede", w.stalled(CameraWatch.STALL_MS + 1))
        w.frame(4_000)
        assertFalse(w.stalled(7_000))
        assertTrue("billederne holdt op", w.stalled(4_000 + CameraWatch.STALL_MS + 1))
    }
}
