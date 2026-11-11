package tv.own.myowntv.ui.components
import tv.own.owntv.*
import tv.own.owntv.core.*
import tv.own.owntv.core.backup.*
import tv.own.owntv.core.brand.*
import tv.own.owntv.core.categorylock.*
import tv.own.owntv.core.companion.*
import tv.own.owntv.core.content.*
import tv.own.owntv.core.customize.*
import tv.own.owntv.core.database.*
import tv.own.owntv.core.database.dao.*
import tv.own.owntv.core.database.entity.*
import tv.own.owntv.core.di.*
import tv.own.owntv.core.drm.*
import tv.own.owntv.core.epg.*
import tv.own.owntv.core.home.*
import tv.own.owntv.core.i18n.*
import tv.own.owntv.core.launcher.*
import tv.own.owntv.core.live.*
import tv.own.owntv.core.menu.*
import tv.own.owntv.core.model.*
import tv.own.owntv.core.nav.*
import tv.own.owntv.core.network.*
import tv.own.owntv.core.parser.*
import tv.own.owntv.core.player.*
import tv.own.owntv.core.reminder.*
import tv.own.owntv.core.repository.*
import tv.own.owntv.core.settings.*
import tv.own.owntv.core.setup.*
import tv.own.owntv.core.sports.*
import tv.own.owntv.core.sports.model.*
import tv.own.owntv.core.stalker.*
import tv.own.owntv.core.storage.*
import tv.own.owntv.core.sync.*
import tv.own.owntv.core.sync.local.*
import tv.own.owntv.core.sync.work.*
import tv.own.owntv.core.theme.*
import tv.own.owntv.core.tv.*
import tv.own.owntv.core.ui.*
import tv.own.owntv.core.ui.stage.*
import tv.own.owntv.core.update.*
import tv.own.owntv.core.util.*
import tv.own.owntv.core.weather.*
import tv.own.owntv.player.*

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import tv.own.myowntv.R
import tv.own.myowntv.ui.theme.mpx

/**
 * Small "Resume playback?" prompt shown (in the "Ask to resume" mode) when a movie/episode has a
 * saved position. Resume is pre-focused; Back dismisses without starting playback.
 */
@Composable
fun ResumeDialog(
    positionMs: Long,
    onResume: () -> Unit,
    onStartOver: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Resume is focused: carrying on is the common answer.
    tv.own.myowntv.ui.stage.StageConfirm(
        title = stringResource(R.string.common_resume_prompt),
        body = stringResource(R.string.common_resume_position, formatTimestamp(positionMs)),
        cancel = stringResource(R.string.common_start_over),
        confirm = stringResource(R.string.common_resume),
        onConfirm = onResume,
        onCancel = onStartOver,
        onBack = onDismiss,
        eyebrow = null,
        width = 640.mpx,
    )
}

/**
 * 0:42 / 23:45 / 1:23:45 style timestamp — the app's one duration format.
 *
 * Used for a resume position, the player's elapsed/remaining readout and the audio bar alike. The
 * player used to carry two private copies of this arithmetic against a duplicate pair of string
 * resources (`player_track_*`), which were byte-identical to these in every locale.
 */
@Composable
fun formatTimestamp(ms: Long): String = formatTimestamp(LocalResources.current, ms)

/** The same format outside composition — a value read at a button press rather than on every tick. */
fun formatTimestamp(res: android.content.res.Resources, ms: Long): String {
    val totalSec = ms.coerceAtLeast(0L) / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) {
        res.getString(R.string.common_timestamp_hours, h, m, s)
    } else {
        res.getString(R.string.common_timestamp_minutes, m, s)
    }
}
