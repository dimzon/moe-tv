package tv.own.owntv.core.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import tv.own.owntv.core.database.entity.CategoryEntity
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.CustomCategoryMemberEntity
import tv.own.owntv.core.database.entity.FavoriteEntity
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.database.entity.WatchHistoryEntity
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.model.SourceType

/**
 * The lock-aware counts against a real database: an item in an excluded (locked) category drops out,
 * an item with no category stays, a favorite or member whose item no longer exists counts in neither
 * form, and an empty exclusion list counts exactly what the plain query does.
 */
@RunWith(AndroidJUnit4::class)
class ExcludingCountDaoTest {

    private lateinit var db: OwnTVDatabase
    private var source = 0L
    private var locked = 0L
    private var open = 0L

    @Before
    fun setUp() = runBlocking {
        db = ownTVTestDatabase()
        source = db.sourceDao().insert(SourceEntity(name = "Test", type = SourceType.RUTUBE, url = "https://test"))
        val (a, b) = db.categoryDao().insertAll(
            listOf(
                CategoryEntity(sourceId = source, mediaType = MediaType.LIVE, name = "Locked"),
                CategoryEntity(sourceId = source, mediaType = MediaType.LIVE, name = "Open"),
            ),
        )
        locked = a
        open = b
        db.channelDao().insertAll(
            listOf(
                channel(1, locked),
                channel(2, locked),
                channel(3, open),
                channel(4, null),
            ),
        )
    }

    @After
    fun tearDown() = db.close()

    /** Membership is many-to-many now: [categoryId] goes into the junction, not the channel row. */
    private suspend fun channel(id: Long, categoryId: Long?): ChannelEntity {
        val row = ChannelEntity(remoteId = "ch$id", name = "Ch $id")
        if (categoryId != null) {
            db.channelCategoryDao().insertAll(listOf(ChannelCategoryEntity(channelHexId = row.remoteId, categoryId = categoryId)))
        }
        return row
    }

    @Test
    fun channelCountsDropOnlyTheExcludedCategory() = runBlocking {
        val channels = db.channelDao()
        assertEquals(2, channels.countAllExcluding(listOf(locked)).first())
        assertEquals(channels.countAll().first(), channels.countAllExcluding(emptyList()).first())
    }

    @Test
    fun favoritesAndHistoryKeepUncategorisedItemsAndSkipOrphans() = runBlocking {
        listOf("ch1", "ch3", "ch4", ORPHAN).forEach {
            db.favoriteDao().add(FavoriteEntity(mediaType = MediaType.LIVE, itemId = it))
            db.historyDao().record(WatchHistoryEntity(mediaType = MediaType.LIVE, itemId = it))
        }
        val channels = db.channelDao()
        assertEquals(2, channels.countFavoritesExcluding(listOf(locked)).first())
        assertEquals(2, channels.countHistoryExcluding(listOf(locked)).first())
        assertEquals(
            channels.countFavorites().first(),
            channels.countFavoritesExcluding(emptyList()).first(),
        )
        assertEquals(
            channels.countHistory().first(),
            channels.countHistoryExcluding(emptyList()).first(),
        )
    }

    @Test
    fun customCategoryCountsKeepUncategorisedItemsAndSkipOrphans() = runBlocking {
        val custom = db.customCategoryDao()
        custom.insertAll(
            listOf("ch1", "ch3", "ch4", ORPHAN).mapIndexed { i, id -> member(FIRST, id, i) } + member(SECOND, "ch2", 0),
        )
        assertEquals(2, custom.countMembersExcluding(MediaType.LIVE, FIRST, listOf(locked)).first())
        assertEquals(
            custom.countMembers(MediaType.LIVE, FIRST).first(),
            custom.countMembersExcluding(MediaType.LIVE, FIRST, emptyList()).first(),
        )
        val contexts = listOf(FIRST, SECOND)
        assertEquals(
            mapOf(FIRST to 2),
            custom.observeCountsByContextsExcluding(MediaType.LIVE, contexts, listOf(locked))
                .first().associate { it.contextKey to it.count },
        )
        assertEquals(
            custom.observeCountsByContexts(MediaType.LIVE, contexts).first().toSet(),
            custom.observeCountsByContextsExcluding(MediaType.LIVE, contexts, emptyList()).first().toSet(),
        )
    }

    private fun member(context: String, itemId: String, position: Int) = CustomCategoryMemberEntity(
        mediaType = MediaType.LIVE, contextKey = context, itemId = itemId, position = position,
    )

    private companion object {
        const val ORPHAN = "ch-orphan"
        const val FIRST = "custom:first"
        const val SECOND = "custom:second"
    }
}
