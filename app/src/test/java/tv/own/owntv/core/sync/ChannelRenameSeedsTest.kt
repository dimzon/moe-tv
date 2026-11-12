package tv.own.owntv.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rename dictionary is data (`assets/channel_renames.json`): parse it, never trust it blindly. */
class ChannelRenameSeedsTest {

    @Test
    fun parsesThePlainHexToNameObject() {
        val map = ChannelRenameSeeds.parse("""{"89e4d6310399df1d824f861e4b312b49":"AKC.tv","e08e513c4e33f52fce3a975b5cb6932c":"ТВОЙ КИНОТЕАТР"}""")
        assertEquals(2, map.size)
        assertEquals("AKC.tv", map["89e4d6310399df1d824f861e4b312b49"])
        assertEquals("ТВОЙ КИНОТЕАТР", map["e08e513c4e33f52fce3a975b5cb6932c"])
    }

    @Test
    fun blankNamesAreSkippedNotShipped() {
        val map = ChannelRenameSeeds.parse("""{"hex-1":"  ","hex-2":"X"}""")
        assertEquals(setOf("hex-2"), map.keys)
    }

    @Test
    fun malformedJsonYieldsNothing() {
        assertTrue(ChannelRenameSeeds.parse("not json at all").isEmpty())
    }
}
