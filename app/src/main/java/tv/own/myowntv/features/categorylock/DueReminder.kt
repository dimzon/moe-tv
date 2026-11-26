package tv.own.myowntv.features.categorylock

import tv.own.owntv.core.reminder.ReminderSchedule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import tv.own.owntv.core.categorylock.firstUnsealed
import tv.own.owntv.core.categorylock.sealedCategoryIds
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.entity.ReminderEntity
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.reminder.ReminderManager

/**
 * The programme reminder to bring up now, null when none is due. One on a channel filed under a
 * locked category waits: it neither asks nor switches until the PIN opens the category, so a
 * reminder cannot show or tune what the lock keeps out of sight. Reminders are global: there are
 * no profiles.
 */
@Composable
internal fun rememberDueReminder(): ReminderEntity? {
    val reminders = koinInject<ReminderManager>()
    val customize = koinInject<CustomizationStore>()
    val channels = koinInject<ChannelDao>()
    val due = remember(reminders, customize, channels) {
        reminders.due.firstUnsealed(customize.sealedCategoryIds(MediaType.LIVE)) {
            channels.getById(it)?.let { c -> tv.own.owntv.core.database.ChannelMembership.primaryCat(c) }
        }
    }
    val reminder by due.collectAsStateWithLifecycle(null)
    // The manager's alarm tick may never arrive while the app stays open. Re-check the programme
    // window locally so an ended reminder cannot cover the next one for ever.
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    LaunchedEffect(reminder?.id, now) {
        val current = reminder ?: return@LaunchedEffect
        if (current.stopMs <= now) reminders.dismiss(current.id)
    }
    return reminder?.let { dueReminder(listOf(it), now) }
}

/** The first reminder still inside its programme window; pure so expiry is unit-tested. */
internal fun dueReminder(reminders: List<ReminderEntity>, nowMs: Long): ReminderEntity? =
    ReminderSchedule.due(reminders, nowMs).firstOrNull()
