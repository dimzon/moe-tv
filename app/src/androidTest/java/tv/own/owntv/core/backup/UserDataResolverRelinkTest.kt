package tv.own.owntv.core.backup

import tv.own.owntv.core.database.ownTVTestDatabase
import androidx.datastore.preferences.core.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import tv.own.owntv.core.database.OwnTVDatabase
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.FavoriteEntity
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.model.SourceType

/**
 * The user data follows its channel across a re-sync **by construction** now: the key is the
 * provider's hex remoteId — the channel's primary key — not a local row id that a refresh invalidates.
 * There is no capture/relink pass left to fail silently; what remains to pin is the purge contract:
 * a record is dropped only when the provider genuinely stopped publishing its channel.
 *
 * Instrumentation rather than a JVM test on purpose: the mechanism IS the DAO queries and a real
 * transaction, so faking the DAOs would only test the fakes.
 */
@RunWith(AndroidJUnit4::class)
class UserDataResolverRelinkTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var db: OwnTVDatabase
    private lateinit var resolver: UserDataResolver

    private var sourceId = 0L

    @Before
    fun setUp() = runBlocking {
        db = ownTVTestDatabase()
        resolver = UserDataResolver(
            context = context,
            channelDao = db.channelDao(),
            favoriteDao = db.favoriteDao(),
            historyDao = db.historyDao(),
            contentOrderDao = db.contentOrderDao(),
            customCategoryDao = db.customCategoryDao(),
            tombstoneDao = db.tombstoneDao(),
            db = db,
        )
        sourceId = db.sourceDao().insert(
            SourceEntity(name = "RuTube", type = SourceType.RUTUBE, url = "https://rutube.test"),
        )
        clearPending()
    }

    @After
    fun tearDown() = runBlocking {
        clearPending()
        db.close()
    }

    /** Empty the on-disk pending set — the only state in this test that is not the in-memory database. */
    private suspend fun clearPending() {
        context.pendingStore.edit { it.remove(PENDING_KEY) }
    }

    /**
     * The core promise: a re-sync rewrites the channel row and the favorite stays attached, because
     * both are keyed on the same hex.
     */
    @Test
    fun favoriteSurvivesAResyncBecauseTheKeyIsTheProvidersHexId() = runBlocking {
        val key = insertChannel(remoteId = "m-1", name = "BBC One")
        db.favoriteDao().add(FavoriteEntity(mediaType = MediaType.LIVE, itemId = key, addedAt = 500))

        // The sync: REPLACE by PK — same channel, same key, new row.
        db.channelDao().upsertAll(listOf(ChannelEntity(remoteId = key, name = "BBC One HD")))

        val favorites = db.favoriteDao().getAllOnce()
        assertEquals(1, favorites.size)
        assertEquals(key, favorites.single().itemId)
        assertEquals("the original addedAt must be preserved", 500L, favorites.single().addedAt)
    }

    /** The post-sync pass leaves a favorite alone while its channel exists. */
    @Test
    fun afterSyncKeepsTheRowWhileTheChannelExists() = runBlocking {
        val key = insertChannel(remoteId = "m-2", name = "Still Here")
        db.favoriteDao().add(FavoriteEntity(mediaType = MediaType.LIVE, itemId = key, addedAt = 1))

        resolver.afterSync(purge = true)

        assertEquals(1, db.favoriteDao().getAllOnce().size)
    }

    /**
     * purge=false is the safety valve for a partially failed sync: content rows can be legitimately
     * missing mid-import, and purging then would permanently delete favorites for content that is
     * simply not re-synced yet. The stale row must survive so the next successful sync can heal it.
     */
    @Test
    fun purgeFalseKeepsAnOrphanedRowSoAFailedSyncCannotDeleteFavorites() = runBlocking {
        val key = insertChannel(remoteId = "m-3", name = "Interrupted")
        db.favoriteDao().add(FavoriteEntity(mediaType = MediaType.LIVE, itemId = key, addedAt = 1))
        db.channelDao().deleteByRemoteIds(listOf(key))

        resolver.afterSync(purge = false)

        assertTrue("purge=false must not delete the orphaned favorite", db.favoriteDao().getAllOnce().isNotEmpty())
    }

    /** …and purge=true is what makes the counts agree once the sync really did succeed. */
    @Test
    fun purgeTrueDropsTheOrphanedRowAfterASuccessfulSync() = runBlocking {
        val key = insertChannel(remoteId = "m-4", name = "Gone For Good")
        db.favoriteDao().add(FavoriteEntity(mediaType = MediaType.LIVE, itemId = key, addedAt = 1))
        db.channelDao().deleteByRemoteIds(listOf(key))

        resolver.afterSync(purge = true)

        assertNull(db.favoriteDao().getAllOnce().firstOrNull { it.itemId == key })
    }

    /**
     * The purge query on its own: it must delete a user-data row only once its content row is gone.
     */
    @Test
    fun purgeSnapshotOrphanDeletesOnlyOnceTheContentRowHasGone() = runBlocking {
        val key = insertChannel(remoteId = "m-9", name = "Purge Me")
        db.favoriteDao().add(FavoriteEntity(mediaType = MediaType.LIVE, itemId = key, addedAt = 1))

        db.favoriteDao().purgeSnapshotOrphan(MediaType.LIVE, key)
        assertEquals("the channel is still there — nothing to purge", 1, db.favoriteDao().getAllOnce().size)

        db.channelDao().deleteByRemoteIds(listOf(key))
        db.favoriteDao().purgeSnapshotOrphan(MediaType.LIVE, key)
        assertEquals("the channel is gone — the favorite must go with it", 0, db.favoriteDao().getAllOnce().size)
    }

    // --- helpers ---

    /** The channel key IS the identity user data is keyed on now — no local row ids left. */
    private suspend fun insertChannel(remoteId: String?, name: String): String {
        val key = remoteId ?: "name-$name"
        db.channelDao().insertAll(listOf(ChannelEntity(remoteId = key, name = name)))
        return key
    }
}
