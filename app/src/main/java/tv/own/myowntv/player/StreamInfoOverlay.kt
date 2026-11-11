package tv.own.myowntv.player
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

import tv.own.myowntv.ui.theme.hudShade
import tv.own.myowntv.ui.theme.hudInk
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import tv.own.myowntv.R
import tv.own.owntv.player.PlayerHeartbeat
import tv.own.owntv.player.PlaybackEngine
import tv.own.owntv.player.StreamInfoRow
import tv.own.owntv.player.StreamInfoValue
import tv.own.owntv.player.StreamInfoLabel
import tv.own.owntv.player.titleRes
import tv.own.owntv.player.displayText

/**
 * Non-interactive technical readout for the current stream (codec, resolution, HDR, bitrate, decoder, audio,
 * buffer, source). Reads [PlaybackEngine.streamInfo] live — re-polled once a second so bitrate/buffer update.
 * Toggled from the player's info button.
 */
@Composable
fun StreamInfoOverlay(player: PlaybackEngine, modifier: Modifier = Modifier) {
    // Starts empty and is filled by the effect below: the read goes through the engine's own
    // machinery, so there is nothing to gather synchronously during composition.
    var rows by remember { mutableStateOf(emptyList<StreamInfoRow>()) }
    // Re-read on the player's shared cosmetic beat rather than a timer of its own, so having the
    // overlay up costs one wake-up rather than two (see [PlayerHeartbeat]).
    LaunchedEffect(player) {
        PlayerHeartbeat.beat.collect { rows = player.streamInfo() }
    }
    if (rows.isEmpty()) return
    val res = LocalResources.current

    Column(
        modifier = modifier
            .widthIn(min = 300.dp, max = 460.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(hudShade(0.78f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            stringResource(R.string.player_stream_info),
            style = tv.own.myowntv.ui.theme.stageText(22, 700),
            color = tv.own.myowntv.ui.theme.stageAccent.accent,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(2.dp))
        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth().padding(top = 5.dp)) {
                Text(
                    stringResource(row.label.titleRes),
                    style = tv.own.myowntv.ui.theme.stageText(22, 400),
                    color = tv.own.myowntv.ui.theme.StageColors.Muted,
                    modifier = Modifier.weight(0.38f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    row.value.displayText(res),
                    style = tv.own.myowntv.ui.theme.stageText(22, 400),
                    color = hudInk,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(0.62f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
