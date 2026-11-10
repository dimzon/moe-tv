package tv.own.owntv.core.epg

import tv.own.owntv.core.database.ownTVTestDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import tv.own.owntv.core.database.OwnTVDatabase
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType

/**
 * Which finished programmes survive a sync.
 *
 * The retention split deletes rows, and rows it deletes wrongly cannot be got back — a provider's
 * archive is not re-downloadable. Since v57 no channel can replay its history (the app is live-only),
 * so the rule is one window for every guide id: keep the recent past a just-finished programme needs,
 * drop the rest. Both DAO queries that implement it are pinned here against a real database.
 */
@RunWith(AndroidJUnit4::class)
class EpgRetentionTest {

    private lateinit var db: OwnTVDatabase

    private val now = System.currentTimeMillis()
    private val hour = 60L * 60 * 1000

    @Before
    fun setUp() {
        db = ownTVTestDatabase()
        // Programmes must belong to a real playlist row (epg_programmes.sourceId is a foreign key).
        runBlocking { db.sourceDao().insert(SourceEntity(id = 1, name = "Playlist", type = SourceType.M3U, url = "http://example.invalid")) }
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun pruningDropsEveryChannelsOldPastAndKeepsTheRecentOne() = runBlocking {
        db.epgDao().upsertProgrammes(
            listOf(
                // Finished a day ago: nobody can open it, so it goes — for every guide id alike.
                programme("bbc1", startMs = now - 26 * hour, stopMs = now - 25 * hour),
                programme("itv1", startMs = now - 26 * hour, stopMs = now - 25 * hour),
                // Inside the recent window: both keep this one.
                programme("bbc1", startMs = now - 2 * hour, stopMs = now - hour),
                programme("itv1", startMs = now - 2 * hour, stopMs = now - hour),
            ),
        )

        db.epgDao().prune(before = now - 6 * hour)

        assertEquals(2, countAll())
    }

    @Test
    fun aProgrammeStillOnNowIsNeverPruned() = runBlocking {
        db.epgDao().upsertProgrammes(
            listOf(
                programme("bbc1", startMs = now - hour, stopMs = now + hour),
            ),
        )

        db.epgDao().prune(before = now)

        assertEquals(1, countAll())
    }

    /** Lowering "Guide days to keep" must free the rows beyond the horizon, not merely stop updating them. */
    @Test
    fun pruningTheFutureDropsWhatIsBeyondTheStoredHorizon() = runBlocking {
        db.epgDao().upsertProgrammes(
            listOf(
                programme("bbc1", startMs = now + hour, stopMs = now + 2 * hour),
                programme("bbc1", startMs = now + 30 * 24 * hour, stopMs = now + 30 * 24 * hour + hour),
            ),
        )

        db.epgDao().pruneFuture(after = now + 7 * 24 * hour)

        assertEquals(1, countAll())
    }

    private suspend fun countAll(): Int = db.epgDao().countForSources(listOf(1L))

    private fun programme(epgChannelId: String, startMs: Long, stopMs: Long) = EpgProgrammeEntity(
        sourceId = 1,
        epgChannelId = epgChannelId,
        startMs = startMs,
        stopMs = stopMs,
        title = "Programme",
    )
}
