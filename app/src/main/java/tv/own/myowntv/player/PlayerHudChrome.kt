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
import tv.own.owntv.core.profile.*
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

import tv.own.myowntv.ui.theme.hudAccent
import tv.own.myowntv.ui.theme.hudShade
import tv.own.myowntv.ui.theme.hudInk
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import tv.own.myowntv.R
import tv.own.owntv.core.player.PlayerControl
import tv.own.owntv.core.player.ControlCluster
import tv.own.myowntv.ui.components.MyOwnTVIcon
import tv.own.myowntv.ui.theme.OwnTVTheme
import tv.own.myowntv.ui.theme.animationsOn

/**
 * The HUD's chrome: the top strip, the channel/direct-tune OSD cards, the centre transport and the
 * bottom bar. Split out of [PlayerHud] — behaviour unchanged; these are `internal` rather than
 * file-private only because [PlayerHud] now lives in a sibling file.
 */

// ---------------- Top bar ----------------

@Composable
internal fun TopBar(
    player: PlaybackEngine, chips: List<String>,
    onBack: () -> Unit, modifier: Modifier = Modifier,
    // The Now/Next guide, rendered at the far end of the same strip.
    trailing: (@Composable () -> Unit)? = null,
    // The clock. Rendered between two equal-weight halves so it lands on the true centre of the screen
    // whatever the channel name and guide card happen to be doing on either side of it.
    centre: (@Composable () -> Unit)? = null,
    // One more line under the channel name (a Sports match's score).
    below: (@Composable () -> Unit)? = null,
) {
    // Reactive meta so the title row updates instantly on a channel zap (the plain vars aren't observed).
    val meta by player.currentMeta.collectAsStateWithLifecycle()
    val displayTitle = meta.title?.takeIf { it.isNotBlank() } ?: ""
    Row(modifier = modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.Top) {
      // Left half. Equal weight to the right half, so [centre] sits on the real midpoint of the screen
      // rather than the midpoint of whatever space the title happened to leave over.
      Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
        CircleButton(MyOwnTVIcon.BACK, size = 40, onClick = onBack)
        Spacer(Modifier.width(14.dp))
        // The channel logo sits with the channel NAME (identity), not with the programme — so the
        // whole "which channel am I on" group reads as one unit however wide the TV is.
        ChannelLogo(meta.logoUrl, displayTitle, size = 46)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            // The technical chips stack ABOVE the channel name.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chips.forEachIndexed { i, label ->
                    if (i > 0) Box(Modifier.size(3.dp).clip(CircleShape).background(hudInk.copy(alpha = 0.3f)))
                    Text(label, style = tv.own.myowntv.ui.theme.stageText(22, 700), color = hudInk.copy(alpha = 0.5f))
                }
                // The LIVE badge has left the top bar: live status is the stateful badge at the right
                // end of the dock's band A, beside the picture it describes.
            }
            Spacer(Modifier.height(2.dp))
            // Channel number ahead of the name — this is where you look to learn the number of a
            // channel you arrived at by zapping. meta.subtitle carries it ("#123") only while the
            // "Channel numbers" setting is on, so an off setting leaves the name alone.
            Row(verticalAlignment = Alignment.CenterVertically) {
                meta.subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = tv.own.myowntv.ui.theme.stageText(32, 600),
                        color = hudInk.copy(alpha = 0.45f),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(displayTitle, style = tv.own.myowntv.ui.theme.stageText(32, 600), color = hudInk, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            below?.invoke()
        }
      } // end left half
      if (centre != null) {
          Spacer(Modifier.width(20.dp))
          centre()
          Spacer(Modifier.width(20.dp))
      }
      // Right half, pushed to the far edge. Same weight as the left, hence the true centring above.
      Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.Top) {
          if (trailing != null) trailing()
      }
    }
}

/** The channel logo tile, falling back to the first letters of the channel name. */
@Composable
private fun ChannelLogo(logoUrl: String?, title: String?, size: Int, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF004F46)),
        contentAlignment = Alignment.Center,
    ) {
        if (!logoUrl.isNullOrBlank()) AsyncImage(model = logoUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
        else Text((title ?: "?").take(3).uppercase(), style = tv.own.myowntv.ui.theme.stageText(22, 700), color = Color(0xFF6FF8E4), fontWeight = FontWeight.Bold)
    }
}

/** The player's channel OSD: channel logo beside its name and number. */
@Composable
internal fun ChannelOsdCard(
    title: String?,
    subtitle: String?,
    logoUrl: String?,
    modifier: Modifier = Modifier,
    /** Now/next under the name on a zap; null (or a channel with no guide) keeps it name-only. */
    guide: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.widthIn(max = if (guide != null) 420.dp else 340.dp).clip(RoundedCornerShape(14.dp)).background(hudShade(0.55f)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF004F46)), contentAlignment = Alignment.Center) {
            if (!logoUrl.isNullOrBlank()) AsyncImage(model = logoUrl, contentDescription = null, modifier = Modifier.fillMaxSize())
            else Text((title ?: "?").take(3).uppercase(), style = tv.own.myowntv.ui.theme.stageText(22, 700), color = Color(0xFF6FF8E4), fontWeight = FontWeight.Bold)
        }
        Column {
            Text(title ?: "", style = tv.own.myowntv.ui.theme.stageText(25, 500), color = hudInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = tv.own.myowntv.ui.theme.stageText(20, 500), color = hudInk.copy(alpha = 0.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            guide?.invoke()
        }
    }
}

@Composable
internal fun ChannelCard(player: PlaybackEngine, modifier: Modifier = Modifier, guide: (@Composable () -> Unit)? = null) {
    // Collect the reactive meta so the card refreshes the instant a zap changes the channel.
    val meta by player.currentMeta.collectAsStateWithLifecycle()
    ChannelOsdCard(title = meta.title?.takeIf { it.isNotBlank() }, subtitle = meta.subtitle, logoUrl = meta.logoUrl, modifier = modifier, guide = guide)
}

/** Direct-tune entry OSD: the number as it's typed, on the same surface (position, radius, scrim) the
 *  channel card uses, so a resolved number simply becomes that card. A blinking caret says "still
 *  accepting digits" and the bar along the bottom drains over the auto-submit window, so the wait is
 *  visible instead of mysterious. [error] turns it into the failure readout for the same number. */
@Composable
internal fun ChannelNumberCard(digits: String, error: String? = null, modifier: Modifier = Modifier) {
    // Animations Off: a steady caret, with no infinite transition started (never a 0 ms one).
    val caretAlpha = if (!animationsOn) 1f else {
        val caret = rememberInfiniteTransition(label = "tuneCaret")
        val blink by caret.animateFloat(
            initialValue = 1f, targetValue = 0f,
            animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
            label = "tuneCaretAlpha",
        )
        blink
    }
    val countdown = remember { Animatable(0f) }
    // Captured before the draw lambda: a DrawScope is not a composable, so it can't read the theme.
    val accent = hudAccent
    LaunchedEffect(digits, error) {
        if (error != null) { countdown.snapTo(0f); return@LaunchedEffect }
        countdown.snapTo(1f)
        countdown.animateTo(0f, tween(DIRECT_TUNE_TIMEOUT_MS.toInt(), easing = LinearEasing))
    }
    val track = hudInk.copy(alpha = 0.08f)
    Column(
        modifier.widthIn(min = 148.dp, max = 340.dp).clip(RoundedCornerShape(14.dp)).background(hudShade(0.55f))
            // Painted, not laid out: a real bar would fillMaxWidth and stretch the card to its max width.
            .drawWithContent {
                drawContent()
                val barHeight = 3.dp.toPx()
                val top = Offset(0f, size.height - barHeight)
                drawRect(track, topLeft = top, size = Size(size.width, barHeight))
                drawRect(accent, topLeft = top, size = Size(size.width * countdown.value, barHeight))
            }
            .padding(bottom = 3.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 20.dp, top = 12.dp, bottom = 12.dp)) {
            Text(
                stringResource(R.string.player_channel_label),
                style = tv.own.myowntv.ui.theme.stageText(20, 500), color = accent, fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    digits,
                    style = tv.own.myowntv.ui.theme.stageText(43, 400), color = hudInk, fontWeight = FontWeight.Bold,
                    letterSpacing = 3.sp,
                )
                if (error == null) {
                    Box(
                        Modifier.padding(start = 4.dp, bottom = 4.dp).width(3.dp).height(22.dp)
                            .clip(RoundedCornerShape(2.dp)).background(accent.copy(alpha = caretAlpha)),
                    )
                }
            }
            error?.let {
                Text(it, style = tv.own.myowntv.ui.theme.stageText(22, 700), color = Color(0xFFFF8A80), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ---------------- Center transport ----------------

@Composable
internal fun CenterControls(
    player: PlaybackEngine, isPlaying: Boolean,
    playFocus: FocusRequester, modifier: Modifier = Modifier,
) {
    // A live stream has no timeline to step along: the transport is the one button that acts on it —
    // pause the picture (catching up happens by re-tuning, not by rewinding).
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        TransportCapsule {
            CircleButton(if (isPlaying) MyOwnTVIcon.PAUSE else MyOwnTVIcon.PLAY, size = 64, primary = true, modifier = Modifier.focusRequester(playFocus)) { player.togglePlayPause() }
        }
    }
}

// ---------------- Bottom bar ----------------

@Composable
internal fun BottomBar(
    player: PlaybackEngine,
    volume: Int, audioCount: Int, subCount: Int, zoomMode: ZoomMode,
    onInfo: (() -> Unit)? = null, infoOn: Boolean = false, onReport: (() -> Unit)? = null,
    favorite: Boolean = false, onToggleFavorite: (() -> Unit)? = null,
    onPreviousChannel: (() -> Unit)? = null,
    onOtherChannels: (() -> Unit)? = null, otherChannelsCount: Int = 0,
    hasSources: Boolean = false,
    onOpenDialog: (HudDialog) -> Unit, onPip: (() -> Unit)?, onAudioMode: (() -> Unit)?,
    onMultiview: (() -> Unit)? = null, modifier: Modifier = Modifier,
) {
    // Only whether a sleep timer runs: the countdown itself ticks every second, and this bar must not.
    val sleepLeft = org.koin.compose.koinInject<SleepTimer>().remainingMs.collectAsStateWithLifecycle()
    val sleepRunning by androidx.compose.runtime.remember { androidx.compose.runtime.derivedStateOf { sleepLeft.value != null } }
    Dock(modifier = modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 20.dp)) {
        // Band A — the instrument. A live stream has no timeline; its state is the badge, at the
        // right end of the band, beside the picture it describes.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { LiveStateBadge() }
        Spacer(Modifier.height(10.dp))
        // Band B — the tools. Each cluster hugs its own screen edge and the gap between them is the
        // slack a focused button expands into, so growth is always toward the centre: the left cluster
        // pushes only the buttons to its right, the right cluster only those to its left. Walk either
        // cluster outward-in and nothing you have already passed ever moves.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().focusGroup()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // The ORDER comes from core's canonical list, not from the order these lines
                // happen to be written in. Each control keeps its own composable and its own
                // condition; only the sequence is shared, which is what makes this bar and the
                // phone's read alike. The `when` is exhaustive, so a control added to the shared
                // list cannot be silently missing here.
                PlayerControl.clusterFor(tv = true, cluster = ControlCluster.MEDIA).forEach { control ->
                    when (control) {
                        PlayerControl.VOLUME ->
                            CtrlButton(volumeIcon(volume), label = stringResource(R.string.player_tool_volume)) { onOpenDialog(HudDialog.VOLUME) }
                        PlayerControl.SUBTITLES ->
                            CtrlButton(MyOwnTVIcon.SUBTITLE, badge = subCount.takeIf { it > 0 }, label = stringResource(R.string.player_tool_subtitles)) { onOpenDialog(HudDialog.SUBS) }
                        PlayerControl.AUDIO ->
                            CtrlButton(MyOwnTVIcon.AUDIO, badge = audioCount.takeIf { it > 1 }, label = stringResource(R.string.player_tool_audio)) { onOpenDialog(HudDialog.AUDIO) }
                        // Favorite the current channel without leaving the stream (coral heart = on,
                        // the same colour the marker has in browse rows).
                        PlayerControl.FAVOURITE -> if (onToggleFavorite != null) {
                            CtrlButton(MyOwnTVIcon.FAVORITE, active = favorite, activeTint = OwnTVTheme.colors.favorite, label = stringResource(R.string.player_tool_favorite)) { onToggleFavorite() }
                        }
                        // Back to the channel watched before; present only once there is one.
                        PlayerControl.PREVIOUS_CHANNEL -> if (onPreviousChannel != null) {
                            CtrlButton(MyOwnTVIcon.HISTORY, label = stringResource(R.string.player_previous_channel)) { onPreviousChannel() }
                        }
                        // Sports Hub: only for a channel started from a match.
                        PlayerControl.OTHER_CHANNELS -> if (onOtherChannels != null) {
                            CtrlButton(MyOwnTVIcon.LIST, label = stringResource(R.string.sports_other_channels_count, otherChannelsCount)) { onOtherChannels() }
                        }
                        // Not shown on the TV: Back from the player already returns to the match page.
                        PlayerControl.MATCH -> Unit
                        // The same channel in another quality, category or playlist; the
                        // list is read when it opens.
                        PlayerControl.SOURCES -> if (hasSources) {
                            CtrlButton(MyOwnTVIcon.PLAYLIST, label = stringResource(R.string.player_tool_sources)) { onOpenDialog(HudDialog.SOURCES) }
                        }
                        // Belongs to the tools cluster, or to controls the live-only player never
                        // routes here; `clusterFor` does not hand them to this loop.
                        PlayerControl.BRIGHTNESS, PlayerControl.CHANNEL_LIST,
                        PlayerControl.ASPECT, PlayerControl.QUALITY, PlayerControl.MINI_PLAYER, PlayerControl.AUDIO_ONLY,
                        PlayerControl.MULTIVIEW, PlayerControl.SLEEP_TIMER,
                        PlayerControl.INFO, PlayerControl.REPORT,
                        PlayerControl.GO_LIVE,
                        -> Unit
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // The ORDER is core's, exactly as in the media cluster above. Each control
                // keeps its own composable and condition; only the sequence is shared.
                PlayerControl.clusterFor(tv = true, cluster = ControlCluster.TOOLS).forEach { control ->
                    when (control) {
                        // Aspect/zoom sizes the one video surface itself — the view is scaled, never
                        // the decoder (see ExoPreviewSurface and Modifier.videoZoom).
                        // A press steps to the next mode (named beside the label, so each press shows
                        // where it landed); OK held opens the whole list.
                        PlayerControl.ASPECT ->
                            CtrlButton(
                                MyOwnTVIcon.ASPECT,
                                active = zoomMode != ZoomMode.FIT,
                                label = stringResource(R.string.player_tool_aspect),
                                value = stringResource(zoomMode.labelRes),
                                onLongClick = { onOpenDialog(HudDialog.ZOOM) },
                            ) { player.setZoomModeByUser(ZoomMode.entries[(zoomMode.ordinal + 1) % ZoomMode.entries.size]) }
                        // Only when this stream offers several; tinted while a pick overrides Auto.
                        PlayerControl.QUALITY -> {
                            val qualities by player.videoQualities.collectAsStateWithLifecycle()
                            val pick by player.videoQualityPick.collectAsStateWithLifecycle()
                            if (qualities.isNotEmpty()) {
                                CtrlButton(MyOwnTVIcon.VIDEO, active = pick != null, label = stringResource(R.string.player_tool_quality)) { onOpenDialog(HudDialog.QUALITY) }
                            }
                        }
                        PlayerControl.MINI_PLAYER -> if (onPip != null) {
                            CtrlButton(MyOwnTVIcon.PIP, label = stringResource(R.string.player_tool_mini)) { onPip() }
                        }
                        PlayerControl.AUDIO_ONLY -> if (onAudioMode != null) {
                            CtrlButton(MyOwnTVIcon.HEADPHONES, label = stringResource(R.string.player_tool_audio_only)) { onAudioMode() }
                        }
                        // Live only, and only once the user has switched Multiview on: four tiles
                        // from the channel already playing. Next to the mini-player button, which is
                        // its nearest relative.
                        PlayerControl.MULTIVIEW -> if (onMultiview != null) {
                            CtrlButton(MyOwnTVIcon.LIST_GRID, label = stringResource(R.string.multiview_button)) { onMultiview() }
                        }
                        // Stop after a while; tinted while a countdown is running.
                        PlayerControl.SLEEP_TIMER ->
                            CtrlButton(MyOwnTVIcon.BEDTIME, active = sleepRunning, label = stringResource(R.string.player_sleep_timer)) { onOpenDialog(HudDialog.SLEEP_TIMER) }
                        // Stream technical info (codec/res/HDR/bitrate/decoder/audio/buffer) —
                        // toggles the overlay. Parked at the far right, where the redundant
                        // exit-fullscreen button used to sit (Back already leaves the player, so
                        // that button never did anything the remote couldn't).
                        PlayerControl.INFO -> if (onInfo != null) {
                            CtrlButton(MyOwnTVIcon.INFO, active = infoOn, label = stringResource(R.string.player_tool_info)) { onInfo() }
                        }
                        // "Report this stream": copies the readout the user is looking at into the
                        // playback log, so a "this channel judders" complaint carries the
                        // codec/decoder/bitrate that caused it. Only offered while the info overlay
                        // is open — there is nothing to report otherwise, and the bar stays as short
                        // as it was for everyone who never needs this. That is the rule on both
                        // apps.
                        PlayerControl.REPORT -> if (infoOn && onReport != null) {
                            CtrlButton(MyOwnTVIcon.SHARE, label = stringResource(R.string.player_tool_report)) { onReport() }
                        }
                        // Belongs to the media cluster, or is a control this player never draws;
                        // `clusterFor` does not hand them to this loop.
                        PlayerControl.GO_LIVE, PlayerControl.VOLUME, PlayerControl.BRIGHTNESS,
                        PlayerControl.SUBTITLES, PlayerControl.AUDIO,
                        PlayerControl.FAVOURITE, PlayerControl.PREVIOUS_CHANNEL,
                        PlayerControl.OTHER_CHANNELS, PlayerControl.MATCH, PlayerControl.SOURCES,
                        PlayerControl.CHANNEL_LIST,
                        -> Unit
                    }
                }
            }
        }
    }
}

private fun volumeIcon(volume: Int): MyOwnTVIcon = when {
    volume == 0 -> MyOwnTVIcon.VOLUME_MUTE
    volume < 50 -> MyOwnTVIcon.VOLUME_LOW
    else -> MyOwnTVIcon.VOLUME_HIGH
}

