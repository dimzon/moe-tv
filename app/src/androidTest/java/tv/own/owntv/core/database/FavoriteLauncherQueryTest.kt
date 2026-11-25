package tv.own.owntv.core.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.ContentOrderEntity
import tv.own.owntv.core.database.entity.FavoriteEntity

/** The launcher Favorites row reads the same ordered join as Live → Favorites. */
@RunWith(AndroidJUnit4::class)
class FavoriteLauncherQueryTest {
    private lateinit var db: OwnTVDatabase

    @Before
    fun setUp() {
        db = ownTVTestDatabase()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun manualOrderComesFirstThenNewestFavorites() = runBlocking {
        db.channelDao().insertAll(
            listOf(
                ChannelEntity(remoteId = "a", name = "A"),
                ChannelEntity(remoteId = "b", name = "B"),
                ChannelEntity(remoteId = "c", name = "C"),
            ),
        )
        db.favoriteDao().add(FavoriteEntity(itemId = "a", addedAt = 100))
        db.favoriteDao().add(FavoriteEntity(itemId = "b", addedAt = 200))
        db.favoriteDao().add(FavoriteEntity(itemId = "c", addedAt = 300))
        db.contentOrderDao().insertAll(
            listOf(
                ContentOrderEntity(contextKey = ContentOrderEntity.FAV_CONTEXT, itemId = "b", position = 0),
                ContentOrderEntity(contextKey = ContentOrderEntity.FAV_CONTEXT, itemId = "a", position = 1),
            ),
        )

        val rows = db.channelDao().observeFavoritesManual(ContentOrderEntity.FAV_CONTEXT).first()
        assertEquals(listOf("b", "a", "c"), rows.map { it.remoteId })
    }

    @Test
    fun addRemoveRenameAndOrphanAreReflectedInTheSnapshot() = runBlocking {
        val dao = db.channelDao()
        assertEquals(emptyList<ChannelEntity>(), dao.observeFavoritesManual(ContentOrderEntity.FAV_CONTEXT).first())

        db.favoriteDao().add(FavoriteEntity(itemId = "later", addedAt = 100))
        db.favoriteDao().add(FavoriteEntity(itemId = "orphan", addedAt = 200))
        assertEquals(emptyList<ChannelEntity>(), dao.observeFavoritesManual(ContentOrderEntity.FAV_CONTEXT).first())

        db.channelDao().insertAll(listOf(ChannelEntity(remoteId = "later", name = "Old name")))
        assertEquals(listOf("later"), dao.observeFavoritesManual(ContentOrderEntity.FAV_CONTEXT).first().map { it.remoteId })
        db.channelDao().upsertAll(listOf(ChannelEntity(remoteId = "later", name = "New name")))
        assertEquals("New name", dao.observeFavoritesManual(ContentOrderEntity.FAV_CONTEXT).first().single().name)

        db.favoriteDao().remove("later")
        assertEquals(emptyList<ChannelEntity>(), dao.observeFavoritesManual(ContentOrderEntity.FAV_CONTEXT).first())
    }
}
