package tv.own.myowntv.features.categorylock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.own.owntv.core.database.entity.ReminderEntity

class DueReminderTest {
    private val min = 60_000L
    private fun reminder(id: Long, start: Long, stop: Long, lead: Int = 0) = ReminderEntity(
        id = id, channelId = "ch$id", title = "Show $id",
        startMs = start, stopMs = stop, leadMinutes = lead, createdAt = 0,
    )

    @Test
    fun `only a reminder inside its programme window is due`() {
        val early = reminder(1, start = 200 * min, stop = 300 * min, lead = 5)
        val ended = reminder(2, start = 0, stop = 50 * min)
        val future = reminder(3, start = 400 * min, stop = 500 * min)
        assertNull(dueReminder(listOf(early), nowMs = 195 * min - 1))
        assertEquals(early, dueReminder(listOf(ended, future, early), nowMs = 195 * min))
        assertNull(dueReminder(listOf(ended), nowMs = 50 * min))
    }

    @Test
    fun `the soonest live reminder is shown first`() {
        val later = reminder(1, start = 200 * min, stop = 300 * min)
        val sooner = reminder(2, start = 150 * min, stop = 250 * min)
        assertEquals(sooner, dueReminder(listOf(later, sooner), nowMs = 150 * min))
    }
}
