package tv.own.owntv.core.epg

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import tv.own.owntv.core.database.OwnTVDatabase
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.ownTVTestDatabase
import tv.own.owntv.core.i18n.LocaleStore
import tv.own.owntv.core.network.HttpClient
import tv.own.owntv.core.repository.EpgRepository
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.sync.RutubeApi
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class EpgRepositorySyncResultTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: OwnTVDatabase
    private lateinit var settings: SettingsRepository
    @Volatile private var allNotFound = false

    @Before
    fun setUp(): Unit = runBlocking {
        db = ownTVTestDatabase()
        db.channelDao().insertAll(listOf(ChannelEntity(remoteId = "channel", name = "Channel")))
        settings = SettingsRepository(
            context,
            LocaleStore(context.getSharedPreferences("ut_locale", Context.MODE_PRIVATE), context),
        )
        settings.markEpgSynced(123L)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun failedChannelPassDoesNotAdvanceTheLastSuccessfulTimestamp() = runBlocking {
        val repository = repository()

        try {
            repository.refresh()
            fail("an incomplete EPG pass must not report success")
        } catch (error: IOException) {
            assertTrue(error.message!!.contains("failedChannels=1"))
            assertEquals(123L, settings.sourceLastEpgSyncAt.first())
        }
    }

    @Test
    fun all404PassStillAdvancesTheTimestamp() = runBlocking {
        allNotFound = true
        val repository = repository()

        assertEquals(0, repository.refresh())
        assertTrue(settings.sourceLastEpgSyncAt.first()!! > 123L)
    }

    private fun repository(): EpgRepository {
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val code = if (allNotFound) 404 else 500
            val body = "failure"
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("test")
                .body(body.toResponseBody(null))
                .build()
        }.build()
        return EpgRepository(
            epgDao = db.epgDao(),
            channelDao = db.channelDao(),
            rutube = RutubeApi(HttpClient(http)),
            settings = settings,
        )
    }
}
