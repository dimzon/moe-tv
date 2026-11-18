package tv.own.owntv.core.content

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tv.own.owntv.core.database.ChannelMembership
import tv.own.owntv.core.database.SingleSource
import tv.own.owntv.core.database.entity.ChannelEntity

/** Curated search lists must keep ordinary channels and drop only fully locked ones. */
class CuratedChannelEligibilityTest {

    private val originalSnapshot = ChannelMembership.snapshot
    private val liveSources = listOf(SingleSource.id)

    @Before
    fun setUp() {
        ChannelMembership.snapshot = emptyMap()
    }

    @After
    fun tearDown() {
        ChannelMembership.snapshot = originalSnapshot
    }

    private fun channel(id: String, categoryIds: Set<Long>): ChannelEntity {
        val entity = ChannelEntity(remoteId = id, name = id)
        ChannelMembership.snapshot = ChannelMembership.snapshot + (entity.remoteId to categoryIds)
        return entity
    }

    @Test
    fun `a curated list keeps a channel reachable through one open category`() {
        val channel = channel("open", categoryIds = setOf(1L, 2L))

        assertTrue(isCuratedChannel(channel, liveSources, sealedCategoryIds = setOf(1L)))
    }

    @Test
    fun `a curated list drops a channel whose every category is sealed`() {
        val channel = channel("sealed", categoryIds = setOf(1L, 2L))

        assertFalse(isCuratedChannel(channel, liveSources, sealedCategoryIds = setOf(1L, 2L)))
    }

    @Test
    fun `a curated list drops a channel from an inactive source`() {
        val channel = channel("other-source", categoryIds = setOf(1L))

        assertFalse(isCuratedChannel(channel, listOf(999L), sealedCategoryIds = emptySet()))
    }

    @Test
    fun `a curated list keeps a channel without categories even when categories are sealed`() {
        val channel = channel("no-categories", categoryIds = emptySet())

        assertTrue(isCuratedChannel(channel, liveSources, sealedCategoryIds = setOf(1L)))
    }

    @Test
    fun `no sealed categories means every channel of an active source is kept`() {
        assertTrue(isCuratedChannel(channel("a", setOf(1L)), liveSources, sealedCategoryIds = emptySet()))
        assertTrue(isCuratedChannel(channel("b", setOf(1L, 2L)), liveSources, sealedCategoryIds = emptySet()))
    }
}
