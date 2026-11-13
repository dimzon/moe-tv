package tv.own.owntv.core.epg

import org.junit.Assert.assertEquals
import org.junit.Test
import tv.own.owntv.core.database.entity.EpgProgrammeEntity

/**
 * A channel two guide feeds carry, where the feeds disagree. The EPG Sources list order decides: the
 * feed nearer the top is drawn in full, and one further down only fills the time it leaves empty.
 */
class GuidePriorityTest {

    private val h = 60 * 60_000L

    private fun row(id: Long, start: Long, stop: Long, title: String) = EpgProgrammeEntity(
        id = id,
        epgChannelId = "news.example",
        startMs = start,
        stopMs = stop,
        title = title,
    )

    private fun ids(rows: List<EpgProgrammeEntity>) = rows.map { it.id }

    @Test
    fun `rows from the one store are collapsed exactly as before`() {
        val rows = listOf(
            row(1, 0, 2 * h, "Film"),
            row(2, 10 * 60_000, 2 * h, "Film"),
            row(3, 2 * h, 3 * h, "News"),
        )
        assertEquals(ids(EpgDedupe.collapse(rows)), ids(GuidePriority.of(emptyList()).resolve(rows)))
    }

    @Test
    fun `a single row is returned untouched`() {
        val only = row(1, 20 * h, 21 * h, "Evening News")
        assertEquals(listOf(only), GuidePriority.of(listOf(7L)).resolve(listOf(only)))
    }
}
