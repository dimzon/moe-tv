package tv.own.owntv.core.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.network.StreamHeaders

class SourceOverridesTest {

    private fun source(
        timeout: Int? = null, referer: String? = null,
    ) = SourceEntity(
        name = "p", type = SourceType.M3U, url = "http://x",
        liveTuneTimeoutSecs = timeout, httpReferer = referer,
    )

    @Test fun nullColumnsFollowTheGlobalSetting() {
        val s = source()
        assertNull(SourceOverrides.liveTuneTimeoutSecsOf(s))
        assertEquals("Cookie: a", SourceOverrides.headersWithReferer("Cookie: a", s))
        assertNull(SourceOverrides.liveTuneTimeoutSecsOf(null))
    }

    @Test fun tuneTimeoutParse() {
        assertEquals(0, SourceOverrides.liveTuneTimeoutSecsOf(source(timeout = 0)))
        assertEquals(60, SourceOverrides.liveTuneTimeoutSecsOf(source(timeout = 999)))
    }

    @Test fun refererIsAddedButAStreamsOwnWins() {
        val merged = StreamHeaders.decode(SourceOverrides.headersWithReferer("Cookie: a", source(referer = " https://r/ ")))
        assertEquals(mapOf("Cookie" to "a", "Referer" to "https://r/"), merged)
        assertEquals("Referer: https://own", SourceOverrides.headersWithReferer("Referer: https://own", source(referer = "https://r/")))
        assertEquals("Referer: https://r/", SourceOverrides.headersWithReferer(null, source(referer = "https://r/")))
        assertNull(SourceOverrides.headersWithReferer(null, source(referer = "  ")))
    }
}
