package tv.own.owntv.core.reminder

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import tv.own.owntv.core.database.dao.ReminderDao
import tv.own.owntv.core.database.entity.ReminderEntity

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderManagerTest {

    @Test
    fun observesRestoredRowsAndRearmsIdempotently() = runTest {
        val dao = FakeReminderDao()
        val scheduler = FakeScheduler()
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        try {
            ReminderManager(dao, scheduler, scope = scope, clock = { 1_000L })
            advanceUntilIdle()
            assertEquals("initial empty database still rearms", 1, scheduler.rearms)

            val reminder = reminder()
            dao.emit(listOf(reminder))
            advanceUntilIdle()
            assertEquals("a restored reminder must be armed", 2, scheduler.rearms)

            dao.emit(listOf(reminder))
            advanceUntilIdle()
            assertEquals("an identical Flow value must not rearm", 2, scheduler.rearms)

            dao.emit(listOf(reminder.copy(title = "Updated")))
            advanceUntilIdle()
            assertEquals("a changed reminder must be rearmed", 3, scheduler.rearms)
        } finally {
            scope.cancel()
        }
    }

    private fun reminder() = ReminderEntity(
        id = 1,
        channelId = "channel",
        title = "Programme",
        startMs = 10_000L,
        stopMs = 20_000L,
        leadMinutes = 5,
        createdAt = 0L,
        updatedAt = 100L,
    )

    private class FakeScheduler : ReminderAlarmScheduler {
        var rearms = 0
        override suspend fun rearmAll() { rearms++ }
        override fun arm(id: Long, atMs: Long) = Unit
        override fun cancel(id: Long) = Unit
    }

    private class FakeReminderDao : ReminderDao {
        private val rows = MutableStateFlow<List<ReminderEntity>>(emptyList())
        fun emit(value: List<ReminderEntity>) { rows.value = value }
        override fun observeAll(): Flow<List<ReminderEntity>> = rows
        override suspend fun all(): List<ReminderEntity> = rows.value
        override suspend fun upsert(reminder: ReminderEntity): Long {
            emit(rows.value.filterNot { it.id == reminder.id } + reminder)
            return reminder.id
        }
        override suspend fun delete(id: Long) { emit(rows.value.filterNot { it.id == id }) }
        override suspend fun getById(id: Long): ReminderEntity? = rows.value.firstOrNull { it.id == id }
        override suspend fun getByChannelAndStart(channelId: String, startMs: Long): ReminderEntity? =
            rows.value.firstOrNull { it.channelId == channelId && it.startMs == startMs }
        override suspend fun removeIfOlderThan(channelId: String, startMs: Long, at: Long): Int {
            val removed = rows.value.filter { it.channelId == channelId && it.startMs == startMs && it.updatedAt <= at }
            emit(rows.value - removed.toSet())
            return removed.size
        }
        override suspend fun deleteEnded(now: Long): Int {
            val removed = rows.value.filter { it.stopMs <= now }
            emit(rows.value - removed.toSet())
            return removed.size
        }
    }
}
