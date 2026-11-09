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
import tv.own.owntv.core.live.MAX_MULTIVIEW_TILES
import tv.own.owntv.core.live.MIN_MULTIVIEW_TILES

/**
 * The **most** tiles the Multiview grid may have. Every value is always offered — nothing is greyed
 * out — and the grid still opens with two, growing only when the user asks for more.
 */
@Composable
internal fun MultiviewTilesDialog(current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val tiles = (MIN_MULTIVIEW_TILES..MAX_MULTIVIEW_TILES).toList()
    // Each option is a ceiling too, so it reads the same as the row it came from. A simple choice:
    // on the settings page it opens in the panel.
    PickerDialog(
        title = stringResource(R.string.settings_multiview_tiles_max),
        options = tiles.map { it.toString() to stringResource(R.string.settings_multiview_tiles_max_value, it) },
        selected = current.toString(),
        onSelect = { onPick(it.toInt()) },
        onDismiss = onDismiss,
    )
}

/**
 * Three or four tiles need three or four provider connections and three or four hardware decoders.
 *
 * Shaped exactly like the Auto-frame-rate warning, and asked in Settings only. It never
 * refuses: "Use anyway" gives the count the user picked, and a tile that then cannot start says so
 * itself. Focus starts on Keep two.
 */
@Composable
internal fun MultiviewWarningDialog(onUseAnyway: () -> Unit, onKeepTwo: () -> Unit) {
    tv.own.myowntv.ui.stage.StageConfirm(
        title = stringResource(R.string.settings_multiview_warning_title),
        body = stringResource(R.string.settings_multiview_warning_description),
        cancel = stringResource(R.string.settings_multiview_keep_two),
        confirm = stringResource(R.string.settings_multiview_use_anyway),
        onConfirm = onUseAnyway,
        onCancel = onKeepTwo,
        focusCancel = true,
    )
}
