@file:OptIn(ExperimentalCoroutinesApi::class)

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
import tv.own.owntv.core.player.*
import tv.own.owntv.core.reminder.*
import tv.own.owntv.core.repository.*
import tv.own.owntv.core.settings.*
import tv.own.owntv.core.setup.*
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

import tv.own.owntv.core.brand.AppIcon
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.own.owntv.player.AudioOutputPolicy
import tv.own.owntv.core.player.SurroundMode
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.repository.SourceRepository
import tv.own.owntv.core.settings.ChNavLimits
import tv.own.owntv.core.settings.PanelSection
import tv.own.owntv.core.settings.PanelShares
import tv.own.owntv.core.settings.GuideWidthShares
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.settings.SubtitleStyle
import tv.own.owntv.core.theme.AccentColor
import tv.own.owntv.core.theme.ThemeMode
import tv.own.owntv.core.theme.UiZoom

/** Device-wide settings: personalization, playback, network, and the single RuTube source's overrides. */
class SettingsViewModel(
    private val sourceRepository: SourceRepository,
    private val settings: SettingsRepository,
    private val channelDao: tv.own.owntv.core.database.dao.ChannelDao,
    private val categoryDao: tv.own.owntv.core.database.dao.CategoryDao,
    private val customizationStore: tv.own.owntv.core.customize.CustomizationStore,
    private val navVisibility: tv.own.owntv.core.nav.NavVisibility,
    private val userDataWriter: tv.own.owntv.core.backup.UserDataWriter,
    private val okHttpClient: okhttp3.OkHttpClient,
    private val companion: tv.own.owntv.core.companion.CompanionController,
    private val playbackPrefs: tv.own.owntv.core.player.PlaybackPrefsStore,
) : ViewModel() {
    companion object {
        private const val TAG = "OwnTVHome"
    }

    // ---- Remote server lifecycle, shared by the companion flows below. ----
    /** Server lifecycle (Idle / Starting / Listening with PIN+QR / Failed) for the Remote screen. */
    val remoteState get() = companion.state

    fun stopRemoteListener() = companion.stop()

    // ---- Remote background image: another device uploads a photo over LAN (same PIN/QR companion flow). ----

    /** Background images received from the remote device in image-upload mode. */
    val remoteImages get() = companion.images

    fun startRemoteImageListener(port: Int) = companion.startForImageUpload(port)

    /** Clear the watch history (the "recently watched" rows).
     *  [type] null = everything; otherwise just LIVE / MOVIE / SERIES. */
    fun clearWatchHistory() {
        viewModelScope.launch {
            // userDataWriter records each deletion so a local sync does not hand the cleared
            // history straight back.
            userDataWriter.clearHistory()
        }
    }

    /** The sources (the built-in RuTube one today); null until the first read, so a count never
     *  shows 0 while loading. */
    val loadedSources: StateFlow<List<SourceEntity>?> = sourceRepository.observeSources()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val sources: StateFlow<List<SourceEntity>> = loadedSources
        .map { it.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val livePreviewEnabled: StateFlow<Boolean> = settings.livePreviewEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val livePreviewPanelActive: StateFlow<Boolean> = settings.livePreviewPanelActive
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setLivePreviewEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setLivePreviewEnabled(enabled) }
    }

    val livePreviewAudio: StateFlow<Boolean> = settings.livePreviewAudio
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setLivePreviewAudio(enabled: Boolean) {
        viewModelScope.launch { settings.setLivePreviewAudio(enabled) }
    }

    val guidePreviewOnOk: StateFlow<Boolean> = settings.guidePreviewOnOk
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val livePreviewOnOk: StateFlow<Boolean> = settings.livePreviewOnOk
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setGuidePreviewOnOk(enabled: Boolean) {
        viewModelScope.launch { settings.setGuidePreviewOnOk(enabled) }
    }

    fun setLivePreviewOnOk(enabled: Boolean) {
        viewModelScope.launch { settings.setLivePreviewOnOk(enabled) }
    }

    val preferSdr: StateFlow<Boolean> = settings.preferSdr
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setPreferSdr(enabled: Boolean) {
        viewModelScope.launch { settings.setPreferSdr(enabled) }
    }

    val autoFrameRateFilms: StateFlow<Boolean> = settings.autoFrameRateFilms
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val autoFrameRateLive: StateFlow<Boolean> = settings.autoFrameRateLive
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // Multiview. The tile count is never reduced behind the user's back: the
    // warning above two tiles is asked once and obeyed either way.
    val multiviewEnabled: StateFlow<Boolean> = settings.multiviewEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val multiviewTiles: StateFlow<Int> = settings.multiviewTiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), tv.own.owntv.core.live.DEFAULT_MULTIVIEW_TILES)

    val multiviewWarningAccepted: StateFlow<Boolean> = settings.multiviewWarningAccepted
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setMultiviewEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setMultiviewEnabled(enabled) }
    }

    fun setMultiviewTiles(tiles: Int, acceptWarning: Boolean = false) {
        viewModelScope.launch {
            if (acceptWarning) settings.setMultiviewWarningAccepted(true)
            settings.setMultiviewTiles(tiles)
        }
    }

    /** [live]: the Live TV switch; otherwise films and series. */
    fun setAutoFrameRate(live: Boolean, enabled: Boolean) {
        viewModelScope.launch { if (live) settings.setAutoFrameRateLive(enabled) else settings.setAutoFrameRateFilms(enabled) }
    }

    // The old surround BOOLEAN is gone from here: [surroundMode] replaced it, and the leftover flow
    // defaulted to `true` where the setting's own default is `false` — a trap for anyone who wired a UI
    // to it. The legacy key itself still lives in SettingsRepository, which reads it so an upgrading
    // user's old choice carries into the three-state setting.
    val surroundMode: StateFlow<SurroundMode> = settings.surroundMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SurroundMode.AUTO)

    /** Picked from its list. Like [cycleSurroundMode], it gives the audio output another chance. */
    fun setSurroundMode(mode: SurroundMode) {
        AudioOutputPolicy.clearLatch()
        viewModelScope.launch { settings.setSurroundMode(mode) }
    }

    /** Cycle Auto → Stereo only → Surround → Auto. Any change clears the session's stereo latch: the
     *  user touching this control is explicitly asking the audio output for another chance. */
    fun cycleSurroundMode() {
        val next = when (surroundMode.value) {
            SurroundMode.AUTO -> SurroundMode.STEREO
            SurroundMode.STEREO -> SurroundMode.SURROUND
            SurroundMode.SURROUND -> SurroundMode.AUTO
        }
        AudioOutputPolicy.clearLatch()
        viewModelScope.launch { settings.setSurroundMode(next) }
    }

    // Passthrough, night mode, volume levelling.
    val audioPassthrough: StateFlow<Boolean> = settings.audioPassthrough.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    fun setAudioPassthrough(enabled: Boolean) { viewModelScope.launch { settings.setAudioPassthrough(enabled) } }
    val nightMode: StateFlow<Boolean> = settings.nightMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    fun setNightMode(enabled: Boolean) { viewModelScope.launch { settings.setNightMode(enabled) } }
    val volumeLevelling: StateFlow<Boolean> = settings.volumeLevelling.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    fun setVolumeLevelling(enabled: Boolean) { viewModelScope.launch { settings.setVolumeLevelling(enabled) } }

    // Maximum video quality, tunneled playback (the row exists only where it can work).
    val maxVideoHeightChoices: List<Int> get() = settings.maxVideoHeightChoices
    val maxVideoHeight: StateFlow<Int> = settings.maxVideoHeight.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    fun setMaxVideoHeight(height: Int) { viewModelScope.launch { settings.setMaxVideoHeight(height) } }
    val tunnelingSupported: Boolean get() = tv.own.owntv.player.Tunneling.supported
    val tunneledPlayback: StateFlow<Boolean> = settings.tunneledPlayback.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val tunnelingFailed: StateFlow<Boolean> = settings.tunnelingFailed.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    fun setTunneledPlayback(enabled: Boolean) {
        if (enabled) tv.own.owntv.player.Tunneling.failedThisSession = false // the user asks for another try
        viewModelScope.launch { settings.setTunneledPlayback(enabled) }
    }

    /** Global guide shift in minutes (0 = off). Per-channel overrides live in the channel menu. */
    val epgOffsetMinutes: StateFlow<Int> = settings.epgOffsetMinutes
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val epgOffsetRangeMinutes: IntRange = settings.epgOffsetRangeMinutes

    /** Nudge the global EPG offset by [deltaMinutes] (the picker's − / + steps), clamped to range. */
    fun adjustEpgOffset(deltaMinutes: Int) {
        viewModelScope.launch { settings.setEpgOffsetMinutes(epgOffsetMinutes.value + deltaMinutes) }
    }

    fun setEpgOffsetMinutes(minutes: Int) {
        viewModelScope.launch { settings.setEpgOffsetMinutes(minutes) }
    }

    /** How many days of upcoming guide to store — one value for every EPG source. */
    val guideDaysToKeep: StateFlow<Int> = settings.guideDaysToKeep
        .stateIn(viewModelScope, SharingStarted.Eagerly, tv.own.owntv.core.settings.GuideRetention.DEFAULT_DAYS)

    fun setGuideDaysToKeep(days: Int) {
        viewModelScope.launch { settings.setGuideDaysToKeep(days) }
    }

    val androidTvHomeEnabled: StateFlow<Boolean> = settings.androidTvHomeEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setAndroidTvHomeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            Log.d(TAG, "setAndroidTvHomeEnabled enabled=$enabled")
            settings.setAndroidTvHomeEnabled(enabled)
        }
    }

    // --- Video Player Settings ---
    val hwDecoding: StateFlow<Boolean> = settings.hwDecoding.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    fun setHwDecoding(enabled: Boolean) { viewModelScope.launch { settings.setHwDecoding(enabled) } }
    val softwareDecodeAbove1080p: StateFlow<Boolean> = settings.softwareDecodeAbove1080p.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    fun setSoftwareDecodeAbove1080p(enabled: Boolean) { viewModelScope.launch { settings.setSoftwareDecodeAbove1080p(enabled) } }

    /** Volume every item starts at, before any per-item value the player remembered. */
    val defaultVolume: StateFlow<Int> = settings.defaultVolume.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 100)
    fun setDefaultVolume(percent: Int) { viewModelScope.launch { settings.setDefaultVolume(percent) } }

    /** How many individual channels/films/episodes have a remembered zoom, and how many a volume.
     *  Counted and reset separately — wanting every film back at the default aspect is not a request
     *  to lose the levels set on the quiet ones. */
    val savedZoomCount: StateFlow<Int> =
        playbackPrefs.observeZoomCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val savedVolumeCount: StateFlow<Int> =
        playbackPrefs.observeVolumeCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun clearSavedZoom() { viewModelScope.launch { playbackPrefs.clearZoom() } }

    fun clearSavedVolume() { viewModelScope.launch { playbackPrefs.clearVolume() } }

    /** Same again for the per-item A/V-sync offsets (DB v35). */
    val savedAudioDelayCount: StateFlow<Int> =
        playbackPrefs.observeAudioDelayCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun clearSavedAudioDelay() { viewModelScope.launch { playbackPrefs.clearAudioDelay() } }

    /** Rewind/forward step. The separate live-archive one went with catch-up. */
    val seekStepSec: StateFlow<Int> = settings.seekStepSec
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), tv.own.owntv.core.settings.SeekSteps.DEFAULT_SEEK_STEP_SEC)
    fun setSeekStepSec(seconds: Int) { viewModelScope.launch { settings.setSeekStepSec(seconds) } }

    val measuredStreamStats: StateFlow<Boolean> = settings.measuredStreamStats.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), settings.measuredStreamStatsDefault)
    fun setMeasuredStreamStats(enabled: Boolean) { viewModelScope.launch { settings.setMeasuredStreamStats(enabled) } }

    val detailedDiagnostics: StateFlow<Boolean> = settings.detailedDiagnostics.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    fun setDetailedDiagnostics(enabled: Boolean) { viewModelScope.launch { settings.setDetailedDiagnostics(enabled) } }

    val directTune: StateFlow<Boolean> = settings.directTune.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    fun setDirectTune(enabled: Boolean) { viewModelScope.launch { settings.setDirectTune(enabled) } }

    // Auto frame rate's film extras.
    val afrPauseMaxSecs: Int get() = settings.afrPauseMaxSecs
    val afrPauseSecs: StateFlow<Int> = settings.afrPauseSecs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    fun setAfrPauseSecs(secs: Int) { viewModelScope.launch { settings.setAfrPauseSecs(secs) } }
    val afrMatchResolution: StateFlow<Boolean> = settings.afrMatchResolution.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    fun setAfrMatchResolution(enabled: Boolean) { viewModelScope.launch { settings.setAfrMatchResolution(enabled) } }

    // External player is a Live TV toggle — handing a live channel's URL to VLC/MX Player.
    val externalPlayerLive: StateFlow<Boolean> = settings.externalPlayerLive.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    fun setExternalPlayerLive(enabled: Boolean) { viewModelScope.launch { settings.setExternalPlayerLive(enabled) } }

    val updateCheckOnStart: StateFlow<Boolean> =
        settings.updateCheckOnStart.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    fun setUpdateCheckOnStart(enabled: Boolean) { viewModelScope.launch { settings.setUpdateCheckOnStart(enabled) } }

    /** The Settings rows pinned to the Quick group, in display order. */
    val quickPinnedKeys: StateFlow<List<String>> =
        settings.quickPinnedKeys.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun setQuickPinnedKeys(keys: List<String>) { viewModelScope.launch { settings.setQuickPinnedKeys(keys) } }

    /** How one long-press content menu is arranged: its order and the actions hidden from it. */
    fun menuLayout(menu: tv.own.owntv.core.model.ContentMenu) = settings.menuLayout(menu)
    fun setMenuLayout(menu: tv.own.owntv.core.model.ContentMenu, layout: tv.own.owntv.core.menu.MenuLayout) {
        viewModelScope.launch { settings.setMenuLayout(menu, layout) }
    }

    val resumeLastChannel: StateFlow<Boolean> =
        settings.resumeLastChannel.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    fun setResumeLastChannel(enabled: Boolean) { viewModelScope.launch { settings.setResumeLastChannel(enabled) } }

    // Startup landing (v4.0.0): Home / Last channel / Live·Favorites.
    val startupMode: StateFlow<tv.own.owntv.core.settings.StartupMode> =
        settings.startupMode()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), tv.own.owntv.core.settings.StartupMode.HOME)
    fun setStartupMode(mode: tv.own.owntv.core.settings.StartupMode) {
        viewModelScope.launch { settings.setStartupMode(mode) }
    }

    val startupChannel: StateFlow<tv.own.owntv.core.settings.StartupChannelRef?> =
        settings.startupChannel()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _startupChannelQuery = MutableStateFlow("")
    val startupChannelQuery: StateFlow<String> = _startupChannelQuery.asStateFlow()
    private val startupChannelRefresh = MutableStateFlow(0)

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    val startupChannelResults: StateFlow<List<tv.own.owntv.core.database.entity.ChannelEntity>> =
        combine(
            _startupChannelQuery.debounce(180),
            startupChannelRefresh,
        ) { query, _ -> query }
            .mapLatest { loadStartupChannelResults(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setStartupChannelQuery(query: String) { _startupChannelQuery.value = query }

    fun refreshStartupChannelPicker() { startupChannelRefresh.value++ }

    fun setStartupChannel(channel: tv.own.owntv.core.database.entity.ChannelEntity) {
        viewModelScope.launch {
            settings.setSpecificStartupChannel(
                tv.own.owntv.core.settings.StartupChannelRef(
                    remoteId = channel.remoteId,
                    name = channel.name,
                ),
            )
        }
    }

    private suspend fun loadStartupChannelResults(query: String): List<tv.own.owntv.core.database.entity.ChannelEntity> {
        val customizations = customizationStore.observeVisible(tv.own.owntv.core.model.MediaType.LIVE).first()
        val hiddenCategoryIds = tv.own.owntv.core.content.AdultCategoryClassifier.hiddenCategoryIds(
            categoryDao.observe().first(),
            customizations.hiddenCategories,
        )
        return channelDao.searchList(query.trim(), 500)
            .asSequence()
            .filter { tv.own.owntv.core.customize.CustomizeKeys.channel(it) !in customizations.hiddenItems }
            .filter { tv.own.owntv.core.database.ChannelMembership.visible(it, hiddenCategoryIds) }
            .take(300)
            .toList()
    }

    val defaultZoom: StateFlow<String> = settings.defaultZoom.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "FIT")
    fun setDefaultZoom(name: String) { viewModelScope.launch { settings.setDefaultZoom(name) } }

    // Subtitle appearance — while subtitleStyleEnabled is false the other four are inert, and
    // each of them separately does nothing until moved off its own "Default" value.
    val subtitleStyleEnabled: StateFlow<Boolean> = settings.subtitleStyleEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    fun setSubtitleStyleEnabled(enabled: Boolean) { viewModelScope.launch { settings.setSubtitleStyleEnabled(enabled) } }

    // The one subtitle size: ExoPlayer renders overlays, so the multiplier is tuned for it.
    val subtitleScaleExo: StateFlow<Float> = settings.subtitleScaleExo.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubtitleStyle.SCALE_DEFAULT)
    fun setSubtitleScaleExo(scale: Float) { viewModelScope.launch { settings.setSubtitleScaleExo(scale) } }

    val subtitleFont: StateFlow<tv.own.owntv.core.theme.AppFontFamily?> = settings.subtitleFont
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun setSubtitleFont(font: tv.own.owntv.core.theme.AppFontFamily?) { viewModelScope.launch { settings.setSubtitleFont(font) } }

    val subtitleColor: StateFlow<String> = settings.subtitleColor.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubtitleStyle.COLOR_DEFAULT)
    fun setSubtitleColor(hex: String) { viewModelScope.launch { settings.setSubtitleColor(hex) } }

    val subtitlePosition: StateFlow<SubtitleStyle.Position> = settings.subtitlePosition.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubtitleStyle.Position.DEFAULT)
    fun setSubtitlePosition(position: SubtitleStyle.Position) { viewModelScope.launch { settings.setSubtitlePosition(position) } }

    val subtitleBgOpacity: StateFlow<Int> = settings.subtitleBgOpacity.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubtitleStyle.OPACITY_DEFAULT)
    fun setSubtitleBgOpacity(pct: Int) { viewModelScope.launch { settings.setSubtitleBgOpacity(pct) } }

    val audioDelayMs: StateFlow<Int> = settings.audioDelayMs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    fun setAudioDelayMs(ms: Int) { viewModelScope.launch { settings.setAudioDelayMs(ms) } }
    val outputAudioDelays: StateFlow<tv.own.owntv.core.player.OutputAudioDelays> =
        settings.outputAudioDelays.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), tv.own.owntv.core.player.OutputAudioDelays.NONE)
    fun setOutputAudioDelay(kind: tv.own.owntv.core.player.AudioOutputKind, ms: Int) { viewModelScope.launch { settings.setOutputAudioDelay(kind, ms) } }

    // --- CH+- key paging (browse panels): master toggle + per-direction skip counts ---
    val chNavEnabled: StateFlow<Boolean> = settings.chNavEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    fun setChNavEnabled(enabled: Boolean) { viewModelScope.launch { settings.setChNavEnabled(enabled) } }
    val chNavUpSkip: StateFlow<Int> = settings.chNavUpSkip.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChNavLimits.DEFAULT_SKIP)
    fun setChNavUpSkip(n: Int) { viewModelScope.launch { settings.setChNavUpSkip(n) } }
    val chNavDownSkip: StateFlow<Int> = settings.chNavDownSkip.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChNavLimits.DEFAULT_SKIP)
    fun setChNavDownSkip(n: Int) { viewModelScope.launch { settings.setChNavDownSkip(n) } }
    val remoteShortcutBindings: StateFlow<List<tv.own.owntv.core.settings.RemoteShortcutBinding>> =
        settings.remoteShortcutBindings.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            tv.own.owntv.core.settings.RemoteShortcutBindings.defaults,
        )
    fun setRemoteShortcutBinding(binding: tv.own.owntv.core.settings.RemoteShortcutBinding) {
        viewModelScope.launch { settings.setRemoteShortcutBinding(binding) }
    }
    fun removeRemoteShortcutBinding(keyCode: Int, press: tv.own.owntv.core.settings.RemoteShortcutPress) {
        viewModelScope.launch { settings.removeRemoteShortcutBinding(keyCode, press) }
    }
    fun resetRemoteShortcutBindings() { viewModelScope.launch { settings.resetRemoteShortcutBindings() } }

    // --- Manual panel widths: one StateFlow per section, so Live/Movies/Series each read their own ---
    private fun <T> panelFlows(source: (PanelSection) -> kotlinx.coroutines.flow.Flow<T>, initial: T): Map<PanelSection, StateFlow<T>> =
        PanelSection.entries.associateWith {
            source(it).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)
        }

    val panelWidthEnabled: Map<PanelSection, StateFlow<Boolean>> = panelFlows(settings::panelWidthEnabled, false)
    val panelShares: Map<PanelSection, StateFlow<PanelShares?>> = panelFlows(settings::panelShares, null)

    fun setPanelWidths(s: PanelSection, enabled: Boolean, shares: PanelShares) {
        viewModelScope.launch { settings.setPanelWidths(s, enabled, shares) }
    }

    val guideWidthEnabled: StateFlow<Boolean> = settings.guideWidthEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val guideWidthShares: StateFlow<GuideWidthShares?> = settings.guideWidthShares
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun setGuideWidths(enabled: Boolean, shares: GuideWidthShares) {
        viewModelScope.launch { settings.setGuideWidths(enabled, shares) }
    }

    val preferredAudioLang: StateFlow<String> = settings.preferredAudioLang.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    fun setPreferredAudioLang(lang: String) { viewModelScope.launch { settings.setPreferredAudioLang(lang) } }

    val preferredSubLang: StateFlow<String> = settings.preferredSubLang.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    fun setPreferredSubLang(lang: String) { viewModelScope.launch { settings.setPreferredSubLang(lang) } }

    // --- Personalization (theme / accent / UI zoom) ---
    val themeMode: StateFlow<ThemeMode> = settings.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.DARK)
    fun setThemeMode(mode: ThemeMode) { viewModelScope.launch { settings.setThemeMode(mode) } }

    val accent: StateFlow<AccentColor> = settings.accent.stateIn(viewModelScope, SharingStarted.Eagerly, AccentColor.TEAL)
    fun setAccent(accent: AccentColor) { viewModelScope.launch { settings.setAccent(accent) } }

    /** Custom accent hex ("#52DBC8"); blank = the preset is in effect. */
    val customAccent: StateFlow<String> = settings.customAccent.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    fun setCustomAccent(hex: String) { viewModelScope.launch { settings.setCustomAccent(hex) } }

    /** Focus highlight: ring color hex (blank = accent) and ring width in dp. */
    val focusHighlight: StateFlow<String> = settings.focusHighlight.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    val focusHighlightWidth: StateFlow<Int> = settings.focusHighlightWidth.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 2)
    fun setFocusHighlight(hex: String) { viewModelScope.launch { settings.setFocusHighlight(hex) } }
    fun setFocusHighlightWidth(dp: Int) { viewModelScope.launch { settings.setFocusHighlightWidth(dp) } }

    // --- Glass: the background image and the Glass on/off and opacity ---
    val bgImagePath: StateFlow<String> = settings.bgImagePath.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    val glassConfig: StateFlow<tv.own.owntv.core.theme.GlassConfig> = settings.glassConfig.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), tv.own.owntv.core.theme.GlassConfig())
    fun setBgImagePath(path: String) { viewModelScope.launch { settings.setBgImagePath(path) } }

    // --- Glass & background ---
    val backgroundConfig: StateFlow<tv.own.owntv.core.theme.BackgroundConfig> =
        settings.backgroundConfig.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), tv.own.owntv.core.theme.BackgroundConfig())
    /** A newly picked picture: Background becomes Picture, on Soft. */
    fun setNewBackgroundPicture(path: String) { viewModelScope.launch { settings.setNewBackgroundPicture(path) } }
    fun setBackgroundStyle(style: tv.own.owntv.core.theme.BackgroundStyle) { viewModelScope.launch { settings.setBackgroundStyle(style) } }
    fun setPictureLook(look: tv.own.owntv.core.theme.PictureLook) { viewModelScope.launch { settings.setPictureLook(look) } }
    fun setBackgroundDim(pct: Int) { viewModelScope.launch { settings.setBackgroundDim(pct) } }
    fun setBackgroundBlur(pct: Int) { viewModelScope.launch { settings.setBackgroundBlur(pct) } }
    fun setBackgroundAccentLight(on: Boolean) { viewModelScope.launch { settings.setBackgroundAccentLight(on) } }
    fun resetGlassAndBackground() { viewModelScope.launch { settings.resetGlassAndBackground() } }
    fun setGlassScopeBitmask(bits: Int) { viewModelScope.launch { settings.setGlassScopeBitmask(bits) } }
    fun setGlassAlphaPercent(pct: Int) { viewModelScope.launch { settings.setGlassAlphaPercent(pct) } }

    // --- Nav menu customization (v4.3.0) ---
    /** STATIC (default): user picks which icons to hide. DYNAMIC: icons adapt to the active playlist. */
    val navMenuMode: StateFlow<tv.own.owntv.core.settings.SettingsRepository.NavMenuMode> =
        settings.navMenuMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), tv.own.owntv.core.settings.SettingsRepository.NavMenuMode.STATIC)
    fun setNavMenuMode(mode: tv.own.owntv.core.settings.SettingsRepository.NavMenuMode) {
        viewModelScope.launch { settings.setNavMenuMode(mode) }
    }

    // --- Stage navigation (the rail's style, size, length, hide delay) ---
    val navStyle: StateFlow<tv.own.owntv.core.settings.SettingsRepository.NavStyle> =
        settings.navStyle.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), tv.own.owntv.core.settings.SettingsRepository.NavStyle.DOCKED)
    fun setNavStyle(style: tv.own.owntv.core.settings.SettingsRepository.NavStyle) {
        viewModelScope.launch { settings.setNavStyle(style) }
    }
    val navSize: StateFlow<tv.own.owntv.core.settings.SettingsRepository.NavSize> =
        settings.navSize.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), tv.own.owntv.core.settings.SettingsRepository.NavSize.COMPACT)
    fun setNavSize(size: tv.own.owntv.core.settings.SettingsRepository.NavSize) {
        viewModelScope.launch { settings.setNavSize(size) }
    }
    val navLength: StateFlow<tv.own.owntv.core.settings.SettingsRepository.NavLength> =
        settings.navLength.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), tv.own.owntv.core.settings.SettingsRepository.NavLength.FIT)
    fun setNavLength(length: tv.own.owntv.core.settings.SettingsRepository.NavLength) {
        viewModelScope.launch { settings.setNavLength(length) }
    }
    val navWiden: StateFlow<tv.own.owntv.core.settings.SettingsRepository.NavSize?> =
        settings.navWiden.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun setNavWiden(size: tv.own.owntv.core.settings.SettingsRepository.NavSize?) { viewModelScope.launch { settings.setNavWiden(size) } }

    val navHideAfterMs: StateFlow<Int> =
        settings.navHideAfterMs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), tv.own.owntv.core.settings.SettingsRepository.NavHideAfter.DEFAULT_MS)
    fun setNavHideAfterMs(ms: Int) {
        viewModelScope.launch { settings.setNavHideAfterMs(ms) }
    }

    /** Browse sections the user has hidden (STATIC mode only). */
    val navMenuHidden: StateFlow<Set<tv.own.owntv.core.nav.MainSection>> = settings.navMenuHidden
        .map { raw -> raw.mapNotNull { name -> runCatching { tv.own.owntv.core.nav.MainSection.valueOf(name) }.getOrNull() }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())
    fun setNavSectionHidden(section: tv.own.owntv.core.nav.MainSection, hidden: Boolean) {
        viewModelScope.launch {
            val current = navMenuHidden.first()
            val next = if (hidden) current + section else current - section
            settings.setNavMenuHidden(next.map { it.name }.toSet())
        }
    }

    /**
     * The DYNAMIC-mode icon set the active playlist *would* show (v4.3.0). Mirrors [ShellViewModel]'s rail
     * computation so the Nav menu settings screen's read-only DYNAMIC rows report the same state the rail
     * reflects. Re-emits automatically as content arrives (Room invalidates the count flows on every write).
     */
    val dynamicCaps: StateFlow<Set<tv.own.owntv.core.nav.MainSection>> = navVisibility.dynamicCaps()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), tv.own.owntv.core.nav.MainSection.allBrowse)

    val uiZoomPercent: StateFlow<Int> = settings.uiZoomPercent.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiZoom.DEFAULT)
    fun setUiZoom(percent: Int) { viewModelScope.launch { settings.setUiZoomPercent(UiZoom.clamp(percent)) } }

    val appIcon: StateFlow<AppIcon> = settings.appIcon.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppIcon.DEFAULT)
    fun setAppIcon(icon: AppIcon) { viewModelScope.launch { settings.setAppIcon(icon) } }
    val brandAccentTriangle: StateFlow<Boolean> = settings.brandAccentTriangle.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    fun setBrandAccentTriangle(on: Boolean) { viewModelScope.launch { settings.setBrandAccentTriangle(on) } }

    // Docked mini-player: size (% of screen width) and screen position.
    val miniPlayerSizePct: StateFlow<Int> =
        settings.miniPlayerSizePct.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), tv.own.owntv.core.player.MiniPlayerSize.DEFAULT)
    fun setMiniPlayerSize(percent: Int) { viewModelScope.launch { settings.setMiniPlayerSizePct(percent) } }

    val miniPlayerPosition: StateFlow<tv.own.owntv.core.player.MiniPlayerPosition> =
        settings.miniPlayerPosition
            .map { tv.own.owntv.core.player.MiniPlayerPosition.fromName(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), tv.own.owntv.core.player.MiniPlayerPosition.DEFAULT)
    fun setMiniPlayerPosition(position: tv.own.owntv.core.player.MiniPlayerPosition) {
        viewModelScope.launch { settings.setMiniPlayerPosition(position.name) }
    }

    // Live TV latency: preset + custom seconds.
    val liveLatencyMode: StateFlow<tv.own.owntv.core.settings.LiveLatency> =
        settings.liveLatencyMode
            .map { tv.own.owntv.core.settings.LiveLatency.fromName(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), tv.own.owntv.core.settings.LiveLatency.DEFAULT)
    fun setLiveLatencyMode(mode: tv.own.owntv.core.settings.LiveLatency) {
        viewModelScope.launch { settings.setLiveLatencyMode(mode.name) }
    }

    val liveLatencyCustomSecs: StateFlow<Int> =
        settings.liveLatencyCustomSecs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), tv.own.owntv.core.settings.LiveBuffer.CUSTOM_DEFAULT)
    fun setLiveLatencyCustomSecs(secs: Int) {
        viewModelScope.launch { settings.setLiveLatencyCustomSecs(secs) }
    }

    /** "Pre-buffer": the global choice, in seconds (0 = Off). */
    val livePrerollSecs: StateFlow<Int> =
        settings.livePrerollSecs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), tv.own.owntv.core.settings.LiveBuffer.PREROLL_OFF)
    fun setLivePrerollSecs(secs: Int) {
        viewModelScope.launch { settings.setLivePrerollSecs(secs) }
    }

    /** "Give up on a channel after": the whole-tune budget in seconds (0 = Never). */
    val liveTuneTimeoutSecs: StateFlow<Int> =
        settings.liveTuneTimeoutSecs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), tv.own.owntv.player.LiveLadder.DEFAULT_BUDGET_SECS)
    fun setLiveTuneTimeoutSecs(secs: Int) {
        viewModelScope.launch { settings.setLiveTuneTimeoutSecs(secs) }
    }

    /**
     * The old "per-playlist override" of the above. With one built-in source its overrides ARE the
     * global settings (schema v5), so this writes the global value: `-1` = back to Off.
     */
    fun setSourcePreroll(sourceId: Long, secs: Int) {
        viewModelScope.launch { settings.setLivePrerollSecs(if (secs < 0) 0 else secs) }
    }

    /** One source: `null` = its "follow global" sentinel → the global default budget. */
    fun setSourceTuneTimeout(sourceId: Long, secs: Int?) {
        viewModelScope.launch {
            settings.setLiveTuneTimeoutSecs(secs ?: tv.own.owntv.core.player.PlaybackDefaults.LIVE_TUNE_BUDGET_SECS)
        }
    }

    /** One source: `null` mode = back to the default preset, which is what "follow global" means. */
    fun setSourceLiveLatency(sourceId: Long, mode: String?, customSecs: Int) {
        viewModelScope.launch {
            settings.setLiveLatencyMode(mode ?: tv.own.owntv.core.settings.LiveLatency.DEFAULT.name)
            settings.setLiveLatencyCustomSecs(customSecs)
        }
    }

    val animationLevel: StateFlow<tv.own.owntv.core.theme.AnimationLevel> =
        settings.animationLevel.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), tv.own.owntv.core.theme.AnimationLevel.FULL)
    fun setAnimationLevel(level: tv.own.owntv.core.theme.AnimationLevel) { viewModelScope.launch { settings.setAnimationLevel(level) } }

    val liveLayout: StateFlow<tv.own.owntv.core.settings.SettingsRepository.LiveLayout> =
        settings.liveLayout.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), tv.own.owntv.core.settings.SettingsRepository.LiveLayout.STAGE)
    fun setLiveLayout(layout: tv.own.owntv.core.settings.SettingsRepository.LiveLayout) { viewModelScope.launch { settings.setLiveLayout(layout) } }
    val liveView: StateFlow<tv.own.owntv.core.settings.SettingsRepository.LiveView> =
        settings.liveView.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), tv.own.owntv.core.settings.SettingsRepository.LiveView.LIST)
    fun setLiveView(view: tv.own.owntv.core.settings.SettingsRepository.LiveView) { viewModelScope.launch { settings.setLiveView(view) } }
    val reminderMode: StateFlow<tv.own.owntv.core.settings.SettingsRepository.ReminderMode> =
        settings.reminderMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), tv.own.owntv.core.settings.SettingsRepository.ReminderMode.ASK)
    fun setReminderMode(mode: tv.own.owntv.core.settings.SettingsRepository.ReminderMode) { viewModelScope.launch { settings.setReminderMode(mode) } }
    val reminderLeadMinutes: StateFlow<Int> =
        settings.reminderLeadMinutes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), tv.own.owntv.core.reminder.ReminderSchedule.DEFAULT_LEAD_MINUTES)
    fun setReminderLeadMinutes(minutes: Int) { viewModelScope.launch { settings.setReminderLeadMinutes(minutes) } }
    /** Live TV's Stage-layout widths: the categories sheet on its own, list + preview = 100. */
    val liveStageWidths: StateFlow<tv.own.owntv.core.settings.LiveStageWidths?> =
        settings.liveStageWidths.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun setLiveStageWidths(enabled: Boolean, widths: tv.own.owntv.core.settings.LiveStageWidths) {
        viewModelScope.launch { settings.setLiveStageWidths(enabled, widths) }
    }

    val ambientGlowEnabled: StateFlow<Boolean> =
        settings.ambientGlowEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    fun setAmbientGlowEnabled(enabled: Boolean) { viewModelScope.launch { settings.setAmbientGlowEnabled(enabled) } }

    val ambientGlowPulse: StateFlow<Boolean> =
        settings.ambientGlowPulse.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    fun setAmbientGlowPulse(enabled: Boolean) { viewModelScope.launch { settings.setAmbientGlowPulse(enabled) } }

    // Weather chip: visibility toggle + manual location override (for VPN users).
    val weatherEnabled: StateFlow<Boolean> =
        settings.weatherEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    fun setWeatherEnabled(enabled: Boolean) { viewModelScope.launch { settings.setWeatherEnabled(enabled) } }
    val weatherLocation: StateFlow<String> =
        settings.weatherLocation.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    fun setWeatherLocation(location: String) { viewModelScope.launch { settings.setWeatherLocation(location) } }
    val weatherFahrenheit: StateFlow<Boolean> =
        settings.weatherFahrenheit.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    fun setWeatherFahrenheit(fahrenheit: Boolean) { viewModelScope.launch { settings.setWeatherFahrenheit(fahrenheit) } }

    val clockColors: StateFlow<tv.own.owntv.core.settings.ClockColors> =
        settings.clockColors.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), tv.own.owntv.core.settings.ClockColors())
    fun setClockColor(part: tv.own.owntv.core.settings.ClockPart, value: String) { viewModelScope.launch { settings.setClockColor(part, value) } }

    // Live TV's "remember last item per category" (default ON). OFF resets the browse list to the top
    // when switching category; ON keeps a separate scroll position per category. The toggle also
    // gates the last-focused-channel restore on re-entry. The Movies/Series versions went with VOD.
    val rememberCategoryLive: StateFlow<Boolean> =
        settings.rememberCategoryLive.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setRememberCategoryLive(enabled: Boolean) {
        viewModelScope.launch { settings.setRememberCategoryLive(enabled) }
    }

    val guideFollowsLiveCategory: StateFlow<Boolean> =
        settings.guideFollowsLiveCategory.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setGuideFollowsLiveCategory(enabled: Boolean) {
        viewModelScope.launch { settings.setGuideFollowsLiveCategory(enabled) }
    }

    val liveBuiltIns: StateFlow<tv.own.owntv.core.live.LiveBuiltIns> =
        settings.liveBuiltIns.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), tv.own.owntv.core.live.LiveBuiltIns())
    fun setLiveShowAllRail(enabled: Boolean) {
        viewModelScope.launch { settings.setLiveShowAllRail(enabled) }
    }

    val rememberLastLive: StateFlow<Boolean> =
        settings.rememberLastLive.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    fun setRememberLastLive(enabled: Boolean) { viewModelScope.launch { settings.setRememberLastLive(enabled) } }

    // --- Global proxy (Approach 1 — one app-wide HTTP proxy) ---

    val proxyConfig: StateFlow<tv.own.owntv.core.network.ProxyConfig> = settings.proxyConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), tv.own.owntv.core.network.ProxyConfig())

    fun saveProxy(enabled: Boolean, host: String, port: Int, username: String, password: String) {
        viewModelScope.launch { settings.saveProxy(enabled, host, port, username, password) }
    }

    sealed interface ProxyTestState {
        data object Idle : ProxyTestState
        data object Testing : ProxyTestState
        data class Ok(val millis: Long) : ProxyTestState
        data class Fail(val failure: ProxyFailure) : ProxyTestState
    }

    sealed interface ProxyFailure {
        data object InvalidAddress : ProxyFailure
        data object HostUnreachable : ProxyFailure
        data object TimedOut : ProxyFailure
        data object ConnectionFailed : ProxyFailure
        data class Http(val code: Int) : ProxyFailure
        data class Unknown(val rawMessage: String?) : ProxyFailure
    }

    private val _proxyTest = MutableStateFlow<ProxyTestState>(ProxyTestState.Idle)
    val proxyTest: StateFlow<ProxyTestState> = _proxyTest.asStateFlow()

    fun resetProxyTest() { _proxyTest.value = ProxyTestState.Idle }

    fun testProxy(host: String, port: Int, username: String, password: String) {
        if (_proxyTest.value == ProxyTestState.Testing) return
        val h = host.trim()
        if (h.isBlank() || port !in 1..65535) {
            _proxyTest.value = ProxyTestState.Fail(ProxyFailure.InvalidAddress)
            return
        }
        _proxyTest.value = ProxyTestState.Testing
        viewModelScope.launch {
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val proxy = java.net.Proxy(
                        java.net.Proxy.Type.HTTP,
                        java.net.InetSocketAddress.createUnresolved(h, port),
                    )
                    val builder = okHttpClient.newBuilder()
                        .proxy(proxy)
                        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    if (username.trim().isNotBlank()) {
                        builder.proxyAuthenticator { _, response ->
                            if (response.request.header("Proxy-Authorization") != null) return@proxyAuthenticator null
                            response.request.newBuilder()
                                .header("Proxy-Authorization", okhttp3.Credentials.basic(username.trim(), password))
                                .build()
                        }
                    } else {
                        builder.proxyAuthenticator(okhttp3.Authenticator.NONE)
                    }
                    val client = builder.build()
                    val request = okhttp3.Request.Builder()
                        .url("https://www.gstatic.com/generate_204")
                        .head()
                        .build()
                    val start = System.currentTimeMillis()
                    client.newCall(request).execute().use { resp ->
                        if (!resp.isSuccessful && resp.code != 204) {
                            throw ProxyHttpException(resp.code)
                        }
                    }
                    System.currentTimeMillis() - start
                }
            }
            _proxyTest.value = result.fold(
                onSuccess = { ProxyTestState.Ok(it) },
                onFailure = { ProxyTestState.Fail(proxyFailure(it)) },
            )
        }
    }

    // --- Global custom DNS — same pattern as proxy ---

    val dnsConfig: StateFlow<tv.own.owntv.core.network.DnsConfig> = settings.dnsConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), tv.own.owntv.core.network.DnsConfig())

    fun saveDns(enabled: Boolean, host: String, port: Int, dohUrl: String) {
        viewModelScope.launch { settings.saveDns(enabled, host, port, dohUrl) }
    }

    sealed interface DnsTestState {
        data object Idle : DnsTestState
        data object Testing : DnsTestState
        data class Ok(val millis: Long) : DnsTestState
        data class Fail(val failure: DnsTestFailure) : DnsTestState
    }

    sealed interface DnsTestFailure {
        data object ServerRequired : DnsTestFailure
        data object ServerNotReachable : DnsTestFailure
        data object TimedOut : DnsTestFailure
        data object NetworkUnreachable : DnsTestFailure
        data object ConnectionRefused : DnsTestFailure
        data class NoAddresses(val host: String) : DnsTestFailure
        data class Unknown(val rawMessage: String) : DnsTestFailure
        data object Generic : DnsTestFailure
    }

    private val _dnsTest = MutableStateFlow<DnsTestState>(DnsTestState.Idle)
    val dnsTest: StateFlow<DnsTestState> = _dnsTest.asStateFlow()

    fun resetDnsTest() { _dnsTest.value = DnsTestState.Idle }

    fun testDns(enabled: Boolean, host: String, port: Int, dohUrl: String) {
        if (_dnsTest.value == DnsTestState.Testing) return
        val doh = dohUrl.trim()
        val h = host.trim()
        if (enabled && h.isBlank() && doh.isBlank()) {
            _dnsTest.value = DnsTestState.Fail(DnsTestFailure.ServerRequired)
            return
        }
        val testHost = "dns.google" // a reliable hostname for testing DNS
        _dnsTest.value = DnsTestState.Testing
        viewModelScope.launch {
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val cfg = tv.own.owntv.core.network.DnsConfig(
                        enabled = enabled,
                        host = h,
                        port = if (port in 1..65535) port else 53,
                        dohUrl = doh,
                    )
                    // Test the selected backend strictly: seed it synchronously so the first lookup
                    // cannot race the Flow collector, and do not let a failed custom server silently
                    // succeed through the device's system DNS.
                    val holder = tv.own.owntv.core.network.DnsConfigHolder(
                        configFlow = kotlinx.coroutines.flow.emptyFlow(),
                        initialConfig = cfg,
                        fallbackToSystem = false,
                    )
                    val start = System.currentTimeMillis()
                    val addrs = holder.dns.lookup(testHost)
                    val ms = System.currentTimeMillis() - start
                    if (addrs.isEmpty()) throw DnsNoAddressesException(testHost)
                    ms
                }
            }
            _dnsTest.value = result.fold(
                onSuccess = { DnsTestState.Ok(it) },
                onFailure = { DnsTestState.Fail(classifyDnsError(it)) },
            )
        }
    }

    private fun classifyDnsError(e: Throwable): DnsTestFailure {
        val msg = e.message.orEmpty()
        return when {
            e is DnsNoAddressesException -> DnsTestFailure.NoAddresses(e.host)
            msg.contains("UnknownHostException", ignoreCase = true) || msg.contains("unknown host", ignoreCase = true) ->
                DnsTestFailure.ServerNotReachable
            msg.contains("timeout", ignoreCase = true) || msg.contains("timed out", ignoreCase = true) ->
                DnsTestFailure.TimedOut
            msg.contains("Network is unreachable", ignoreCase = true) ->
                DnsTestFailure.NetworkUnreachable
            msg.contains("refused", ignoreCase = true) ->
                DnsTestFailure.ConnectionRefused
            e is java.net.SocketTimeoutException ->
                DnsTestFailure.TimedOut
            e is java.io.IOException && msg.isNotBlank() -> DnsTestFailure.Unknown(msg)
            else -> DnsTestFailure.Generic
        }
    }

    private class DnsNoAddressesException(val host: String) : java.io.IOException()

    private class ProxyHttpException(val code: Int) : java.io.IOException()

    private fun proxyFailure(t: Throwable): ProxyFailure = when (t) {
        is ProxyHttpException -> ProxyFailure.Http(t.code)
        is java.net.UnknownHostException -> ProxyFailure.HostUnreachable
        is java.net.SocketTimeoutException -> ProxyFailure.TimedOut
        is java.net.ConnectException -> ProxyFailure.ConnectionFailed
        else -> ProxyFailure.Unknown(t.message?.takeIf { it.isNotBlank() })
    }
}
