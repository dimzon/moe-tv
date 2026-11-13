package tv.own.owntv.core.reminder

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.own.owntv.core.backup.UserDataResolver
import tv.own.owntv.core.database.dao.ReminderDao
import tv.own.owntv.core.database.entity.ReminderEntity

/**
 * Programme reminders: what the guide's "Remind me" writes and what the start-time prompt
 * reads. Core keeps the time; each app draws the prompt from [due] and calls [dismiss] when the user
 * answers it.
 */
class ReminderManager(
    private val reminderDao: ReminderDao,
    private val scheduler: ReminderScheduler,
    /**
     * Sync plumbing, injected so a dismissed reminder records a `prog` tombstone and does not come
     * back from the household on the next merge. Optional only so the class works where sync is not
     * wired (tests); DI passes it.
     */
    private val userData: UserDataResolver? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Bumped by the alarm, so [due] re-reads the clock at the moment a reminder comes up. */
    private val tick = MutableStateFlow(0L)

    /** Reminders whose moment has come and whose programme is still on, soonest first. */
    val due: StateFlow<List<ReminderEntity>> = combine(reminderDao.observeAll(), tick) { all, _ ->
        ReminderSchedule.due(all, clock())
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    init {
        // Every launch re-arms: some TVs never deliver BOOT_COMPLETED.
        scope.launch { scheduler.rearmAll() }
    }

    /** All the user's reminders. */
    fun observe(): Flow<List<ReminderEntity>> = reminderDao.observeAll()

    /**
     * Remind of a programme; setting it again for the same programme replaces it.
     * Returns false for a programme that has already started.
     */
    suspend fun add(
        channelId: String,
        title: String,
        startMs: Long,
        stopMs: Long,
        leadMinutes: Int = ReminderSchedule.DEFAULT_LEAD_MINUTES,
    ): Boolean {
        val now = clock()
        if (!ReminderSchedule.canRemind(startMs, now)) return false
        val reminder = ReminderEntity(
            channelId = channelId,
            title = title, startMs = startMs, stopMs = stopMs, leadMinutes = leadMinutes, createdAt = now,
            // Per-row LWW stamp: a sync apply only replaces this row with an incoming reminder that is newer.
            updatedAt = now,
        )
        val id = reminderDao.upsert(reminder)
        ReminderSchedule.wakeAtFor(reminder, now)?.let { scheduler.arm(id, it) }
        return true
    }

    /**
     * Cancel a reminder, or answer its prompt (Watch or Dismiss both end it).
     *
     * When sync is wired, the dismissal is recorded as a `prog` tombstone (identity
     * (channelId, startMs)) BEFORE the row goes, so the other devices hear about the deletion and
     * the merge does not put the reminder straight back.
     */
    suspend fun dismiss(id: Long) {
        val reminder = reminderDao.getById(id)
        if (reminder != null) {
            userData?.recordReminderDeletion(reminder.channelId, reminder.startMs, at = clock())
        }
        scheduler.cancel(id)
        reminderDao.delete(id)
    }

    /** Called by [ReminderAlarmReceiver]. */
    internal suspend fun onAlarm() {
        tick.value = clock()
        scheduler.rearmAll()
    }
}
