package tv.own.owntv.core.database

import tv.own.owntv.core.database.entity.CategoryEntity
import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.entity.ChannelEntity

/**
 * Behavioral tests for the bounded window queries added for direct-tune's zap-list rebuild.
 * These verify the (sortOrder, id) cursor predicate matches the ORDER BY so no channel slips
 * through the gap when sortOrder ties — the regression the DAO was fixed for.
 */
@RunWith(AndroidJUnit4::class)
class ChannelDaoBoundedWindowTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: OwnTVDatabase
    private lateinit var dao: ChannelDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, OwnTVDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.channelDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun findByNumber_returnsAllMatchingChannels_acrossCategories() = runBlocking {
        seedCategory(
            categoryId = 100,
            channels = listOf(
                ChannelSpec(id = 1L, name = "A-News",   number = 5, sortOrder = 0),
                ChannelSpec(id = 2L, name = "A-Music", number = 5, sortOrder = 1),  // duplicate number
            ),
        )
        seedCategory(
            categoryId = 200,
            channels = listOf(
                ChannelSpec(id = 3L, name = "B-News",   number = 5, sortOrder = 0),
            ),
        )

        val results = dao.findByNumber(5)
        assertEquals(3, results.size)
        assertEquals(setOf("ch1", "ch2", "ch3"), results.map { it.id }.toSet())
    }

    @Test
    fun findByNumber_returnsEmptyForUnknownNumber() = runBlocking {
        seedCategory(
            categoryId = 100,
            channels = listOf(
                ChannelSpec(id = 1L, name = "A-News",   number = 5, sortOrder = 0),
            ),
        )
        assertEquals(emptyList<ChannelEntity>(), dao.findByNumber(99))
    }

    /**
     * findByNumber must return duplicates in a stable, deterministic order so the resolver's
     * single-match fast path is meaningful. The ORDER BY is (sourceId, sortOrder, name, id).
     */
    @Test
    fun findByNumber_duplicateNumber_returnsInDeterministicOrder() = runBlocking {
        seedCategory(
            categoryId = 100,
            channels = listOf(
                ChannelSpec(id = 1L, name = "Bravo", number = 7, sortOrder = 0),
                ChannelSpec(id = 2L, name = "Alpha", number = 7, sortOrder = 0),  // same sortOrder, name sorts first
                ChannelSpec(id = 3L, name = "Alpha", number = 7, sortOrder = 0),  // same name + sortOrder → id tiebreaker
                ChannelSpec(id = 4L, name = "Charlie", number = 7, sortOrder = 1),
            ),
        )

        val results = dao.findByNumber(7)
        assertEquals(listOf("ch2", "ch3", "ch1", "ch4"), results.map { it.id })
    }

    @Test
    fun channelsAfterCategory_excludesTunedChannel_andStopsAtLimit() = runBlocking {
        // 8 channels in category 100; tuned channel is id=5 (sortOrder=4). Half-window=3 means
        // the next 3 strictly-after rows: id=6,7,8.
        seedCategory(
            categoryId = 100,
            channels = (1L..8L).map { i ->
                ChannelSpec(id = i, name = "Ch$i", number = i.toInt(), sortOrder = i.toInt() - 1)
            },
        )

        val after = dao.channelsAfterCategory(
            categoryId = 100, afterSortOrder = 4, afterRemoteId = "ch5", limit = 3,
        )
        assertEquals(listOf("ch6", "ch7", "ch8"), after.map { it.id })
    }

    @Test
    fun channelsBeforeCategory_returnsInReverse_andExcludesTunedChannel() = runBlocking {
        // Tuned channel is id=5 (sortOrder=4). Half-window=3 means the 3 strictly-before rows
        // (id=4,3,2) in DESC order.
        seedCategory(
            categoryId = 100,
            channels = (1L..8L).map { i ->
                ChannelSpec(id = i, name = "Ch$i", number = i.toInt(), sortOrder = i.toInt() - 1)
            },
        )

        val before = dao.channelsBeforeCategory(
            categoryId = 100, beforeSortOrder = 4, beforeRemoteId = "ch5", limit = 3,
        )
        assertEquals(listOf("ch4", "ch3", "ch2"), before.map { it.id })
    }

    /**
     * The exact regression: sortOrder ties. Three channels at sortOrder=5, ids 10/11/12; tuned
     * channel is id=11. After-query must return id=12 (strictly greater), never id=10 or 11 —
     * the old `(sortOrder > x)` predicate would have skipped id=12 entirely.
     */
    @Test
    fun channelsAfterCategory_handlesSortOrderTie_viaIdTiebreaker() = runBlocking {
        seedCategory(
            categoryId = 100,
            channels = listOf(
                ChannelSpec(id = 10L, name = "Tied-A", number = 1, sortOrder = 5),
                ChannelSpec(id = 11L, name = "Tuned",  number = 2, sortOrder = 5),  // tuned
                ChannelSpec(id = 12L, name = "Tied-B", number = 3, sortOrder = 5),  // must appear in AFTER
                ChannelSpec(id = 13L, name = "Tied-C", number = 4, sortOrder = 5),  // must appear in AFTER after id=12
            ),
        )

        val after = dao.channelsAfterCategory(
            categoryId = 100, afterSortOrder = 5, afterRemoteId = "ch11", limit = 10,
        )
        assertEquals(listOf("ch12", "ch13"), after.map { it.id })
    }

    @Test
    fun channelsBeforeCategory_handlesSortOrderTie_viaIdTiebreaker() = runBlocking {
        seedCategory(
            categoryId = 100,
            channels = listOf(
                ChannelSpec(id = 10L, name = "Tied-A", number = 1, sortOrder = 5),  // must appear in BEFORE
                ChannelSpec(id = 11L, name = "Tied-B", number = 2, sortOrder = 5),  // must appear in BEFORE
                ChannelSpec(id = 12L, name = "Tuned",  number = 3, sortOrder = 5),  // tuned
                ChannelSpec(id = 13L, name = "Tied-C", number = 4, sortOrder = 5),
            ),
        )

        val before = dao.channelsBeforeCategory(
            categoryId = 100, beforeSortOrder = 5, beforeRemoteId = "ch12", limit = 10,
        )
        // DESC order: id=11 first, then id=10.
        assertEquals(listOf("ch11", "ch10"), before.map { it.id })
    }

    @Test
    fun channelsAfterSource_spansAcrossCategories() = runBlocking {
        // Source 10 has two categories. Tuned channel in cat 100; AFTER must include channels
        // from cat 200 that have a higher sortOrder than the tuned channel.
        seedCategory(
            categoryId = 100,
            channels = listOf(
                ChannelSpec(id = 1L, name = "Cat100-A", number = 1, sortOrder = 0),
                ChannelSpec(id = 2L, name = "Cat100-B", number = 2, sortOrder = 1),  // tuned
            ),
        )
        seedCategory(
            categoryId = 200,
            channels = listOf(
                ChannelSpec(id = 3L, name = "Cat200-A", number = 3, sortOrder = 2),
                ChannelSpec(id = 4L, name = "Cat200-B", number = 4, sortOrder = 3),
            ),
        )

        val after = dao.channelsAfterSource(afterSortOrder = 1, afterRemoteId = "ch2", limit = 10)
        assertEquals(listOf("ch3", "ch4"), after.map { it.id })
    }

    @Test
    fun channelsAfterSource_includesTiedPositionsByRemoteId() = runBlocking {
        // One built-in source: the window is the whole catalogue's order. A tied sortOrder is
        // ordered by the hex key, so the cursor must resume at exactly the next key.
        seedCategory(
            categoryId = 100,
            channels = listOf(
                ChannelSpec(id = 1L, name = "A-1", number = 1, sortOrder = 0),
                ChannelSpec(id = 2L, name = "A-2", number = 2, sortOrder = 1),  // tuned
                ChannelSpec(id = 3L, name = "A-3", number = 3, sortOrder = 1),
            ),
        )

        val after = dao.channelsAfterSource(afterSortOrder = 1, afterRemoteId = "ch2", limit = 10)
        assertEquals(listOf("ch3"), after.map { it.id })
    }

    @Test
    fun channelsBeforeSource_handlesSortOrderTie() = runBlocking {
        seedCategory(
            categoryId = 100,
            channels = listOf(
                ChannelSpec(id = 10L, name = "Tied-A", number = 1, sortOrder = 5),
                ChannelSpec(id = 11L, name = "Tied-B", number = 2, sortOrder = 5),
                ChannelSpec(id = 12L, name = "Tuned",  number = 3, sortOrder = 5),
                ChannelSpec(id = 13L, name = "Tied-C", number = 4, sortOrder = 5),
            ),
        )

        val before = dao.channelsBeforeSource( beforeSortOrder = 5, beforeRemoteId = "ch12", limit = 10,
        )
        assertEquals(listOf("ch11", "ch10"), before.map { it.id })
    }

    /**
     * Integration check for the direct-tune resolver: findByNumber returns all candidates in
     * a deterministic order, and resolveDirectTuneCandidate applies the zap-context tiebreaker
     * exactly once per (source, number) group. Hidden channels are filtered before resolution
     * (LiveViewModel does that), so we test with an unfiltered candidate set here.
     */
    @Test
    fun resolveDirectTune_integration_withDaoCandidates() = runBlocking {
        seedCategory(
            categoryId = 100,
            channels = listOf(
                ChannelSpec(id = 1L, name = "News-A",   number = 42, sortOrder = 0),
                ChannelSpec(id = 2L, name = "Music-A", number = 42, sortOrder = 1),  // duplicate
                ChannelSpec(id = 3L, name = "Movies-A", number = 42, sortOrder = 2),  // duplicate
            ),
        )

        val candidates = dao.findByNumber(42)
            .map { it.id }

        // No zap context → ambiguous (3 candidates, none in zap).
        assertNull(tv.own.myowntv.player.resolveDirectTuneCandidate(candidates, emptySet()))

        // Exactly one in zap context → that one wins.
        assertEquals("ch2", tv.own.myowntv.player.resolveDirectTuneCandidate(candidates, setOf("ch2")))

        // Two in zap context → still ambiguous (multiple visible duplicates).
        assertNull(tv.own.myowntv.player.resolveDirectTuneCandidate(candidates, setOf("ch1", "ch2")))
    }

    // --- helpers ---

    private suspend fun seedCategory(
        categoryId: Long,
        channels: List<ChannelSpec>,
    ) {
        val rows = channels.map { spec ->
            ChannelEntity(
                // The junction's natural key: the hex the channel is known by everywhere.
                remoteId = "ch${spec.id}",
                name = spec.name,
                number = spec.number,
                sortOrder = spec.sortOrder,
            )
        }
        dao.upsertAll(rows)
        // The junction has FKs to both the catalogue and the category, so the category must exist.
        db.categoryDao().insertAll(listOf(CategoryEntity(id = categoryId, name = "Cat $categoryId")))
        db.channelCategoryDao().insertAll(
            rows.map { ChannelCategoryEntity(channelHexId = it.remoteId, categoryId = categoryId) },
        )
    }

    private data class ChannelSpec(val id: Long, val name: String, val number: Int, val sortOrder: Int)
}