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
import tv.own.owntv.core.database.entity.WatchHistoryEntity

/**
 * The lock-aware counts against a real database: an item in an excluded (locked) category drops out,
 * an item with no category stays, a favorite or member whose item no longer exists counts in neither
 * form, and an empty exclusion list counts exactly what the plain query does.
 */
@RunWith(AndroidJUnit4::class)
class ExcludingCountDaoTest {

    private lateinit var db: OwnTVDatabase
    private var locked = 0L
    private var open = 0L

    @Before
    fun setUp() = runBlocking {
        db = ownTVTestDatabase()
        val (a, b) = db.categoryDao().insertAll(
            listOf(
                CategoryEntity(name = "Locked"),
                CategoryEntity(name = "Open"),
            ),
        )
        locked = a
        open = b
        // Channels first: the junction has FKs to both the catalogue and the category.
        db.channelDao().insertAll((1..4).map { ChannelEntity(remoteId = "ch$it", name = "Ch $it") })
        db.channelCategoryDao().insertAll(
            listOf(
                ChannelCategoryEntity(channelHexId = "ch1", categoryId = locked),
                ChannelCategoryEntity(channelHexId = "ch2", categoryId = locked),
                ChannelCategoryEntity(channelHexId = "ch3", categoryId = open),
            ),
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun channelCountsDropOnlyTheExcludedCategory() = runBlocking {
        val channels = db.channelDao()
        assertEquals(2, channels.countAllExcluding(listOf(locked)).first())
        assertEquals(channels.countAll().first(), channels.countAllExcluding(emptyList()).first())
    }

    @Test
    fun favoritesAndHistoryKeepUncategorisedItemsAndSkipOrphans() = runBlocking {
        listOf("ch1", "ch3", "ch4", ORPHAN).forEach {
            db.favoriteDao().add(FavoriteEntity(itemId = it))
            db.historyDao().record(WatchHistoryEntity(itemId = it))
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
        assertEquals(2, custom.countMembersExcluding(FIRST, listOf(locked)).first())
        assertEquals(
            custom.countMembers(FIRST).first(),
            custom.countMembersExcluding(FIRST, emptyList()).first(),
        )
        val contexts = listOf(FIRST, SECOND)
        assertEquals(
            mapOf(FIRST to 2),
            custom.observeCountsByContextsExcluding(contexts, listOf(locked))
                .first().associate { it.contextKey to it.count },
        )
        assertEquals(
            custom.observeCountsByContexts(contexts).first().toSet(),
            custom.observeCountsByContextsExcluding(contexts, emptyList()).first().toSet(),
        )
    }

    private fun member(context: String, itemId: String, position: Int) = CustomCategoryMemberEntity(
        contextKey = context, itemId = itemId, position = position,
    )

    private companion object {
        const val ORPHAN = "ch-orphan"
        const val FIRST = "custom:first"
        const val SECOND = "custom:second"
    }
}
