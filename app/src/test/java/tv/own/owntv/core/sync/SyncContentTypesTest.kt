package tv.own.owntv.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType

/** The app is live-only: [SyncContentTypes] has exactly one section, and every derivation is about it. */
class SyncContentTypesTest {

    @Test
    fun `effectiveFor intersects the request with what the playlist has enabled`() {
        val on = SourceEntity(name = "m3u", type = SourceType.M3U, url = "http://x", syncLive = true)
        val effective = SyncContentTypes().effectiveFor(on)
        assertEquals(SyncContentTypes(live = true), effective)
        assertTrue(effective.isCompleteFor(SyncContentTypes.enabledFor(on)))
        assertTrue(effective.isCompleteFor(SyncContentTypes.enabledOf(on)))

        val off = on.copy(syncLive = false)
        val nothing = SyncContentTypes().effectiveFor(off)
        assertEquals(SyncContentTypes(live = false), nothing)
        assertFalse(nothing.hasAny)
        // A pass that covered nothing is still complete for a target that asks for nothing.
        assertTrue(nothing.isCompleteFor(SyncContentTypes.enabledFor(off)))
    }

    @Test
    fun `a pass that skipped live is not complete for a target that wants it`() {
        val target = SyncContentTypes(live = true)
        assertFalse(SyncContentTypes(live = false).isCompleteFor(target))
        assertTrue(SyncContentTypes(live = true).isCompleteFor(target))
    }

    @Test
    fun `remainderAfter is enabled-relative`() {
        val enabled = SyncContentTypes(live = true)
        assertEquals(
            SyncContentTypes(live = false),
            enabled.remainderAfter(SyncContentTypes(live = true)),
        )
        assertEquals(
            SyncContentTypes(live = true),
            enabled.remainderAfter(SyncContentTypes(live = false)),
        )
    }

    @Test
    fun `choices derive enabled and priority scopes`() {
        assertEquals(SyncContentTypes(live = true), SyncContentTypes.fromChoices(SyncScopeChoice.Now))
        assertEquals(SyncContentTypes(live = true), SyncContentTypes.fromChoices(SyncScopeChoice.Later))
        assertEquals(SyncContentTypes(live = false), SyncContentTypes.fromChoices(SyncScopeChoice.Off))

        assertEquals(SyncContentTypes(live = true), SyncContentTypes.priorityFromChoices(SyncScopeChoice.Now))
        assertEquals(SyncContentTypes(live = false), SyncContentTypes.priorityFromChoices(SyncScopeChoice.Later))
        assertEquals(SyncContentTypes(live = false), SyncContentTypes.priorityFromChoices(SyncScopeChoice.Off))
    }
}
