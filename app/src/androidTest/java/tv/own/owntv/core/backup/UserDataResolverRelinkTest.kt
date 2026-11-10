package tv.own.owntv.core.backup

import tv.own.owntv.core.database.ownTVTestDatabase
import androidx.datastore.preferences.core.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import tv.own.owntv.core.database.OwnTVDatabase
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.FavoriteEntity
import tv.own.owntv.core.database.entity.ProfileEntity
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.model.SourceType

/**
 * [UserDataResolver] is the reason favorites and history survive a re-sync.
 * Content rows are clear-then-insert, so every id a user-data row points at is invalidated on every
 * refresh; the resolver exports each record against a stable identity beforehand and re-attaches it
 * afterwards. If it silently fails to re-attach, the user just finds their favorites gone — no
 * crash, no log, nothing to notice until it is too late to recover. None of it was covered.
 *
 * Instrumentation rather than a JVM test on purpose: the whole mechanism IS the DAO queries
 * (findByRemote / findByName) and a real transaction, so faking the DAOs would only test the fakes.
 * The app is live-only, so channels are the content rows the resolver relinks.
 */
@RunWith(AndroidJUnit4::class)
class UserDataResolverRelinkTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var db: OwnTVDatabase
    private lateinit var resolver: UserDataResolver

    private var profileId = 0L
    private var sourceId = 0L

    @Before
    fun setUp() = runBlocking {
        db = ownTVTestDatabase()
        resolver = UserDataResolver(
            context = context,
            channelDao = db.channelDao(),
            profileDao = db.profileDao(),
            favoriteDao = db.favoriteDao(),
            historyDao = db.historyDao(),
            contentOrderDao = db.contentOrderDao(),
            customCategoryDao = db.customCategoryDao(),
            tombstoneDao = db.tombstoneDao(),
            db = db,
        )
        profileId = db.profileDao().insert(ProfileEntity(name = "Primary", avatarColor = 0x112233))
        sourceId = db.sourceDao().insert(
            SourceEntity(name = "Portal", type = SourceType.XTREAM, url = "https://portal.test", username = "user"),
        )
        clearPending()
    }

    @After
    fun tearDown() = runBlocking {
        // Records left unresolved by one case must not survive into the next: the database is fresh
        // per test and hands out row ids from 1 again, so a leftover pending record matches content it
        // was never about and quietly adds a favorite row the assertions then count.
        clearPending()
        db.close()
    }

    /** Empty the on-disk pending set — the only state in this test that is not the in-memory database. */
    private suspend fun clearPending() {
        context.pendingStore.edit { it.remove(PENDING_KEY) }
    }

    /**
     * The core promise: a channel is deleted and re-inserted by a sync (new row id), and the favorite
     * that pointed at the old id ends up pointing at the new one.
     */
    @Test
    fun favoriteFollowsAChannelAcrossAResyncThatChangesItsRowId() = runBlocking {
        val oldId = insertChannel(remoteId = "m-1", name = "BBC One")
        db.favoriteDao().add(FavoriteEntity(profileId = profileId, mediaType = MediaType.LIVE, itemId = oldId, addedAt = 500))

        val snapshot = resolver.exportForSource(sourceId, setOf("fav"))
        assertEquals(1, snapshot.length())

        // The sync: clear-then-insert. Same channel, brand new id.
        db.channelDao().clearSource(sourceId)
        val newId = insertChannel(remoteId = "m-1", name = "BBC One")
        assert(newId != oldId)

        resolver.relinkAfterSync(snapshot)

        val favorites = db.favoriteDao().getAllOnce()
        assertEquals(1, favorites.size)
        assertEquals(newId, favorites.single().itemId)
        assertEquals("the original addedAt must be preserved", 500L, favorites.single().addedAt)
    }

    /**
     * Providers that expose no stable id at all (plain M3U) fall back to the name. Same guarantee,
     * weaker key — worth pinning, because the fallback is what most M3U users actually rely on.
     */
    @Test
    fun favoriteFollowsAChannelByNameWhenTheProviderHasNoRemoteId() = runBlocking {
        val oldId = insertChannel(remoteId = null, name = "Nameless Provider Channel")
        db.favoriteDao().add(FavoriteEntity(profileId = profileId, mediaType = MediaType.LIVE, itemId = oldId, addedAt = 1))

        val snapshot = resolver.exportForSource(sourceId, setOf("fav"))
        db.channelDao().clearSource(sourceId)
        val newId = insertChannel(remoteId = null, name = "Nameless Provider Channel")

        resolver.relinkAfterSync(snapshot)

        assertEquals(newId, db.favoriteDao().getAllOnce().single().itemId)
    }

    /**
     * Content that hasn't come back yet (a source still syncing) must not be thrown away. It stays
     * pending and heals the moment the content appears.
     */
    @Test
    fun anUnresolvableRecordStaysPendingAndHealsWhenTheContentArrives() = runBlocking {
        val oldId = insertChannel(remoteId = "m-late", name = "Arrives Late")
        db.favoriteDao().add(FavoriteEntity(profileId = profileId, mediaType = MediaType.LIVE, itemId = oldId, addedAt = 1))
        val snapshot = resolver.exportForSource(sourceId, setOf("fav"))

        // Sync wiped the catalogue and the channel is not back yet.
        db.channelDao().clearSource(sourceId)
        resolver.relinkAfterSync(snapshot, purge = true)
        assertEquals("nothing to attach to yet", 0, db.favoriteDao().getAllOnce().size)

        // Content arrives (later sync), and the retry re-attaches it.
        val newId = insertChannel(remoteId = "m-late", name = "Arrives Late")
        resolver.resolvePending()

        assertEquals(newId, db.favoriteDao().getAllOnce().single().itemId)
    }

    /**
     * purge=false is the safety valve for a partially failed sync: content rows can be legitimately
     * missing mid-import, and purging then would permanently delete favorites for content that is
     * simply not re-synced yet. The stale row must survive so the next successful sync can heal it.
     */
    @Test
    fun purgeFalseKeepsAnOrphanedRowSoAFailedSyncCannotDeleteFavorites() = runBlocking {
        val oldId = insertChannel(remoteId = "m-2", name = "Interrupted")
        db.favoriteDao().add(FavoriteEntity(profileId = profileId, mediaType = MediaType.LIVE, itemId = oldId, addedAt = 1))
        val snapshot = resolver.exportForSource(sourceId, setOf("fav"))

        db.channelDao().clearSource(sourceId)
        resolver.relinkAfterSync(snapshot, purge = false)

        val stale = db.favoriteDao().getAllOnce().firstOrNull { it.itemId == oldId }
        assertNotNull("purge=false must not delete the orphaned favorite", stale)
    }

    /** …and purge=true is what makes the counts agree once the sync really did succeed. */
    @Test
    fun purgeTrueDropsTheOrphanedRowAfterASuccessfulSync() = runBlocking {
        val oldId = insertChannel(remoteId = "m-3", name = "Gone For Good")
        db.favoriteDao().add(FavoriteEntity(profileId = profileId, mediaType = MediaType.LIVE, itemId = oldId, addedAt = 1))
        val snapshot = resolver.exportForSource(sourceId, setOf("fav"))

        db.channelDao().clearSource(sourceId)
        resolver.relinkAfterSync(snapshot, purge = true)

        assertNull(db.favoriteDao().getAllOnce().firstOrNull { it.itemId == oldId })
    }

    /**
     * A record whose profile no longer exists must not be re-attached to whatever profile happens to
     * hold that id now — deleting a profile and restoring a backup would otherwise dump a stranger's
     * favorites (and, for a Kids profile, their content) into someone else's account.
     */
    @Test
    fun aRecordForADeletedProfileIsNotAttachedToAnyoneElse() = runBlocking {
        val channelId = insertChannel(remoteId = "m-4", name = "Orphan Profile")
        // A real second profile, deleted between the export and the relink — which is the actual
        // sequence this guards against. Inventing an id that never existed cannot stand in for it:
        // favorites carry a foreign key to profiles, so such a row is rejected before the test starts.
        val secondProfile = ProfileEntity(name = "Second", avatarColor = 0x445566)
        val secondProfileId = db.profileDao().insert(secondProfile)
        db.favoriteDao().add(FavoriteEntity(profileId = secondProfileId, mediaType = MediaType.LIVE, itemId = channelId, addedAt = 1))
        val snapshot = resolver.exportForSource(sourceId, setOf("fav"))
        assertEquals(1, snapshot.length())
        // Deleting the profile cascades its favorites away; the snapshot still names it.
        db.profileDao().delete(secondProfile.copy(id = secondProfileId))
        assertEquals(0, db.favoriteDao().getAllOnce().size)

        resolver.relinkAfterSync(snapshot, purge = false)

        assertNull(
            "a record for a missing profile must not land on the surviving profile",
            db.favoriteDao().getAllOnce().firstOrNull { it.itemId == channelId },
        )
    }

    /**
     * The purge query on its own: it must delete a user-data row only once its content row is gone.
     * Every "the old row is still there" failure in this class routes through it, so it is worth
     * pinning apart from the resolver that calls it.
     */
    @Test
    fun purgeSnapshotOrphanDeletesOnlyOnceTheContentRowHasGone() = runBlocking {
        val channelId = insertChannel(remoteId = "m-9", name = "Purge Me")
        db.favoriteDao().add(FavoriteEntity(profileId = profileId, mediaType = MediaType.LIVE, itemId = channelId, addedAt = 1))

        db.favoriteDao().purgeSnapshotOrphan(profileId, MediaType.LIVE, channelId)
        assertEquals("the channel is still there — nothing to purge", 1, db.favoriteDao().getAllOnce().size)

        db.channelDao().clearSource(sourceId)
        db.favoriteDao().purgeSnapshotOrphan(profileId, MediaType.LIVE, channelId)
        assertEquals("the channel is gone — the favorite must go with it", 0, db.favoriteDao().getAllOnce().size)
    }

    // --- helpers ---

    private suspend fun insertChannel(remoteId: String?, name: String): Long {
        db.channelDao().insertAll(
            listOf(ChannelEntity(sourceId = sourceId, name = name, streamUrl = "https://portal.test/$name", remoteId = remoteId)),
        )
        return db.channelDao().findByName(sourceId, name)!!.id
    }
}
