package tv.own.myowntv.features.live
import tv.own.owntv.*
import tv.own.owntv.core.*
import tv.own.owntv.core.backup.*
import tv.own.owntv.core.brand.*
import tv.own.owntv.core.catalog.*
import tv.own.owntv.core.categorylock.*
import tv.own.owntv.core.companion.*
import tv.own.owntv.core.content.*
import tv.own.owntv.core.customize.*
import tv.own.owntv.core.database.*
import tv.own.owntv.core.database.dao.*
import tv.own.owntv.core.database.entity.*
import tv.own.owntv.core.di.*
import tv.own.owntv.core.download.*
import tv.own.owntv.core.drm.*
import tv.own.owntv.core.epg.*
import tv.own.owntv.core.home.*
import tv.own.owntv.core.i18n.*
import tv.own.owntv.core.launcher.*
import tv.own.owntv.core.live.*
import tv.own.owntv.core.menu.*
import tv.own.owntv.core.metadata.*
import tv.own.owntv.core.model.*
import tv.own.owntv.core.nav.*
import tv.own.owntv.core.network.*
import tv.own.owntv.core.parser.*
import tv.own.owntv.core.player.*
import tv.own.owntv.core.profile.*
import tv.own.owntv.core.recording.*
import tv.own.owntv.core.reminder.*
import tv.own.owntv.core.repository.*
import tv.own.owntv.core.settings.*
import tv.own.owntv.core.setup.*
import tv.own.owntv.core.sports.*
import tv.own.owntv.core.sports.model.*
import tv.own.owntv.core.stalker.*
import tv.own.owntv.core.storage.*
import tv.own.owntv.core.subtitles.*
import tv.own.owntv.core.sync.*
import tv.own.owntv.core.sync.local.*
import tv.own.owntv.core.sync.work.*
import tv.own.owntv.core.theme.*
import tv.own.owntv.core.timeshift.*
import tv.own.owntv.core.trending.*
import tv.own.owntv.core.tv.*
import tv.own.owntv.core.ui.*
import tv.own.owntv.core.ui.stage.*
import tv.own.owntv.core.update.*
import tv.own.owntv.core.util.*
import tv.own.owntv.core.weather.*
import tv.own.owntv.player.*
import tv.own.owntv.player.ffmpeg.*

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Text
import tv.own.myowntv.R
import tv.own.owntv.core.database.entity.RecordingEntity
import tv.own.owntv.core.recording.RecordingSchedule
import tv.own.myowntv.ui.components.MyOwnTVIcon
import tv.own.myowntv.ui.components.TimeWheel
import tv.own.myowntv.ui.components.twoDigits
import tv.own.myowntv.ui.format.rememberBestDateFormatter
import tv.own.myowntv.ui.stage.StageButton
import tv.own.myowntv.ui.stage.StagePopup
import tv.own.myowntv.ui.theme.StageColors
import tv.own.myowntv.ui.theme.mpx
import tv.own.myowntv.ui.theme.stageText
import java.util.Calendar

/**
 * "Schedule recording…" from a channel's menu (#2): a day, a start and an end, guide or not.
 *
 * The same wheels as the catch-up time picker, pointed forwards — the day wheel runs from today to
 * [DAYS_AHEAD] days out. An end at or before the start is the next day, and says so under the end
 * time. A window already over is refused in words; one already under way records from now. A clash on
 * the playlist is a warning, never a block: the user may know their provider has a spare connection.
 */
@Composable
internal fun ScheduleRecordingDialog(
    channelName: String,
    clashesFor: suspend (startMs: Long, stopMs: Long) -> List<RecordingEntity>,
    onSchedule: (startMs: Long, stopMs: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    // One "now" for the dialog's lifetime, as in the catch-up picker: the wheels must not shift under the user.
    val nowMs = remember { System.currentTimeMillis() }
    val formatDay = rememberBestDateFormatter("EEEMMMd")
    // Start at the next five minutes, for an hour — the neighbourhood most schedules are nudged from.
    val firstStart = remember { Calendar.getInstance().apply { timeInMillis = nowMs; add(Calendar.MINUTE, 5 - get(Calendar.MINUTE) % 5) } }
    var day by remember { mutableIntStateOf(if (firstStart.get(Calendar.DAY_OF_YEAR) == Calendar.getInstance().apply { timeInMillis = nowMs }.get(Calendar.DAY_OF_YEAR)) 0 else 1) }
    var startMin by remember { mutableIntStateOf(firstStart.get(Calendar.HOUR_OF_DAY) * 60 + firstStart.get(Calendar.MINUTE)) }
    var endMin by remember { mutableIntStateOf((startMin + 60) % MINUTES_PER_DAY) }

    fun instantOf(dayOffset: Int, minuteOfDay: Int): Long = Calendar.getInstance().apply {
        timeInMillis = nowMs
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, dayOffset)
        add(Calendar.MINUTE, minuteOfDay)
    }.timeInMillis

    val startMs = instantOf(day, startMin)
    val stopMs = instantOf(day, endMin)
    val window = RecordingSchedule.manualWindow(startMs, stopMs, nowMs)
    val clashes by produceState(emptyList<RecordingEntity>(), startMs, stopMs) { value = clashesFor(startMs, stopMs) }

    // Hours and minutes wrap like a clock; the day stops at today and at the last day offered.
    fun nudgeStart(delta: Int) { startMin = Math.floorMod(startMin + delta, MINUTES_PER_DAY) }
    fun nudgeEnd(delta: Int) { endMin = Math.floorMod(endMin + delta, MINUTES_PER_DAY) }

    val dayFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { dayFocus.requestFocus() } }
    StagePopup(
        onDismiss = onDismiss,
        title = stringResource(R.string.recording_schedule),
        eyebrow = channelName,
        body = stringResource(R.string.recording_schedule_hint),
        width = 1080.mpx,
        buttons = {
            StageButton(stringResource(R.string.common_cancel), onClick = onDismiss, height = 56.mpx, textSize = 19)
            StageButton(
                stringResource(R.string.recording_record),
                onClick = { if (window != null) onSchedule(startMs, stopMs) },
                icon = MyOwnTVIcon.REC, height = 56.mpx, textSize = 19, tinted = true,
            )
        },
    ) {
        Row(
            Modifier.fillMaxWidth().focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(12.mpx),
            verticalAlignment = Alignment.Bottom,
        ) {
            TimeWheel(
                value = formatDay(instantOf(day, 0)),
                onUp = { day = (day - 1).coerceAtLeast(0) },
                onDown = { day = (day + 1).coerceAtMost(DAYS_AHEAD) },
                modifier = Modifier.weight(1.6f).focusRequester(dayFocus),
            )
            ClockGroup(stringResource(R.string.recording_schedule_start), startMin, ::nudgeStart, Modifier.weight(2f))
            ClockGroup(stringResource(R.string.recording_schedule_end), endMin, ::nudgeEnd, Modifier.weight(2f))
        }
        val nextDay = endMin <= startMin
        val note = when {
            window == null -> stringResource(R.string.recording_schedule_past)
            clashes.isNotEmpty() -> stringResource(R.string.recording_clash_with, clashes.first().title)
            else -> null
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.mpx), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(note.orEmpty(), style = stageText(17, 600), color = StageColors.Warn, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (nextDay) Text(stringResource(R.string.recording_schedule_next_day), style = stageText(17, 600), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A label over an hour wheel and a minute wheel — "Start  21 : 00". */
@Composable
private fun ClockGroup(label: String, minuteOfDay: Int, nudge: (Int) -> Unit, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = stageText(16, 700), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 8.mpx))
        Row(horizontalArrangement = Arrangement.spacedBy(8.mpx), verticalAlignment = Alignment.CenterVertically) {
            TimeWheel(value = twoDigits(minuteOfDay / 60), onUp = { nudge(60) }, onDown = { nudge(-60) }, modifier = Modifier.weight(1f))
            Text(":", style = stageText(30, 800), color = StageColors.Muted)
            // Minute only, so 21:59 → 21:00 rather than 22:00, as on a clock's minute hand.
            TimeWheel(
                value = twoDigits(minuteOfDay % 60),
                onUp = { nudge(if (minuteOfDay % 60 == 59) -59 else 1) },
                onDown = { nudge(if (minuteOfDay % 60 == 0) 59 else -1) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** How far ahead the day wheel goes: a week, more than any guide-less schedule needs planning. */
private const val DAYS_AHEAD = 6
private const val MINUTES_PER_DAY = 24 * 60
