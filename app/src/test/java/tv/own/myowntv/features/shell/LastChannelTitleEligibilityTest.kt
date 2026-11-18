package tv.own.myowntv.features.shell

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tv.own.owntv.core.database.ChannelMembership
import tv.own.owntv.core.database.entity.ChannelEntity

/** The last-watched channel's title on the Live rail: hidden only when fully sealed or fully hidden. */
class LastChannelTitleEligibilityTest {

    private val originalSnapshot = ChannelMembership.snapshot
    private val categoryKeyOf: suspend (Long) -> String? = { id -> "cat-$id" }

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
    fun `title shows when a channel has an open category and none is hidden`() = runBlocking {
        val channel = channel("ok", categoryIds = setOf(1L, 2L))

        assertTrue(
            canShowLastChannelTitle(
                channel,
                sealedCategoryIds = setOf(1L),
                hiddenCategoryKeys = emptySet(),
                categoryKeyOf = categoryKeyOf,
            ),
        )
    }

    @Test
    fun `title hides when every category is sealed`() = runBlocking {
        val channel = channel("sealed", categoryIds = setOf(1L, 2L))

        assertFalse(
            canShowLastChannelTitle(
                channel,
                sealedCategoryIds = setOf(1L, 2L),
                hiddenCategoryKeys = emptySet(),
                categoryKeyOf = categoryKeyOf,
            ),
        )
    }

    @Test
    fun `title shows when only one of the categories is hidden`() = runBlocking {
        val channel = channel("partly-hidden", categoryIds = setOf(1L, 2L))

        assertTrue(
            canShowLastChannelTitle(
                channel,
                sealedCategoryIds = emptySet(),
                hiddenCategoryKeys = setOf("cat-2"),
                categoryKeyOf = categoryKeyOf,
            ),
        )
    }

    @Test
    fun `title hides when every category is hidden in Customize`() = runBlocking {
        val channel = channel("hidden", categoryIds = setOf(1L, 2L))

        assertFalse(
            canShowLastChannelTitle(
                channel,
                sealedCategoryIds = emptySet(),
                hiddenCategoryKeys = setOf("cat-1", "cat-2"),
                categoryKeyOf = categoryKeyOf,
            ),
        )
    }

    @Test
    fun `title shows for a channel with no categories`() = runBlocking {
        val channel = channel("no-categories", categoryIds = emptySet())

        assertTrue(
            canShowLastChannelTitle(
                channel,
                sealedCategoryIds = setOf(1L),
                hiddenCategoryKeys = setOf("cat-1"),
                categoryKeyOf = categoryKeyOf,
            ),
        )
    }
}
