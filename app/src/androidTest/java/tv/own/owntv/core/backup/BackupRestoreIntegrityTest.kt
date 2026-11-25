package tv.own.owntv.core.backup

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import tv.own.owntv.core.database.OwnTVDatabase
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.ownTVTestDatabase
import tv.own.owntv.core.i18n.LocaleStore
import tv.own.owntv.core.settings.SettingsRepository
import java.io.File

@RunWith(AndroidJUnit4::class)
class BackupRestoreIntegrityTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: OwnTVDatabase
    private lateinit var resolver: UserDataResolver
    private lateinit var settings: SettingsRepository
    private lateinit var manager: BackupManager

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
        settings = SettingsRepository(
            context,
            LocaleStore(context.getSharedPreferences("ut_locale", Context.MODE_PRIVATE), context),
        )
        settings.clearRestoreMarker()
        manager = BackupManager(settings, resolver)
        context.pendingStore.edit { it.remove(PENDING_KEY) }
        db.channelDao().insertAll(listOf(ChannelEntity(remoteId = "channel", name = "Channel")))
    }

    @After
    fun tearDown() = runBlocking {
        context.pendingStore.edit { it.remove(PENDING_KEY) }
        settings.clearRestoreMarker()
        db.close()
    }

    @Test
    fun malformedSelectedPayloadDoesNotClearTombstonesOrStartRestore() = runBlocking {
        db.tombstoneDao().record("fav", """{"rid":"channel"}""", 500L)
        val file = backup(
            userData = JSONArray().put(JSONObject().put("kind", "fav").put("rid", "channel").put("at", "not-a-time")),
        )

        val result = manager.import(file, sections = setOf(BackupManager.Section.FAVORITES))

        assertTrue(result.isFailure)
        assertEquals(1, resolver.exportTombstones(setOf("fav")).length())
        assertNull(settings.restoreInProgress.first())
    }

    @Test
    fun restoringFavoritesClearsOnlyFavoriteTombstones() = runBlocking {
        db.tombstoneDao().record("fav", """{"rid":"channel"}""", 500L)
        db.tombstoneDao().record("order", """{"rid":"channel","ctx":"custom:1"}""", 500L)
        db.tombstoneDao().record("prog", """{"rid":"channel","startMs":1000}""", 500L)
        val file = backup(
            userData = JSONArray().put(JSONObject().put("kind", "fav").put("rid", "channel").put("at", 100L)),
        )

        val result = manager.import(file, sections = setOf(BackupManager.Section.FAVORITES))

        assertTrue(result.isSuccess)
        assertEquals(0, resolver.exportTombstones(setOf("fav")).length())
        assertEquals(1, resolver.exportTombstones(setOf("order")).length())
        assertEquals(1, resolver.exportTombstones(setOf("prog")).length())
        assertEquals("channel", db.favoriteDao().getAllOnce().single().itemId)
    }

    @Test
    fun malformedUnselectedSectionIsIgnored() = runBlocking {
        val file = backup(
            userData = JSONArray()
                .put(JSONObject().put("kind", "fav").put("rid", "channel").put("at", 100L))
                .put(JSONObject().put("kind", "order").put("rid", "channel").put("ctx", "custom:1").put("pos", "bad")),
        )

        val result = manager.import(file, sections = setOf(BackupManager.Section.FAVORITES))

        assertTrue(result.isSuccess)
        assertEquals(1, db.favoriteDao().getAllOnce().size)
    }

    private fun backup(userData: JSONArray): File = folder.newFile().apply {
        writeText(
            JSONObject().put("version", 26)
                .put("sections", JSONArray().put("FAVORITES"))
                .put("userData", userData)
                .toString(),
        )
    }
}
