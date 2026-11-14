package tv.own.myowntv
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

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.room.useWriterConnection
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import tv.own.owntv.core.util.Perf
import tv.own.owntv.core.launcher.LauncherDeepLink
import tv.own.myowntv.features.setup.Onboarding
import tv.own.myowntv.features.shell.MyOwnTVShell
import tv.own.myowntv.features.shell.ShellViewModel
import tv.own.myowntv.ui.theme.BlurredBackdrop
import tv.own.myowntv.ui.theme.LocalBackground
import tv.own.myowntv.ui.theme.LocalBlurredBackdrop
import tv.own.myowntv.ui.theme.LocalGlass
import tv.own.myowntv.ui.theme.LocalUiFontScaleFactor
import tv.own.myowntv.ui.theme.OwnTVTheme
import tv.own.owntv.core.theme.UiFontScale
import tv.own.owntv.core.theme.UiZoom

open class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "OwnTVHome"

        /** Bounded wait for the startup database probe (see [probeDatabase]). */
        private const val DB_PROBE_TIMEOUT_MS = 5_000L

        /**
         * Hard ceiling on the splash. A stuck DataStore read must never leave the user
         * staring at a static logo — past this the splash dismisses whatever the state is.
         *
         * Kept short deliberately: the old build drew its (blank) first frame at ~1.1s, so anything
         * beyond ~2s of splash is a perceived-responsiveness regression even though the app is doing
         * strictly less work than before.
         */
        private const val SPLASH_TIMEOUT_MS = 2_000L

        /**
         * How much longer, once the destination is known, the splash waits for the start-up screen. Home settles
         * at once, so only a start on the last channel ever waits; on a 55k-channel playlist on the TCL that
         * took 2.2–3.3 s from launch, and a shorter wait showed Home for a moment before Live TV or the Guide.
         */
        private const val STARTUP_SETTLE_TIMEOUT_MS = 3_000L

        /** First-run onboarding, remembered across launches. There are no profiles to check — this
         *  flag is what decides between the setup wizard and the shell. */
        private const val SETUP_PREFS = "setup_state"
        private const val KEY_ONBOARDED = "onboarded"
    }

    private fun isOnboarded(): Boolean =
        getSharedPreferences(SETUP_PREFS, android.content.Context.MODE_PRIVATE).getBoolean(KEY_ONBOARDED, false)

    private fun markOnboarded() {
        getSharedPreferences(SETUP_PREFS, android.content.Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ONBOARDED, true).apply()
    }

    /**
     * The destination is now decided synchronously (the first-run flag), so this latches true at the
     * top of [onCreate] — the only failure it still guards against is a database that cannot be
     * opened, which shows its own recovery screen instead.
     */
    @Volatile
    private var contentReady = false

    /**
     * Whether the shell has opened where the app's start-up choice says (the TV Guide, Live TV, the
     * player...). The shell first composes on Home and applies that choice a moment later, so the splash
     * stays up until then and Home never flashes on the way. Owned by this activity, so a relaunch in a
     * process that is still alive waits again.
     */
    @Volatile
    private var startupSettled = false

    private val livePreview: tv.own.owntv.player.LivePreviewEngine by inject()
    private val engines: tv.own.owntv.player.PlaybackEngines by inject()
    private val visibility: tv.own.owntv.core.player.PlaybackVisibilityWatch by inject()
    private val handOff: tv.own.myowntv.features.pip.AppHandOff by inject()
    // Activity-scoped: the same instance Compose retrieves via koinViewModel() inside setContent.
    private val shellViewModel: ShellViewModel by viewModel()
    // The sole locale authority (SharedPreferences-backed; see docs/internationalization.md 0b).
    private val localeStore: tv.own.owntv.core.i18n.LocaleStore by inject()
    private var pendingDeepLink by mutableStateOf<LauncherDeepLink?>(null)

    /** The last collected Fullscreen-mode value, so [onResume] can re-apply it without reading DataStore. */
    private var fullscreenRequested = true

    /**
     * Wrap the Activity base with the selected locale so its own `Resources` resolve correctly —
     * Application wrapping alone leaves the Activity wrong, because
     * `ActivityThread.createBaseContextForActivity` never derives from the Application object
     * (see docs/internationalization.md 0b, "Both Application and Activity must wrap").
     */
    override fun attachBaseContext(newBase: android.content.Context) {
        val tag = tv.own.owntv.core.i18n.LocaleStore.from(newBase).readBlocking()
        super.attachBaseContext(tv.own.owntv.core.i18n.AppLocale.wrap(newBase, tag))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDeepLink = LauncherDeepLink.parse(intent.data)
        Log.d(TAG, "onNewIntent deepLinkHost=${intent.data?.host} deepLinkType=${pendingDeepLink?.javaClass?.simpleName ?: "none"}")
    }

    /**
     * Left the app — Home, another app, exited, logged out, the picture-in-picture window closed, or the
     * television switched off while this activity kept running: stop playback and free the demuxer cache +
     * decoder buffers — holding them while invisible got the process LMK-killed on real TVs. Every engine:
     * the Live engine (its audio must stop too), each Multiview tile and the hero. The relock first: it
     * reads what is playing before the engines free it.
     *
     * Back: bring back what was freed — a live channel re-tuned to the live edge — so Play resumes
     * instead of sitting on a dead stream. No-op on a fresh launch. Not before the relock check is done:
     * what it found sealed must not come back first.
     */
    private val onVisibility: (Boolean) -> Unit = { visible ->
        if (visible) shellViewModel.afterSealCheck { engines.onAppForegrounded() }
        else { shellViewModel.relockCategories(); engines.onAppBackgrounded() }
    }

    override fun onStop() {
        super.onStop()
        // A picture-in-picture window only pauses the activity, so playback there never reaches this.
        if (!isChangingConfigurations) visibility.activityStopped()
        handOff.onActivityStopped()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onDestroy() {
        visibility.removeListener(onVisibility)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        // The bars may have come back while another app, a dialog or a hand-off held the focus — re-hide.
        applyFullscreen(fullscreenRequested)
        handOff.onActivityResumed()
    }

    /**
     * Immersive fullscreen: hide the system status + navigation bars — SmartRuTube's
     * makeActivityFullscreen2 through the androidx compat wrappers, so one branch serves both the
     * API 26–29 legacy SYSTEM_UI_FLAG_* mapping and the API 30+ WindowInsetsController. The setting
     * lives in Settings › Appearance ("Fullscreen mode"); on a television there are no bars, so the
     * whole call is a no-op there.
     */
    private fun applyFullscreen(enabled: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (enabled) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            // Transient bars: a swipe from the edge reveals them briefly, then they hide themselves.
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (Build.VERSION.SDK_INT >= 28) {
                // Draw under the display cutout: without this a notched phone letterboxes the strip
                // where the hidden status bar used to sit.
                window.attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            if (Build.VERSION.SDK_INT >= 28) {
                window.attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
            }
        }
    }

    /** Every screen this activity opens (an external player, a system settings page) is a hand-off. */
    override fun startActivity(intent: Intent, options: Bundle?) {
        tv.own.myowntv.features.pip.startAsHandOff(intent) { super.startActivity(it, options) }
    }

    override fun onStart() {
        super.onStart()
        // Paired with onStop (see [onVisibility]); notably the TV screensaver, which kicks in during a long pause.
        visibility.activityStarted()
        // Staleness-based auto refresh on resume (interval modes only — STARTUP is cold-start only). The
        // ViewModel throttles this internally so a quick toggle doesn't re-run the check.
        shellViewModel.checkAutoRefresh(includeStartup = false)
    }

    /**
     * Probe the database before anything touches it. Room opens lazily, so a failed migration would
     * otherwise surface as a random crash inside whichever coroutine queried first — and with the
     * destructive fallback gone that crash would repeat on every launch. Opening it here, on a
     * worker thread with a bounded wait, turns that into a deterministic recovery screen.
     *
     * On a healthy install this is a version check on an already-migrated file: milliseconds, and it
     * is work the first query would have done anyway. A timeout is treated as "healthy" so a slow
     * device never sits on a blank window.
     */
    private fun probeDatabase(): String? {
        var error: String? = null
        val worker = Thread {
            // Core configures a SQLiteDriver, and `openHelper` throws outright
            // once one is set. Opening a connection is what this probe was always really doing —
            // forcing Room to run the migration chain now rather than inside the first query.
            runCatching {
                kotlinx.coroutines.runBlocking {
                    get<tv.own.owntv.core.database.OwnTVDatabase>().useWriterConnection { }
                }
            }.onFailure { error = it.message ?: it.javaClass.simpleName }
        }
        worker.start()
        worker.join(DB_PROBE_TIMEOUT_MS)
        if (error != null) Log.e(TAG, "database probe failed: $error")
        return error
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be installed BEFORE super.onCreate() per the library's contract. It also applies
        // postSplashScreenTheme (Theme.MyOwnTV), so the activity ends up in the same theme as before.
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        tv.own.myowntv.features.pip.PictureInPictureWindow.attach(this, get())
        // The window only pauses the activity, so onStop's relock never runs while it is open: going into
        // it is leaving the app, and a channel from a category the PIN opened stops there too.
        addOnPictureInPictureModeChangedListener { if (it.isInPictureInPictureMode) shellViewModel.relockCategories() }
        visibility.addListener(onVisibility)
        val splashDeadline = SystemClock.uptimeMillis() + SPLASH_TIMEOUT_MS
        var readyAt = 0L
        splash.setKeepOnScreenCondition {
            val now = SystemClock.uptimeMillis()
            if (!contentReady) return@setKeepOnScreenCondition now < splashDeadline
            if (readyAt == 0L) readyAt = now
            // Then until the shell is on the start-up screen, so Home does not flash first.
            !startupSettled && now < readyAt + STARTUP_SETTLE_TIMEOUT_MS
        }
        pendingDeepLink = LauncherDeepLink.parse(intent.data)
        Log.d(TAG, "onCreate deepLinkHost=${intent.data?.host} deepLinkType=${pendingDeepLink?.javaClass?.simpleName ?: "none"}")
        val dbError = probeDatabase()
        if (dbError != null) {
            contentReady = true // the recovery screen IS the destination — don't hold the splash over it
            startupSettled = true
            setContent {
                tv.own.myowntv.features.recovery.DatabaseRecoveryScreen(
                    message = dbError,
                    onRetry = { recreate() },
                    onResetData = {
                        // Explicit, twice-confirmed user choice — the only thing that clears a
                        // database SQLite refuses to open. Never automatic: automatic deletion was a real bug once.
                        runCatching { deleteDatabase(tv.own.owntv.core.database.OwnTVDatabase.NAME) }
                        finishAffinity()
                    },
                )
            }
            return
        }
        Perf.stamp("db-probed") // main thread was blocked here: everything above is pre-composition
        // The destination needs no asynchronous data any more: one stored flag says wizard or shell,
        // and the splash is never held over a decision that was already made.
        contentReady = true
        if (!isOnboarded()) startupSettled = true // the wizard is the destination; nothing to settle on
        setContent {
            // First composition pass — splits "process start → Compose is running" from
            // "Compose is running → the destination's data arrived".
            // Deliberately a Unit-returning remember: the point is the side effect happening DURING
            // the first composition pass. LaunchedEffect/SideEffect would run after it and measure
            // something else.
            @Suppress("RememberReturnType")
            remember { Perf.stamp("first-composition"); Unit }

            // Hold the screen on while video is actually playing, so the TV screensaver doesn't
            // start mid-channel; released when paused/stopped (then the screensaver is fine).
            // Buffering counts as playing: the screensaver timer runs from the last remote press, so
            // releasing for a one-second rebuffer mid-channel started the screensaver at once.
            val playing by livePreview.isPlaying.collectAsStateWithLifecycle()
            val buffering by livePreview.buffering.collectAsStateWithLifecycle()
            val failed by livePreview.error.collectAsStateWithLifecycle()
            val keepAwake = (playing || buffering) && failed == null
            LaunchedEffect(keepAwake) {
                if (keepAwake) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }

            // Fullscreen mode (Settings › Appearance): hides the system bars while on. onResume
            // re-applies [fullscreenRequested], so this effect only reacts to live setting changes.
            val fullscreenMode by shellViewModel.fullscreenMode.collectAsStateWithLifecycle()
            LaunchedEffect(fullscreenMode) {
                fullscreenRequested = fullscreenMode
                applyFullscreen(fullscreenMode)
            }

            val viewModel: ShellViewModel = koinViewModel()
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val accent by viewModel.accent.collectAsStateWithLifecycle()
            val customAccent by viewModel.customAccent.collectAsStateWithLifecycle()
            val focusHighlight by viewModel.focusHighlight.collectAsStateWithLifecycle()
            val focusHighlightWidth by viewModel.focusHighlightWidth.collectAsStateWithLifecycle()
            val uiZoomPercent by viewModel.uiZoomPercent.collectAsStateWithLifecycle()
            val fontCustomization by viewModel.fontCustomization.collectAsStateWithLifecycle()
            val animationLevel by viewModel.animationLevel.collectAsStateWithLifecycle()
            // Animations Off also stills the launch screen. Android shows it before the app runs, so the
            // choice can only be handed over for the next launch, and only from Android 13 on.
            LaunchedEffect(animationLevel) {
                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    val still = animationLevel == tv.own.owntv.core.theme.AnimationLevel.OFF
                    splashScreen.setSplashScreenTheme(
                        if (still) stillLaunchTheme(tv.own.owntv.core.brand.AppIconSwitcher.applied(this@MainActivity)) else 0,
                    )
                }
            }
            val bgImagePath by viewModel.bgImagePath.collectAsStateWithLifecycle()
            val glassConfig by viewModel.glassConfig.collectAsStateWithLifecycle()
            val background by viewModel.backgroundConfig.collectAsStateWithLifecycle()
            val sourceSummary by viewModel.sourceSummary.collectAsStateWithLifecycle()
            val weather by viewModel.weather.collectAsStateWithLifecycle()
            val weatherFahrenheit by viewModel.weatherFahrenheit.collectAsStateWithLifecycle()
            val selectedSection by viewModel.selectedSection.collectAsStateWithLifecycle()
            val visibleSections by viewModel.visibleSections.collectAsStateWithLifecycle()
            val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()

            // Profiles are gone: the only destination decision left is "first run or not", a flag
            // the wizard raises when it finishes. Read once per launch, flipped in-session so the
            // shell composes the moment onboarding ends — no restart.
            var onboarded by remember { mutableStateOf(isOnboarded()) }


            OwnTVTheme(
                themeMode = themeMode,
                accent = accent,
                systemInDarkTheme = isSystemInDarkTheme(),
                customAccent = customAccent,
                focusHighlight = focusHighlight,
                focusBorderWidthDp = focusHighlightWidth,
                animationLevel = animationLevel,
                mainFontFamily = fontCustomization.mainFamily,
                popupFontFamily = fontCustomization.popupFamily,
                popupFontSizePercent = fontCustomization.popupFontSizePercent,
                popupSizePercent = fontCustomization.popupSizePercent,
            ) {
                val base = LocalDensity.current
                // Root viewport size in px — the area the background image fills and that the blurred
                // backdrop stands in for. Captured here (top of the tree) so glass panels can map their
                // own on-screen rect into the blurred bitmap's coordinate space.
                var rootSizePx by remember { mutableStateOf(Size.Zero) }
                // Glass & background: a picture is drawn blurred (its Blur) and glass frosts it, so the
                // blurred copy is built whenever a picture is shown, glass on or off. The blur itself is
                // CPU work done once per picture, so every device gets it.
                val needsBackdropAssets = background.showsPicture
                val supportsFrostPyramid = true
                val blurred by produceState<BlurredBackdrop?>(
                    initialValue = null,
                    bgImagePath,
                    needsBackdropAssets,
                    supportsFrostPyramid,
                    rootSizePx,
                ) {
                    if (!needsBackdropAssets || rootSizePx.width <= 0f || rootSizePx.height <= 0f) {
                        value = null
                        return@produceState
                    }
                    // Decode + blur on a background dispatcher; never block the main thread.
                    val path = bgImagePath
                    value = produceBlurredBackdrop(path, rootSizePx, supportsFrostPyramid)
                }
                CompositionLocalProvider(
                    LocalDensity provides Density(
                        base.density * UiZoom.factor(uiZoomPercent),
                        base.fontScale * UiFontScale.factor(fontCustomization.sizePercent),
                    ),
                    LocalUiFontScaleFactor provides UiFontScale.factor(fontCustomization.sizePercent),
                    LocalGlass provides glassConfig,
                    LocalBlurredBackdrop provides blurred,
                    LocalBackground provides background,
                ) {
                    // Wrap the shell in the locale locals (LocalResources/LocalContext/
                    // LocalConfiguration/LocalLayoutDirection) for the currently selected locale, and
                    // trigger the single documented Activity.recreate() across a script-family switch.
                    // Same-script switches apply instantly here. See docs/internationalization.md 0b.
                    tv.own.owntv.core.i18n.LocalizedContent(localeStore) {
                    Box(
                        modifier = Modifier.fillMaxSize()
                            // Solid base color first (fallback if no image / image fails to load).
                            .background(tv.own.myowntv.ui.theme.LocalStagePalette.current.page)
                            .onGloballyPositioned {
                                rootSizePx = Size(it.size.width.toFloat(), it.size.height.toFloat())
                            },
                    ) {
                        // Background image sits behind everything when a path is set; otherwise the solid
                        // base color shows through and glass renders over that instead.
                        if (background.showsPicture) BackgroundLayer(background, blurred)

                        Box(modifier = Modifier.fillMaxSize()) {
                        if (!onboarded) {
                            // First run: the setup wizard, and the shell the moment it finishes.
                            Onboarding(
                                onDone = { markOnboarded(); onboarded = true },
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            LaunchedEffect(Unit) { Perf.stamp("destination-ready") } // splash hand-off — the number startup work moves
                            MyOwnTVShell(
                                selectedSection = selectedSection,
                                visibleSections = visibleSections,
                                onSelectSection = viewModel::selectSection,
                                themeMode = themeMode,
                                uiZoomPercent = uiZoomPercent,
                                onSetZoom = viewModel::setUiZoom,
                                fontCustomization = fontCustomization,
                                onSetFontCustomization = viewModel::setFontCustomization,
                                sourceSummary = sourceSummary,
                                weatherInfo = weather,
                                weatherFahrenheit = weatherFahrenheit,
                                pendingDeepLink = pendingDeepLink,
                                onDeepLinkConsumed = { pendingDeepLink = null },
                                isOffline = !isOnline,
                                onExitApp = { finish() },
                                onStartupSettled = { startupSettled = true },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        } // end foreground Box (above the background layer)
                    }
                    } // end LocalizedContent
                }
            }
        }
    }
}

/** The user's picture behind the entire shell (Glass & background). Callers check [BackgroundConfig.showsPicture]. */
@androidx.compose.runtime.Composable
private fun BackgroundLayer(background: tv.own.owntv.core.theme.BackgroundConfig, blurred: BlurredBackdrop?) {
    tv.own.myowntv.ui.components.BackgroundPicture(background, blurred, Modifier.fillMaxSize())
}
