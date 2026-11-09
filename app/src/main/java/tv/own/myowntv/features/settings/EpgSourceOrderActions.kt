package tv.own.myowntv.features.settings
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

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import tv.own.myowntv.R
import tv.own.myowntv.ui.components.MyOwnTVIcon

/**
 * Move up / Move down for a guide's panel. The EPG Sources order is the guide priority: where two
 * feeds list the same channel, the higher one is shown. Both moves are always offered, as on
 * Customize; at either end the store changes nothing, so the focused action never disappears.
 */
@Composable
internal fun epgSourceOrderActions(count: Int, onMove: (up: Boolean) -> Unit): List<StageAction> {
    if (count < 2) return emptyList()
    return listOf(
        StageAction(MyOwnTVIcon.CHEVRON_UP, stringResource(R.string.settings_row_menu_move_up), { onMove(true) }),
        StageAction(MyOwnTVIcon.CHEVRON_DOWN, stringResource(R.string.settings_row_menu_move_down), { onMove(false) }),
    )
}

/** Where the guide at [index] stands, or null when there is only one guide and nothing to rank. */
@Composable
internal fun epgSourcePriorityLine(index: Int, count: Int): String? =
    if (count < 2) null else stringResource(R.string.settings_epg_sources_priority, index + 1, count)
