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

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import tv.own.myowntv.ui.components.longPressMenuGuard
import tv.own.myowntv.R
import tv.own.owntv.player.PlaybackEngine
import tv.own.owntv.player.ScreenOff
import tv.own.owntv.player.SleepTimer
import tv.own.owntv.player.TrackOption
import tv.own.owntv.player.ZoomMode
import tv.own.myowntv.ui.theme.mpx
import tv.own.myowntv.ui.theme.stageText
import tv.own.myowntv.ui.theme.stageAccent
import tv.own.myowntv.ui.theme.StageColors
import tv.own.myowntv.ui.components.displayLabel

/**
 * Every dialog the HUD opens — tracks, zoom, quality, volume, sources and the sleep timer — plus the
 * scaffold and rows they share. Split out of [PlayerHud]; behaviour unchanged.
 */

/** A/V-sync nudge step, kept identical to the Settings stepper so a value found in the player can be
 *  reproduced there exactly. */
private const val AV_SYNC_STEP_MS = 25

@Composable
internal fun TrackDialog(
    title: String,
    tracks: List<TrackOption>,
    onSelect: (TrackOption) -> Unit,
    onOff: (() -> Unit)?,
    onDismiss: () -> Unit,
    audioDelayMs: Int? = null,                 // non-null on the Audio dialog → show the A/V-sync nudge
    onAdjustAudioDelay: ((Int) -> Unit)? = null,
    // Whether this channel already has a remembered A/V-sync offset, and the action that remembers
    // or forgets it. Both accompany [onAdjustAudioDelay].
    audioDelayRemembered: Boolean = false,
    onToggleRememberAudioDelay: (() -> Unit)? = null,
) {
    val focus = remember { FocusRequester() }
    BackHandler { onDismiss() }
    // Open with focus on the CURRENTLY-selected track (so re-opening to change it lands on the right row),
    // else the "Off" row if nothing's selected, else the first track. The requestFocus must run from
    // INSIDE the target row (below) — a top-level LaunchedEffect fires before the LazyColumn has composed
    // that row, so requestFocus would throw "not initialized" and focus would fall back to the first item.
    val selectedIndex = tracks.indexOfFirst { it.selected }
    val focusOff = onOff != null && selectedIndex < 0
    // Safety net: the per-row one-shot requestFocus below can fire while the dialog window is still
    // mid-transition (seen on HDR/HDR10/DTS streams, whose surface re-layout delays window focus) or
    // before the engine has reported the tracks at all — leaving the dialog with NO focused row and
    // the D-pad locked out. Retry over a few frames, and re-run whenever the track list (re)arrives.
    // The selected row can sit beyond the LazyColumn viewport (e.g. subtitle 11 of 20): it never
    // composes, its focusRequester never attaches, and focus falls back to the first row ("Off").
    // Scroll it into view before requesting focus.
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(tracks.size, focusOff) {
        val target = if (selectedIndex >= 0) selectedIndex + (if (onOff != null) 1 else 0) else 0
        repeat(10) {
            androidx.compose.runtime.withFrameNanos { }
            if (selectedIndex >= 0) runCatching { listState.scrollToItem(target) }
            if (runCatching { focus.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
            delay(50)
        }
    }
    DialogScaffold(title = title, onDismiss = onDismiss, state = listState) {
        if (tracks.isEmpty() && onOff == null) {
            item { Text(stringResource(R.string.player_no_tracks), style = stageText(18, 400), color = StageColors.Muted, modifier = Modifier.padding(vertical = 10.mpx)) }
        }
        if (onOff != null) {
            item {
                if (focusOff) LaunchedEffect(Unit) { androidx.compose.runtime.withFrameNanos {}; runCatching { focus.requestFocus() } }
                OptionRow(label = stringResource(R.string.common_off), selected = selectedIndex < 0, modifier = if (focusOff) Modifier.focusRequester(focus) else Modifier, onClick = onOff)
            }
        }
        items(tracks.size) { index ->
            val track = tracks[index]
            val focusThis = index == selectedIndex || (selectedIndex < 0 && onOff == null && index == 0)
            if (focusThis) LaunchedEffect(Unit) { androidx.compose.runtime.withFrameNanos {}; runCatching { focus.requestFocus() } }
            OptionRow(
                // Image-based subs (PGS/VOBSUB/DVB) are rendered by ExoPlayer on its own layer —
                // mark them so it's clear they're a different kind of track, but they're fully selectable.
                label = if (!track.image) track.displayLabel() else stringResource(R.string.player_image_track, track.displayLabel()),
                selected = track.selected,
                modifier = if (focusThis) Modifier.focusRequester(focus) else Modifier,
                onClick = { onSelect(track) },
            )
        }
        // A/V-sync nudge (audio dialog) — fixes a stream arriving with the provider's own drift,
        // or a display whose picture-processing delay pushes the image behind the sound.
        if (onAdjustAudioDelay != null) {
            item {
                // 25 ms steps, matching Settings: what this corrects is the display's own picture-processing
                // delay, which lands in the tens of milliseconds — 50 ms could bracket it but not hit it.
                StepperRow(
                    title = stringResource(R.string.player_av_sync),
                    value = formatDelay(audioDelayMs ?: 0),
                    step = AV_SYNC_STEP_MS,
                    canStep = { it in -5_000..5_000 },
                    current = audioDelayMs ?: 0,
                    onStep = onAdjustAudioDelay,
                )
            }
            // Lip-sync error belongs to the stream, not to the user: this keeps the offset for THIS
            // channel, so it comes back next time without following you onto anything else.
            if (onToggleRememberAudioDelay != null) {
                item {
                    tv.own.myowntv.ui.stage.StagePopupOption(
                        title = stringResource(R.string.player_av_sync_remember),
                        onClick = onToggleRememberAudioDelay,
                        leading = { f -> tv.own.myowntv.ui.stage.StagePopupCheck(audioDelayRemembered, f) },
                    )
                }
            }
        }
    }
}

/**
 * Requests [focus] with retries: dialog-window content composes a frame or two after the calling
 * effect starts, so a one-shot requestFocus can fire before the target row exists and silently fail.
 */
private suspend fun requestFocusRetrying(focus: FocusRequester) {
    repeat(10) {
        androidx.compose.runtime.withFrameNanos {}
        if (runCatching { focus.requestFocus() }.getOrDefault(false)) return
        delay(50)
    }
}

@Composable
private fun formatDelay(ms: Int): String = when {
    ms == 0 -> stringResource(R.string.player_delay_zero)
    ms > 0 -> stringResource(R.string.player_delay_positive, ms)
    else -> stringResource(R.string.player_delay_negative, ms)
}

/**
 * The sleep timer's choices, in the television's dialog: Off while one is running, the shared minute
 * choices, and "End of programme" only when the guide says when that is.
 * The title turns into the countdown while a timer runs, so re-opening it says what is set.
 */
@Composable
internal fun SleepTimerDialog(
    timer: SleepTimer,
    /** When the programme on air ends; null offers no such row. */
    programmeEndMs: Long?,
    onDismiss: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { requestFocusRetrying(focus) }
    BackHandler { onDismiss() }
    val remaining by timer.remainingMs.collectAsStateWithLifecycle()
    val title = remaining?.let {
        stringResource(R.string.player_sleep_timer_remaining, stringResource(R.string.player_duration_minutes, SleepTimer.minutesLeft(it)))
    } ?: stringResource(R.string.player_sleep_timer)
    // Read once, as the dialog opens: the rows must not change under the D-pad while it is up.
    val running = remember { remaining != null }
    DialogScaffold(title = title, onDismiss = onDismiss) {
        if (running) {
            item {
                OptionRow(
                    label = stringResource(R.string.common_off),
                    selected = false,
                    modifier = Modifier.focusRequester(focus),
                    onClick = { timer.cancel(); onDismiss() },
                )
            }
        }
        items(SleepTimer.CHOICES_MINUTES.size) { index ->
            val minutes = SleepTimer.CHOICES_MINUTES[index]
            OptionRow(
                label = stringResource(R.string.player_duration_minutes, minutes),
                selected = false,
                modifier = if (!running && index == 0) Modifier.focusRequester(focus) else Modifier,
                onClick = { timer.start(minutes * 60_000L); onDismiss() },
            )
        }
        programmeEndMs?.takeIf { it > System.currentTimeMillis() }?.let { endMs ->
            item {
                OptionRow(
                    label = stringResource(R.string.player_sleep_timer_end_of_programme),
                    selected = false,
                    onClick = { timer.start(endMs - System.currentTimeMillis()); onDismiss() },
                )
            }
        }
        item { ScreenOffRow() }
    }
}

/**
 * "Also turn off the screen": ticked is the system grant itself ([ScreenOff]), so it is re-read after
 * the system screen answers rather than stored. A set with no such screen says so instead.
 */
@Composable
private fun ScreenOffRow(screenOff: ScreenOff = koinInject()) {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(screenOff.isAllowed()) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { allowed = screenOff.isAllowed() }
    tv.own.myowntv.ui.stage.StagePopupOption(
        title = stringResource(R.string.player_sleep_timer_screen_off),
        leading = { f -> tv.own.myowntv.ui.stage.StagePopupCheck(allowed, f) },
        onClick = {
            if (allowed) {
                screenOff.revoke()
                allowed = false
            } else {
                runCatching { tv.own.myowntv.features.pip.startAsHandOff(screenOff.requestIntent()) { ask.launch(it) } }.onFailure {
                    android.widget.Toast.makeText(context, R.string.player_sleep_timer_screen_off_unavailable, android.widget.Toast.LENGTH_LONG).show()
                }
            }
        },
    )
}

@Composable
internal fun ZoomDialog(current: ZoomMode, onSelect: (ZoomMode) -> Unit, onDismiss: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { requestFocusRetrying(focus) }
    BackHandler { onDismiss() }
    // Land focus on the current mode (not always the first row) so re-opening starts on your selection.
    val selectedIndex = ZoomMode.entries.indexOf(current).coerceAtLeast(0)
    // Opened by holding OK on the aspect button: the hold's repeats must not pick the focused row.
    DialogScaffold(
        title = stringResource(R.string.settings_player_zoom),
        onDismiss = onDismiss,
        modifier = Modifier.longPressMenuGuard(),
    ) {
        items(ZoomMode.entries.size) { index ->
            val mode = ZoomMode.entries[index]
            OptionRow(label = stringResource(mode.labelRes), selected = mode == current, modifier = if (index == selectedIndex) Modifier.focusRequester(focus) else Modifier, onClick = { onSelect(mode) })
        }
    }
}

/** Auto (Settings → Maximum video quality), then every height this stream offers, highest first. */
/**
 * "Sources": the playing channel and its other versions, best picture first, the one on screen ringed.
 * Each row names the version, then its category and playlist, with the provider's quality marks at the
 * end. Opens once the list is read (a quick indexed lookup, then cached for the channel).
 */
@Composable
internal fun SourcesDialog(sources: HudSources, onDismiss: () -> Unit) {
    val versions by produceState<List<tv.own.owntv.core.live.ChannelVersion>?>(null) { value = sources.load() }
    val list = versions ?: return
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { requestFocusRetrying(focus) }
    BackHandler { onDismiss() }
    val separator = stringResource(R.string.content_epg_bits_separator)
    val focusIndex = list.indexOfFirst { it.channel.id == sources.playingId }.coerceAtLeast(0)
    DialogScaffold(title = stringResource(R.string.player_tool_sources), onDismiss = onDismiss) {
        items(list.size) { index ->
            val v = list[index]
            val playing = v.channel.id == sources.playingId
            tv.own.myowntv.ui.stage.StagePopupOption(
                title = v.name,
                subtitle = listOfNotNull(v.categoryName, v.playlistName).filter { it.isNotBlank() }.distinct().joinToString(separator).ifEmpty { null },
                value = v.tags.joinToString(" ").ifEmpty { null },
                chosen = playing,
                modifier = if (index == focusIndex) Modifier.focusRequester(focus) else Modifier,
                leading = { f -> tv.own.myowntv.ui.stage.StagePopupRadio(playing, f) },
                onClick = { onDismiss(); if (!playing) sources.onPick(v.channel) },
            )
        }
        // Only the channel itself: say so, rather than leave a list of one that looks like a fault.
        if (list.size <= 1) item {
            tv.own.myowntv.ui.stage.StagePopupOption(title = stringResource(R.string.player_sources_none), onClick = onDismiss, enabled = false)
        }
    }
}

@Composable
internal fun QualityDialog(heights: List<Int>, current: Int?, onSelect: (Int?) -> Unit, onDismiss: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { requestFocusRetrying(focus) }
    BackHandler { onDismiss() }
    val options: List<Int?> = listOf<Int?>(null) + heights
    val selectedIndex = options.indexOf(current).coerceAtLeast(0)
    DialogScaffold(title = stringResource(R.string.player_tool_quality), onDismiss = onDismiss) {
        items(options.size) { index ->
            val height = options[index]
            OptionRow(
                label = if (height == null) stringResource(R.string.settings_auto) else stringResource(R.string.settings_video_quality_lines, height),
                selected = height == current,
                modifier = if (index == selectedIndex) Modifier.focusRequester(focus) else Modifier,
                onClick = { onSelect(height) },
            )
        }
    }
}

@Composable
internal fun VolumeDialog(player: PlaybackEngine, onDismiss: () -> Unit) {
    val volume by player.volume.collectAsStateWithLifecycle()
    // Mute the channel and "–" disables; without the shared guard focus died there (see
    // [tv.own.myowntv.ui.components.rememberStepperFocus]).
    val steppers = tv.own.myowntv.ui.components.rememberStepperFocus(
        plusEnabled = volume < 150,
        minusEnabled = volume > 0,
    )
    // Real dialog window for the same focus isolation as DialogScaffold (see there).
    tv.own.myowntv.ui.stage.StagePopup(
        onDismiss = onDismiss, title = stringResource(R.string.player_volume), eyebrow = null, width = 640.mpx,
        buttons = {
            tv.own.myowntv.ui.stage.StageButton(stringResource(if (volume == 0) R.string.player_unmute else R.string.player_mute), onClick = { player.toggleMute() }, height = 56.mpx, textSize = 19)
            tv.own.myowntv.ui.stage.StageButton(stringResource(R.string.common_done), onClick = onDismiss, height = 56.mpx, textSize = 19, tinted = true)
        },
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.mpx, Alignment.CenterHorizontally)) {
            StepButton(stringResource(R.string.common_minus), enabled = volume > 0, modifier = Modifier.focusRequester(steppers.minus)) { player.adjustVolumeByUser(-5) }
            Text(stringResource(R.string.player_percent, volume), style = stageText(44, 800), color = stageAccent.accent, modifier = Modifier.width(160.mpx), textAlign = TextAlign.Center)
            StepButton(stringResource(R.string.common_plus), enabled = volume < 150, modifier = Modifier.focusRequester(steppers.plus)) { player.adjustVolumeByUser(5) }
        }
    }
}

/**
 * The settings-row stepper in a popup: the row holds focus, ◀ ▶ move [current] by [step] while
 * [canStep] allows the result, and the value sits on the right as "− 0 ms +".
 */
@Composable
private fun StepperRow(title: String, value: String, step: Int, canStep: (Int) -> Boolean, current: Int, onStep: (Int) -> Unit, onClick: () -> Unit = {}) {
    tv.own.myowntv.ui.stage.StagePopupOption(
        title = title,
        onClick = onClick,
        modifier = Modifier.onPreviewKeyEvent { e ->
            val delta = when (e.key) {
                Key.DirectionLeft -> -step
                Key.DirectionRight -> step
                else -> return@onPreviewKeyEvent false
            }
            if (e.type == KeyEventType.KeyDown && canStep(current + delta)) onStep(delta)
            true
        },
        trailing = { tv.own.myowntv.ui.stage.StageStepper(value) },
    )
}

/** A round − / + on the volume popup: a Stage button, dimmed while it can go no further. */
@Composable
private fun StepButton(label: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    tv.own.myowntv.ui.stage.StageButton(label, onClick = { if (enabled) onClick() }, round = true, height = 72.mpx, textSize = 30, modifier = modifier.alpha(if (enabled) 1f else 0.45f))
}

/**
 * The player's pick-one popups (tracks, zoom, quality, sources, sleep timer) as Stage popups: the title,
 * then `.opt` rows. A REAL dialog window, not an in-place overlay: it owns the D-pad focus scope, so
 * nothing in the HUD behind it (play button, catch-all focusable, stream-info chips) can compete for or
 * steal focus — which is what intermittently locked the subtitle/audio pickers out of focus on
 * codec-heavy (HDR/DTS) streams. Back is handled by the window itself via onDismissRequest.
 */
@Composable
private fun DialogScaffold(
    title: String,
    onDismiss: () -> Unit,
    // Applied to the list, so it sees the rows' keys first (a guard against a still-held OK).
    modifier: Modifier = Modifier,
    state: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    tv.own.myowntv.ui.stage.StagePopup(onDismiss = onDismiss, title = title, eyebrow = null, width = 640.mpx, scroll = false) {
        LazyColumn(state = state, modifier = Modifier.weight(1f, fill = false).then(modifier), verticalArrangement = Arrangement.spacedBy(4.mpx), content = content)
    }
}

/** `.opt` with a radio: one of a list, the current one ringed. */
@Composable
private fun OptionRow(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    tv.own.myowntv.ui.stage.StagePopupOption(
        title = label, onClick = onClick, modifier = modifier, chosen = selected,
        leading = { f -> tv.own.myowntv.ui.stage.StagePopupRadio(selected, f) },
    )
}

