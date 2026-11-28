package tv.own.myowntv.features.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.own.myowntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.launcher.LauncherDeepLink
import tv.own.owntv.core.nav.MainSection
import tv.own.owntv.core.update.UpdateManager
import tv.own.myowntv.features.update.UpdateDialog
import tv.own.myowntv.features.update.UpdateStatusToast
import tv.own.myowntv.features.categorylock.rememberDueReminder
import tv.own.myowntv.features.epg.EpgScreen
import tv.own.myowntv.features.home.HomeScreen
import tv.own.myowntv.features.home.HomeViewModel
import tv.own.myowntv.features.live.LiveScreen
import tv.own.myowntv.features.live.LiveViewModel
import tv.own.myowntv.features.search.SearchScreen
import tv.own.myowntv.features.search.SearchViewModel
import tv.own.owntv.core.settings.RemoteShortcutAction
import tv.own.owntv.core.settings.RemoteShortcutBindings
import tv.own.myowntv.player.MiniPlayer
import tv.own.myowntv.player.PlayerHud
import tv.own.myowntv.features.shell.components.ExitDialog
import tv.own.myowntv.features.shell.components.IncompleteRestoreDialog
import tv.own.myowntv.features.shell.components.SettingsScreen
import tv.own.myowntv.features.shell.components.SolidAmbientBackdrop
import androidx.compose.ui.res.stringResource
import tv.own.myowntv.player.alignment
import tv.own.myowntv.ui.theme.mpx
import tv.own.myowntv.ui.stage.stageBackground
import tv.own.myowntv.ui.components.MyOwnTVIcon
import tv.own.myowntv.ui.components.LocalRemoteShortcuts
import tv.own.myowntv.ui.components.RemoteShortcutEnvironment
import tv.own.myowntv.ui.theme.LocalGlass
import tv.own.owntv.core.theme.ThemeMode
import tv.own.owntv.core.live.LiveKey
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/** Which layer currently holds focus (drives Back navigation). */
private enum class ShellLayer { SIDEBAR, CONTENT }

/** Player presentation: hidden, fullscreen, docked mini-player, or audio-only now-playing bar. */
internal enum class PlayerMode { NONE, FULLSCREEN, MINI, AUDIO }

/**
 * The MD3 shell: a fixed navigation panel (Layer 1) plus the active destination. Settings is a
 * single-pane sectioned screen; browse sections keep the Folder Rail → Content → Preview layout.
 */
@Composable
fun MyOwnTVShell(
    selectedSection: MainSection,
    visibleSections: Set<MainSection>,
    onSelectSection: (MainSection) -> Unit,
    themeMode: ThemeMode,
    uiZoomPercent: Int,
    onSetZoom: (Int) -> Unit,
    fontCustomization: tv.own.owntv.core.theme.FontCustomization,
    onSetFontCustomization: (tv.own.owntv.core.theme.FontCustomization) -> Unit,
    sourceSummary: String?,
    weatherInfo: tv.own.owntv.core.weather.WeatherInfo? = null,
    weatherFahrenheit: Boolean = false,
    pendingDeepLink: LauncherDeepLink?,
    onDeepLinkConsumed: () -> Unit,
    isOffline: Boolean = false,
    onExitApp: () -> Unit,
    modifier: Modifier = Modifier,
    // Called once the app's start-up choice has been applied; the splash waits for it.
    onStartupSettled: () -> Unit = {},
) {
    val noSourceLabel = stringResource(R.string.shell_no_source)

    val scope = rememberCoroutineScope()
    val sidebarFocus = remember { FocusRequester() }
    // When focus was last sent to the rail on purpose: ◀ / ▶ from the content, or the app itself
    // ([focusRail]). A rail visit that follows neither does not open the floating rail (#7.1).
    var railKeyAt by remember { mutableLongStateOf(0L) }
    val focusRail = {
        railKeyAt = android.os.SystemClock.uptimeMillis()
        runCatching { sidebarFocus.requestFocus() }
        Unit
    }
    val contentFocusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val contentLtr = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Ltr
    val homeFirstRowFocus = remember { FocusRequester() }
    var focusedLayer by remember { mutableStateOf(ShellLayer.SIDEBAR) }
    var showExit by remember { mutableStateOf(false) }
    // Held by the Activity-scoped ShellViewModel, so a recreation (a script-family language switch)
    // does not close the channel that is playing.
    val shellVm = org.koin.androidx.compose.koinViewModel<ShellViewModel>()
    var playerMode by shellVm.playerMode
    // One-shot: set when leaving the player so the returning browse screen re-focuses the item you played.
    var restoreFocus by remember { mutableStateOf(false) }
    var searchReturn by remember { mutableStateOf<MainSection?>(null) }
    // Docked mini-player size (% of screen width) + position, configurable in Settings and from the
    // mini-player's own controls. Read straight from settings so both entry points stay in sync.
    val settingsRepo = koinInject<tv.own.owntv.core.settings.SettingsRepository>()
    val remoteShortcutsEnabled by settingsRepo.chNavEnabled.collectAsStateWithLifecycle(initialValue = true)
    val clockColors by settingsRepo.clockColors.collectAsStateWithLifecycle(initialValue = tv.own.owntv.core.settings.ClockColors())
    val remoteShortcutBindings by settingsRepo.remoteShortcutBindings.collectAsStateWithLifecycle(
        initialValue = RemoteShortcutBindings.defaults,
    )
    val miniSizePct by settingsRepo.miniPlayerSizePct.collectAsStateWithLifecycle(initialValue = tv.own.owntv.core.player.MiniPlayerSize.DEFAULT)
    val miniPosName by settingsRepo.miniPlayerPosition.collectAsStateWithLifecycle(initialValue = tv.own.owntv.core.player.MiniPlayerPosition.DEFAULT.name)
    // How the player's picture is composited — hardware plane or compatibility texture. The texture
    // reaches screenshots/screen recordings; the plane is what 4K/HDR travel on.
    val videoSurface by settingsRepo.videoSurfaceMode.collectAsStateWithLifecycle(initialValue = tv.own.owntv.core.settings.SettingsRepository.VideoSurface.SURFACE_VIEW)
    val ambientGlowEnabled by settingsRepo.ambientGlowEnabled.collectAsStateWithLifecycle(initialValue = false)
    val ambientGlowPulse by settingsRepo.ambientGlowPulse.collectAsStateWithLifecycle(initialValue = true)
    val shellAnimationLevel by settingsRepo.animationLevel.collectAsStateWithLifecycle(initialValue = tv.own.owntv.core.theme.AnimationLevel.FULL)
    val miniPos = tv.own.owntv.core.player.MiniPlayerPosition.fromName(miniPosName)
    // Reaching the mini player: the mini window floats above the content panel, so D-pad focus
    // search has no spatial path into it from most screens. These are the three deliberate ways in:
    // the rail's Now Playing item, long-press Back, and the media keys.
    val miniEntryFocus = remember { FocusRequester() }
    val audioEntryFocus = remember { FocusRequester() }
    // The browse UI is one focus group with a restorer, so handing focus back to it after the mini
    // player returns to the exact control the user left rather than to the top of the screen.
    val shellContentFocus = remember { FocusRequester() }
    // Stage navigation: where the rail sits, what it shows, its length, the floating hide delay.
    val navStyle by settingsRepo.navStyle.collectAsStateWithLifecycle(initialValue = tv.own.owntv.core.settings.SettingsRepository.NavStyle.DOCKED)
    val navSize by settingsRepo.navSize.collectAsStateWithLifecycle(initialValue = tv.own.owntv.core.settings.SettingsRepository.NavSize.COMPACT)
    val navWiden by settingsRepo.navWiden.collectAsStateWithLifecycle(initialValue = null)
    val navLength by settingsRepo.navLength.collectAsStateWithLifecycle(initialValue = tv.own.owntv.core.settings.SettingsRepository.NavLength.FIT)
    val navHideAfterMs by settingsRepo.navHideAfterMs.collectAsStateWithLifecycle(
        initialValue = tv.own.owntv.core.settings.SettingsRepository.NavHideAfter.DEFAULT_MS,
    )
    val railVm = org.koin.androidx.compose.koinViewModel<RailCountsViewModel>()
    val railCounts by railVm.counts.collectAsStateWithLifecycle()
    val railDetails by railVm.details.collectAsStateWithLifecycle()
    // Where ▶ out of the Stage rail returns to: the content, exactly as it was left.
    val contentAreaFocus = remember { FocusRequester() }
    var homeEntry by remember { mutableStateOf<(() -> Boolean)?>(null) }
    // The guide's entry hook: the TV Guide, or Live TV in Guide view (null while Live TV shows its list).
    var guideEntry by remember { mutableStateOf<(() -> Boolean)?>(null) }
    var moreEntry by remember { mutableStateOf<(() -> Boolean)?>(null) }
    // More › Settings opens Settings at a group, or with its search open.
    var settingsStart by remember { mutableStateOf<tv.own.myowntv.features.shell.components.SettingsStart?>(null) }
    var miniHasFocus by remember { mutableStateOf(false) }
    val shellToast = tv.own.myowntv.ui.components.rememberInAppToast()
    // Audio focus + MediaSession. This is the only place that knows which engine currently owns
    // the speaker, so it hands that engine over and takes it back when the player closes.
    val playbackSession = koinInject<tv.own.owntv.player.PlaybackSession>()
    val homeVm = org.koin.androidx.compose.koinViewModel<HomeViewModel>()
    val searchVm = org.koin.androidx.compose.koinViewModel<SearchViewModel>()
    // Same activity-scoped instances the Live/Guide screens use — lets the fullscreen HUD zap channels
    // up/down (CH+/CH-). Guide tunes start through LiveViewModel too (they set zapSource = LIVE_TV), so
    // there is exactly ONE zap path: liveVm's. The Guide keeps its own EpgViewModel only for the grid.
    val liveVm = org.koin.androidx.compose.koinViewModel<LiveViewModel>()
    // "Previous" from the system media controls on a live channel goes back a channel.
    DisposableEffect(playbackSession, liveVm) {
        playbackSession.livePrevious = { liveVm.previousChannel() }
        onDispose { playbackSession.livePrevious = null }
    }
    val epgVm = org.koin.androidx.compose.koinViewModel<tv.own.myowntv.features.epg.EpgViewModel>()
    val liveCanZap by liveVm.canZap.collectAsStateWithLifecycle()
    val previousLiveChannel by liveVm.previousChannelTarget.collectAsStateWithLifecycle()
    // Auto frame rate: only ever applied to the FULL-SCREEN surface (never the mini-player or the
    // in-pane Live preview) — see FrameRateController.
    val autoFrameRateLive by settingsRepo.autoFrameRateLive.collectAsStateWithLifecycle(initialValue = false)
    // ...and the one-time suggestion to turn it on, for the 25-fps-on-60-Hz judder the direct render path
    // cannot fix by itself. `true` until the flag is read, so it can never flash on first frame.
    val afrPrompted by settingsRepo.autoFrameRatePrompted.collectAsStateWithLifecycle(initialValue = true)
    // Direct tune (type a channel number on the remote). Settings › Live TV › Channel numbers; default on.
    val directTuneEnabled by settingsRepo.directTune.collectAsStateWithLifecycle(initialValue = true)
    val playerRemoteOptions by settingsRepo.playerRemoteOptions.collectAsStateWithLifecycle(initialValue = tv.own.owntv.core.settings.PlayerRemoteOptions())
    // "Prefer EPG logos": start following the setting once, here rather than in Application.onCreate —
    // the store queries nothing at all while the toggle is off, so cold start stays free of EPG reads.
    // Which section armed the current fullscreen stream — picks whose channel list CH+/CH- step through.
    var zapSource by shellVm.zapSource
    // In-player channel-list overlay (Left while controls hidden, live only).
    var showChannelList by remember { mutableStateOf(false) }
    // In-player watch-history list (Right while controls hidden, live only).
    var showHistoryList by remember { mutableStateOf(false) }
    // Multiview: the grid, while it is up, and which tile is waiting for a channel to be picked.
    val multiviewEnabled by settingsRepo.multiviewEnabled.collectAsStateWithLifecycle(false)
    val multiviewTileCount by settingsRepo.multiviewTiles.collectAsStateWithLifecycle(
        tv.own.owntv.core.live.DEFAULT_MULTIVIEW_TILES,
    )
    val enginePool = koinInject<tv.own.owntv.player.LiveEnginePool>()
    val playbackEngines = koinInject<tv.own.owntv.player.PlaybackEngines>()
    val streamRegistry = koinInject<tv.own.owntv.core.live.OpenStreamRegistry>()
    var multiview by remember { mutableStateOf<tv.own.myowntv.features.multiview.MultiviewState?>(null) }
    var multiviewPickFor by remember { mutableStateOf<Int?>(null) }
    // Publish the active engine to the system (audio focus + MediaSession), and detach when the player
    // is closed — an inactive session must not keep answering the TV's transport keys or the Assistant.
    // During Multiview that is the tile with the sound; the preview engine it used to stay on is stopped.
    val multiviewAudibleEngine = multiview?.let { it.pool.peek(it.audible) }
    LaunchedEffect(playerMode, multiviewAudibleEngine) {
        playbackSession.attach(
            when {
                multiview != null -> multiviewAudibleEngine
                playerMode == PlayerMode.NONE -> null
                else -> liveVm.previewEngine
            },
        )
    }
    // The pool outlives this composable (it is app-wide), the grid state does not. If the shell is torn
    // down with the grid up, release its engines and stream claims, or they play on as ghosts that
    // refuse later tiles.
    val currentMultiview by rememberUpdatedState(multiview)
    DisposableEffect(Unit) {
        onDispose { currentMultiview?.releaseAll() }
    }
    // Channels kept from the Live list (the second way into the grid). Pressing play on any channel is
    // what says "now": the grid opens with them already in it, and the selection is spent.
    val multiviewSelection by liveVm.multiviewSelection.collectAsStateWithLifecycle()
    val zapChannels by liveVm.zapChannels.collectAsStateWithLifecycle()
    val zapListTitle by liveVm.zapListTitle.collectAsStateWithLifecycle()
    val zapListKey by liveVm.zapListKey.collectAsStateWithLifecycle()
    // Favorites and History have no provider name to show — their labels are UI strings, so the overlay
    // used to head both of them "All channels".
    // Read unconditionally and chosen by a plain function: a composable call skipped on one side of `?:`
    // changed this screen's group structure, and the player below — video surface and HUD — was rebuilt
    // whenever the channel browser's heading switched between a built-in rail and a named category.
    val favoritesTitle = stringResource(R.string.content_category_favorites)
    val historyTitle = stringResource(R.string.content_category_history)
    val allChannelsTitle = stringResource(R.string.content_category_all_channels)
    fun builtInListTitle(key: LiveKey?): String = when (key) {
        LiveKey.Favorites -> favoritesTitle
        LiveKey.History -> historyTitle
        else -> allChannelsTitle
    }
    val zapOverlayTitle = zapListTitle ?: builtInListTitle(zapListKey)
    val showCategoryBrowser by liveVm.showCategoryBrowser.collectAsStateWithLifecycle()
    val browserRailItems = liveVm.railItems.collectAsStateWithLifecycle().value.let { all -> remember(all) { all.filterNot { it.sealed } } }
    // The channel browser's right-hand list (◀◀ in the player) and its heading.
    val peekChannels by liveVm.peekChannels.collectAsStateWithLifecycle()
    val peekKey by liveVm.peekKey.collectAsStateWithLifecycle()
    val peekTitle = browserRailItems.firstOrNull { it.key == peekKey }?.title?.takeIf { it.isNotBlank() }
        ?: if (peekChannels == zapChannels) zapOverlayTitle else builtInListTitle(peekKey)
    val browserRailCounts by liveVm.railCounts.collectAsStateWithLifecycle()
    val previewChannel by liveVm.previewChannel.collectAsStateWithLifecycle()
    val overlayProgrammes by liveVm.nowProgrammes.collectAsStateWithLifecycle()
    val overlayFavoriteIds by liveVm.favoriteIds.collectAsStateWithLifecycle()
    // Favorite state for the player HUD's in-stream favorite toggle.
    val liveFavoriteIds by liveVm.favoriteIds.collectAsStateWithLifecycle()
    // Favorite toggle for the playing channel. Hoisted here because the audio capsule in the top bar
    // carries the same control as the fullscreen HUD.
    val favToggle: (() -> Unit)? =
        previewChannel?.let { ch -> { liveVm.toggleFavorite(ch) } }
    val favActive = previewChannel?.let { liveFavoriteIds.contains(it.id) } ?: false
    // Tell core which playlist is on screen, so its background catalogue drain steps aside while the
    // user is watching. Core cannot work this out alone: the player engines are handed
    // a URL and have no notion of a sourceId, and fullscreen playback deliberately never claims a
    // connection in OpenStreamRegistry — that register is the Multiview budget. This screen
    // holds the row, so this is the only place the answer exists.
    val watchSession = koinInject<tv.own.owntv.core.live.WatchSession>()
    val watchingSourceId = if (playerMode == PlayerMode.NONE) {
        null
    } else {
        previewChannel?.sourceId
    }
    // DisposableEffect, not LaunchedEffect: zapping to another playlist has to close the old session
    // before opening the new one, and leaving the shell has to close the last one — a leaked session
    // would hold the drain off for good.
    DisposableEffect(watchingSourceId) {
        watchingSourceId?.let { watchSession.open(it) }
        onDispose { watchingSourceId?.let { watchSession.close(it) } }
    }
    // Current programme per channel for the in-player channel list overlay (small subtitle under each row).
    // Only resolved while the overlay is actually open. Keyed on the channel set so a zap-list change re-resolves.
    // Shares the Live list's resolved titles, so opening the overlay over a list already on screen
    // asks for nothing, and only genuinely new channels cost a query.
    val overlayNowPlaying by liveVm.nowPlaying.collectAsStateWithLifecycle()
    LaunchedEffect(showChannelList, zapChannels) {
        if (showChannelList && zapChannels.size > 1) liveVm.ensureNowPlaying(zapChannels)
    }
    LaunchedEffect(showChannelList, showCategoryBrowser, peekChannels) {
        if (showChannelList && showCategoryBrowser && peekChannels.isNotEmpty()) liveVm.ensureNowPlaying(peekChannels)
    }
    // Recently-watched channels for the right-hand history overlay — re-read each time it opens (and
    // after a zap, since tuning writes a new history row) so the newest channel is always on top.
    val historyEntries by produceState(emptyList<tv.own.owntv.core.database.dao.ChannelWithWatchedAt>(), showHistoryList, previewChannel?.id) {
        if (!showHistoryList) { value = emptyList(); return@produceState }
        value = runCatching { liveVm.historyChannels() }.getOrDefault(emptyList())
    }
    val historyChannels = remember(historyEntries) { historyEntries.map { it.channel } }
    val historyWatchedAt = remember(historyEntries) { historyEntries.associate { it.channel.id to it.watchedAt } }
    val historyNowPlaying = overlayNowPlaying // one shared map; the history rail adds to it below
    LaunchedEffect(historyChannels) { liveVm.ensureNowPlaying(historyChannels) }
    // The single most-recent resumable channel, surfaced as a shared top-bar "Continue" chip.
    val continueTarget by homeVm.continueTarget.collectAsStateWithLifecycle()

    // The start-up action runs once when the shell first appears.
    val resumeSettings = koinInject<tv.own.owntv.core.settings.SettingsRepository>()
    val startupChannelUnavailable = androidx.compose.ui.res.stringResource(tv.own.myowntv.R.string.settings_startup_channel_unavailable)
    LaunchedEffect(Unit) {
        if (playerMode != PlayerMode.NONE) { onStartupSettled(); return@LaunchedEffect }
        when (val startupMode = resumeSettings.startupMode().first()) {
            tv.own.owntv.core.settings.StartupMode.LAST_CHANNEL -> {
                val ch = liveVm.lastWatchedLiveChannel()
                if (ch != null && playerMode == PlayerMode.NONE) {
                    zapSource = MainSection.LIVE_TV
                    // There is no caller-owned browse rail here. Let LiveViewModel build the channel's
                    // provider context instead of permanently arming a one-item list that disables CH+/CH-.
                    if (liveVm.watchFullscreen(ch, emptyList())) playerMode = PlayerMode.FULLSCREEN
                }
            }
            // The TV Guide or Live TV on the last channel's category, the channel focused and in the preview,
            // as TiviMate opens. Live TV's list scrolls to it (revealChannel); a guide focuses its row on load.
            // The category is shown for this run, not remembered as the user's pick.
            tv.own.owntv.core.settings.StartupMode.LAST_CHANNEL_GUIDE,
            tv.own.owntv.core.settings.StartupMode.LAST_CHANNEL_LIVE -> {
                val ch = liveVm.lastWatchedLiveChannel()
                val toGuide = startupMode == tv.own.owntv.core.settings.StartupMode.LAST_CHANNEL_GUIDE
                if (ch != null && playerMode == PlayerMode.NONE) {
                    if (!toGuide && resumeSettings.liveView.first() == tv.own.owntv.core.settings.SettingsRepository.LiveView.LIST) {
                        liveVm.revealChannel(ch)
                    } else {
                        val key = liveVm.railKeyOf(ch)
                        if (toGuide) epgVm.setCategoryFilter(key) else liveVm.show(key)
                        epgVm.noteChannelTuned(ch)
                        liveVm.onChannelFocused(ch)
                        restoreFocus = true
                    }
                    onSelectSection(if (toGuide) MainSection.EPG else MainSection.LIVE_TV)
                }
            }
            // Open straight to Live TV on the Favorites folder, with focus landing inside the channel list
            // (restoreFocus drives LiveScreen to focus the first/last channel, not the nav panel). Favorites
            // is this startup's choice, not the user's pick: the remembered category stays as it was.
            tv.own.owntv.core.settings.StartupMode.FAVORITES -> {
                onSelectSection(MainSection.LIVE_TV)
                liveVm.show(tv.own.owntv.core.live.LiveKey.Favorites)
                restoreFocus = true
            }
            tv.own.owntv.core.settings.StartupMode.SPECIFIC_CHANNEL -> {
                val ref = resumeSettings.startupChannel().first()
                var channel: ChannelEntity? = null
                if (ref != null) {
                    if (!ref.remoteId.isNullOrBlank()) {
                        channel = liveVm.resolveLauncherChannel(
                            LauncherDeepLink.Live(remoteId = ref.remoteId),
                        )
                    }
                    if (channel == null) {
                        channel = liveVm.resolveLauncherChannel(
                            LauncherDeepLink.Live(name = ref.name),
                        )
                    }
                }
                if (channel != null && playerMode == PlayerMode.NONE) {
                    zapSource = MainSection.LIVE_TV
                    if (liveVm.watchFullscreen(channel, emptyList())) playerMode = PlayerMode.FULLSCREEN
                } else {
                    onSelectSection(MainSection.HOME)
                    shellToast.show(startupChannelUnavailable)
                }
            }
            tv.own.owntv.core.settings.StartupMode.HOME -> Unit
        }
        tv.own.owntv.core.util.Perf.stamp("startup-settled")
        onStartupSettled()
    }

    // Movies/Series/Live load on first open via their reactive Paging flows — their indexed first page is
    // cheap, so they need NO preloading (a Live-TV-only user pays nothing for them). The TV Guide is the ONE
    // exception: load() pulls every guide channel + a programme window, which is heavy enough that doing it on
    // open felt slow. So warm EPG in the background shortly after the shell renders — opening the Guide is then
    // instant, matching how it behaved before. (EpgScreen also calls load() on mount, so this is a pure pre-warm
    // and is skipped if the user is already on EPG.)
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1_200)
        if (selectedSection != MainSection.EPG) { tv.own.owntv.core.util.Perf.stamp("epg-preload"); epgVm.load() }
    }

    // Opening a channel from a browse screen goes fullscreen — UNLESS the player is already docked as a
    // mini-player, in which case it stays docked and just swaps to the newly-selected stream (the VM
    // already started it), so picking a channel updates the PiP window in place (#6).
    fun openFullscreen() {
        restoreFocus = false
        zapSource = MainSection.LIVE_TV
        homeVm.stopPreview()
        // A new stream is opening — make sure any Audio Mode video-off state is cleared, else the
        // engine keeps video output off and the new channel would play with no picture.
        runCatching { liveVm.previewEngine.exitAudioOnly() }
        if (playerMode != PlayerMode.MINI) playerMode = PlayerMode.FULLSCREEN
    }
    // Restore video output (no-op unless we were in Audio Mode). The engine's surface is re-attached
    // by the surface remount right after.
    val resumeVideo = {
        runCatching { liveVm.previewEngine.exitAudioOnly() }
    }
    // The mini-player's own expand button always maximizes.
    val expandPlayer = {
        // Before the mode flips, so the HUD's first frame already shows the channel playing, not the
        // row last highlighted behind the mini player.
        liveVm.onPlayerMode(mini = false, open = true)
        resumeVideo(); restoreFocus = false; playerMode = PlayerMode.FULLSCREEN
    }
    // Every other mode change (the mini player opening, the player closing) reaches Live TV here.
    LaunchedEffect(playerMode) { liveVm.onPlayerMode(mini = playerMode == PlayerMode.MINI, open = playerMode != PlayerMode.NONE) }
    val exitPlayer = {
        resumeVideo() // restore video output before stop so the next played channel isn't left picture-less
        playerMode = PlayerMode.NONE
        showChannelList = false
        showHistoryList = false
        liveVm.hideCategoryBrowser()
        liveVm.onFullscreenExited() // no longer full-screen → let the preview re-take the engine
        // Closing back into a guide: it focuses the channel that was playing (zapped-to included).
        epgVm.playerClosed = selectedSection == MainSection.EPG ||
            (selectedSection == MainSection.LIVE_TV && liveVm.liveView.value == tv.own.owntv.core.settings.SettingsRepository.LiveView.GUIDE)
        restoreFocus = true
        focusRail()
        Unit
    }
    SealedPlaybackEffect(shellVm, liveVm, { currentMultiview }, exitPlayer)
    /**
     * Turn the channel on screen into tile 1 of the grid, with [extra] filling the tiles after it.
     *
     * The fullscreen stream is stopped first, always: tile 1 opens an engine of its own, and a
     * playlist that allows two streams would otherwise be asked for three.
     */
    val openMultiview = { first: tv.own.owntv.core.database.entity.ChannelEntity?,
                          extra: List<tv.own.owntv.core.database.entity.ChannelEntity> ->
        liveVm.previewEngine.stop()
        val state = tv.own.myowntv.features.multiview.MultiviewState(
            pool = enginePool,
            registry = streamRegistry,
            live = liveVm,
            maxTiles = multiviewTileCount,
        )
        var tile = 0
        first?.let { state.fill(tile++, it) }
        // Channels kept with "Add to Multiview" grow the grid as they are placed, up to the ceiling —
        // the user asked for these by name, so they are the one case that sizes the grid itself.
        extra.filter { it.id != first?.id }.forEach { channel ->
            if (tile >= state.tiles.size) state.addTile()
            if (tile < state.tiles.size) state.fill(tile++, channel)
        }
        liveVm.clearMultiviewSelection()
        state
    }
    // The sleep timer's "stop" on a television. Closing the player alone is not a stop here: a
    // live channel carries on in the preview pane, and a Multiview grid is not the player at all. So
    // all three go, in the order Multiview's own Back uses, and the preview last.
    val sleepTimer = koinInject<tv.own.owntv.player.SleepTimer>()
    val stopForSleep by rememberUpdatedState {
        // Sleep is terminal. Drop Home/screensaver restore tokens before any engine is released or
        // stopped, otherwise returning to the foreground would replay the channel just put to sleep.
        playbackEngines.discardBackgroundRestore()
        multiview?.let { grid ->
            grid.releaseAll()
            multiview = null
            multiviewPickFor = null
        }
        if (playerMode != PlayerMode.NONE) exitPlayer()
        liveVm.stopPreview()
    }
    DisposableEffect(sleepTimer) {
        sleepTimer.stopPlayback = { stopForSleep() }
        onDispose { sleepTimer.stopPlayback = null }
    }
    // The kept selection is spent the moment a channel actually starts playing full screen: that
    // press is the "and now open it" the plan describes, and nothing else in the app claims it.
    LaunchedEffect(playerMode, previewChannel?.id, multiviewSelection.size) {
        if (multiviewEnabled &&
            multiview == null &&
            multiviewSelection.isNotEmpty() &&
            playerMode == PlayerMode.FULLSCREEN &&
            zapSource == MainSection.LIVE_TV &&
            previewChannel != null
        ) {
            multiview = openMultiview(previewChannel, multiviewSelection)
        }
    }

    val dockPlayer = {
        resumeVideo()
        playerMode = PlayerMode.MINI
        restoreFocus = true
        focusRail()
        Unit
    }
    // Switch the current stream to audio-only and surface the now-playing bar in the top bar. Stop the
    // video decoder FIRST, then drop the video surface by leaving FULLSCREEN/MINI.
    val toAudioMode = {
        liveVm.previewEngine.enterAudioOnly()
        playerMode = PlayerMode.AUDIO
        restoreFocus = true
        focusRail()
        Unit
    }

    // The engine on the speaker — what the rail item pictures and what the media keys act on while
    // the window is docked.
    val dockedEngine = liveVm.previewEngine
    val dockedPlaying by dockedEngine.isPlaying.collectAsStateWithLifecycle()
    // Leaving the app while video plays can keep it going in a picture-in-picture window, which shows
    // the video alone. Coming back from the window always lands on full screen.
    val (inPip, pipSource) = tv.own.myowntv.features.pip.rememberShellPictureInPicture(
        playerShown = playerMode == PlayerMode.FULLSCREEN || playerMode == PlayerMode.MINI,
        multiview = multiview != null,
        engine = dockedEngine,
        videoAspect = liveVm.previewEngine.videoAspect,
        onReturnToApp = { if (playerMode == PlayerMode.MINI) expandPlayer() },
    )
    val nowPlayingRail = when (playerMode) {
        PlayerMode.MINI, PlayerMode.AUDIO -> tv.own.myowntv.features.shell.components.NowPlayingRail(
            // A live channel's logo; the play mark otherwise.
            logoUrl = previewChannel?.logoUrl,
            audioMode = playerMode == PlayerMode.AUDIO,
            playing = dockedPlaying,
        )
        else -> null
    }
    // Auto-hide: once focus has been in the content for the chosen delay, the capsule slides away.
    var railIdleHidden by remember { mutableStateOf(false) }
    // Focus on a top-right pill (reached from the rail) is not "in the rail": the rail closes, so its
    // scrim never covers the pill that holds focus.
    var clusterFocused by remember { mutableStateOf(false) }
    val railHasFocus = focusedLayer == ShellLayer.SIDEBAR && !clusterFocused
    // Whether the rail *looks* focused. Focus also lands on the rail by itself — for a moment while a
    // page swaps, or for as long as a series is still loading and has nothing to focus — and showing
    // those visits opened the floating rail and restarted its hide delay, so it popped up on every
    // page (#7.1). So a visit opens it only when it was meant: within [RAIL_KEY_WINDOW_MS] of ◀ / ▶ or
    // of [focusRail], or once a key is pressed while the rail holds focus.
    var railFocused by remember { mutableStateOf(false) }
    // Keyed on [railKeyAt] too: at start-up focus already counts as on the rail before [focusRail] runs.
    LaunchedEffect(railHasFocus, railKeyAt) {
        railFocused = railHasFocus && android.os.SystemClock.uptimeMillis() - railKeyAt <= RAIL_KEY_WINDOW_MS
    }
    LaunchedEffect(railFocused, navStyle, navHideAfterMs) {
        railIdleHidden = false
        if (navStyle == tv.own.owntv.core.settings.SettingsRepository.NavStyle.FLOATING && !railFocused) {
            kotlinx.coroutines.delay(navHideAfterMs.toLong())
            railIdleHidden = true
        }
    }
    // Docked always shows its Size; Floating rests as the capsule, opens at its Size while it holds
    // focus and hides once focus has been in the content for the delay. Compact stays the capsule —
    // except Docked + Compact with Widen on focus, which opens at that size over the content (the content
    // keeps the compact room, so nothing behind it moves).
    val widenDocked = navStyle == tv.own.owntv.core.settings.SettingsRepository.NavStyle.DOCKED && navSize == tv.own.owntv.core.settings.SettingsRepository.NavSize.COMPACT && navWiden != null && railFocused
    val railSize = if (widenDocked) navWiden ?: navSize else navSize
    val railState = when {
        widenDocked -> tv.own.myowntv.features.shell.components.RailState.OPEN
        navStyle == tv.own.owntv.core.settings.SettingsRepository.NavStyle.DOCKED -> tv.own.myowntv.features.shell.components.RailState.PINNED
        railFocused -> tv.own.myowntv.features.shell.components.RailState.OPEN
        railIdleHidden -> tv.own.myowntv.features.shell.components.RailState.HIDDEN
        else -> tv.own.myowntv.features.shell.components.RailState.CAPSULE
    }
    // OK on the rail item moves focus INTO the window (it is not a section and navigates nowhere).
    val enterNowPlaying = {
        when (playerMode) {
            PlayerMode.MINI -> runCatching { miniEntryFocus.requestFocus() }
            PlayerMode.AUDIO -> runCatching { audioEntryFocus.requestFocus() }
            else -> Unit
        }
        Unit
    }
    // Long-press Back toggles: into the window, and back out to the control you came from.
    val toggleNowPlayingFocus = {
        if (miniHasFocus) runCatching { shellContentFocus.requestFocus() } else enterNowPlaying()
        Unit
    }

    val continueLastWatched = {
        continueTarget?.let { target ->
            if (target.kind == tv.own.myowntv.features.home.ContinueKind.LIVE) {
                scope.launch {
                    if (liveVm.ensurePlayingByIdAsync(target.channelId)) openFullscreen()
                }
            }
        }
        Unit
    }
    val openShortcutSection: (MainSection) -> Unit = { section ->
        if (playerMode == PlayerMode.FULLSCREEN) dockPlayer()
        if (section == MainSection.SEARCH) {
            searchVm.setQuery("")
        }
        onSelectSection(section)
        // A shortcut may be fired by a control inside the destination being replaced (most notably
        // Settings). Once that screen leaves composition its focused node disappears, so hand focus
        // to the persistent sidebar after the new destination has rendered.
        scope.launch {
            withFrameNanos { }
            focusRail()
        }
    }
    val dispatchRemoteShortcut: (RemoteShortcutAction) -> Unit = { action ->
        when (action) {
            RemoteShortcutAction.OPEN_HOME -> openShortcutSection(MainSection.HOME)
            RemoteShortcutAction.OPEN_LIVE_TV -> openShortcutSection(MainSection.LIVE_TV)
            // The VOD sections are gone; a shortcut still bound to one does nothing until the core
            // enum sheds these entries (wave 2).
            RemoteShortcutAction.OPEN_MOVIES, RemoteShortcutAction.OPEN_SERIES, RemoteShortcutAction.OPEN_DOWNLOADS -> Unit
            RemoteShortcutAction.OPEN_PLAYLIST_SWITCHER -> Unit
            RemoteShortcutAction.OPEN_GUIDE -> openShortcutSection(MainSection.EPG)
            RemoteShortcutAction.OPEN_SEARCH -> openShortcutSection(MainSection.SEARCH)
            RemoteShortcutAction.OPEN_SETTINGS -> openShortcutSection(MainSection.SETTINGS)
            RemoteShortcutAction.CONTINUE_LAST_WATCHED -> continueLastWatched()
            RemoteShortcutAction.FOCUS_NOW_PLAYING -> enterNowPlaying()
            RemoteShortcutAction.EXPAND_NOW_PLAYING -> if (playerMode == PlayerMode.MINI || playerMode == PlayerMode.AUDIO) expandPlayer()
            RemoteShortcutAction.ENTER_MINI_PLAYER -> if (playerMode == PlayerMode.FULLSCREEN) dockPlayer()
            RemoteShortcutAction.ENTER_AUDIO_MODE -> if (playerMode == PlayerMode.FULLSCREEN || playerMode == PlayerMode.MINI) toAudioMode()
            RemoteShortcutAction.PLAY_PAUSE -> if (playerMode != PlayerMode.NONE) dockedEngine.togglePlayPause()
            // "Return to live" re-tunes the channel's live stream — the one live-edge action left after
            // rewind went; harmless (and cheap) when already at the live edge.
            RemoteShortcutAction.RETURN_TO_LIVE -> if (playerMode != PlayerMode.NONE && zapSource == MainSection.LIVE_TV) liveVm.goToLive()
            RemoteShortcutAction.PREVIOUS_CHANNEL -> if (playerMode != PlayerMode.NONE && zapSource == MainSection.LIVE_TV) liveVm.previousChannel()
            RemoteShortcutAction.PAGE_TOWARD_FIRST,
            RemoteShortcutAction.PAGE_TOWARD_LAST,
            RemoteShortcutAction.JUMP_TO_FIRST,
            RemoteShortcutAction.JUMP_TO_LAST,
            RemoteShortcutAction.OPEN_SUBTITLE_CONTROLS,
            RemoteShortcutAction.OPEN_AUDIO_CONTROLS,
            RemoteShortcutAction.OPEN_ASPECT_CONTROLS,
            RemoteShortcutAction.TOGGLE_PLAYBACK_INFO,
            -> Unit
        }
    }
    val latestRemoteShortcutDispatch by rememberUpdatedState(dispatchRemoteShortcut)
    val remoteShortcutEnvironment = remember(remoteShortcutsEnabled, remoteShortcutBindings) {
        RemoteShortcutEnvironment(
            enabled = remoteShortcutsEnabled,
            bindings = remoteShortcutBindings,
            dispatch = { action -> latestRemoteShortcutDispatch(action) },
        )
    }

    LaunchedEffect(sidebarFocus) {
        tv.own.owntv.core.util.Perf.stamp("shell-composed")
        withFrameNanos { }
        focusRail()
    }

    LaunchedEffect(pendingDeepLink) {
        val deepLink = pendingDeepLink ?: return@LaunchedEffect
        when (deepLink) {
            LauncherDeepLink.OpenLiveSection -> {
                onSelectSection(MainSection.LIVE_TV)
                onDeepLinkConsumed()
            }
            LauncherDeepLink.OpenFavoritesSection -> {
                onSelectSection(MainSection.LIVE_TV)
                liveVm.show(LiveKey.Favorites)
                onDeepLinkConsumed()
            }
            else -> {
                // Live links resolve through the live view model; old movie/episode/show links
                // predate the live-only app and are consumed silently.
                val channel = (deepLink as? LauncherDeepLink.Live)?.let { liveVm.resolveLauncherChannel(it) }
                if (channel != null) {
                    onSelectSection(MainSection.LIVE_TV)
                    // A launcher tile has no browse rail, so arm the channel's provider context before
                    // opening the player. A bare ensurePlaying() leaves CH+/CH- with no list to step through.
                    if (liveVm.watchFullscreen(channel, emptyList())) openFullscreen()
                }
                onDeepLinkConsumed()
            }
        }
    }

    // Stop a leftover live preview when you leave the Live section or the Guide, which previews through it
    // too (but never while fullscreen/mini plays).
    // The preview runs on the ExoPlayer live engine: stopping only the fullscreen player here (as when a
    // second engine owned the preview) left it decoding and holding a provider connection after a
    // shortcut or deep link out.
    LaunchedEffect(selectedSection, playerMode) {
        if (selectedSection != MainSection.LIVE_TV && selectedSection != MainSection.EPG && playerMode == PlayerMode.NONE) liveVm.stopPreview()
        if (selectedSection != MainSection.HOME || playerMode != PlayerMode.NONE) homeVm.stopPreview()
    }

    LaunchedEffect(selectedSection, playerMode) {
        if (selectedSection == MainSection.HOME && playerMode == PlayerMode.NONE) {
            homeVm.refresh()
        }
    }

    // The section a Search result opened ("Go to"), so the first Back there returns to Search. Leaving
    // that section any other way forgets it.
    fun returnToSearch() {
        searchReturn = null
        onSelectSection(MainSection.SEARCH)
    }
    LaunchedEffect(selectedSection) {
        if (selectedSection != MainSection.SEARCH && selectedSection != searchReturn) searchReturn = null
    }

    BackHandler {
        when {
            playerMode == PlayerMode.FULLSCREEN -> exitPlayer()
            showExit -> showExit = false
            // Settings is reached through More now, so Back out of its root goes back there — one
            // level out, not two. `SettingsScreen` passes its own `onBack` for the same purpose, but
            // its root handler does not always win against this one, which left Back looking dead on
            // the first press: the fallback below simply moved focus to the rail and nothing else
            // happened. Handled here as well so the answer is the same whichever handler fires; a
            // sub-screen of Settings still wins, because its handler is composed deeper than this.
            selectedSection == MainSection.SETTINGS -> { restoreFocus = true; onSelectSection(MainSection.MORE) }
            // Reached from a Search result: Back returns to Search once, its query, tab and row kept.
            searchReturn == selectedSection && focusedLayer != ShellLayer.SIDEBAR -> returnToSearch()
            focusedLayer == ShellLayer.SIDEBAR -> showExit = true
            else -> focusRail()
        }
    }

    // With Glass on, the shell's own base paints must be transparent so the background (rendered in
    // MainActivity behind this shell) shows through the gaps between and around panels. Solid otherwise.
    val shellBase = if (LocalGlass.current.enabled) Color.Transparent else tv.own.myowntv.ui.theme.LocalStagePalette.current.page

    // Long-press Back (the second way in). Deliberately timed from the FIRST KeyDown rather than counted from
    // key repeats: several TVs re-fire KeyDown while a key is held, so a repeat-counting version fires
    // the long-press the instant Back is touched. Short Back is never consumed, so every existing Back
    // handler — HUD hide, search clear, dialog dismiss, direct-tune cancel — behaves exactly as before.
    val backDispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    var backHoldJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var backLongFired by remember { mutableStateOf(false) }
    var shortcutKeyCode by remember { mutableStateOf(android.view.KeyEvent.KEYCODE_UNKNOWN) }
    var shortcutLongFired by remember { mutableStateOf(false) }
    var shortcutHoldJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val longPressBackArmed = playerMode == PlayerMode.MINI && !showExit
    // Media keys (the third way in) act on the docked window wherever focus is — but only if nothing nearer
    // claimed them first, which is why this is a bubbling handler: a focused browse list still gets its
    // CH+/CH− paging, and the full-screen HUD still owns zapping.
    val dockedZap: ((Int) -> Unit)? = when {
        playerMode != PlayerMode.MINI -> null
        zapSource == MainSection.LIVE_TV && liveCanZap -> liveVm::zap
        else -> null
    }

    CompositionLocalProvider(
        LocalRemoteShortcuts provides remoteShortcutEnvironment,
        tv.own.myowntv.features.shell.components.LocalClockWeather provides (weatherInfo to weatherFahrenheit),
    ) {
    Box(
        modifier = modifier.fillMaxSize().background(shellBase)
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown) {
                    if (e.key == Key.DirectionLeft || e.key == Key.DirectionRight) railKeyAt = android.os.SystemClock.uptimeMillis()
                    // A key pressed on the rail is the user using it, however focus got there.
                    if (railHasFocus) railFocused = true
                }
                if (e.key != Key.Back) return@onPreviewKeyEvent false
                when (e.type) {
                    KeyEventType.KeyDown -> {
                        if (longPressBackArmed && backHoldJob == null) {
                            backLongFired = false
                            backHoldJob = scope.launch {
                                kotlinx.coroutines.delay(600)
                                backLongFired = true
                                toggleNowPlayingFocus()
                            }
                        }
                        false
                    }
                    KeyEventType.KeyUp -> {
                        backHoldJob?.cancel()
                        backHoldJob = null
                        val fired = backLongFired
                        backLongFired = false
                        fired // consume ONLY when the long-press already acted, so Back isn't run twice
                    }
                    else -> false
                }
            }
            .onKeyEvent { e ->
                val shortcutKey = e.nativeKeyEvent.keyCode
                val shellBindings = remoteShortcutBindings.filter {
                    it.keyCode == shortcutKey &&
                        it.action != RemoteShortcutAction.PAGE_TOWARD_FIRST &&
                        it.action != RemoteShortcutAction.PAGE_TOWARD_LAST &&
                        it.action != RemoteShortcutAction.JUMP_TO_FIRST &&
                        it.action != RemoteShortcutAction.JUMP_TO_LAST
                }
                if (remoteShortcutsEnabled && shellBindings.isNotEmpty()) {
                    when (e.type) {
                        KeyEventType.KeyDown -> {
                            if (shortcutKeyCode != shortcutKey) {
                                shortcutHoldJob?.cancel()
                                shortcutKeyCode = shortcutKey
                                shortcutLongFired = false
                                shellBindings.firstOrNull { it.press == tv.own.owntv.core.settings.RemoteShortcutPress.LONG }
                                    ?.let { binding ->
                                        shortcutHoldJob = scope.launch {
                                            kotlinx.coroutines.delay(600)
                                            if (shortcutKeyCode == shortcutKey) {
                                                shortcutLongFired = true
                                                dispatchRemoteShortcut(binding.action)
                                            }
                                        }
                                    }
                            }
                            return@onKeyEvent true
                        }
                        KeyEventType.KeyUp -> if (shortcutKeyCode == shortcutKey) {
                            shortcutHoldJob?.cancel()
                            shortcutHoldJob = null
                            if (!shortcutLongFired) {
                                shellBindings.firstOrNull { it.press == tv.own.owntv.core.settings.RemoteShortcutPress.SHORT }
                                    ?.let { dispatchRemoteShortcut(it.action) }
                            }
                            shortcutKeyCode = android.view.KeyEvent.KEYCODE_UNKNOWN
                            shortcutLongFired = false
                            return@onKeyEvent true
                        }
                    }
                }
                // Back that bubbled all the way up here is a Back nothing nearer wanted as a key, so it
                // is ours to turn into a back-press. Compose would otherwise map it to
                // FocusDirection.Exit and spend it moving focus one level out of whatever focus target
                // holds it — silently, because the highlight usually does not visibly move. That
                // consumed the KeyDown before Activity.onKeyDown could startTracking(), so the KeyUp
                // was discarded as untracked and NO BackHandler ran: Back out of Settings looked dead
                // until a second press, by which time the exit had nowhere left to go. Running the
                // dispatcher here keeps the answer the same on the first press, everywhere.
                // Deeper key handlers (a text field's Back-to-close, the direct-tune cancel) still win,
                // because this is a bubbling handler and they have already declined by the time it runs.
                if (e.key == Key.Back) {
                    if (e.type == KeyEventType.KeyUp && !e.nativeKeyEvent.isCanceled) backDispatcher?.onBackPressed()
                    return@onKeyEvent backDispatcher != null
                }
                if (e.type != KeyEventType.KeyDown || playerMode != PlayerMode.MINI) return@onKeyEvent false
                when (e.key) {
                    Key.MediaPlayPause -> { dockedEngine.togglePlayPause(); true }
                    // PLAY and PAUSE say which one: a toggle turned a PLAY sent while already playing (a
                    // remote, or the system after the television comes back) into a pause.
                    Key.MediaPlay -> { if (!dockedEngine.isPlaying.value) dockedEngine.togglePlayPause(); true }
                    Key.MediaPause -> { if (dockedEngine.isPlaying.value) dockedEngine.togglePlayPause(); true }
                    Key.ChannelUp -> dockedZap?.let { it(tv.own.myowntv.player.zapDirection(up = true, playerRemoteOptions)); true } ?: false
                    Key.ChannelDown -> dockedZap?.let { it(tv.own.myowntv.player.zapDirection(up = false, playerRemoteOptions)); true } ?: false
                    else -> false
                }
            },
    ) {
      // Browse UI — hidden while the player is fullscreen (stays visible behind the docked mini-player).
      if (playerMode != PlayerMode.FULLSCREEN) {
        Column(
            modifier = Modifier.fillMaxSize()
                // One group with a restorer: leaving for the mini player and coming back lands on the
                // exact control that was focused, without every screen having to remember its own.
                .focusRequester(shellContentFocus)
                .focusRestorer()
                .focusGroup(),
        ) {
          if (isOffline) OfflineBanner()
          val selectFromNav: (MainSection) -> Unit = { section ->
              if (section == MainSection.SEARCH) {
                  searchVm.setQuery("")
              }
              onSelectSection(section)
          }
          // Audio Mode: the now-playing bar — in the top bar left of the weather chip, or in the Stage
          // cluster in the Continue pill's place. Present only while PlayerMode.AUDIO.
          val audioBarContent: (@Composable () -> Unit)? = if (playerMode == PlayerMode.AUDIO) {
              {
                  val zapFn: ((Int) -> Unit)? = when {
                      zapSource == MainSection.LIVE_TV && liveCanZap -> liveVm::zap
                      else -> null
                  }
                  tv.own.myowntv.player.AudioNowPlayingBar(
                      player = liveVm.previewEngine,
                      canPrev = zapFn != null,
                      canNext = zapFn != null,
                      onPrev = { zapFn?.invoke(-1) },
                      onNext = { zapFn?.invoke(1) },
                      onExpand = expandPlayer,
                      onClose = exitPlayer,
                      // Always reachable while Audio Mode is active (from the Search/Continue
                      // pills on the left or the playlist chip on the right) — not gated on the
                      // nav panel like the other chips, because its own D-pad trap keeps focus
                      // inside once entered and Back is the only way out.
                      focusable = true,
                      entryFocusRequester = audioEntryFocus,
                      favorite = favActive,
                      onToggleFavorite = favToggle,
                  )
              }
          } else null
          Box(Modifier.weight(1f).fillMaxWidth()) {
          Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    // Transparent while Glass is on, so the picture or the Stage page shows through the gaps
                    // between the panels; the page colour otherwise.
                    .background(shellBase)
                    // The Stage page, across the whole width — under a docked rail's reserve too.
                    // Over a picture it draws only the Accent light; the picture shows through.
                    .stageBackground(tv.own.myowntv.ui.theme.stageAccent.accent),
            ) {
                // Live TV's view (List / Guide), and how a guide — the TV Guide or Live TV's Guide view —
                // starts a channel: through LiveViewModel, the one live path.
                val liveView by liveVm.liveView.collectAsStateWithLifecycle()
                val guidePlayChannel: (ChannelEntity, List<ChannelEntity>) -> Unit = { ch, _ ->
                    restoreFocus = false
                    liveVm.watchFromGuide(ch)
                    zapSource = MainSection.LIVE_TV
                    homeVm.stopPreview()
                    // Live TV set to play externally → the channel went to another app;
                    // don't mount the fullscreen player over it.
                    if (playerMode != PlayerMode.MINI && !liveVm.externalPlayerOn.value) playerMode = PlayerMode.FULLSCREEN
                }
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth()
                        .then(
                            // The floating rail sits over the content, so the content starts at the screen
                            // edge (or beside a docked rail).
                            Modifier
                                .padding(
                                    start = if (navStyle == tv.own.owntv.core.settings.SettingsRepository.NavStyle.DOCKED) {
                                        tv.own.myowntv.features.shell.components.railDockedReserve(navSize)
                                    } else 0.dp,
                                )
                                // ◀ with nothing further left in the content goes back to the rail, however the
                                // content was entered. The D-pad search alone missed it whenever the open rail
                                // overlapped the content's left edge, which left Back as the only way out.
                                .onKeyEvent { e ->
                                    val back = if (contentLtr) Key.DirectionLeft else Key.DirectionRight
                                    if (e.key != back || e.type != KeyEventType.KeyDown) return@onKeyEvent false
                                    if (!contentFocusManager.moveFocus(if (contentLtr) androidx.compose.ui.focus.FocusDirection.Left else androidx.compose.ui.focus.FocusDirection.Right)) {
                                        focusRail()
                                    }
                                    true
                                }
                                .focusRequester(contentAreaFocus)
                                .focusRestorer()
                                .focusGroup(),
                        ),
                ) {
                    when {
                        // More — the hub the rail's last item opens. Settings is a row in it.
                        selectedSection == MainSection.MORE -> tv.own.myowntv.features.more.MoreScreen(
                            onOpenSettings = { group, search ->
                                settingsStart = tv.own.myowntv.features.shell.components.SettingsStart(group, search)
                                onSelectSection(MainSection.SETTINGS)
                            },
                            // The same path Home uses, so the engine ladder and Continue all apply.
                            onPlayChannel = { id -> scope.launch { if (liveVm.ensurePlayingByIdAsync(id)) openFullscreen() } },
                            onChildFocused = { focusedLayer = ShellLayer.CONTENT },
                            onEntryHook = { moreEntry = it },
                            restoreFocus = restoreFocus,
                            onRestored = { restoreFocus = false },
                            modifier = Modifier
                                .fillMaxSize()
                                .onFocusChanged { if (it.hasFocus) focusedLayer = ShellLayer.CONTENT }
                                .focusGroup(),
                        )

                        selectedSection == MainSection.SETTINGS -> tv.own.myowntv.features.settings.lock.SettingsPinGate(onDismiss = { settingsStart = null; restoreFocus = true; onSelectSection(shellVm.sectionToLeaveLockedSettings()) }) { SettingsScreen(
                            themeMode = themeMode,
                            uiZoomPercent = uiZoomPercent,
                            onSetZoom = onSetZoom,
                            fontCustomization = fontCustomization,
                            onSetFontCustomization = onSetFontCustomization,
                            // Settings is reached through More now, so Back out of its root goes
                            // back there rather than to the rail — one level out, not two.
                            onBack = { restoreFocus = true; onSelectSection(MainSection.MORE) },
                            start = settingsStart,
                            onStartConsumed = { settingsStart = null },
                            modifier = Modifier
                                .fillMaxSize()
                                .onFocusChanged { if (it.hasFocus) focusedLayer = ShellLayer.CONTENT }
                                .focusGroup(),
                        ) }

                        selectedSection == MainSection.HOME -> HomeScreen(
                            vm = homeVm,
                            onPlayChannel = { id, zap -> scope.launch { if (liveVm.ensurePlayingByIdAsync(id, zap)) openFullscreen() } },
                            onChildFocused = { focusedLayer = ShellLayer.CONTENT },
                            restoreFocus = restoreFocus,
                            onRestored = {
                                restoreFocus = false
                            },
                            previewEnabled = playerMode == PlayerMode.NONE,
                            firstRowFocusRequester = homeFirstRowFocus,
                            // Beside the resting capsule; a docked rail already reserves its own width.
                            onEntryHook = { homeEntry = it },
                            contentStart = if (navStyle == tv.own.owntv.core.settings.SettingsRepository.NavStyle.DOCKED) 64.mpx else 150.mpx,
                            modifier = Modifier.fillMaxSize(),
                        )

                        selectedSection == MainSection.SEARCH -> SearchScreen(
                            vm = searchVm,
                            liveVm = liveVm,
                            previewEnabled = playerMode == PlayerMode.NONE,
                            // OK on a result goes to it and Back comes back here once
                            // (searchReturn): the channel is revealed in its own category.
                            onGoToChannel = { ch ->
                                restoreFocus = false
                                if (liveView == tv.own.owntv.core.settings.SettingsRepository.LiveView.GUIDE) {
                                    liveVm.setLiveView(tv.own.owntv.core.settings.SettingsRepository.LiveView.LIST)
                                }
                                liveVm.revealChannel(ch)
                                searchReturn = MainSection.LIVE_TV
                                onSelectSection(MainSection.LIVE_TV)
                            },
                            onChildFocused = { focusedLayer = ShellLayer.CONTENT },
                            modifier = Modifier.fillMaxSize(),
                        )

                        selectedSection == MainSection.LIVE_TV && liveView == tv.own.owntv.core.settings.SettingsRepository.LiveView.GUIDE -> EpgScreen(
                            onPlayChannel = guidePlayChannel,
                            previewEnabled = playerMode == PlayerMode.NONE,
                            liveMode = true,
                            onEntryHook = { guideEntry = it },
                            onListView = { liveVm.setLiveView(tv.own.owntv.core.settings.SettingsRepository.LiveView.LIST) },
                            restoreFocus = restoreFocus,
                            onRestored = { restoreFocus = false },
                            modifier = Modifier
                                .fillMaxSize()
                                .onFocusChanged { if (it.hasFocus) focusedLayer = ShellLayer.CONTENT }
                                .focusGroup(),
                        )

                        selectedSection == MainSection.LIVE_TV -> LiveScreen(
                            onFullscreen = { openFullscreen() },
                            onOpenGuide = { liveVm.setLiveView(tv.own.owntv.core.settings.SettingsRepository.LiveView.GUIDE) },
                            onChildFocused = { focusedLayer = ShellLayer.CONTENT },
                            previewEnabled = playerMode == PlayerMode.NONE,
                            restoreFocus = restoreFocus,
                            onRestored = { restoreFocus = false },
                            modifier = Modifier.fillMaxSize(),
                        )

                        selectedSection == MainSection.EPG -> EpgScreen(
                            onFullscreen = { openFullscreen() },
                            onPlayChannel = guidePlayChannel,
                            previewEnabled = playerMode == PlayerMode.NONE,
                            onEntryHook = { guideEntry = it },
                            restoreFocus = restoreFocus,
                            onRestored = { restoreFocus = false },
                            modifier = Modifier
                                .fillMaxSize()
                                .onFocusChanged { if (it.hasFocus) focusedLayer = ShellLayer.CONTENT }
                                .focusGroup(),
                        )

                    }
                }
            }
          }
            tv.own.myowntv.features.shell.components.StageStatusCluster(
                continuePill = continueTarget?.let { target ->
                    val live = target.kind == tv.own.myowntv.features.home.ContinueKind.LIVE
                    tv.own.myowntv.features.shell.components.ContinuePill(
                        icon = if (live) MyOwnTVIcon.LIVE_TV else MyOwnTVIcon.PLAY,
                        iconFilled = !live,
                        action = stringResource(
                            when (target.action) {
                                tv.own.myowntv.features.home.ContinueAction.LAST_CHANNEL -> R.string.content_action_last_channel
                                else -> R.string.content_action_resume
                            },
                        ),
                        title = target.name,
                        onClick = continueLastWatched,
                    )
                },
                audioBar = audioBarContent,
                // Out of reach from deep in a list: from the rail or on Home only.
                pillsFocusable = focusedLayer == ShellLayer.SIDEBAR || clusterFocused || selectedSection == MainSection.HOME,
                weatherInfo = weatherInfo,
                weatherFahrenheit = weatherFahrenheit,
                clockColors = clockColors,
                playlistDownFocusRequester = homeFirstRowFocus.takeIf { selectedSection == MainSection.HOME },
                modifier = Modifier.align(Alignment.TopEnd).onFocusChanged { clusterFocused = it.hasFocus }.focusGroup(),
            )
            tv.own.myowntv.features.shell.components.StageRail(
                state = railState,
                size = railSize,
                length = navLength,
                selected = selectedSection,
                visibleSections = visibleSections,
                onSelect = selectFromNav,
                count = { section -> railCounts[section]?.takeIf { it > 0 } },
                detail = { section -> railDetail(section, railDetails) },
                selectedItemFocusRequester = sidebarFocus,
                contentFocusRequester = contentAreaFocus,
                enterContent = when (selectedSection) {
                    MainSection.HOME -> homeEntry
                    MainSection.EPG, MainSection.LIVE_TV -> guideEntry
                    MainSection.MORE -> moreEntry
                    else -> null
                },
                onFocused = { focusedLayer = ShellLayer.SIDEBAR },
                nowPlaying = nowPlayingRail,
                onNowPlaying = enterNowPlaying,
            )
          }
        }
        // The solid-mode wizard aura is deliberately drawn after the opaque browse surfaces so it
        // remains visible, exactly like the approved concept. It never intercepts input; fullscreen
        // video and glass mode skip it entirely. Global Animations Off also freezes the slow pulse.
        SolidAmbientBackdrop(
            glowEnabled = ambientGlowEnabled,
            pulseEnabled = ambientGlowPulse && shellAnimationLevel != tv.own.owntv.core.theme.AnimationLevel.OFF,
            modifier = Modifier.fillMaxSize(),
        )
      }

      // Unobtrusive background-sync pill (bottom middle): visible while any catalog sync runs —
      // backgrounded first import, remainder worker, auto refresh — but never over fullscreen video.
      if (playerMode != PlayerMode.FULLSCREEN) {
          tv.own.myowntv.features.shell.components.SyncStatusPill(modifier = Modifier.align(Alignment.BottomCenter))
      }

      // Player surface — hoisted so it persists across fullscreen <-> mini (same call site = the
      // SurfaceView isn't recreated when docking/expanding, so playback never blips). NOT composed in
      // AUDIO mode: there's no video surface — audio plays and the top-bar now-playing bar drives it.
      // Multiview draws over everything, including the player it grew out of, and owns its own
      // engines. The player surface below is not composed while it is up: its stream was stopped when
      // the grid opened, so there is nothing behind the tiles to keep alive.
      multiview?.let { grid ->
          tv.own.myowntv.features.multiview.MultiviewScreen(
              state = grid,
              // Categories first, then that category's channels. The player's overlay starts at the
              // channels because it already has one playing and its category is the obvious place to
              // look; an empty tile has no such context, and a flat list of every channel across
              // every playlist is tens of thousands of rows to scroll.
              onPickChannel = { tile -> multiviewPickFor = tile; liveVm.showCategories() },
              onFullscreen = { channel ->
                  grid.releaseAll()
                  multiview = null
                  multiviewPickFor = null
                  liveVm.ensurePlaying(channel)
                  playerMode = PlayerMode.FULLSCREEN
              },
              // Leaving the grid leaves *playback*, by design.
              //
              // It used to promote the focused tile to full screen, which is what the plan asked for.
              // In use that is wrong twice over: a grid of four is put away by someone who has
              // finished watching, and being handed one of the four full screen means a stream is
              // still running and still costing a connection when the user believes they closed
              // everything. Back now lands on the Live TV list, with nothing playing.
              onExit = {
                  grid.releaseAll()
                  multiview = null
                  multiviewPickFor = null
                  exitPlayer()
              },
              modifier = Modifier.fillMaxSize(),
          )
          // Filling a tile reuses the very list the Live screen and the in-player overlay use, so the
          // categories, ordering and hidden channels can never disagree between them.
          multiviewPickFor?.let { tile ->
              if (showCategoryBrowser) {
                  // Step one, and where the picker always starts: every Live TV category across every
                  // playlist, so a tile can be filled from a playlist the user was not browsing —
                  // which is the whole point of the grid. Back here leaves the picker entirely.
                  tv.own.myowntv.features.shell.components.CategoryBrowserOverlay(
                      railItems = browserRailItems,
                      railCounts = browserRailCounts,
                      currentKey = grid.tiles.getOrNull(tile)?.channel?.let { c -> tv.own.owntv.core.database.ChannelMembership.primaryCat(c) }?.let { tv.own.owntv.core.live.LiveKey.Folder(it) },
                      onSelect = { key -> liveVm.loadChannelsForCategory(key) },
                      onDismiss = { liveVm.hideCategoryBrowser(); multiviewPickFor = null },
                      modifier = Modifier.fillMaxSize(),
                  )
              } else if (zapChannels.isNotEmpty()) {
                  tv.own.myowntv.features.shell.components.ChannelListOverlay(
                      channels = zapChannels,
                      currentId = grid.tiles.getOrNull(tile)?.channel?.id,
                      title = zapOverlayTitle,
                      showNumbers = directTuneEnabled,
                      nowProgrammes = overlayProgrammes,
                      favoriteIds = overlayFavoriteIds,
                      alignEnd = true,
                      // Step two: that category's channels. Back goes up to the categories rather than
                      // out, so a wrong turn costs one press instead of starting again.
                      onOpenCategories = { liveVm.showCategories() },
                      onSelect = { grid.fill(tile, it); multiviewPickFor = null },
                      onDismiss = { liveVm.showCategories() },
                      modifier = Modifier.fillMaxSize(),
                  )
              } else {
                  // A category that turns out to be empty must not leave the picker with nothing
                  // drawn: Back would fall past it to the grid's own handler and close Multiview
                  // altogether. Go back to the categories instead, which is where the user was.
                  LaunchedEffect(Unit) { liveVm.showCategories() }
              }
          }
      }

      // Shell-level reminders must survive section changes and appear over browse, Mini/Audio Mode,
      // Multiview and PiP, not only inside the fullscreen player.
      val reminderManager = koinInject<tv.own.owntv.core.reminder.ReminderManager>()
      val reminderMode by settingsRepo.reminderMode.collectAsStateWithLifecycle(tv.own.owntv.core.settings.SettingsRepository.ReminderMode.ASK)
      val dueReminder = rememberDueReminder()
      val watchReminder: (tv.own.owntv.core.database.entity.ReminderEntity) -> Unit = { r ->
          // A reminder switch replaces the whole grid. Release every tile and its resolve job before
          // the full-screen engine starts, or the old tile could resolve into playback underneath it.
          multiview?.releaseAll()
          multiview = null
          multiviewPickFor = null
          scope.launch {
              // Enter the deliberate-tune path first: it cancels any deferred CH+/- before the
              // reminder dismissal's database write can suspend.
              val opened = liveVm.ensurePlayingByIdAsync(r.channelId)
              reminderManager.dismiss(r.id)
              if (opened) openFullscreen()
          }
      }
      val reminderText = dueReminder?.let {
          listOf(it.title, tv.own.myowntv.features.epg.reminderEyebrow(it)).joinToString(" · ")
      }
      LaunchedEffect(dueReminder?.id, reminderMode) {
          val r = dueReminder ?: return@LaunchedEffect
          when (reminderMode) {
              tv.own.owntv.core.settings.SettingsRepository.ReminderMode.SWITCH -> watchReminder(r)
              tv.own.owntv.core.settings.SettingsRepository.ReminderMode.NOTIFY -> {
                  reminderText?.let(shellToast::show)
                  reminderManager.dismiss(r.id)
              }
              tv.own.owntv.core.settings.SettingsRepository.ReminderMode.ASK -> Unit
          }
      }
      if (dueReminder != null && reminderMode == tv.own.owntv.core.settings.SettingsRepository.ReminderMode.ASK) {
          tv.own.myowntv.features.epg.ReminderPrompt(
              reminder = dueReminder,
              onWatch = { watchReminder(dueReminder) },
              onDismiss = { scope.launch { reminderManager.dismiss(dueReminder.id) } },
          )
      }

      if (multiview == null && (playerMode == PlayerMode.FULLSCREEN || playerMode == PlayerMode.MINI)) {
        val isFull = playerMode == PlayerMode.FULLSCREEN
        Box(
            modifier = (if (isFull || inPip) {
                Modifier.fillMaxSize().background(Color.Black)
            } else {
                // Dynamic docked size/position: a screen-width fraction at the chosen corner/edge, so it
                // scales with the panel + UI zoom (never a fixed-size box).
                Modifier.align(miniPos.alignment).padding(24.dp)
                    .fillMaxWidth(tv.own.owntv.core.player.MiniPlayerSize.fraction(miniSizePct)).aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(14.dp)).background(Color.Black)
            }).then(pipSource),
        ) {
            // The live engine's ExoPlayer surface — in BOTH full-screen AND the docked mini-player
            // (same call site = the surface persists across dock/expand, so playback never blips).
            // Subtitles are drawn by the surface itself while a subtitle track is on.
            tv.own.myowntv.player.ExoPreviewSurface(
                engine = liveVm.previewEngine, modifier = Modifier.fillMaxSize(),
                keepAwake = true, autoFrameRate = isFull && !inPip && autoFrameRateLive,
                useTextureView = videoSurface == tv.own.owntv.core.settings.SettingsRepository.VideoSurface.TEXTURE_VIEW,
            )
            // The channel has no video track of its own (a radio channel). Playing it is correct — but a
            // black screen with sound reads as a broken player, so name what is happening. Only ever
            // composed when there is no video to lose.
            val audioOnlyMedia by liveVm.previewEngine.audioOnlyMedia.collectAsStateWithLifecycle()
            if (audioOnlyMedia && !inPip) {
                tv.own.myowntv.player.AudioOnlyBadge(modifier = Modifier.fillMaxSize(), compact = !isFull)
            }
            if (isFull && !inPip && !autoFrameRateLive && !afrPrompted) {
                // Frame rate of the live engine — what the direct render path judders on; it survives
                // "Measured stream stats" being off.
                val activeFps by liveVm.previewEngine.videoFps.collectAsStateWithLifecycle()
                tv.own.myowntv.player.AutoFrameRatePrompt(
                    fps = activeFps,
                    afrEnabled = autoFrameRateLive,
                    alreadyPrompted = afrPrompted,
                    // Mark it answered either way. Enabling only set the setting, so a user who later
                    // turned Auto frame rate back off was offered the "once ever" suggestion all over again.
                    onEnable = {
                        scope.launch {
                            settingsRepo.setAutoFrameRateLive(true)
                            settingsRepo.setAutoFrameRatePrompted()
                        }
                    },
                    onDismiss = { scope.launch { settingsRepo.setAutoFrameRatePrompted() } },
                )
            }
            if (isFull && !inPip) {
                // Dedicated CH+/CH- belong to the Live TV player for its whole lifetime. Do not
                // derive this callback from current list readiness: it legitimately goes through short
                // false/empty windows during startup and retune. Browse panels keep their configurable
                // paging because PlayerHud exists only in full-screen here.
                val zap: ((Int) -> Unit)? =
                    if (zapSource == MainSection.LIVE_TV) liveVm::zap else null
                val isLiveChannel = zapSource == MainSection.LIVE_TV
                // Everything the fullscreen player shows is a tuned live channel.
                val isTunedLive = isLiveChannel
                PlayerHud(
                    player = liveVm.previewEngine, // HUD drives the one live engine
                    onBack = exitPlayer,
                    onPip = dockPlayer,
                    onAudioMode = toAudioMode,
                    // The channel-list overlay draws ABOVE the HUD; while it's open the HUD goes inert so
                    // its hide/error focus grabs can't yank D-pad focus off the overlay.
                    inert = showChannelList || showHistoryList || showCategoryBrowser,
                    onChannelUp = zap?.let { z -> { z(-1) } },
                    onChannelDown = zap?.let { z -> { z(1) } },
                    onOpenChannelList = if (isTunedLive && liveCanZap) { { showChannelList = true } } else null,
                    // Live channels only, and only once Multiview is switched on: the channel on screen
                    // becomes tile 1 and the grid takes over. Everything it needs is already tuned.
                    onMultiview = if (multiviewEnabled && isTunedLive && previewChannel != null) {
                        { multiview = openMultiview(previewChannel, emptyList()) }
                    } else {
                        null
                    },
                    onOpenHistoryList = if (isTunedLive) { { showHistoryList = true } } else null,
                    onPreviousChannel = if (isTunedLive && previousLiveChannel != null) liveVm::previousChannel else null,
                    // "End of programme" sleep: the programme on air is the one being watched.
                    sleepProgrammeEndMs = if (isTunedLive) { { liveVm.nowNext.value?.now?.stopMs } } else null,
                    onTuneToNumber = if (tv.own.myowntv.player.numberKeysTune(directTuneEnabled, playerRemoteOptions) && isTunedLive && previewChannel != null) liveVm::tuneByNumber else null,
                    directTuneContextKey = previewChannel?.id.orEmpty(),
                    remoteOptions = playerRemoteOptions,
                    // In-stream favorite toggle for the current channel.
                    favorite = favActive,
                    onToggleFavorite = favToggle,
                    // The zap banner's now/next. Collected inside the slot, so only the banner redraws.
                    zapGuide = if (isLiveChannel) {
                        {
                            val guide by liveVm.zapGuide.collectAsStateWithLifecycle()
                            tv.own.myowntv.features.shell.components.ZapGuideLines(guide)
                        }
                    } else null,
                    zapChannel = if (isLiveChannel) {
                        previewChannel?.let { tv.own.myowntv.player.DirectTuneChannelInfo(it.number, it.name, it.logoUrl) }
                    } else null,
                    // Guide card for the playing channel (nowNext follows previewChannel = what's playing).
                    liveEpgCard = if (isLiveChannel) {
                        {
                            val epg by liveVm.nowNext.collectAsStateWithLifecycle()
                            tv.own.myowntv.features.shell.components.LiveEpgCard(epg = epg)
                        }
                    } else null,
                    modifier = Modifier.fillMaxSize(),
                )
                // Left — the playing channel's own provider category.
                if (showChannelList && isLiveChannel) {
                    if (showCategoryBrowser) {
                        // Second Left — every Live TV category beside the chosen one's channels. Back
                        // closes both panes and the list with them.
                        tv.own.myowntv.features.shell.components.ChannelBrowserOverlay(
                            railItems = browserRailItems,
                            railCounts = browserRailCounts,
                            shownKey = peekKey,
                            channels = peekChannels,
                            channelsTitle = peekTitle,
                            playingId = previewChannel?.id,
                            entryChannelId = liveVm::peekEntryChannel,
                            onPickCategory = liveVm::peekCategory,
                            onFocusChannel = { liveVm.rememberPeekFocus(it.id) },
                            onWatch = { liveVm.watchFromPeek(it); showChannelList = false },
                            onDismiss = { liveVm.hideCategoryBrowser(); showChannelList = false },
                            nowPlaying = overlayNowPlaying,
                            nowProgrammes = overlayProgrammes,
                            favoriteIds = overlayFavoriteIds,
                            showNumbers = directTuneEnabled,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else if (zapChannels.isNotEmpty()) {
                        // First Left — the channels of the current category. A browsed-to category may
                        // hold a single channel, so this renders for any non-empty list.
                        tv.own.myowntv.features.shell.components.ChannelListOverlay(
                            channels = zapChannels,
                            currentId = previewChannel?.id,
                            nowPlaying = overlayNowPlaying,
                            nowProgrammes = overlayProgrammes,
                            favoriteIds = overlayFavoriteIds,
                            title = zapOverlayTitle,
                            showNumbers = directTuneEnabled,
                            onSelect = { liveVm.ensurePlaying(it); showChannelList = false },
                            onDismiss = { showChannelList = false },
                            onOpenCategories = { liveVm.openChannelBrowser() },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                // Right — recently watched, to hop straight back to the previous channel.
                if (showHistoryList && isLiveChannel && historyChannels.isNotEmpty()) {
                    tv.own.myowntv.features.shell.components.ChannelListOverlay(
                        channels = historyChannels,
                        currentId = previewChannel?.id,
                        nowPlaying = historyNowPlaying,
                        nowProgrammes = overlayProgrammes,
                        favoriteIds = overlayFavoriteIds,
                        watchedAt = historyWatchedAt,
                        title = stringResource(R.string.content_category_recently_watched),
                        showNumbers = directTuneEnabled,
                        alignEnd = true,
                        onSelect = { liveVm.ensurePlaying(it); showHistoryList = false },
                        onDismiss = { showHistoryList = false },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else if (!inPip) {
                MiniPlayer(
                    player = liveVm.previewEngine,
                    onExpand = expandPlayer,
                    onClose = exitPlayer,
                    onCycleSize = { scope.launch { settingsRepo.setMiniPlayerSizePct(tv.own.owntv.core.player.MiniPlayerSize.next(miniSizePct)) } },
                    onCyclePosition = { scope.launch { settingsRepo.setMiniPlayerPosition(miniPos.next().name) } },
                    onAudioMode = toAudioMode,
                    entryFocusRequester = miniEntryFocus,
                    onBack = { runCatching { shellContentFocus.requestFocus() } },
                    modifier = Modifier.fillMaxSize().onFocusChanged { miniHasFocus = it.hasFocus },
                )
            }
        }
      }

    // The Settings PIN before a long-press change, when the Settings lock asks for it.
    tv.own.myowntv.features.settings.lock.MenuChangeGateHost()
    if (showExit) {
        ExitDialog(
            onConfirm = onExitApp,
            onDismiss = { showExit = false },
        )
    }

    // Automatic update check (GitHub Releases) shortly after launch, once per session: a small
        // top-right status card shows "Checking… / up to date" (auto-hides) or stays with
        // Update now / Later when a release is newer. Hidden while in Settings (its manual
        // "Check for updates" dialog drives the same state machine) and during playback.
        // Interrupted restore: the marker outlives the process, so if it's still set at launch
        // the last restore didn't complete. Acknowledging clears it.
        val restoreSettings = koinInject<tv.own.owntv.core.settings.SettingsRepository>()
    val incompleteRestore by restoreSettings.restoreInProgress.collectAsStateWithLifecycle(initialValue = null)
    var restoreNoticeDismissed by remember { mutableStateOf(false) }
    incompleteRestore?.takeIf { !restoreNoticeDismissed }?.let { description ->
        val dismissRestoreNotice: () -> Unit = {
            restoreNoticeDismissed = true
            scope.launch { restoreSettings.clearRestoreMarker() }
        }
        IncompleteRestoreDialog(
                description = description,
                onDismiss = dismissRestoreNotice,
            )
    }

        val updateManager = koinInject<UpdateManager>()
        var showStartupToast by remember { mutableStateOf(false) }
        var showChangelog by remember { mutableStateOf(false) }
        val settingsRepo = koinInject<tv.own.owntv.core.settings.SettingsRepository>()
        val updateCheckOnStart by settingsRepo.updateCheckOnStart.collectAsStateWithLifecycle(initialValue = false)
        LaunchedEffect(updateCheckOnStart) {
            // Once per launch: a rebuilt shell must not bring the "latest version" card back.
            if (updateCheckOnStart && !showStartupToast && !shellVm.startupUpdateCheckDone) {
                kotlinx.coroutines.delay(5_000)
                shellVm.startupUpdateCheckDone = true
                showStartupToast = true
                updateManager.check()
            }
        }
        if (showChangelog) {
            // Full "What's New" changelog (same dialog the manual Settings check uses), shown when
            // the startup card's "What's New" is pressed. No re-check — the release is already loaded.
            val dismissChangelog: () -> Unit = {
                showChangelog = false
                showStartupToast = false
                updateManager.reset()
            }
            UpdateDialog(onDismiss = dismissChangelog, checkOnOpen = false)
        } else if (showStartupToast && selectedSection != MainSection.SETTINGS && playerMode == PlayerMode.NONE) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
                UpdateStatusToast(
                    onDone = { showStartupToast = false; updateManager.reset() },
                    onViewChangelog = { showChangelog = true },
                )
            }
        }
    }
    // Last in the root Box: shell feedback, including NOTIFY reminders, must draw over opaque
    // fullscreen/Multiview content rather than behind it.
    tv.own.myowntv.ui.components.InAppToast(shellToast)
    }
}

/** Long enough to read "Auto-matched 12 channels. 40 channels need review." from the sofa. */

/** The line under a rail item at Size = Extra wide; real data only, so most items can have none. */
@Composable
private fun railDetail(section: MainSection, d: RailDetails): String? = when (section) {
    MainSection.LIVE_TV -> d.lastChannel?.let { stringResource(R.string.shell_rail_detail_last, it) }
    MainSection.MORE -> stringResource(R.string.shell_rail_detail_more)
    else -> null
}

/** How recently ◀ / ▶ or [focusRail] must have happened for a rail visit to open the rail. */
private const val RAIL_KEY_WINDOW_MS = 400L
