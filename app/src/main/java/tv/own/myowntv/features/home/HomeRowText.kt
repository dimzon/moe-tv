package tv.own.myowntv.features.home
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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.tv.material3.Text
import tv.own.myowntv.ui.theme.StageColors
import tv.own.myowntv.ui.theme.mpx
import tv.own.myowntv.ui.theme.stageText
import tv.own.myowntv.R
import tv.own.owntv.core.model.HomeLiveRowMode
import tv.own.owntv.core.model.HomeRow

@Composable
fun HomeRow.displayTitle(): String = stringResource(
    when (this) {
        HomeRow.HERO -> R.string.home_row_keep_watching
        HomeRow.RECENT_CHANNELS -> R.string.home_row_recent_channels
        HomeRow.FAVORITE_CHANNELS -> R.string.home_row_favorite_channels
    },
)

@Composable
fun HomeRow.settingsDescription(): String = stringResource(
    when (this) {
        HomeRow.HERO -> R.string.home_row_hero_description
        HomeRow.RECENT_CHANNELS -> R.string.home_row_recent_description
        HomeRow.FAVORITE_CHANNELS -> R.string.home_row_favorite_description
    },
)

/** "42 min", "1 h 05 min". */
@Composable
fun durationText(minutes: Long): String =
    if (minutes >= 60) {
        stringResource(R.string.home_duration_hours_minutes, (minutes / 60).toInt(), (minutes % 60).toInt())
    } else {
        stringResource(R.string.player_duration_minutes, minutes.toInt())
    }

/** "31 min left", "1 h 05 min left". */
@Composable
fun timeLeftText(minutes: Long): String = stringResource(R.string.home_time_left, durationText(minutes))

/**
 * `.rowh`: a Home row's title in 26/800 (27 under the full hero), with an optional dim 17/700 word
 * after it — the count ("Keep watching 5") or "On now". Cards start 46 below its top (48 under the hero).
 */
@Composable
fun HomeRowHeader(title: String, small: String?, start: Dp, big: Boolean = false) {
    Row(
        Modifier.padding(start = start).height((if (big) 48 else 46).mpx),
        horizontalArrangement = Arrangement.spacedBy(14.mpx),
    ) {
        Text(
            title, style = stageText(if (big) 27 else 26, 800), color = StageColors.Text, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.alignByBaseline(),
        )
        if (small != null) {
            Text(small, style = stageText(17, 700), color = StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.alignByBaseline())
        }
    }
}

@Composable
fun HomeLiveRowMode.displayLabel(): String = stringResource(
    when (this) {
        HomeLiveRowMode.CARDS -> R.string.home_row_cards
        HomeLiveRowMode.ON_NOW -> R.string.home_row_on_now
    },
)
