package tv.own.owntv.core.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import tv.own.owntv.core.database.entity.ChannelEntity

/**
 * The curated-rename contract: the hex-keyed dictionary renames a channel with no FK attached, so
 * deleting the channel never breaks the pass, and a channel that returns with the same videoId is
 * renamed again. `replaceAll` mirrors the shipped dictionary wholesale.
 */
@RunWith(AndroidJUnit4::class)
class ChannelRenameDaoTest {

    private lateinit var db: OwnTVDatabase

    @Before
    fun setUp() = runBlocking {
        db = ownTVTestDatabase()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun insertChannel(remoteId: String, name: String) {
        db.channelDao().insertAll(
            listOf(
                ChannelEntity(remoteId = remoteId, name = name),
            ),
        )
    }

    @Test
    fun applyRewritesTheStoredName() = runBlocking {
        db.channelRenameDao().replaceAll(listOf(ChannelRenameEntity("hex-1", "Красивое имя")))
        insertChannel("hex-1", "provider name")
        db.channelRenameDao().applyToChannels()
        assertEquals("Красивое имя", db.channelDao().getById("hex-1")?.name)
    }

    @Test
    fun aDeletedChannelLeavesAnInertRenameThatWaits() = runBlocking {
        db.channelRenameDao().replaceAll(listOf(ChannelRenameEntity("hex-1", "Красивое имя")))
        insertChannel("hex-1", "provider name")
        db.channelDao().deleteByRemoteIds(listOf("hex-1"))
        // No FK: the orphaned rename must not throw, and applying over an empty catalogue is a no-op.
        db.channelRenameDao().applyToChannels()
        assertTrue(db.channelDao().snapshotAll(10).isEmpty())
        // The channel returns with the same videoId (a re-sync) and is renamed again.
        insertChannel("hex-1", "provider name")
        db.channelRenameDao().applyToChannels()
        assertEquals("Красивое имя", db.channelDao().getById("hex-1")?.name)
    }

    @Test
    fun replaceAllMirrorsTheDictionaryWholesale() = runBlocking {
        db.channelRenameDao().replaceAll(listOf(ChannelRenameEntity("hex-1", "A"), ChannelRenameEntity("hex-2", "B")))
        db.channelRenameDao().replaceAll(listOf(ChannelRenameEntity("hex-2", "B2")))
        assertEquals(listOf("B2"), db.channelRenameDao().all().map { it.name })
    }
}
