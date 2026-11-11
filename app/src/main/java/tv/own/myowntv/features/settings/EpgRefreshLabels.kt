package tv.own.myowntv.features.settings
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import tv.own.myowntv.R
import tv.own.owntv.core.settings.EpgAutoRefresh
import tv.own.owntv.core.settings.EpgRefresh

/** How an EPG source's auto refresh reads, on its row badge and in the add / edit form. */
@Composable
internal fun epgRefreshLabel(refresh: EpgRefresh): String =
    if (refresh.mode == EpgAutoRefresh.MANUAL) {
        pluralStringResource(R.plurals.settings_sources_refresh_days, refresh.manualDays, refresh.manualDays)
    } else {
        epgAutoRefreshLabel(refresh.mode)
    }

@Composable
internal fun epgAutoRefreshLabel(mode: EpgAutoRefresh): String = stringResource(
    when (mode) {
        EpgAutoRefresh.OFF -> R.string.settings_sources_refresh_off
        EpgAutoRefresh.STARTUP -> R.string.settings_sources_refresh_startup
        EpgAutoRefresh.HOURS_1 -> R.string.settings_epg_refresh_1h
        EpgAutoRefresh.HOURS_3 -> R.string.settings_epg_refresh_3h
        EpgAutoRefresh.HOURS_6 -> R.string.settings_epg_refresh_6h
        EpgAutoRefresh.HOURS_12 -> R.string.settings_epg_refresh_12h
        EpgAutoRefresh.HOURS_24 -> R.string.settings_epg_refresh_24h
        EpgAutoRefresh.HOURS_48 -> R.string.settings_epg_refresh_48h
        EpgAutoRefresh.MANUAL -> R.string.settings_sources_refresh_manual
    },
)
