package tv.own.owntv.core.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.database.dao.ChannelSearchResult
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity

/** The search filter buttons: each kind switched on and off on its own, none on meaning everything. */
class SearchKindTest {

    private val bbcOne = ChannelEntity(remoteId = "bbc1", name = "BBC One")
    private val channel = ChannelSearchResult(channel = bbcOne, categoryName = null)
    private val otherChannel = ChannelSearchResult(
        channel = ChannelEntity(remoteId = "bbc2", name = "BBC Two"),
        categoryName = null,
    )
    private val programme = ProgrammeSearchResult(
        programme = EpgProgrammeEntity(sourceId = 1, epgChannelId = "bbc1", startMs = 0, stopMs = 1, title = "News"),
        channel = bbcOne,
    )

    private val results = SearchResults(
        channels = listOf(channel, otherChannel),
        programmes = listOf(programme),
    )

    @Test
    fun noKindPicked_isEverything() {
        assertSame(results, results.only(emptySet()))
    }

    @Test
    fun oneKind_keepsOnlyThatKind() {
        val channels = results.only(setOf(SearchKind.LIVE))
        assertEquals(results.channels, channels.channels)
        assertTrue(channels.programmes.isEmpty())
    }

    @Test
    fun severalKinds_keepEachOfThem() {
        val both = results.only(setOf(SearchKind.LIVE, SearchKind.TV_SHOWS))
        assertEquals(2, both.count(SearchKind.LIVE))
        assertEquals(1, both.count(SearchKind.TV_SHOWS))
    }

    @Test
    fun aKindWithNothingFound_leavesNothing() {
        assertTrue(SearchResults().only(setOf(SearchKind.TV_SHOWS)).isEmpty)
    }
}
