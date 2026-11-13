package tv.own.owntv.core.epg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.database.entity.GuideChannelName

/**
 * The candidate merge, which is what the "Match EPG" picker and auto-match both see.
 *
 * The two sources are the catalogue (`channels`) and the guide rows (`epg_programmes`). Every case
 * here is a real report or a real near-miss: a channel with no programmes yet, and programme rows
 * for an id the catalogue no longer names.
 */
class GuideCandidatesTest {

    @Test
    fun `programme rows without a catalogue channel still produce candidates`() {
        // The defect: an id known only to the guide was invisible to the picker while its rows were
        // being drawn by the grid.
        val merged = GuideCandidates.merge(
            named = emptyList(),
            withProgrammes = listOf("bbc1", "itv1"),
        )

        assertEquals(listOf("bbc1", "itv1"), merged.map { it.epgChannelId })
        assertTrue(merged.all { it.hasProgrammes })
        assertNull(merged.first().displayName)
    }

    @Test
    fun `a catalogue channel with no programmes is offered but flagged`() {
        // Not filtered out: a channel may name a guide today and schedule it tomorrow. But auto-match
        // must be able to tell the difference, which is what hasProgrammes is for.
        val merged = GuideCandidates.merge(
            named = listOf(named("sky1", "Sky One")),
            withProgrammes = emptyList(),
        )

        assertEquals(1, merged.size)
        assertEquals("Sky One", merged.single().displayName)
        assertFalse(merged.single().hasProgrammes)
    }

    @Test
    fun `a blank name is treated as no name`() {
        val merged = GuideCandidates.merge(
            named = listOf(named("bbc1", "   ")),
            withProgrammes = emptyList(),
        )

        assertNull(merged.single().displayName)
    }

    @Test
    fun `the two sources are combined, not concatenated`() {
        // One id in both sources is one candidate, not two.
        val merged = GuideCandidates.merge(
            named = listOf(named("bbc1", "BBC One"), named("sky1", "Sky One")),
            withProgrammes = listOf("bbc1", "itv1"),
        )

        assertEquals(listOf("bbc1", "sky1", "itv1"), merged.map { it.epgChannelId })
        assertEquals(
            mapOf("bbc1" to true, "sky1" to false, "itv1" to true),
            merged.associate { it.epgChannelId to it.hasProgrammes },
        )
        assertNull(merged.first { it.epgChannelId == "itv1" }.displayName)
    }

    @Test
    fun `no sources means no candidates`() {
        assertEquals(emptyList<GuideCandidate>(), GuideCandidates.merge(emptyList(), emptyList()))
    }

    @Test
    fun `names and ids are normalized on the fly`() {
        val merged = GuideCandidates.merge(
            named = listOf(named("bbc.one.uk", "BBC One")),
            withProgrammes = emptyList(),
        )

        assertEquals(EpgMatcher.normalizeForEpg("BBC One"), merged.single().normName)
        assertEquals(EpgMatcher.normalizeForEpg("bbc.one.uk"), merged.single().normId)
    }

    @Test
    fun `an id known only to the guide still gets a normalized id`() {
        val merged = GuideCandidates.merge(named = emptyList(), withProgrammes = listOf("itv.one.uk"))

        assertEquals("", merged.single().normName)
        assertEquals(EpgMatcher.normalizeForEpg("itv.one.uk"), merged.single().normId)
    }

    private fun named(epgChannelId: String, displayName: String?) =
        GuideChannelName(epgChannelId, displayName)
}
