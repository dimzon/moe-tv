package tv.own.myowntv.player
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

import tv.own.myowntv.ui.theme.hudShade
import tv.own.myowntv.ui.theme.hudInk
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import tv.own.myowntv.R
import tv.own.myowntv.ui.format.rememberBestDateFormatter
import tv.own.myowntv.ui.format.rememberSystemTimeFormatter

/**
 * The player's clock, centred at the top of the HUD.
 *
 * Sits in the middle rather than a corner because that band is empty — so the guide card on the right
 * keeps exactly the position it has always had. A corner clock would have pushed the guide down on
 * live channels for no gain.
 *
 * The single column is labelled. The label is what stops a lone "13:00" from being read as a broken
 * device clock, and it costs one small line. (There is no second "Programme time" column any more:
 * the archive clock belonged to catch-up, which is gone — what is on screen is always now.)
 */
@Composable
internal fun PlayerClock(modifier: Modifier = Modifier) {
    val formatTime = rememberSystemTimeFormatter()
    val formatDate = rememberBestDateFormatter("EEEdMMM")
    // Minute precision is all that is displayed, so a 10 s tick keeps it honest without waking the
    // frame loop pointlessly. The HUD is only on screen in bursts anyway.
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(10_000); nowMs = System.currentTimeMillis() } }

    Row(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(hudShade(0.38f))
            .border(1.dp, hudInk.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ClockColumn(
            label = stringResource(R.string.content_clock_current),
            time = formatTime(nowMs),
            date = formatDate(nowMs),
            labelColor = hudInk.copy(alpha = 0.45f),
            timeColor = hudInk,
            dateColor = hudInk.copy(alpha = 0.45f),
        )
    }
}

@Composable
private fun ClockColumn(
    label: String,
    time: String,
    date: String,
    labelColor: Color,
    timeColor: Color,
    dateColor: Color,
) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            label.uppercase(),
            style = tv.own.myowntv.ui.theme.stageText(20, 500).copy(letterSpacing = 1.2.sp),
            color = labelColor,
            fontWeight = FontWeight.Bold,
        )
        Text(
            time,
            style = tv.own.myowntv.ui.theme.stageText(43, 400),
            color = timeColor,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            date.uppercase(),
            style = tv.own.myowntv.ui.theme.stageText(20, 500).copy(letterSpacing = 0.8.sp),
            color = dateColor,
        )
    }
}
