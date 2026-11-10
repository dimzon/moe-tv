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

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import tv.own.myowntv.R
import tv.own.owntv.core.settings.SettingsRepository.NavSize
import tv.own.owntv.core.settings.SettingsRepository.NavStyle
import tv.own.myowntv.ui.components.MyOwnTVIcon
import tv.own.myowntv.ui.stage.StagePopupOption
import tv.own.myowntv.ui.theme.StageColors
import tv.own.myowntv.ui.theme.mpx

/**
 * The Navigation popup's Menu labels row. OK steps to the next choice and writes it as the Size and
 * Widen on focus that give it, so the rows below follow straight away.
 */
@Composable
internal fun MenuLabelsOption(
    style: NavStyle,
    size: NavSize,
    widen: NavSize?,
    onSize: (NavSize) -> Unit,
    onWiden: (NavSize?) -> Unit,
) {
    val labels = MenuLabels.of(style, size, widen)
    StagePopupOption(
        title = stringResource(R.string.settings_nav_menu_labels),
        subtitle = stringResource(R.string.settings_nav_menu_labels_desc),
        value = stringResource(labels.labelRes),
        onClick = {
            val rail = MenuLabels.apply(MenuLabels.next(style, labels), style, size, widen)
            // Widen first: Size is what moves the content, so it changes once, with Widen already set.
            onWiden(rail.widen)
            onSize(rail.size)
        },
        leading = { MyOwnTVIcon(MyOwnTVIcon.TEXT_SIZE, StageColors.Text, Modifier.size(21.mpx)) },
    )
}

/** "Labels: When focused": the Navigation row's short summary, after the rail style. */
@Composable
internal fun menuLabelsSummary(style: NavStyle, size: NavSize, widen: NavSize?): String =
    stringResource(R.string.settings_nav_menu_labels_summary, stringResource(MenuLabels.of(style, size, widen).labelRes))
