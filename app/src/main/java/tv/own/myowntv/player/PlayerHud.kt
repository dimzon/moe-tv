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

import tv.own.myowntv.ui.theme.hudShade
import tv.own.myowntv.ui.theme.hudInk
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import tv.own.myowntv.R
import tv.own.owntv.core.i18n.HorizontalDirection
import tv.own.owntv.core.i18n.horizontalDirection
import tv.own.owntv.core.settings.PlayerRemoteOptions
import tv.own.owntv.core.settings.RemoteShortcutAction
import tv.own.owntv.core.settings.RemoteShortcutPress
import tv.own.myowntv.ui.components.LocalRemoteShortcuts
import tv.own.myowntv.ui.components.MyOwnTVIcon
import tv.own.myowntv.ui.components.OwnTVSpinner
import tv.own.myowntv.ui.components.displayText // PlayerFailureReason.displayText, for the error overlay
import tv.own.myowntv.ui.theme.gradientWash

/**
 * The full-screen player HUD. This file owns the HUD's STATE — visibility, direct tune, the dialog the
 * user has open, the zap/engine flashes — and composes the pieces that draw it. Those pieces live in
 * sibling files: [PlayerHudChrome] (top bar, OSD cards, transport, bottom bar), [PlayerHudControls]
 * (buttons, sliders, shared formatters) and [PlayerHudDialogs] (every dialog the HUD opens).
 */

@Composable
private fun PlaybackFailure.displayText(): String {
    // Resolved through the same [describe] mapping the toast renderer uses. LocalConfiguration is read
    // so a language change still recomposes this, exactly as stringResource would.
    androidx.compose.ui.platform.LocalConfiguration.current
    val resources = androidx.compose.ui.platform.LocalContext.current.resources
    return describe { id, args -> resources.getString(id, *args.toTypedArray()) }
}

internal const val DIRECT_TUNE_TIMEOUT_MS = 2_000L
private const val DIRECT_TUNE_FEEDBACK_MS = 1_500L
private const val DIRECT_TUNE_PLAYBACK_WAIT_MS = 8_000L
private const val MAX_DIRECT_TUNE_DIGITS = 5
private const val PLAYER_SHORTCUT_LONG_PRESS_MS = 600L

/** Re-poll for late-arriving audio/subtitle tracks while the track menu is open and still empty.
 *  20 × 300 ms = 6 s, comfortably past the slowest observed HDR/DTS track report, then it stops. */
private const val TRACK_POLL_MS = 300L
private const val TRACK_POLL_TRIES = 20

/** A stall shorter than this is an ordinary re-buffer and gets the plain spinner. Past it, a line under the
 *  spinner says the stream is reconnecting by itself, so a long outage doesn't look like a hung app.
 *  Counted from the stall, which itself only starts once the buffer (~10s) has run dry. */
private const val RECONNECT_NOTICE_AFTER_MS = 10_000L

internal enum class HudDialog { NONE, AUDIO, SUBS, ZOOM, QUALITY, VOLUME, SLEEP_TIMER, SOURCES }

/**
 * "Sources": the other versions of the live channel playing ([tv.own.owntv.core.live.ChannelVersions]).
 * [load] is read as the list opens, never on a zap; [playingId] marks the one on screen; [onPick]
 * tunes the pick through Live TV's normal tune.
 */
class HudSources(
    val load: suspend () -> List<tv.own.owntv.core.live.ChannelVersion>,
    val playingId: Long,
    val onPick: (tv.own.owntv.core.database.entity.ChannelEntity) -> Unit,
)

/** What the top-left channel OSD shows for direct tune: the digits being typed, the channel a number
 *  resolved to, or a failure message. All three render as the same card as the channel OSD. */
private sealed interface TuneOsd {
    data class Entry(val digits: String) : TuneOsd
    data class Tuned(val info: DirectTuneChannelInfo) : TuneOsd
    data class Message(val digits: String, val text: String) : TuneOsd
}

@Composable
fun PlayerHud(
    player: PlaybackEngine,
    onBack: () -> Unit,
    onPip: (() -> Unit)? = null,
    // Switch to audio-only mode (stops video decode, surfaces the top-bar now-playing bar). Null hides it.
    onAudioMode: (() -> Unit)? = null,
    // Live only, and only when Multiview is switched on in Settings: turn this channel into tile 1 of
    // the grid. Null hides the button entirely — the feature is opt-in in Settings.
    onMultiview: (() -> Unit)? = null,
    // True while the shell draws an overlay ABOVE the HUD (e.g. the channel-list overlay). The HUD goes
    // inert: its auto-hide timer pauses and — crucially — it makes no focus requests, so it can't yank
    // D-pad focus off the overlay. The existing dialog guard below covers only the HUD's OWN dialogs;
    // shell-level overlays need this flag. Default false = no behavior change for other callers.
    inert: Boolean = false,
    onChannelUp: (() -> Unit)? = null,
    onChannelDown: (() -> Unit)? = null,
    // Live: open the channel-list overlay (Left while the controls are hidden). Null = not a live channel.
    onOpenChannelList: (() -> Unit)? = null,
    // Live: open the watch-history list (Right while the controls are hidden) — jump straight back to a
    // recent channel without leaving full-screen. Null = not a live channel.
    onOpenHistoryList: (() -> Unit)? = null,
    // Back to the channel watched before this one. Null hides the button (no previous channel yet).
    onPreviousChannel: (() -> Unit)? = null,
    // Direct tune: enter a provider channel number to switch channels. Null = disabled (not live / no channel).
    onTuneToNumber: (suspend (Int) -> DirectTuneResult)? = null,
    // Channel identity key for direct tune: changing this cancels any in-flight submission.
    directTuneContextKey: Long = 0L,
    remoteOptions: PlayerRemoteOptions = PlayerRemoteOptions(), // Settings › Remote shortcuts › Player buttons
    // Favorite toggle for the CURRENT item (the live channel). Null hides the button
    // (no item context). [favorite] = current state — fills the star teal when true.
    favorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    // Live guide card (Before / Now playing / Next for the playing channel) — supplied by the shell
    // (the EPG data lives in LiveViewModel, not the player). Rendered on the right edge whenever the
    // controls are visible, like the top-bar channel card; informational only, never focusable.
    liveEpgCard: (@Composable () -> Unit)? = null,
    // Now/next lines for the channel-change banner (the playing channel's guide). Null = name only.
    zapGuide: (@Composable () -> Unit)? = null,
    // The channel a CH+/- step moved to. The banner names it at once; the engine's metadata still
    // names the previous channel until the new stream opens, next to [zapGuide]'s new programme.
    zapChannel: DirectTuneChannelInfo? = null,
    // When the programme on air ends, read as the sleep timer opens. Null = no "End of programme".
    sleepProgrammeEndMs: (() -> Long?)? = null,
    // Sports Hub — only for a channel started from a match; all null means the plain Live player.
    // The match's score line under the channel name, drawn only while the controls are up.
    matchLine: (@Composable () -> Unit)? = null,
    // The other channels showing the match, and their count for the tool's label.
    onOtherChannels: (() -> Unit)? = null,
    otherChannelsCount: Int = 0,
    // The match page.
    // A goal toast for another followed match is up: OK switches to it instead of showing the controls.
    onToastOk: (() -> Unit)? = null,
    // Live only: the Sources button and its list. Null hides the button.
    sources: HudSources? = null,
    modifier: Modifier = Modifier,
) {
    // This HUD serves the one live engine: there is no position, duration or playback speed to read —
    // a live stream has no timeline, only a live edge.
    val layoutDirection = LocalLayoutDirection.current
    val isPlaying by player.isPlaying.collectAsStateWithLifecycle()
    val buffering by player.buffering.collectAsStateWithLifecycle()
    val error by player.error.collectAsStateWithLifecycle()
    val errorInfo by player.errorInfo.collectAsStateWithLifecycle()
    val providerBackOff by player.providerBackOff.collectAsStateWithLifecycle()
    val stalledSinceMs by player.stalledSinceMs.collectAsStateWithLifecycle()
    val volume by player.volume.collectAsStateWithLifecycle()
    val videoRes by player.videoRes.collectAsStateWithLifecycle()
    val streamChips by player.streamChips.collectAsStateWithLifecycle()
    val audioCount by player.audioCount.collectAsStateWithLifecycle()
    val audioDelayMs by player.audioDelayMs.collectAsStateWithLifecycle()
    val audioDelayRemembered by player.audioDelayRemembered.collectAsStateWithLifecycle()
    val subCount by player.subCount.collectAsStateWithLifecycle()
    val zoomMode by player.zoomMode.collectAsStateWithLifecycle()
    val tuneNotFound = stringResource(R.string.player_channel_not_found)
    val multipleChannels = stringResource(R.string.player_multiple_channels)
    val tuneFailed = stringResource(R.string.player_tune_failed)

    var dialog by remember { mutableStateOf(HudDialog.NONE) }
    val playFocus = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }
    val catchFocus = remember { FocusRequester() }

    var controlsVisible by remember { mutableStateOf(true) }
    var showInfo by remember { mutableStateOf(false) } // stream technical-info overlay
    // Used only by "Report this stream", which writes the current readout into the playback log.
    val reportContext = androidx.compose.ui.platform.LocalContext.current
    var wakeTick by remember { mutableIntStateOf(0) }
    val forceShow = error != null || dialog != HudDialog.NONE
    // First Back hides the controls (instead of leaving the channel); with the controls already hidden
    // this handler is disabled, so Back falls through to the shell, which exits the player. Also disabled
    // while an error/dialog is up (a dialog handles its own Back; an error should exit).
    BackHandler(enabled = controlsVisible && !forceShow) { controlsVisible = false }
    // Channel zap (live only): a brief "now watching" card on up/down without revealing the full HUD.
    val canZap = onChannelUp != null && onChannelDown != null
    var channelFlash by remember { mutableIntStateOf(0) }
    var showFlash by remember { mutableStateOf(false) }
    LaunchedEffect(channelFlash) { if (channelFlash > 0) { showFlash = true; delay(3000); showFlash = false } }

    // A brief bottom-centre toast, mirroring the channel-flash pattern above: the smoothness hint and
    // the "report saved" acknowledgement both surface through it.
    var toastMsg by remember { mutableStateOf<String?>(null) }
    var toastFlash by remember { mutableIntStateOf(0) }
    LaunchedEffect(toastFlash) { if (toastFlash > 0) { delay(1800); toastMsg = null } }
    // A channel juddering on the live engine: say once, per channel, that a retry may play it smoothly.
    // Longer than a plain toast — this one has to be read.
    val smoothnessHint by player.smoothnessHint.collectAsStateWithLifecycle()
    val smoothnessHintText = stringResource(R.string.player_smoothness_hint)
    LaunchedEffect(smoothnessHint) {
        if (smoothnessHint) {
            toastMsg = smoothnessHintText
            delay(6_000)
            if (toastMsg == smoothnessHintText) toastMsg = null
        }
    }

    // ---- Direct tune (channel-number entry) ----
    var digitBuffer by remember { mutableStateOf("") }
    var submissionRequest by remember { mutableStateOf<Int?>(null) }
    var submissionTick by remember { mutableIntStateOf(0) }

    var lookupInFlight by remember { mutableStateOf(false) }

    var tuneOsd by remember { mutableStateOf<TuneOsd?>(null) }
    var tuneOsdTick by remember { mutableIntStateOf(0) }

    val digitsActive = digitBuffer.isNotEmpty()
    val heldDigitKeys = remember { mutableSetOf<Key>() }

    val cancelDirectTune: () -> Unit = {
        digitBuffer = ""
        submissionRequest = null
        tuneOsd = null
        heldDigitKeys.clear()
        submissionTick++
        tuneOsdTick++
    }

    val zap: (Int) -> Unit = { d ->
        cancelDirectTune()
        (if (d < 0) onChannelUp else onChannelDown)?.invoke(); channelFlash++
    }

    // Restartable timeout: each new digit restarts the ~2 s window. On expiry, submit.
    LaunchedEffect(digitBuffer) {
        if (digitBuffer.isEmpty()) return@LaunchedEffect
        delay(DIRECT_TUNE_TIMEOUT_MS)
        val num = digitBuffer.toIntOrNull()
        digitBuffer = ""
        if (num != null) { submissionRequest = num; submissionTick++ }
        else tuneOsd = null
    }
    // Submission: keyed on the immutable tick so setting submissionRequest=null doesn't cancel us.
    // lookupInFlight covers only the suspend callback, not the result-display period.
    LaunchedEffect(submissionTick) {
        val num = submissionRequest ?: return@LaunchedEffect
        submissionRequest = null
        lookupInFlight = true
        val result = try {
            onTuneToNumber?.invoke(num)
        } finally {
            lookupInFlight = false
            // A KeyUp can be lost when focus or the window changes mid-entry (dialog, PiP, app switch),
            // which would strand that digit in the held set and make the key dead until the next KeyUp.
            // A completed submission ends the entry, so no held state can legitimately survive it.
            heldDigitKeys.clear()
        }
        tuneOsd = when (result) {
            is DirectTuneResult.Found -> TuneOsd.Tuned(result.channel)
            is DirectTuneResult.NotFound -> TuneOsd.Message(num.toString(), tuneNotFound)
            is DirectTuneResult.Ambiguous -> TuneOsd.Message(num.toString(), multipleChannels)
            is DirectTuneResult.Failed -> TuneOsd.Message(num.toString(), tuneFailed)
            is DirectTuneResult.Cancelled -> null
            null -> null
        }
        if (tuneOsd != null) tuneOsdTick++
    }
    // Result-feedback expiry, keyed on tuneOsdTick so a new entry invalidates the old timer. A tuned
    // channel holds the OSD until the new stream is actually on screen (the lookup returns the moment
    // playback is KICKED OFF, not when it starts) and then DIRECT_TUNE_FEEDBACK_MS longer.
    LaunchedEffect(tuneOsdTick) {
        when (val osd = tuneOsd) {
            is TuneOsd.Tuned -> {
                if (osd.info.restarted) {
                    withTimeoutOrNull(DIRECT_TUNE_PLAYBACK_WAIT_MS) {
                        // Two phases: the outgoing stream can still report playing for a beat (Stalker
                        // resolves its URL asynchronously), so wait for the teardown before the start.
                        snapshotFlow { isPlaying && !buffering && error == null }.first { !it }
                        snapshotFlow { (isPlaying && !buffering) || error != null }.first { it }
                    }
                }
                delay(DIRECT_TUNE_FEEDBACK_MS)
                tuneOsd = null
            }
            is TuneOsd.Message -> { delay(DIRECT_TUNE_FEEDBACK_MS); tuneOsd = null }
            is TuneOsd.Entry, null -> Unit
        }
    }
    // Cancellation triggers (CH+/-, D-pad, overlay open, HUD dialog open).
    LaunchedEffect(inert) { if (inert) cancelDirectTune() }
    LaunchedEffect(dialog) { if (dialog != HudDialog.NONE) cancelDirectTune() }
    // Channel-key cleanup: narrow to pending entry state only. Do not clear timed result feedback
    // from a successful tune that changed the playing channel.
    LaunchedEffect(directTuneContextKey) {
        if (digitBuffer.isNotEmpty() || submissionRequest != null) {
            digitBuffer = ""
            submissionRequest = null
            heldDigitKeys.clear()
            submissionTick++
            // Abandoned digits have no timer of their own — drop the card with the entry it belonged to.
            if (tuneOsd is TuneOsd.Entry) tuneOsd = null
        }
    }
    // Back cancels digit entry before it hides/exits controls.
    BackHandler(enabled = digitsActive) { digitBuffer = ""; tuneOsd = null }

    // Only for the "report this stream" button, whose readout is now gathered off the main thread.
    val hudScope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(forceShow) { if (forceShow) controlsVisible = true }
    LaunchedEffect(controlsVisible, player) { if (controlsVisible) player.refreshStreamChips() }
    DisposableEffect(showInfo, player) {
        if (showInfo) player.refreshStreamChips()
        player.setBitrateTrackingEnabled(showInfo)
        onDispose { player.setBitrateTrackingEnabled(false) }
    }
    LaunchedEffect(controlsVisible, wakeTick, forceShow, inert) {
        // Don't auto-hide under an overlay — hiding is what triggers the catch-all focus grab below.
        if (controlsVisible && !forceShow && !inert) { delay(4500); controlsVisible = false }
    }
    LaunchedEffect(controlsVisible, error, dialog, inert) {
        // Never steal focus while a dialog is open (its rows own it) or while a shell overlay is up
        // (inert — the overlay owns the D-pad); when either closes this re-runs and hands focus back.
        if (dialog != HudDialog.NONE || inert) return@LaunchedEffect
        if (controlsVisible) {
            when (error) {
                null -> runCatching { playFocus.requestFocus() }
                else -> runCatching { retryFocus.requestFocus() }
            }
        } else runCatching { catchFocus.requestFocus() }
    }

    // The player sits over opaque video (never a glass surface — see Glass.kt), so its HUD buttons
    // stay flat regardless of glass mode: opt out of the DIALOGS default explicitly.
    val remoteShortcuts = LocalRemoteShortcuts.current
    var shortcutKeyCode by remember { mutableIntStateOf(android.view.KeyEvent.KEYCODE_UNKNOWN) }
    var shortcutPressedAt by remember { mutableStateOf(0L) }
    val okPress = remember { PlayerOkPressTracker() }
    val dispatchPlayerShortcut: (RemoteShortcutAction) -> Unit = { action ->
        when (action) {
            RemoteShortcutAction.OPEN_SUBTITLE_CONTROLS -> { controlsVisible = true; dialog = HudDialog.SUBS }
            RemoteShortcutAction.OPEN_AUDIO_CONTROLS -> { controlsVisible = true; dialog = HudDialog.AUDIO }
            RemoteShortcutAction.OPEN_ASPECT_CONTROLS -> { controlsVisible = true; dialog = HudDialog.ZOOM }
            RemoteShortcutAction.TOGGLE_PLAYBACK_INFO -> showInfo = !showInfo
            else -> remoteShortcuts.dispatch(action)
        }
    }
    Box(
        modifier = modifier.fillMaxSize()
            .onPreviewKeyEvent { e ->
            // ---- Direct-tune digit capture (before the existing KeyDown guard) ----
            // Number keys are consumed globally here, HUD visible or not: on a TV remote a digit press
            // during live playback can only mean "tune to this channel", and swallowing both KeyDown and
            // KeyUp keeps a half-typed number from leaking into whatever else is focused underneath.
            // onTuneToNumber is null outside fullscreen live (see MyOwnTVShell), so nothing else is affected.
            if (onTuneToNumber != null && !inert && dialog == HudDialog.NONE) {
                val digit = keyToDigit(e.key)
                if (digit != null) {
                    if (e.type == KeyEventType.KeyUp) {
                        heldDigitKeys.remove(e.key)
                        return@onPreviewKeyEvent true
                    }
                    if (e.type == KeyEventType.KeyDown) {
                        if (lookupInFlight || !heldDigitKeys.add(e.key)) {
                            return@onPreviewKeyEvent true
                        }
                        val enteredDigits = digitBuffer + digit
                        tuneOsd = TuneOsd.Entry(enteredDigits)
                        tuneOsdTick++
                        if (enteredDigits.length == MAX_DIRECT_TUNE_DIGITS) {
                            digitBuffer = ""
                            submissionRequest = enteredDigits.toIntOrNull()
                            submissionTick++
                        } else {
                            digitBuffer = enteredDigits
                        }
                        return@onPreviewKeyEvent true
                    }
                }
                // Enter/Center/NumpadEnter: submit immediately while digits are pending.
                if (e.type == KeyEventType.KeyDown && !lookupInFlight && digitsActive &&
                    (e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter)
                ) {
                    val num = digitBuffer.toIntOrNull()
                    digitBuffer = ""
                    if (num != null) { submissionRequest = num; submissionTick++ }
                    return@onPreviewKeyEvent true
                }
            }
            // A dedicated TV channel key belongs to the channel player for the whole session. Handle it
            // before configurable browse shortcuts so an OSD button, an engine handoff, or a paging binding
            // cannot temporarily claim it. KeyUp is swallowed too, keeping the complete press inside the HUD.
            if (canZap && (e.key == Key.ChannelUp || e.key == Key.ChannelDown)) {
                if (e.type == KeyEventType.KeyDown) {
                    zap(zapDirection(up = e.key == Key.ChannelUp, remoteOptions))
                }
                return@onPreviewKeyEvent true
            }
            // Configured player shortcuts run inside this original, stable preview handler. Direct
            // tune above has first priority, so number keys keep changing live channels fullscreen.
            if (remoteShortcuts.enabled && !inert && dialog == HudDialog.NONE && !digitsActive) {
                val keyCode = e.nativeKeyEvent.keyCode
                val keyBindings = remoteShortcuts.bindings.filter {
                    it.keyCode == keyCode &&
                        it.action != RemoteShortcutAction.PAGE_TOWARD_FIRST &&
                        it.action != RemoteShortcutAction.PAGE_TOWARD_LAST &&
                        it.action != RemoteShortcutAction.JUMP_TO_FIRST &&
                        it.action != RemoteShortcutAction.JUMP_TO_LAST
                }
                if (keyBindings.isNotEmpty()) {
                    when (e.type) {
                        KeyEventType.KeyDown -> {
                            if (shortcutKeyCode != keyCode) {
                                shortcutKeyCode = keyCode
                                shortcutPressedAt = e.nativeKeyEvent.eventTime
                            }
                            return@onPreviewKeyEvent true
                        }
                        KeyEventType.KeyUp -> if (shortcutKeyCode == keyCode) {
                            val heldLong = e.nativeKeyEvent.eventTime - shortcutPressedAt >= PLAYER_SHORTCUT_LONG_PRESS_MS
                            val binding = if (heldLong) {
                                keyBindings.firstOrNull { it.press == RemoteShortcutPress.LONG }
                                    ?: keyBindings.firstOrNull { it.press == RemoteShortcutPress.SHORT }
                            } else {
                                keyBindings.firstOrNull { it.press == RemoteShortcutPress.SHORT }
                            }
                            shortcutKeyCode = android.view.KeyEvent.KEYCODE_UNKNOWN
                            binding?.let { dispatchPlayerShortcut(it.action) }
                            return@onPreviewKeyEvent true
                        }
                    }
                }
            }
            // Player buttons: OK with the controls hidden. No plan (the default) leaves OK to the code below.
            if (isPlayerOkKey(e.key) && e.type != KeyEventType.Unknown) {
                val okPlan = if (controlsVisible || inert || dialog != HudDialog.NONE || digitsActive || onToastOk != null) null
                    else playerOkPlan(remoteOptions, PlayerOkTargets(channelList = onOpenChannelList != null, guide = true, programmeInfo = true))
                val ok = okPress.onKey(e.type == KeyEventType.KeyDown, e.nativeKeyEvent.repeatCount, e.nativeKeyEvent.eventTime, okPlan)
                if (ok is PlayerOkResult.Fire) when (ok.outcome) {
                    PlayerOkOutcome.SHOW_CONTROLS -> controlsVisible = true
                    PlayerOkOutcome.CHANNEL_LIST -> onOpenChannelList?.invoke()
                    PlayerOkOutcome.GUIDE -> dispatchPlayerShortcut(RemoteShortcutAction.OPEN_GUIDE)
                    PlayerOkOutcome.PROGRAMME_INFO -> channelFlash++
                }
                if (ok != PlayerOkResult.Pass) return@onPreviewKeyEvent true
            }
            // ---- Existing key handling (unchanged, but skip for digit KeyUp already consumed above) ----
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when {
                // Channel surfing: media prev/next always zap alongside the dedicated CH keys handled
                // above. D-pad Up/Down zap ONLY on live streams while the HUD is hidden (when it's visible,
                // Up/Down navigate the controls) —
                // this is the only way to change channels on remotes without CH keys (e.g. Fire TV).
                //
                // Direction is channel-number order, not list-position order: "up" (CH+, D-pad Up) is
                // the NEXT channel — further down an ascending list, delta +1 — matching the
                // de facto TV convention (Live Channels, YouTube TV, Pluto TV). CH and D-pad keys move together:
                // Swap channel up/down flips both (see zapDirection), never the media keys. Wrapping is
                // intended: CH-/Down from the first channel lands on the last, and vice versa.
                canZap && e.key == Key.MediaNext -> { zap(1); true }
                canZap && e.key == Key.MediaPrevious -> { zap(-1); true }
                canZap && !controlsVisible && e.key == Key.DirectionUp -> { zap(zapDirection(up = true, remoteOptions)); true }
                canZap && !controlsVisible && e.key == Key.DirectionDown -> { zap(zapDirection(up = false, remoteOptions)); true }
                // The category list lives at logical Start; history lives at logical End.
                onOpenChannelList != null && !controlsVisible &&
                    e.key.horizontalDirection(layoutDirection) == HorizontalDirection.START -> { onOpenChannelList(); true }
                onOpenHistoryList != null && !controlsVisible &&
                    e.key.horizontalDirection(layoutDirection) == HorizontalDirection.END -> { onOpenHistoryList(); true }
                onToastOk != null && !controlsVisible &&
                    (e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter) -> { onToastOk(); true }
                controlsVisible -> { wakeTick++; false }
                else -> false
            }
        },
    ) {
        if (!controlsVisible) {
            Box(
                Modifier.fillMaxSize().focusRequester(catchFocus).focusable()
                    .onKeyEvent { e -> if (e.type == KeyEventType.KeyDown && e.key != Key.Back) { controlsVisible = true; true } else false },
            )
        }

        // Stream technical info — drawn over everything (and kept up even when the controls auto-hide), so
        // you can read live bitrate/buffer while watching. Toggled from the bottom bar's info button.
        if (showInfo) {
            // Sits clear of the taller unified top strip (logo + guide) rather than under a title row.
            StreamInfoOverlay(player, modifier = Modifier.align(Alignment.TopEnd).padding(top = 112.dp, end = 20.dp))
        }

        // Top-left OSD stack: the channel card (briefly on a zap, or the freshly tuned channel) plus the
        // direct-tune card, which pushes down under it. Drawn outside the controls-visible block so both
        // zapping and digit entry stay visible with the HUD hidden. With the controls up the unified top
        // strip already names the channel, so only a direct tune — whose card names the channel the stream
        // is still switching to — draws here.
        val tuned = (tuneOsd as? TuneOsd.Tuned)?.info
        Column(
            modifier = Modifier.align(Alignment.TopStart)
                .padding(start = 28.dp, top = if (controlsVisible) 92.dp else 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (tuned != null || (showFlash && !controlsVisible)) {
                // A fresh tune drives the card from the lookup result, not player metadata: Stalker
                // publishes its metadata after an async resolve, which would show the old channel.
                val named = tuned ?: zapChannel
                if (named != null) {
                    ChannelOsdCard(title = named.name, subtitle = named.number?.let { stringResource(R.string.player_channel_number, it) }, logoUrl = named.logoUrl, guide = zapGuide)
                } else {
                    ChannelCard(player, guide = zapGuide)
                }
            }
            when (val osd = tuneOsd) {
                is TuneOsd.Entry -> ChannelNumberCard(osd.digits)
                is TuneOsd.Message -> ChannelNumberCard(osd.digits, error = osd.text)
                is TuneOsd.Tuned, null -> Unit
            }
        }

        if (controlsVisible) {
            // Scrims: a FLAT semi-transparent panel behind the controls, feathered to transparent only at
            // the inner edge. A pure gradient faded out exactly where the chips and the Now/Next text sit,
            // so those washed out on bright scenes; a hard-edged band would instead draw a visible seam
            // across the picture. The colour stops give the panel first, then the feather.
            Box(Modifier.align(Alignment.TopStart).fillMaxWidth().height(210.dp)
                .gradientWash(
                    vertical = true,
                    0.0f to hudShade(0.72f),
                    0.5f to hudShade(0.68f),
                    1.0f to Color.Transparent,
                ))
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(260.dp)
                .gradientWash(
                    vertical = true,
                    0.0f to Color.Transparent,
                    0.45f to hudShade(0.68f),
                    1.0f to hudShade(0.78f),
                ))

            // One unified strip: back · logo · chips-over-channel-name · Now/Next guide. The channel name
            // used to be drawn twice (here and in a floating card below), with the guide stranded on the
            // right edge — that space belongs to the history list now.
            TopBar(
                player, streamChips.ifEmpty { listOfNotNull(videoRes) }, onBack,
                modifier = Modifier.align(Alignment.TopStart),
                below = matchLine,
                trailing = if (error == null) liveEpgCard else null,
                // Hidden behind an error overlay along with the rest of the chrome: a clock ticking
                // over a failure message just draws the eye to the wrong thing.
                centre = if (error == null) {
                    { PlayerClock() }
                } else null,
            )

            // Hide the transport (play/pause) and bottom bar while an error is up — the error
            // overlay owns the screen with its own Retry, so the play button must not show behind it.
            if (error == null) {
                CenterControls(player, isPlaying, playFocus, modifier = Modifier.align(Alignment.Center))

                val reportSavedMessage = stringResource(R.string.player_report_saved)

                BottomBar(
                    player = player,
                    volume = volume, audioCount = audioCount, subCount = subCount, zoomMode = zoomMode,
                    onPreviousChannel = onPreviousChannel,
                    onOtherChannels = onOtherChannels, otherChannelsCount = otherChannelsCount,
                    hasSources = sources != null,
                    onInfo = { showInfo = !showInfo }, infoOn = showInfo,
                    onReport = {
                        val meta = player.currentMeta.value
                        // The readout is gathered on the player's own thread now, so the report is
                        // written from a coroutine. The confirmation still flashes immediately — the user
                        // pressed a button and must see it acknowledged.
                        hudScope.launch {
                            val snapshot = buildString {
                                appendLine(player.streamInfo().joinToString("\n") { (k, v) -> "  $k: $v" })
                            }
                            PlaybackErrorLog.report(
                                context = reportContext,
                                engine = "ExoPlayer",
                                live = true,
                                title = meta.title,
                                snapshot = snapshot,
                            )
                        }
                        toastMsg = reportSavedMessage
                        toastFlash++
                    },
                    favorite = favorite, onToggleFavorite = onToggleFavorite,
                    onOpenDialog = { dialog = it }, onPip = onPip, onAudioMode = onAudioMode,
                    onMultiview = onMultiview,
                    modifier = Modifier.align(Alignment.BottomStart),
                )
            }
        }

        // Bottom-centre toast, semi-transparent — the smoothness hint and the "report saved"
        // acknowledgement both surface here.
        toastMsg?.let { msg ->
            Box(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 104.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    msg,
                    style = tv.own.myowntv.ui.theme.stageText(25, 600),
                    color = hudInk,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(hudShade(0.55f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        // Status overlay (always shown).
        when {
            error != null -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.player_playback_error), style = tv.own.myowntv.ui.theme.stageText(40, 600), color = Color.White)
                Spacer(Modifier.height(8.dp))
                error?.let {
                    Text(it.displayText(), style = tv.own.myowntv.ui.theme.stageText(25, 400), color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                }
                // Structured technical detail so a user can report the real cause without adb/logcat:
                // plain reason → media spec (codec • resolution • decoder) → raw engine/codec line.
                errorInfo?.let { info ->
                    info.reason?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it.displayText(), style = tv.own.myowntv.ui.theme.stageText(25, 400), color = Color.White.copy(alpha = 0.92f), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(0.8f))
                    }
                    info.spec?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it.displayText(), style = tv.own.myowntv.ui.theme.stageText(22, 700), color = Color.White.copy(alpha = 0.55f), textAlign = TextAlign.Center)
                    }
                    info.raw?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.player_raw_error, it), style = tv.own.myowntv.ui.theme.stageText(22, 400), color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(0.8f))
                    }
                }
                Spacer(Modifier.height(18.dp))
                tv.own.myowntv.ui.stage.StageButton(stringResource(R.string.common_retry), onClick = { player.retry() }, modifier = Modifier.focusRequester(retryFocus), icon = MyOwnTVIcon.PLAY, tinted = true)
            }
            // A provider wait looks like loading, because that is what it is: the channel is queued behind
            // the panel's own countdown and the engine re-asks by itself. The line under the spinner says
            // why nothing is happening yet, so nobody reaches for Retry (or thinks the channel is dead).
            buffering -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                OwnTVSpinner(sizeDp = 56)
                val stalledSince = stalledSinceMs
                if (providerBackOff == null && stalledSince != null) {
                    val showNotice by produceState(false, stalledSince) {
                        delay(RECONNECT_NOTICE_AFTER_MS - (android.os.SystemClock.elapsedRealtime() - stalledSince))
                        value = true
                    }
                    if (showNotice) {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            stringResource(R.string.player_reconnecting),
                            style = tv.own.myowntv.ui.theme.stageText(25, 400),
                            color = Color.White.copy(alpha = 0.85f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(0.7f),
                        )
                    }
                }
                providerBackOff?.let { wait ->
                    Spacer(Modifier.height(14.dp))
                    Text(
                        stringResource(
                            R.string.player_provider_retry_after,
                            wait.httpCode,
                            wait.message ?: stringResource(R.string.player_provider_busy),
                            wait.secondsLeft,
                        ),
                        style = tv.own.myowntv.ui.theme.stageText(25, 400),
                        color = Color.White.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.7f),
                    )
                }
            }
        }
    }

    when (dialog) {
        // Track lists are SNAPSHOT once when the dialog opens (re-polled only while still empty —
        // heavy HDR/DTS streams report their tracks late). Reading player.xxxTracks() directly in
        // composition handed the dialog a fresh list on every HUD recomposition, endlessly rebuilding
        // the rows and losing/yanking D-pad focus.
        //
        // The re-poll is BOUNDED: a stream that genuinely has no such track (most radio channels have
        // no subtitles) would otherwise wake the CPU every 300 ms for as long as the menu stays open.
        // Tracks that arrive later than TRACK_POLL_TRIES × 300 ms have never been observed.
        HudDialog.AUDIO -> {
            var audioTracks by remember { mutableStateOf(player.audioTracks()) }
            LaunchedEffect(Unit) {
                repeat(TRACK_POLL_TRIES) {
                    if (audioTracks.isNotEmpty()) return@LaunchedEffect
                    delay(TRACK_POLL_MS)
                    audioTracks = player.audioTracks()
                }
            }
            TrackDialog(
                stringResource(R.string.player_audio_track), audioTracks,
                onSelect = { player.selectAudio(it.id); dialog = HudDialog.NONE }, onOff = null,
                onDismiss = { dialog = HudDialog.NONE },
                // A/V-sync nudge wherever the engine can actually shift audio: a live stream can
                // arrive with the provider's own drift baked in (and tunneled playback cannot shift it).
                audioDelayMs = if (player.audioDelayAvailable()) audioDelayMs else null,
                onAdjustAudioDelay = if (player.audioDelayAvailable()) ({ d -> player.adjustAudioDelay(d) }) else null,
                audioDelayRemembered = audioDelayRemembered,
                onToggleRememberAudioDelay = if (player.audioDelayAvailable()) ({ player.toggleRememberAudioDelay() }) else null,
            )
        }
        HudDialog.SUBS -> {
            var subTracks by remember { mutableStateOf(player.textTracks()) }
            LaunchedEffect(Unit) {
                repeat(TRACK_POLL_TRIES) {
                    if (subTracks.isNotEmpty()) return@LaunchedEffect
                    delay(TRACK_POLL_MS)
                    subTracks = player.textTracks()
                }
            }
            TrackDialog(
                stringResource(R.string.player_subtitles), subTracks,
                onSelect = { player.selectSubtitle(it.id); dialog = HudDialog.NONE },
                onOff = { player.disableSubtitles(); dialog = HudDialog.NONE },
                onDismiss = { dialog = HudDialog.NONE },
            )
        }
        HudDialog.ZOOM -> ZoomDialog(current = zoomMode, onSelect = { player.setZoomModeByUser(it); dialog = HudDialog.NONE }, onDismiss = { dialog = HudDialog.NONE })
        HudDialog.QUALITY -> {
            // Snapshot as it opens, like the track lists above: a live list would rebuild the rows.
            val heights = remember { player.videoQualities.value }
            val pick by player.videoQualityPick.collectAsStateWithLifecycle()
            QualityDialog(heights, pick, onSelect = { player.selectVideoQuality(it); dialog = HudDialog.NONE }, onDismiss = { dialog = HudDialog.NONE })
        }
        HudDialog.VOLUME -> VolumeDialog(player, onDismiss = { dialog = HudDialog.NONE })
        HudDialog.SOURCES -> sources?.let { s ->
            SourcesDialog(s, onDismiss = { dialog = HudDialog.NONE })
        } ?: run { dialog = HudDialog.NONE }
        HudDialog.SLEEP_TIMER -> SleepTimerDialog(
            timer = org.koin.compose.koinInject(),
            programmeEndMs = remember { sleepProgrammeEndMs?.invoke() },
            onDismiss = { dialog = HudDialog.NONE },
        )
        HudDialog.NONE -> Unit
    }
}
