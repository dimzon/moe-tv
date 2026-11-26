package tv.own.myowntv.features.live

import tv.own.owntv.core.live.EpgNowNext
import tv.own.owntv.core.live.nowNextForChannel
import tv.own.owntv.core.live.rowProgramme
import tv.own.owntv.core.parser.XtEpgEntry
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
        assertNull(nowNextForChannel("old" to old, "new", nowMs = 1_000L))
        assertEquals(old, nowNextForChannel("old" to old, "old", nowMs = 1_000L))
    }

    @Test
    fun `an answer is used once it was read for the channel on screen`() {
        val fresh = epg("New show")
        assertEquals(fresh, nowNextForChannel("new" to fresh, "new", nowMs = 1_000L))
    }

    @Test
    fun `no answer means no programme, whatever the channel`() {
        assertNull(nowNextForChannel(null to null, "new", nowMs = 1_000L))
        assertNull(nowNextForChannel("old" to epg("Old show"), null, nowMs = 1_000L))
    }

    @Test
    fun `a focused row falls back to its own guide while the debounced read is pending`() {
        val fromGuide = show("New show from guide")
        val row = rowProgramme(
            "old" to epg("Old show"),
            "new",
            mapOf("new" to fromGuide),
            nowMs = 1_000L,
        )
        assertEquals(fromGuide, row)
    }

    @Test
    fun `a fresh answer wins for its row and other rows keep their own guide`() {
        val fresh = show("New show from preview")
        val other = show("Other show")
        val keyed = "new" to epg(fresh.title)
        val perChannel = mapOf("new" to show("New show from guide"), "other" to other)
        assertEquals(fresh, rowProgramme(keyed, "new", perChannel, nowMs = 1_000L))
        assertEquals(other, rowProgramme(keyed, "other", perChannel, nowMs = 1_000L))
    }

    @Test
    fun `an expired focused answer is rejected at the programme boundary`() {
        val old = show("Old show")
        assertNull(nowNextForChannel("new" to EpgNowNext(old, null), "new", nowMs = old.stopMs))
    }

    @Test
    fun `an expired batch fallback is rejected too`() {
        val old = show("Old show")
        assertNull(rowProgramme("new" to null, "new", mapOf("new" to old), nowMs = old.stopMs))
    }

    @Test
    fun `an expired keyed answer yields to the fresh batch answer`() {
        val old = show("Old show")
        val fresh = XtEpgEntry("New show", null, old.stopMs, old.stopMs + 3_600_000L)
        assertEquals(
            fresh,
            rowProgramme("new" to EpgNowNext(old, fresh), "new", mapOf("new" to fresh), nowMs = old.stopMs),
        )
    }
}
