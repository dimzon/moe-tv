package tv.own.myowntv.features.live
import tv.own.owntv.*
import tv.own.owntv.core.*
import tv.own.owntv.core.live.*
import tv.own.owntv.core.parser.*

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The now/next read is debounced, so the highlight can reach the next channel before its answer
 * arrives. The answer carries the id it was read for: until that id matches the channel on screen,
 * its programme must not appear in the row, the pane or the HUD.
 */
class PreviewProgrammeKeyTest {

    private fun show(title: String) = XtEpgEntry(title, null, 0L, 3_600_000L)
    private fun epg(title: String) = EpgNowNext(now = show(title), next = null)

    @Test
    fun `the previous channel's answer is not offered to the newly focused one`() {
        val old = epg("Old show")
        assertNull(nowNextForChannel("old" to old, "new"))
        assertEquals(old, nowNextForChannel("old" to old, "old"))
    }

    @Test
    fun `an answer is used once it was read for the channel on screen`() {
        val fresh = epg("New show")
        assertEquals(fresh, nowNextForChannel("new" to fresh, "new"))
    }

    @Test
    fun `no answer means no programme, whatever the channel`() {
        assertNull(nowNextForChannel(null to null, "new"))
        assertNull(nowNextForChannel("old" to epg("Old show"), null))
    }

    @Test
    fun `a focused row falls back to its own guide while the debounced read is pending`() {
        val fromGuide = show("New show from guide")
        val row = rowProgramme(
            "old" to epg("Old show"),
            "new",
            mapOf("new" to fromGuide),
        )
        assertEquals(fromGuide, row)
    }

    @Test
    fun `a fresh answer wins for its row and other rows keep their own guide`() {
        val fresh = show("New show from preview")
        val other = show("Other show")
        val keyed = "new" to epg(fresh.title)
        val perChannel = mapOf("new" to show("New show from guide"), "other" to other)
        assertEquals(fresh, rowProgramme(keyed, "new", perChannel))
        assertEquals(other, rowProgramme(keyed, "other", perChannel))
    }
}
