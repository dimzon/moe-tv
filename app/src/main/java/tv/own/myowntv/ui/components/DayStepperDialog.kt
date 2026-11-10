package tv.own.myowntv.ui.components
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

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.key
import tv.own.myowntv.ui.theme.mpx

/**
 * A day count, chosen with left and right.
 *
 * One focusable value that steps by a day; the remote's own key repeat handles holding. The range
 * clamps rather than wraps, so a held key settles on an end instead of jumping from the top back to
 * the bottom.
 *
 * Shared, because three settings ask the same question in different words — the playlist refresh
 * interval, the EPG refresh interval, and how many days of guide to keep — and they were about to be
 * three copies of the same stepper.
 */
@Composable
internal fun DayStepperDialog(
    title: String,
    hint: String,
    initialDays: Int,
    minDays: Int,
    maxDays: Int,
    label: @Composable (Int) -> String,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var days by remember { mutableIntStateOf(initialDays.coerceIn(minDays, maxDays)) }
    // ◀ ▶ step a day (the remote's key repeat handles holding); OK keeps it, Back leaves it as it was.
    val stepper: @Composable () -> Unit = {
        tv.own.myowntv.features.settings.PanelStepper(
            value = label(days),
            onStep = { d -> days = (days + d).coerceIn(minDays, maxDays) },
            onReset = null,
            onDone = { onConfirm(days) },
            confirm = true,
        )
    }
    // On a settings page it opens in the panel; elsewhere a Stage popup.
    if (tv.own.myowntv.features.settings.panelEditor(onDismiss) { stepper() }) return
    tv.own.myowntv.ui.stage.StagePopup(onDismiss = onDismiss, title = title, body = hint, width = 760.mpx) { stepper() }
}
