package tv.own.owntv.core.live

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.database.dao.EpgDao
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import java.lang.reflect.Proxy

class LiveEpgReaderTest {
    private val channel = ChannelEntity(remoteId = "channel", name = "Channel")
    private val custom = SectionCustomizations()

    private fun programme(title: String, start: Long, stop: Long) = EpgProgrammeEntity(
        epgChannelId = channel.epgChannelId, startMs = start, stopMs = stop, title = title,
    )

    private fun dao(
        rows: List<EpgProgrammeEntity>,
        storedBatch: Boolean = true,
        onUpcoming: (Long) -> Unit = {},
    ): EpgDao = Proxy.newProxyInstance(
        EpgDao::class.java.classLoader,
        arrayOf(EpgDao::class.java),
    ) { _, method, args ->
        when (method.name) {
            "upcoming" -> {
                val at = args!![1] as Long
                onUpcoming(at)
                flowOf(rows.filter { it.stopMs > at })
            }
            "previousProgramme" -> rows.lastOrNull { it.stopMs <= (args!![1] as Long) }
            "coverageDays" -> 1
            "programmeSummariesForChannels" -> {
                if (!storedBatch) emptyList()
                else {
                    val values = args!!
                    val keys = values[0] as List<*>
                    val at = values[1] as Long
                    rows.filter { it.epgChannelId in keys && at in it.startMs until it.stopMs }
                }
            }
            "toString" -> "FakeEpgDao"
            else -> error("Unexpected DAO call: ${method.name}")
        }
    } as EpgDao

    @Test
    fun `now-next cache expires exactly when the current programme stops`() = runTest {
        val rows = listOf(programme("Now", 100, 200), programme("Next", 200, 300))
        val reads = mutableListOf<Long>()
        val reader = LiveEpgReader(dao(rows, onUpcoming = { reads += it }))

        assertEquals("Now", reader.nowNext(channel, custom, 0, nowMs = 100)?.now?.title)
        assertEquals("Now", reader.nowNext(channel, custom, 0, nowMs = 199)?.now?.title)
        assertEquals(listOf(100L), reads)
        assertEquals("Next", reader.nowNext(channel, custom, 0, nowMs = 200)?.now?.title)
        assertEquals(listOf(100L, 200L), reads)
    }

    @Test
    fun `a gap answer expires when the next programme starts`() = runTest {
        val rows = listOf(programme("Next", 200, 300))
        val reads = mutableListOf<Long>()
        val reader = LiveEpgReader(dao(rows, onUpcoming = { reads += it }))

        val gap = reader.nowNext(channel, custom, 0, nowMs = 100)
        assertNull(gap?.now)
        assertEquals("Next", gap?.next?.title)
        assertNull(reader.nowNext(channel, custom, 0, nowMs = 199)?.now)
        assertEquals(listOf(100L), reads)
        assertEquals("Next", reader.nowNext(channel, custom, 0, nowMs = 200)?.now?.title)
        assertEquals(listOf(100L, 200L), reads)
    }

    @Test
    fun `batch fallback never revives an expired cached programme`() = runTest {
        val reader = LiveEpgReader(dao(listOf(programme("Now", 100, 200)), storedBatch = false))
        reader.nowNext(channel, custom, 0, nowMs = 100)
        val result = reader.nowProgrammesFor(listOf(channel), custom, 0, nowMs = 200)
        assertTrue(result.isEmpty())
    }
}
