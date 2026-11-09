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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import tv.own.owntv.core.live.CatchupJumps
import tv.own.myowntv.R
import tv.own.myowntv.ui.theme.mpx
import tv.own.myowntv.ui.format.rememberBestDateFormatter

/**
 * "Go back to…" — pick a point in a live channel's catch-up archive and start there.
 *
 * Rows are wall-clock times rather than "3 hours ago" on purpose: the user is looking for the news
 * that aired at 19:00, so a clock spares them the arithmetic, and it is what the guide-less catch-up
 * pickers on other TV players show. A row that lands on an earlier day carries its weekday, because
 * "19:00" alone cannot distinguish today from yesterday.
 *
 * Shared by both entry points — the Live TV long-press Catch-up dialog (when the channel has no
 * guide, so there are no programmes to list) and the fullscreen player's own button.
 */
@Composable
internal fun CatchupJumpRows(
    offsetsSec: List<Int>,
    firstFocus: FocusRequester,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    // Last row: leave the suggestions behind and type an exact day + time. Null hides it.
    onChooseExact: (() -> Unit)? = null,
    // First row: back to the start of the programme on air, focused when the list opens. Its offset is
    // taken when pressed. Null hides it.
    programmeStart: tv.own.owntv.core.live.CatchupProgrammeStart? = null,
) {
    // One "now" for the whole list: recomputing per row would let the clock tick between rows and
    // print two different times for the same offset.
    val nowMs = remember { System.currentTimeMillis() }
    val zone = remember { java.util.TimeZone.getDefault() }
    val timeOnly = rememberBestDateFormatter("Hm")
    val withDay = rememberBestDateFormatter("EEEHm")
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(2.mpx)) {
        if (programmeStart != null) item(key = "programme-start") {
            tv.own.myowntv.ui.stage.StagePopupOption(
                title = if (programmeStart.title.isEmpty()) stringResource(R.string.content_catchup_jump_programme_start_untitled)
                else stringResource(R.string.content_catchup_jump_programme_start, programmeStart.title),
                subtitle = stringResource(R.string.content_catchup_jump_programme_start_at, timeOnly(programmeStart.startMs)),
                onClick = { onPick(programmeStart.offsetSec(System.currentTimeMillis())) },
                modifier = Modifier.focusRequester(firstFocus),
                leading = { tv.own.myowntv.ui.stage.StagePopupIcon(tv.own.myowntv.ui.components.MyOwnTVIcon.SKIP_PREVIOUS) },
            )
        }
        items(offsetsSec, key = { it }) { offset ->
            val at = CatchupJumps.instantFor(offset, nowMs)
            tv.own.myowntv.ui.stage.StagePopupOption(
                title = if (CatchupJumps.crossesDay(offset, nowMs, zone)) withDay(at) else timeOnly(at),
                onClick = { onPick(offset) },
                modifier = if (programmeStart == null && offset == offsetsSec.first()) Modifier.focusRequester(firstFocus) else Modifier,
                leading = { tv.own.myowntv.ui.stage.StagePopupIcon(tv.own.myowntv.ui.components.MyOwnTVIcon.CATCHUP) },
            )
        }
        if (onChooseExact != null) item(key = "exact") {
            tv.own.myowntv.ui.stage.StagePopupOption(
                title = stringResource(R.string.content_catchup_jump_exact), onClick = onChooseExact, chosen = true,
                leading = { tv.own.myowntv.ui.stage.StagePopupIcon(tv.own.myowntv.ui.components.MyOwnTVIcon.CLOCK) },
            )
        }
    }
}

/** The standalone popup form, used by the player's "Go back to…" button. */
@Composable
internal fun CatchupJumpDialog(
    title: String,
    offsetsSec: List<Int>,
    windowSec: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
    programmeStart: tv.own.owntv.core.live.CatchupProgrammeStart? = null,
) {
    val firstFocus = remember { FocusRequester() }
    var manual by remember { mutableStateOf(false) }
    if (manual) {
        CatchupManualTimeDialog(
            windowSec = windowSec,
            onPick = { manual = false; onPick(it) },
            onDismiss = { manual = false },
        )
    }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(60); runCatching { firstFocus.requestFocus() }
    }
    tv.own.myowntv.ui.stage.StagePopup(
        onDismiss = onDismiss,
        title = title,
        body = stringResource(R.string.content_catchup_jump_prompt),
        eyebrow = null,
        scroll = false,
        buttons = { tv.own.myowntv.ui.stage.StageButton(stringResource(R.string.content_close), onClick = onDismiss, height = 56.mpx, textSize = 19) },
    ) {
        CatchupJumpRows(
            offsetsSec = offsetsSec,
            firstFocus = firstFocus,
            onPick = onPick,
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            onChooseExact = { manual = true },
            programmeStart = programmeStart,
        )
    }
}
