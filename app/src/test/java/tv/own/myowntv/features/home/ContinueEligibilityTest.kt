package tv.own.myowntv.features.home

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tv.own.owntv.core.database.ChannelMembership
import tv.own.owntv.core.database.entity.ChannelEntity

/**
 * The Home "Continue watching" chip must follow the same M:N rule as every other channel list:
 * a channel is hidden only when ALL of its categories are sealed. This is a regression guard for
 * the M:N migration, which briefly inverted the predicate and showed the chip only for locked
 * channels while hiding it for ordinary ones.
 */
class ContinueEligibilityTest {

    private val originalSnapshot = ChannelMembership.snapshot

    @Before
    fun setUp() {
        ChannelMembership.snapshot = emptyMap()
    }

    @After
    fun tearDown() {
        ChannelMembership.snapshot = originalSnapshot
    }

    /** Seeds the junction snapshot the pure visibility helpers read. */
    private fun channel(id: String, categoryIds: Set<Long>): ChannelEntity {
        val entity = ChannelEntity(remoteId = id, name = id)
        ChannelMembership.snapshot = ChannelMembership.snapshot + (entity.remoteId to categoryIds)
        return entity
    }

    @Test
    fun `a channel with one open and one sealed category keeps its chip`() {
        // The rule under test: hiding needs EVERY category to be sealed.
        val channel = channel("visible", categoryIds = setOf(1L, 2L))

        assertTrue(canContinueWatching(channel, sealedCategoryIds = setOf(1L)))
    }

    @Test
    fun `a channel whose every category is sealed loses its chip`() {
        val channel = channel("sealed", categoryIds = setOf(1L, 2L))

        assertFalse(canContinueWatching(channel, sealedCategoryIds = setOf(1L, 2L)))
    }

    @Test
    fun `a single-category channel is hidden when that category is sealed`() {
        val channel = channel("one-category", categoryIds = setOf(7L))

        assertFalse(canContinueWatching(channel, sealedCategoryIds = setOf(7L)))
    }

    @Test
    fun `a channel with no categories is never hidden`() {
        val channel = channel("no-categories", categoryIds = emptySet())

        assertTrue(canContinueWatching(channel, sealedCategoryIds = setOf(1L, 2L)))
    }

    @Test
    fun `a channel missing from the junction snapshot is never hidden`() {
        // Never synced / no genres yet — mirrors the old `categoryId == null` keep.
        val channel = ChannelEntity(remoteId = "unseeded", name = "unseeded")

        assertTrue(canContinueWatching(channel, sealedCategoryIds = setOf(1L)))
    }

    @Test
    fun `no sealed categories means every channel is eligible`() {
        assertTrue(canContinueWatching(channel("a", setOf(1L)), sealedCategoryIds = emptySet()))
        assertTrue(canContinueWatching(channel("b", setOf(1L, 2L)), sealedCategoryIds = emptySet()))
    }
}
