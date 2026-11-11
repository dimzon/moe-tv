package tv.own.owntv.core.categorylock

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import tv.own.owntv.core.database.entity.ReminderEntity

/** The lock-aware flows behind the rail and More counts, and programme reminders. */
class LockedFlowsTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val sealed = MutableStateFlow<Set<Long>>(emptySet())

    /** Channel id to its category: 1 and 2 are in 10, 3 is in 20, 4 has none, any other is gone. */
    private val categories = mapOf(1L to 10L, 2L to 10L, 3L to 20L)
    private val categoryOf: suspend (Long) -> Long? = { categories[it] }

    @After
    fun tearDown() = scope.cancel()

    private fun <T> Flow<T>.values(): List<T> = mutableListOf<T>().also { seen -> onEach { seen += it }.launchIn(scope) }

    @Test
    fun aCountRunsThePlainQueryUntilACategoryIsSealedThenLeavesItOut() {
        val asked = mutableListOf<String>()
        val counts = lockAwareCount(sealed, { asked += "plain"; flowOf(5) }) { ids -> asked += "excluding $ids"; flowOf(3) }.values()
        sealed.value = setOf(10)
        sealed.value = emptySet()
        assertEquals(listOf(5, 3, 5), counts)
        assertEquals(listOf("plain", "excluding [10]", "plain"), asked)
    }

    @Test
    fun theLastWatchedIsLookedAtAgainWhenALockClosesOrOpens() {
        val last = MutableStateFlow("Ch 1")
        val seen = last.recheckedOn(sealed).values()
        sealed.value = setOf(10)
        sealed.value = emptySet()
        assertEquals(listOf("Ch 1", "Ch 1", "Ch 1"), seen)
        last.value = "Ch 2"
        assertEquals("Ch 2", seen.last())
    }

    private fun reminder(id: Long, channelId: Long) = ReminderEntity(
        id = id, channelId = channelId, channelName = "Ch $channelId",
        title = "Show $id", startMs = 0, stopMs = 1, leadMinutes = 0, createdAt = 0,
    )

    @Test
    fun aDueReminderOnASealedChannelWaitsAndTheNextOneComesUp() {
        val due = MutableStateFlow(listOf(reminder(1, channelId = 1), reminder(2, channelId = 3)))
        val shown = due.firstUnsealed(sealed = sealed, categoryOf = categoryOf).values()
        assertEquals(1L, shown.last()?.id) // ch 1 (category 10) is first and nothing is sealed
        sealed.value = setOf(10)
        assertEquals(2L, shown.last()?.id) // ch 1's category seals, so ch 3 (category 20) comes up
        sealed.value = setOf(10, 20)
        assertEquals(null, shown.last())
        sealed.value = emptySet()
        assertEquals(1L, shown.last()?.id)
    }

    @Test
    fun aLocalFileGivesItsCategoryWhileThePlayerIsOnIt() {
        val files = PlayingFileCategory()
        files.started("/data/media/film.mp4", categoryId = 10)
        assertEquals(10L, files.categoryOf("/data/media/film.mp4"))
        assertEquals(null, files.categoryOf("http://x/1.ts"))
        assertEquals(null, files.categoryOf(null))
        files.started("/data/media/other.mp4", categoryId = null)
        assertEquals(null, files.categoryOf("/data/media/film.mp4"))
        assertEquals(null, files.categoryOf("/data/media/other.mp4"))
    }
}
