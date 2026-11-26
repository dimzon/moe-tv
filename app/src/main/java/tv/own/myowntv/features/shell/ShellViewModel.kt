package tv.own.myowntv.features.shell

import tv.own.owntv.core.util.launchGuarded
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import tv.own.owntv.core.customize.CategoryUnlockSession
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.nav.MainSection
import tv.own.owntv.core.nav.NavVisibility
import tv.own.owntv.core.network.ConnectivityObserver
import tv.own.owntv.core.weather.WeatherInfo
import tv.own.owntv.core.weather.WeatherRepository
import tv.own.owntv.core.repository.SourceRepository
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.theme.AccentColor
import tv.own.owntv.core.theme.FontCustomization
import tv.own.owntv.core.theme.ThemeMode
import tv.own.owntv.core.theme.UiFontScale
import tv.own.owntv.core.theme.UiZoom
import tv.own.owntv.core.categorylock.CategoryRelock
import tv.own.owntv.core.categorylock.SealedPlaybackGuard

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ShellViewModel(
    private val settings: SettingsRepository,
    private val sourceRepository: SourceRepository,
    connectivity: ConnectivityObserver,
    private val autoRefresh: tv.own.owntv.core.sync.AutoRefresh,
    private val weatherRepository: WeatherRepository,
    private val navVisibility: NavVisibility,
    private val categoryUnlocks: CategoryUnlockSession,
    private val customize: CustomizationStore,
) : ViewModel() {

    /** Which player presentation is on screen, and which section armed it. Held here rather than in
     *  the composition so a recreation of the Activity (a script-family language switch) does not
     *  close a film that is playing. */
    internal val playerMode = mutableStateOf(PlayerMode.NONE)
    internal val zapSource = mutableStateOf<MainSection?>(null)

    /** The startup update check runs once per launch, not once per shell composition. */
    var startupUpdateCheckDone = false

    /** Relocks the categories the PIN opened when the app leaves the screen. */
    private val relock = CategoryRelock(viewModelScope, customize, categoryUnlocks)

    /** Set by the shell while it is up: stops what plays from a category a relock seals. */
    internal var sealedPlayback: SealedPlaybackGuard?
        get() = relock.guard
        set(value) { relock.guard = value }

    /**
     * Closes every category the PIN opened; the app does this whenever it leaves the screen. Call it
     * before the engines free their streams: what is playing is read first, then stopped if now sealed.
     */
    fun relockCategories() = relock.leftApp()

    /** Back in front: [resume] what was freed only once the relock check has stopped what it sealed. */
    fun afterSealCheck(resume: () -> Unit) = relock.afterSealCheck(resume)

    /** Whether the device currently has internet (drives the offline banner). */
    val isOnline: StateFlow<Boolean> = connectivity.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), connectivity.isOnlineNow())

    /** The Auto refresh settings — core's [tv.own.owntv.core.sync.AutoRefresh], shared with the phone. */
    fun checkAutoRefresh(includeStartup: Boolean) {
        viewModelScope.launchGuarded { autoRefresh.check(includeStartup) }
    }

    val themeMode: StateFlow<ThemeMode> = settings.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.DARK)

    val uiZoomPercent: StateFlow<Int> = settings.uiZoomPercent
        .stateIn(viewModelScope, SharingStarted.Eagerly, UiZoom.DEFAULT)

    val fontCustomization: StateFlow<FontCustomization> = settings.fontCustomization
        .stateIn(viewModelScope, SharingStarted.Eagerly, FontCustomization())

    val animationLevel: StateFlow<tv.own.owntv.core.theme.AnimationLevel> = settings.animationLevel
        .stateIn(viewModelScope, SharingStarted.Eagerly, tv.own.owntv.core.theme.AnimationLevel.FULL)

    val accent: StateFlow<AccentColor> = settings.accent
        .stateIn(viewModelScope, SharingStarted.Eagerly, AccentColor.VIOLET)

    /** Custom accent hex ("#52DBC8"); blank = the preset above is in effect. */
    val customAccent: StateFlow<String> = settings.customAccent
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    /** Focus ring color hex; blank = follow the accent. */
    val focusHighlight: StateFlow<String> = settings.focusHighlight
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    /** Focus ring width in dp. */
    val focusHighlightWidth: StateFlow<Int> = settings.focusHighlightWidth
        .stateIn(viewModelScope, SharingStarted.Eagerly, 2)

    /** Background picture path (app-private); blank = no background (panels solid). */
    val bgImagePath: StateFlow<String> = settings.bgImagePath
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    /** Resolved glass config (which surfaces + alpha). Empty scope = feature off. */
    val glassConfig: StateFlow<tv.own.owntv.core.theme.GlassConfig> = settings.glassConfig
        .stateIn(viewModelScope, SharingStarted.Eagerly, tv.own.owntv.core.theme.GlassConfig())

    /** Glass & background: what sits behind every screen. */
    val backgroundConfig: StateFlow<tv.own.owntv.core.theme.BackgroundConfig> = settings.backgroundConfig
        .stateIn(viewModelScope, SharingStarted.Eagerly, tv.own.owntv.core.theme.BackgroundConfig())

    /** The built-in RuTube source's name for the top bar; null means it is not there yet. */
    val sourceSummary: StateFlow<String?> = sourceRepository.observeSources()
        .map { it.firstOrNull()?.name }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Weather chip. Refreshes when connectivity returns, cached 30 min by repository.
     * Gated by the "Show weather" setting (OFF hides the chip) and honouring a manual location
     * override so users on a VPN get their real city instead of the VPN server's.
     */
    val weather: StateFlow<WeatherInfo?> =
        combine(connectivity.isOnline, settings.weatherEnabled, settings.weatherLocation) { online, enabled, loc ->
            Triple(online, enabled, loc)
        }.flatMapLatest { (online, enabled, loc) ->
            if (!online || !enabled) flowOf(null as WeatherInfo?)
            else flow { emit(weatherRepository.get(loc)) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null as WeatherInfo?)

    /** °F display for the weather chip (default °C). */
    val weatherFahrenheit: StateFlow<Boolean> = settings.weatherFahrenheit
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Immersive fullscreen: hide the system status + navigation bars over the whole app.
     *  Eagerly, so MainActivity applies the stored value before the first frame (no bar flash). */
    val fullscreenMode: StateFlow<Boolean> = settings.fullscreenMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val _selectedSection = MutableStateFlow(MainSection.HOME)
    val selectedSection: StateFlow<MainSection> = _selectedSection.asStateFlow()

    // The section Settings was opened from, for Back out of its PIN prompt.
    private var sectionBeforeSettings = MainSection.MORE

    fun selectSection(section: MainSection) {
        if (section == MainSection.SETTINGS && _selectedSection.value != MainSection.SETTINGS) {
            sectionBeforeSettings = _selectedSection.value
        }
        _selectedSection.value = section
    }

    /**
     * Exit uses [tv.own.myowntv.MainActivity]'s `moveTaskToBack` (calling `finish()` there crashes
     * some Android 10 vendor firmwares), so the Activity, this ViewModel and the Compose tree all
     * stay alive in the background. A launcher relaunch therefore only gets `onNewIntent`, never a
     * fresh Activity/ViewModel — without this the shell resumes at the section it was exited on.
     *
     * Restore the cold-launch defaults so the start-up action runs from the same state it would
     * have had after `finish()`: no player, no armed zap source, Home selected.
     */
    fun resetForRelaunch() {
        playerMode.value = PlayerMode.NONE
        zapSource.value = null
        _selectedSection.value = MainSection.HOME
    }

    /** Where to go when Settings stays locked: see [sectionAfterLockedSettings]. */
    fun sectionToLeaveLockedSettings(): MainSection =
        sectionAfterLockedSettings(sectionBeforeSettings, visibleSections.value)

    /** Which browse sections currently show as icons in the rail (v4.3.0 — Nav menu customization).
     *  The rule itself lives in core's [NavVisibility], shared with the mobile app's bottom bar. */
    val visibleSections: StateFlow<Set<MainSection>> = navVisibility.visibleSections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainSection.allBrowse)

    init {
        // v4.3.0 — if the section the user is viewing becomes hidden (in either mode), jump to the first
        // still-visible browse item; if every browse item is hidden, fall back to Settings (always pinned).
        // SEARCH and SETTINGS are never auto-redirected away from.
        visibleSections
            .onEach { visible ->
                val current = _selectedSection.value
                // MORE is exempt for the same reason SETTINGS is: it is a pinned rail item, not a
                // browse section, so it is never in `visible` and would otherwise be redirected away
                // from the moment the user changed anything in Nav menu customization.
                if (current in visible ||
                    current == MainSection.SETTINGS ||
                    current == MainSection.SEARCH ||
                    current == MainSection.MORE
                ) {
                    return@onEach
                }
                _selectedSection.value = MainSection.browseOrder.firstOrNull { it in visible } ?: MainSection.SETTINGS
            }
            .launchIn(viewModelScope)
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launchGuarded { settings.setThemeMode(mode) }
    }

    fun setUiZoom(percent: Int) {
        viewModelScope.launchGuarded { settings.setUiZoomPercent(UiZoom.clamp(percent)) }
    }

    fun setFontCustomization(value: FontCustomization) {
        viewModelScope.launchGuarded {
            settings.setFontCustomization(
                value.copy(
                    sizePercent = UiFontScale.clamp(value.sizePercent),
                    popupFontSizePercent = tv.own.owntv.core.theme.PopupFontScale.clamp(value.popupFontSizePercent),
                    popupSizePercent = tv.own.owntv.core.theme.PopupSizeScale.clamp(value.popupSizePercent),
                ),
            )
        }
    }

    fun setAccent(accent: AccentColor) {
        viewModelScope.launchGuarded { settings.setAccent(accent) }
    }
}
