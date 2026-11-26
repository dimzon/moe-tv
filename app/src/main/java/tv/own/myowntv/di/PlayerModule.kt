package tv.own.myowntv.di

import tv.own.owntv.player.LivePreviewEngine
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
