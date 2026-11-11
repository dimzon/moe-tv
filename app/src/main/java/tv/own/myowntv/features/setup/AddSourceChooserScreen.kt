package tv.own.myowntv.features.setup
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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import tv.own.myowntv.R
import tv.own.myowntv.features.settings.SettingHelp
import tv.own.myowntv.features.settings.SettingValue
import tv.own.myowntv.features.settings.StageFullPage
import tv.own.myowntv.features.settings.StageSettingRow
import tv.own.myowntv.ui.components.MyOwnTVIcon

/**
 * Playlists › Add a source: **From your phone** (fill the form on another device over the LAN)
 * or **Type it here** (Xtream / M3U / Stalker with the remote), each explained in the panel.
 */
@Composable
fun AddSourceChooserScreen(
    onRemote: () -> Unit,
    onManual: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowsFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { rowsFocus.requestFocus() } }
    val playlists = stringResource(R.string.settings_playlists)
    val hints = listOf(
        stringResource(R.string.common_ok) to stringResource(R.string.settings_key_open),
        stringResource(R.string.common_back) to playlists,
    )
    StageFullPage(
        parents = listOf(playlists),
        title = stringResource(R.string.setup_add_source),
        count = "",
        onBack = onBack,
        modifier = modifier,
        rowsFocus = rowsFocus,
        settingsRoot = false,
    ) {
        val phone = stringResource(R.string.setup_from_phone)
        StageSettingRow(
            icon = MyOwnTVIcon.PHONE,
            title = phone,
            desc = stringResource(R.string.settings_line_add_from_phone),
            value = SettingValue.Opens(null),
            onClick = onRemote,
            help = SettingHelp(phone, stringResource(R.string.settings_help_add_from_phone), hints = hints),
        )
        val here = stringResource(R.string.settings_add_type_here)
        StageSettingRow(
            icon = MyOwnTVIcon.PENCIL,
            title = here,
            desc = stringResource(R.string.settings_line_add_type_here),
            value = SettingValue.Opens(null),
            onClick = onManual,
            help = SettingHelp(here, stringResource(R.string.settings_help_add_type_here), hints = hints),
        )
    }
}
