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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Text
import tv.own.owntv.core.live.CatchupJumps
import tv.own.myowntv.R
import tv.own.myowntv.ui.theme.mpx
import tv.own.myowntv.ui.format.rememberBestDateFormatter
import tv.own.myowntv.ui.components.twoDigits

/**
 * Exact-time entry for a catch-up channel: a day wheel plus a two-part HH:MM, so any point the
 * provider still holds is reachable.
 *
 * The suggestion list it opens from can only ever offer round offsets — 1 hour, 3 hours, yesterday at
 * the same minute — so "yesterday at 10:31" had no route. This is that route.
 *
 * Three wheels rather than a text field or a clock face: a TV remote has no comfortable way to type,
 * and an analogue clock is worse still with a D-pad. Left/Right moves between the wheels, OK steps
 * into one, Up/Down then change it (holding a key auto-repeats), and OK or Back steps back out — see
 * [tv.own.myowntv.ui.components.TimeWheel] for why editing is a mode rather than always-on.
 *
 * Every change is pushed through [CatchupJumps.clampToArchive], so the wheels stop dead at the live
 * edge and at the far end of the archive. Nothing needs to explain why: the number simply will not go
 * further than the recording does.
 */
@Composable
internal fun CatchupManualTimeDialog(
    windowSec: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    // One "now" for the dialog's lifetime. A ticking clock would shift every wheel under the user's
    // fingers and make the clamp boundaries move while they aim at them.
    val nowMs = remember { System.currentTimeMillis() }
    val zone = remember { java.util.TimeZone.getDefault() }
    val formatDay = rememberBestDateFormatter("EEEMMMd")

    // Start an hour back: inside the archive on any channel, and a sensible neighbourhood to nudge from.
    var point by remember {
        mutableStateOf(
            CatchupJumps.clampToArchive(
                CatchupJumps.pointAt(CatchupJumps.instantFor(3600, nowMs), nowMs, zone),
                nowMs, zone, windowSec,
            ),
        )
    }
    val maxDaysAgo = remember(windowSec) { CatchupJumps.selectableDays(windowSec) - 1 }

    fun nudge(deltaDays: Int = 0, deltaHours: Int = 0, deltaMinutes: Int = 0) {
        val p = point
        val next = CatchupJumps.Point(
            daysAgo = (p.daysAgo + deltaDays).coerceIn(0, maxDaysAgo),
            // Hours and minutes wrap: rolling 23 → 00 is how a clock behaves, and the archive clamp
            // below catches anything the wrap puts out of reach.
            hour = ((p.hour + deltaHours) + 24) % 24,
            minute = ((p.minute + deltaMinutes) + 60) % 60,
        )
        point = CatchupJumps.clampToArchive(next, nowMs, zone, windowSec)
    }

    val dayFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { dayFocus.requestFocus() } }
    tv.own.myowntv.ui.stage.StagePopup(
        onDismiss = onDismiss,
        title = stringResource(R.string.content_catchup_jump_exact),
        body = stringResource(R.string.content_catchup_jump_hint),
        eyebrow = null,
        width = 760.mpx,
        buttons = {
            tv.own.myowntv.ui.stage.StageButton(stringResource(R.string.common_cancel), onClick = onDismiss, height = 56.mpx, textSize = 19)
            tv.own.myowntv.ui.stage.StageButton(stringResource(R.string.content_play), onClick = { onPick(CatchupJumps.offsetSecOf(point, nowMs, zone)) }, icon = tv.own.myowntv.ui.components.MyOwnTVIcon.PLAY, height = 56.mpx, textSize = 19, tinted = true)
        },
    ) {
        Row(
            Modifier.fillMaxWidth().focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(12.mpx),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tv.own.myowntv.ui.components.TimeWheel(
                value = formatDay(CatchupJumps.instantOf(point, nowMs, zone)),
                onUp = { nudge(deltaDays = -1) },   // toward today
                onDown = { nudge(deltaDays = +1) }, // further back
                modifier = Modifier.weight(1.6f).focusRequester(dayFocus),
            )
            tv.own.myowntv.ui.components.TimeWheel(value = twoDigits(point.hour), onUp = { nudge(deltaHours = +1) }, onDown = { nudge(deltaHours = -1) }, modifier = Modifier.weight(1f))
            Text(":", style = tv.own.myowntv.ui.theme.stageText(30, 800), color = tv.own.myowntv.ui.theme.StageColors.Muted)
            tv.own.myowntv.ui.components.TimeWheel(value = twoDigits(point.minute), onUp = { nudge(deltaMinutes = +1) }, onDown = { nudge(deltaMinutes = -1) }, modifier = Modifier.weight(1f))
        }
    }
}
