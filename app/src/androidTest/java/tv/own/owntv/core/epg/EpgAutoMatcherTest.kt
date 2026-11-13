package tv.own.owntv.core.epg

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.database.OwnTVDatabase
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.database.ownTVTestDatabase

/**
 * Auto-match, held to the two promises it used to break: it does not skip a channel whose guide is
 * empty, and it does not claim to have fixed one when the guide it picked is empty too.
 */
@RunWith(AndroidJUnit4::class)
class EpgAutoMatcherTest {

    private lateinit var db: OwnTVDatabase
    private lateinit var matcher: EpgAutoMatcher

    @Before
    fun setUp() {
        db = ownTVTestDatabase()
        matcher = EpgAutoMatcher(db.channelDao(), GuideCandidates(db.channelDao(), db.epgDao()))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun aChannelWhoseOwnGuideIsEmptyIsStillOffered() = runBlocking {
        // The channel's own guide id has no programmes, but another channel with the same name does.
        // The channel must not be treated as already matched, and the candidate with programmes wins.
        db.channelDao().upsertAll(
            listOf(
                ChannelEntity(remoteId = "1", name = "BBC One"),
                ChannelEntity(remoteId = "bbc1.real", name = "BBC One"),
            ),
        )
        db.epgDao().upsertProgrammes(listOf(programme("bbc1.real")))

        val outcome = matcher.run(SectionCustomizations(), listOf(1L))

        assertTrue(outcome.hadCandidates)
        assertEquals(listOf("1:1" to "bbc1.real"), outcome.applied)
    }

    @Test
    fun aConfidentWinnerWithNoProgrammesIsNeverAppliedSilently() = runBlocking {
        // A 0.95 name hit onto a guide channel with nothing scheduled used to be counted as a
        // success, leaving the user with the same empty row and a message saying it was fixed.
        insertChannel(name = "BBC One", remoteId = "1")

        val outcome = matcher.run(SectionCustomizations(), listOf(1L))

        assertTrue(outcome.applied.isEmpty())
        assertEquals(1, outcome.review.size)
        assertEquals(1, outcome.withheldForNoProgrammes)
        assertEquals("1", outcome.review.single().epgChannelId)
        assertFalse(outcome.review.single().hasProgrammes)
        assertTrue("held back, so still a confident score", outcome.review.single().score >= EpgMatcher.AUTO_THRESHOLD)
    }

    @Test
    fun aChannelThatAlreadyHasAWorkingGuideIsLeftAlone() = runBlocking {
        insertChannel(name = "BBC One", remoteId = "1")
        db.epgDao().upsertProgrammes(listOf(programme("1")))

        val outcome = matcher.run(SectionCustomizations(), listOf(1L))

        assertTrue(outcome.applied.isEmpty())
        assertTrue(outcome.review.isEmpty())
    }

    @Test
    fun aMatchThatSurvivedAReAddedPlaylistIsNotMatchedAgain() = runBlocking {
        // The stored-match resolver answers for this channel, so auto-match must not treat it as
        // unmatched and overwrite the user's own choice.
        insertChannel(name = "BBC One", remoteId = "1")
        db.epgDao().upsertProgrammes(listOf(programme("1"), programme("bbc.chosen")))

        // The match was stored under a source id the playlist no longer has.
        val cust = SectionCustomizations(epgMatches = mapOf("77:1" to "bbc.chosen"))
        val outcome = matcher.run(cust, listOf(1L))

        assertTrue(outcome.applied.isEmpty())
        assertTrue(outcome.review.isEmpty())
    }

    @Test
    fun aHiddenChannelIsNeverMatched() = runBlocking {
        val channel = insertChannel(name = "BBC One", remoteId = "1")
        db.epgDao().upsertProgrammes(listOf(programme("1")))

        val cust = SectionCustomizations(hiddenItems = mapOf(CustomizeKeys.channel(channel) to "BBC One"))
        val outcome = matcher.run(cust, listOf(1L))

        assertTrue(outcome.applied.isEmpty())
        assertTrue(outcome.review.isEmpty())
    }

    @Test
    fun anEmptyCatalogueHasNoCandidates() = runBlocking {
        val outcome = matcher.run(SectionCustomizations(), listOf(1L))

        assertFalse("no candidates is not the same as nothing matched", outcome.hadCandidates)
    }

    private suspend fun insertChannel(name: String, remoteId: String): ChannelEntity {
        val channel = ChannelEntity(remoteId = remoteId, name = name)
        db.channelDao().insertAll(listOf(channel))
        return channel
    }

    private fun programme(epgChannelId: String) = EpgProgrammeEntity(
        epgChannelId = epgChannelId,
        startMs = 1_000,
        stopMs = 2_000,
        title = "Programme",
    )
}
