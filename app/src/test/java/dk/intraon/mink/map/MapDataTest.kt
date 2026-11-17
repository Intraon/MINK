package dk.intraon.mink.map

import dk.intraon.mink.core.Hub
import dk.intraon.mink.core.MarkKind
import org.junit.Assert.assertEquals
import org.junit.Test

class MapDataTest {
    @Test fun maerketsTypeLaesesOgEtMaerkeUdenTypeErAlmindeligt() {
        val log = listOf(
            """{"type":"mark","t":"2026-11-17T12:03:00.000+01:00","lat":55.0001,"lon":9.0001,"n":1}""",
            """{"type":"mark","t":"2026-11-17T12:04:00.000+01:00","lat":55.0002,"lon":9.0002,"n":2,"kind":"possible"}""",
            """{"type":"mark","t":"2026-11-17T12:05:00.000+01:00","lat":55.0003,"lon":9.0003,"n":3,"kind":"found"}""",
            """{"type":"mark","t":"2026-11-17T12:06:00.000+01:00","lat":55.0004,"lon":9.0004,"n":4,"kind":"noget nyt"}""",
            """{"type":"mark","t":"2026-11-17T12:07:00.000+01:00","lat":null,"lon":null,"n":5,"kind":"found"}""",
        )
        val marks = MapData.parse(listOf(log)).marks
        assertEquals("et mærke uden position kan ikke stå på kortet", listOf(1, 2, 3, 4), marks.map { it.n })
        assertEquals(listOf(MarkKind.PLAIN, MarkKind.POSSIBLE, MarkKind.FOUND, MarkKind.PLAIN), marks.map { it.kind })
    }
}
