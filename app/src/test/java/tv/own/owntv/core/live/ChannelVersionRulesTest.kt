package tv.own.owntv.core.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.database.entity.ChannelEntity

/** Which channels the player's Sources list offers as versions of the one playing. */
class ChannelVersionRulesTest {

    private fun f(name: String) = ChannelFacets.of(name)

    @Test
    fun `the same name in another quality is the same channel`() {
        assertTrue(ChannelVersionRules.same(f("ES| DAZN LaLiga FHD"), null, f("DAZN LaLiga HD"), null))
        assertTrue(ChannelVersionRules.same(f("Sky Sport News ᴴᴰ"), null, f("Sky Sport News"), null))
    }

    @Test
    fun `two different countries are two different feeds`() {
        assertFalse(ChannelVersionRules.same(f("ES| DAZN 1"), null, f("AR| DAZN 1"), null))
    }

    @Test
    fun `a different channel is not a version`() {
        assertFalse(ChannelVersionRules.same(f("DAZN 1"), null, f("DAZN 2"), null))
        assertFalse(ChannelVersionRules.same(f("Sky Cinema Action"), null, f("Sky Cinema Comedy"), null))
    }

    @Test
    fun `a shared guide id makes two names one channel when one name holds the other`() {
        assertTrue(ChannelVersionRules.same(f("Sky News Morning HEVC"), "ssn.uk", f("Sky News Morning HQ"), "ssn.uk"))
    }

    @Test
    fun `a sister channel filed under the same guide id is not a version`() {
        assertFalse(ChannelVersionRules.same(f("Sky News Morning"), "ssn.uk", f("Sky Events Morning HEVC HD"), "ssn.uk"))
    }

    @Test
    fun `a very short name is matched by guide id only`() {
        assertFalse(ChannelVersionRules.same(f("TV"), null, f("TV"), null))
    }

    @Test
    fun `best picture first, then the provider's order`() {
        fun v(id: String, rank: Int, order: Int = 0) = ChannelVersion(
            ChannelEntity(remoteId = id, name = "C$id", sortOrder = order),
            "C$id", emptyList(), rank, null,
        )
        val ordered = ChannelVersionRules.ordered(listOf(v("c1", 2, 2), v("c2", 2, 1), v("c3", 4, 0)))
        assertEquals(listOf("c3", "c2", "c1"), ordered.map { it.channel.id })
    }

    @Test
    fun `the channel playing comes first`() {
        fun v(id: String, rank: Int) = ChannelVersion(
            ChannelEntity(remoteId = id, name = "C$id"), "C$id", emptyList(), rank, null,
        )
        assertEquals(listOf("c1", "c2"), ChannelVersionRules.ordered(listOf(v("c1", 0), v("c2", 4)), playingId = "c1").map { it.channel.id })
    }

    @Test
    fun `the search uses the name's own words, not its quality marks`() {
        assertEquals(listOf("dazn", "laliga"), ChannelVersionRules.searchWords(f("ES| DAZN LaLiga FHD")))
    }
}
