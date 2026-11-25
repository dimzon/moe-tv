package tv.own.owntv.core.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import tv.own.owntv.core.database.dao.EpgDao
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import java.io.IOException

class RuTubeEpgSyncIntegrityTest {

    @Test
    fun `all 404 channels are a successful empty pass`() = runTest {
        val dao = FakeEpgDao()
        val syncer = syncer(dao, fetchDay = { _, _ -> emptyList() })

        val summary = syncer.sync(listOf("one", "two"))

        assertEquals(0, summary.channelsWithGuide)
        assertEquals(0, summary.programmesWritten)
        assertFalse(summary.hasFailures)
        assertEquals(listOf(listOf("one", "two")), dao.prunedChannels)
    }

    @Test
    fun `failed channel is counted and excluded from pruning`() = runTest {
        val dao = FakeEpgDao()
        val syncer = syncer(
            dao,
            fetchDay = { id, _ -> if (id == "bad") throw IOException("500") else emptyList() },
            fetchAllDays = { throw IOException("fallback 500") },
        )

        val summary = syncer.sync(listOf("bad", "good"))

        assertEquals(1, summary.failedChannels)
        assertEquals(0, summary.failedWrites)
        assertTrue(summary.hasFailures)
        assertEquals(listOf(listOf("good")), dao.prunedChannels)
    }

    @Test
    fun `a successful mobile fallback recovers the channel`() = runTest {
        val dao = FakeEpgDao()
        val syncer = syncer(
            dao,
            fetchDay = { _, _ -> throw IOException("day failed") },
            fetchAllDays = { id -> listOf(programme(id)) },
        )

        val summary = syncer.sync(listOf("recovered"))

        assertEquals(1, summary.channelsWithGuide)
        assertEquals(1, summary.programmesWritten)
        assertFalse(summary.hasFailures)
        assertEquals(listOf(listOf("recovered")), dao.prunedChannels)
    }

    @Test
    fun `write failure is counted and keeps that channel's old guide`() = runTest {
        val dao = FakeEpgDao(failWritesFor = setOf("bad"))
        val syncer = syncer(dao, fetchDay = { id, _ -> if (id == "bad") listOf(programme(id)) else emptyList() })

        val summary = syncer.sync(listOf("bad", "good"))

        assertEquals(1, summary.failedChannels)
        assertEquals(1, summary.failedWrites)
        assertEquals(0, summary.programmesWritten)
        assertTrue(summary.hasFailures)
        assertEquals(listOf(listOf("good")), dao.prunedChannels)
    }

    @Test
    fun `brief write failure prevents a successful sync result`() = runTest {
        val dao = FakeEpgDao(failAllWrites = true)
        val syncer = syncer(dao, fetchBrief = { mapOf("one" to listOf(programme("one"))) })

        val summary = syncer.sync(listOf("one"))

        assertEquals(0, summary.failedChannels)
        assertEquals(1, summary.failedWrites)
        assertFalse(summary.failedSeed)
        assertTrue(summary.hasFailures)
    }

    @Test
    fun `cancellation escapes and no retention prune runs`() = runTest {
        val dao = FakeEpgDao()
        val syncer = syncer(dao, fetchDay = { _, _ -> throw CancellationException("cancelled") })

        try {
            syncer.sync(listOf("one"))
            fail("cancellation was swallowed")
        } catch (_: CancellationException) {
            assertTrue(dao.prunedChannels.isEmpty())
        }
    }

    @Test
    fun `parallel channel counters are aggregated exactly once`() = runTest {
        val dao = FakeEpgDao()
        val ids = (1..100).map { "channel-$it" }
        val syncer = syncer(dao, fetchDay = { id, _ -> listOf(programme(id)) })

        val summary = syncer.sync(ids)

        assertEquals(ids.size, summary.channelsWithGuide)
        assertEquals(ids.size, summary.programmesWritten)
        assertFalse(summary.hasFailures)
    }

    private fun syncer(
        dao: EpgDao,
        fetchBrief: suspend () -> Map<String, List<RutubeProgramme>> = { emptyMap() },
        fetchDay: suspend (String, String) -> List<RutubeProgramme> = { _, _ -> emptyList() },
        fetchAllDays: suspend (String) -> List<RutubeProgramme> = { emptyList() },
    ) = RuTubeEpgSyncer(dao, fetchBrief, fetchDay, fetchAllDays).apply { guideDays = 1 }

    private fun programme(id: String) = RutubeProgramme(
        title = "Programme $id",
        startUtcMillis = 1_000L,
        endUtcMillis = 2_000L,
    )

    private class FakeEpgDao(
        private val failWritesFor: Set<String> = emptySet(),
        private val failAllWrites: Boolean = false,
    ) : EpgDao {
        val prunedChannels = mutableListOf<List<String>>()
        private val count = MutableStateFlow(0)

        override suspend fun upsertProgrammes(programmes: List<EpgProgrammeEntity>) {
            if (failAllWrites || programmes.any { it.epgChannelId in failWritesFor }) throw IOException("disk full")
            count.value += programmes.size
        }
        override suspend fun prune(before: Long) = Unit
        override suspend fun pruneChannels(before: Long, channelIds: List<String>) { prunedChannels += channelIds }
        override suspend fun pruneFuture(after: Long) = Unit
        override suspend fun previousProgramme(epgChannelId: String, now: Long): EpgProgrammeEntity? = null
        override fun upcoming(epgChannelId: String, now: Long, limit: Int): Flow<List<EpgProgrammeEntity>> = MutableStateFlow(emptyList())
        override suspend fun coverageDays(epgChannelId: String): Int? = null
        override suspend fun programmeDescription(programmeId: Long): String? = null
        override suspend fun programmeById(programmeId: Long): EpgProgrammeEntity? = null
        override suspend fun programmesForChannel(epgKey: String, from: Long, to: Long): List<EpgProgrammeEntity> = emptyList()
        override suspend fun programmeSummariesForChannel(epgKey: String, from: Long, to: Long): List<EpgProgrammeEntity> = emptyList()
        override suspend fun programmeSummariesForChannels(epgKeys: List<String>, from: Long, to: Long): List<EpgProgrammeEntity> = emptyList()
        override suspend fun searchTitles(query: String, from: Long, to: Long, limit: Int): List<EpgProgrammeEntity> = emptyList()
        override suspend fun countAll(): Int = count.value
        override fun countFlow(): Flow<Int> = count
    }
}
