package tv.own.owntv.core.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.epg.GuideCandidates

/**
 * The candidate queries, against a real database.
 *
 * The catalogue supplies names and the guide supplies presence; neither source alone is enough, and
 * only a database can show both halves coming back together.
 */
@RunWith(AndroidJUnit4::class)
class GuideCandidateDaoTest {

    private lateinit var db: OwnTVDatabase
    private lateinit var candidates: GuideCandidates

    @Before
    fun setUp() {
        db = ownTVTestDatabase()
        candidates = GuideCandidates(db.channelDao(), db.epgDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun aCatalogueChannelWithProgrammesIsOneNamedCandidate() = runBlocking {
        db.channelDao().upsertAll(listOf(ChannelEntity(remoteId = "bbc1", name = "BBC One")))
        db.epgDao().upsertProgrammes(listOf(programme("bbc1")))

        val all = candidates.all()

        assertEquals(1, all.size)
        assertEquals("bbc1", all.single().epgChannelId)
        assertEquals("BBC One", all.single().displayName)
        assertTrue(all.single().hasProgrammes)
    }

    @Test
    fun aFeedWithProgrammesButNoChannelEntryIsStillPickable() = runBlocking {
        // A guide row can outlive its catalogue channel (a deleted channel, a re-sync in flight).
        db.epgDao().upsertProgrammes(listOf(programme("itv1")))

        val all = candidates.all()

        assertEquals(listOf("itv1"), all.map { it.epgChannelId })
        assertNull(all.single().displayName)
        assertTrue(all.single().hasProgrammes)
    }

    @Test
    fun aNamedChannelWithNoProgrammesComesBackFlagged() = runBlocking {
        db.channelDao().upsertAll(listOf(ChannelEntity(remoteId = "sky1", name = "Sky One")))

        val all = candidates.all()

        assertEquals(listOf("sky1"), all.map { it.epgChannelId })
        assertEquals("Sky One", all.single().displayName)
        assertFalse(all.single().hasProgrammes)
    }

    @Test
    fun theSearchFindsAChannelByNameAndById() = runBlocking {
        db.channelDao().upsertAll(
            listOf(
                ChannelEntity(remoteId = "bbc.one.uk", name = "BBC One"),
                ChannelEntity(remoteId = "itv.one.uk", name = "ITV1"),
            ),
        )

        assertEquals(listOf("bbc.one.uk"), candidates.forPicker("BBC One", "bbc one").map { it.epgChannelId })
        assertEquals(listOf("itv.one.uk"), candidates.forPicker("ITV", "itv.one").map { it.epgChannelId })
        assertTrue(candidates.forPicker("BBC One", "nothing-matches-this").isEmpty())
    }

    private fun programme(epgChannelId: String, startMs: Long = 1_000) = EpgProgrammeEntity(
        epgChannelId = epgChannelId,
        startMs = startMs,
        stopMs = startMs + 1_000,
        title = "Programme",
    )
}
