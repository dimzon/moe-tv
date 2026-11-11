package tv.own.myowntv.di
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

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import tv.own.owntv.player.HeroPreviewEngine
import tv.own.owntv.player.livePreviewEngine

/** The app's ExoPlayer engines and the objects that drive them. */
val playerModule = module {
    // Tails own-process logcat for MediaCodec/AudioTrack errors the engines can't expose.
    single { tv.own.owntv.player.PlayerDiagnostics() }
    // Built by core's shared builder (named arguments, one copy for both apps).
    // The one long-lived ExoPlayer engine: the fast Live preview pane and promoted fullscreen playback.
    single { livePreviewEngine() }
    // Multiview's engines. The `single` above stays exactly what it was — the one long-lived engine
    // behind the Live preview pane and promoted fullscreen playback. The pool builds its own, one per
    // tile, and is the only thing that owns more than one at a time.
    single { tv.own.owntv.player.LiveEnginePool { livePreviewEngine() } }
    // Muted ExoPlayer engine for the Home hero preview. The last argument lets it ask whether the Live
    // preview already holds a stream, so a one-session provider isn't locked out by the hero preview.
    // Resolved lazily inside the lambda to keep this free of a construction-order dependency.
    single { HeroPreviewEngine(androidContext(), get(), get(), streamInUse = { get<LivePreviewEngine>().currentUrl != null }) }
    // Every engine above, so Home / the screensaver / memory pressure reach all of them in one call —
    // the Multiview pool included, which the hand-kept list in MainActivity used to miss.
    single {
        tv.own.owntv.player.PlaybackEngines(
            context = androidContext(),
            livePreview = get(),
            pool = get(),
            heroPreview = get(),
        )
    }
    // Whether the app can be seen and heard: the activity started and the television still connected.
    // MainActivity feeds it and frees / restores the engines on its changes.
    single { tv.own.owntv.core.player.PlaybackVisibilityWatch(androidContext()) }
    // A screen of another app that OwnTV opens itself: no picture-in-picture, the same stop as Home.
    single {
        val engines = get<tv.own.owntv.player.PlaybackEngines>()
        tv.own.myowntv.features.pip.AppHandOff(
            holdWindow = get<tv.own.myowntv.features.pip.PictureInPicture>()::holdWindow,
            stopPlayback = engines::onAppBackgrounded,
            restorePlayback = engines::onAppForegrounded,
        )
    }
    // Audio focus (duck-don't-pause) + the system MediaSession, driven by whichever engine is playing.
    single { tv.own.owntv.player.PlaybackSession(androidContext()) }
    // Ends itself when the session detaches; what "stop" means is assigned by the shell.
    single { tv.own.owntv.player.ScreenOff(androidContext()) }
    single { tv.own.owntv.player.SleepTimer(active = get<tv.own.owntv.player.PlaybackSession>().active, screenOff = get()) }
}
