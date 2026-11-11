package tv.own.owntv.core.backup

import android.content.Context
import tv.own.owntv.core.database.ownTVTestDatabase
import androidx.datastore.preferences.core.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import tv.own.owntv.core.database.OwnTVDatabase
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.FavoriteEntity
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.database.entity.WatchHistoryEntity
import tv.own.owntv.core.i18n.LocaleStore
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.settings.SettingsRepository

/**
 * The merge rule local sync stands on: a deletion is a fact with a time on it, and the newer of the
 * two facts wins.
 *
 * Every case here is one a user with two devices actually performs — unfavorite on the
 * television, sync, look at the phone — and every one of them fails silently if the rule is wrong.
 * The failure mode is not a crash: it is a favorite quietly coming back from the dead on every sync,
 * for ever, which is exactly the kind of thing nobody reports as a bug and everybody stops trusting.
 *
 * Instrumentation, not JVM: the rule is expressed in DAO queries and a real transaction. The app is
 * live-only, so channels are the content rows these records point at.
 */
@RunWith(AndroidJUnit4::class)
class UserDataTombstoneTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var db: OwnTVDatabase
    private lateinit var resolver: UserDataResolver
    private lateinit var writer: UserDataWriter

    private var sourceId = 0L

    // The `: Unit` is load-bearing: without it Kotlin infers the last expression's type, JUnit
    // rejects the class with "Method setUp() should be void", and the whole suite never runs.
    @Before
    fun setUp(): Unit = runBlocking {
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
        // A real SettingsRepository on the test context: its sync matrix reads the DataStore defaults
        // (favorites/order/reminders give=true, history give=false), which is what these cases need.
        val settings = SettingsRepository(
            context,
            LocaleStore(context.getSharedPreferences("ut_locale", Context.MODE_PRIVATE), context),
        )
        writer = UserDataWriter(
            db = db,
            favoriteDao = db.favoriteDao(),
            historyDao = db.historyDao(),
            customCategoryDao = db.customCategoryDao(),
            userData = resolver,
            settings = settings,
        )
        sourceId = db.sourceDao().insert(
            SourceEntity(name = "RuTube", type = SourceType.RUTUBE, url = "https://rutube.test"),
        )
        context.pendingStore.edit { it.remove(PENDING_KEY) }
    }

    @After
    fun tearDown() = runBlocking {
        context.pendingStore.edit { it.remove(PENDING_KEY) }
        db.close()
    }

    /** Unfavoriting records the deletion, so the other device can be told about it. */
    @Test
    fun removingAFavoriteRecordsADeletionThatTravels() = runBlocking {
        val channelId = insertChannel("m-1", "BBC One")
        db.favoriteDao().add(FavoriteEntity(mediaType = MediaType.LIVE, itemId = channelId, addedAt = 100))

        writer.removeFavorite(MediaType.LIVE, channelId)

        assertEquals(0, db.favoriteDao().getAllOnce().size)
        val exported = resolver.exportTombstones(setOf("fav"))
        assertEquals(1, exported.length())
        assertEquals("fav", exported.getJSONObject(0).getString("kind"))
    }

    /**
     * The whole point. The other device still has the favorite, its copy is older than the deletion,
     * and a merge must not put it back.
     */
    @Test
    fun anIncomingFavoriteOlderThanTheDeletionIsNotReinstated() = runBlocking {
        val channelId = insertChannel("m-1", "BBC One")
        db.favoriteDao().add(FavoriteEntity(mediaType = MediaType.LIVE, itemId = channelId, addedAt = 100))
        writer.removeFavorite(MediaType.LIVE, channelId)

        // What the other device sends: the same favorite, as it was before the deletion.
        resolver.importAll(incomingFavorites(channelId, at = 100))

        assertEquals("a deleted favorite came back", 0, db.favoriteDao().getAllOnce().size)
    }

    /**
     * A *restore* is not a merge, and this is the case that proves it.
     *
     * Same setup as the merge test above — the row was deleted here, and the incoming copy predates
     * the deletion — but the user has explicitly asked for their own snapshot back. Clearing this
     * device's markers first is what `BackupManager.ImportMode.RESTORE` does, and without it a
     * restore onto a device the user has just cleared reinstates nothing while reporting success.
     */
    @Test
    fun aRestoreReinstatesWhatADeletionWouldHaveRefused() = runBlocking {
        val channelId = insertChannel("m-1", "BBC One")
        db.favoriteDao().add(FavoriteEntity(mediaType = MediaType.LIVE, itemId = channelId, addedAt = 100))
        writer.removeFavorite(MediaType.LIVE, channelId)

        resolver.clearDeletions()
        val refused = resolver.importAll(incomingFavorites(channelId, at = 100))

        assertEquals("a restore refused a record", 0, refused)
        assertEquals("the restore did not bring the favorite back", 1, db.favoriteDao().getAllOnce().size)
    }

    /**
     * ...and the marker is gone for good, not merely ignored once. Left behind, the next local sync
     * would delete the restored row a second time — so the restore has to outlive the sync after it.
     */
    @Test
    fun aRestoreLeavesNoMarkerForTheNextSyncToActOn() = runBlocking {
        val channelId = insertChannel("m-1", "BBC One")
        db.favoriteDao().add(FavoriteEntity(mediaType = MediaType.LIVE, itemId = channelId, addedAt = 100))
        writer.removeFavorite(MediaType.LIVE, channelId)
        resolver.clearDeletions()
        resolver.importAll(incomingFavorites(channelId, at = 100))

        assertEquals(0, resolver.exportTombstones(setOf("fav")).length())
        // The same record arriving again from the other device must now be a no-op, not a deletion.
        resolver.importAll(incomingFavorites(channelId, at = 100))
        assertEquals(1, db.favoriteDao().getAllOnce().size)
    }

    /** A merge is untouched by the above: it still refuses, and now says so instead of staying mute. */
    @Test
    fun aMergeStillRefusesAndReportsTheCount() = runBlocking {
        val channelId = insertChannel("m-1", "BBC One")
        db.favoriteDao().add(FavoriteEntity(mediaType = MediaType.LIVE, itemId = channelId, addedAt = 100))
        writer.removeFavorite(MediaType.LIVE, channelId)

        val refused = resolver.importAll(incomingFavorites(channelId, at = 100))

        assertEquals("the refusal was not reported", 1, refused)
        assertEquals(0, db.favoriteDao().getAllOnce().size)
    }

    /** The opposite direction: favoriting it again afterwards is newer, and must survive a sync. */
    @Test
    fun anIncomingFavoriteNewerThanTheDeletionIsApplied() = runBlocking {
        val channelId = insertChannel("m-1", "BBC One")
        db.favoriteDao().add(FavoriteEntity(mediaType = MediaType.LIVE, itemId = channelId, addedAt = 100))
        writer.removeFavorite(MediaType.LIVE, channelId)

        resolver.importAll(incomingFavorites(channelId, at = System.currentTimeMillis() + 60_000))

        assertEquals(1, db.favoriteDao().getAllOnce().size)
    }

    /** A deletion arriving from the other device removes the local row when the local row is older. */
    @Test
    fun anIncomingDeletionRemovesTheLocalRow() = runBlocking {
        val channelId = insertChannel("m-1", "BBC One")
        db.favoriteDao().add(FavoriteEntity(mediaType = MediaType.LIVE, itemId = channelId, addedAt = 100))

        val applied = resolver.applyTombstones(incomingDeletion(channelId, at = 200))

        assertEquals(1, applied)
        assertEquals(0, db.favoriteDao().getAllOnce().size)
    }

    /** ...and leaves it alone when the user re-added it here after the other device deleted it. */
    @Test
    fun anIncomingDeletionOlderThanTheLocalRowLeavesItAlone() = runBlocking {
        val channelId = insertChannel("m-1", "BBC One")
        db.favoriteDao().add(FavoriteEntity(mediaType = MediaType.LIVE, itemId = channelId, addedAt = 500))

        resolver.applyTombstones(incomingDeletion(channelId, at = 200))

        assertEquals("a favorite re-added after the deletion was lost", 1, db.favoriteDao().getAllOnce().size)
    }

    /**
     * A deletion survives the content ids changing underneath it, because it is keyed on the same
     * stable identity a favorite is — otherwise the next playlist refresh would forget every deletion
     * and the next sync would undo them all at once.
     */
    @Test
    fun aDeletionStillMatchesAfterAResyncChangesTheRowId() = runBlocking {
        val oldId = insertChannel("m-1", "BBC One")
        db.favoriteDao().add(FavoriteEntity(mediaType = MediaType.LIVE, itemId = oldId, addedAt = 100))
        writer.removeFavorite(MediaType.LIVE, oldId)

        db.channelDao().clearSource(sourceId)
        val newId = insertChannel("m-1", "BBC One")
        assertTrue(newId != oldId)

        resolver.importAll(incomingFavorites(newId, at = 100))

        assertEquals(0, db.favoriteDao().getAllOnce().size)
    }

    /** The dry run has to promise the same answer the apply will give. */
    @Test
    fun thePreviewAgreesWithWhatTheMergeWillDo() = runBlocking {
        val channelId = insertChannel("m-1", "BBC One")
        val incoming = incomingFavorites(channelId, at = 100).getJSONObject(0)

        assertTrue("a favorite this device does not have is new", resolver.wouldAdd("fav", incoming))

        db.favoriteDao().add(FavoriteEntity(mediaType = MediaType.LIVE, itemId = channelId, addedAt = 100))
        assertFalse("one it already has is not", resolver.wouldAdd("fav", incomingFavorites(channelId, at = 100).getJSONObject(0)))

        val deletion = incomingDeletion(channelId, at = 200).getJSONObject(0)
        assertTrue("a deletion of a row that is here counts", resolver.wouldRemove(deletion))
    }

    // --- newest wins, for records as well as deletions --------------------------------------------

    /** Watch history obeys the same clock: the time only ever moves forward. */
    @Test
    fun watchHistoryNeverMovesBackwards() = runBlocking {
        val channelId = insertChannel("m-1", "BBC One")
        db.historyDao().record(WatchHistoryEntity(mediaType = MediaType.LIVE, itemId = channelId, watchedAt = 500))

        resolver.importAll(incomingHistory(channelId, at = 100))
        assertEquals("history was aged by a sync", 500L, db.historyDao().getAllOnce().single().watchedAt)

        resolver.importAll(incomingHistory(channelId, at = 900))
        assertEquals(900L, db.historyDao().getAllOnce().single().watchedAt)
    }

    private suspend fun incomingHistory(itemId: Long, at: Long): JSONArray {
        val record = resolver.identityOf(MediaType.LIVE, itemId)!!
        return JSONArray().put(record.put("kind", "his").put("at", at))
    }

    private suspend fun incomingFavorites(itemId: Long, at: Long): JSONArray {
        val record = resolver.identityOf(MediaType.LIVE, itemId)!!
        return JSONArray().put(record.put("kind", "fav").put("at", at))
    }

    private suspend fun incomingDeletion(itemId: Long, at: Long): JSONArray = incomingFavorites(itemId, at)

    private suspend fun insertChannel(remoteId: String?, name: String): Long {
        db.channelDao().insertAll(
            listOf(ChannelEntity(sourceId = sourceId, name = name, streamUrl = "https://portal.test/$name", remoteId = remoteId)),
        )
        return db.channelDao().findByName(sourceId, name)!!.id
    }
}
