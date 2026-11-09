package tv.own.myowntv.di

import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import tv.own.myowntv.player.HeroPreviewEngine
import tv.own.myowntv.player.OwnTVPlayer
import tv.own.myowntv.player.livePreviewEngine
import tv.own.myowntv.player.ownTVPlayer

/** App-wide libmpv player. */
val playerModule = module {
    // Tails own-process logcat for MediaCodec/AudioTrack errors the engines can't expose.
    single { tv.own.myowntv.player.PlayerDiagnostics() }
    // Both engines are built by core's shared builders (named arguments, one copy for both apps).
    single { ownTVPlayer() }
    // ExoPlayer engine for the fast Live preview pane (mpv stays the full/fullscreen player).
    single { livePreviewEngine() }
    // Multiview's engines. The `single` above stays exactly what it was — the one long-lived engine
    // behind the Live preview pane and promoted fullscreen playback. The pool builds its own, one per
    // tile, and is the only thing that owns more than one at a time.
    single { tv.own.myowntv.player.LiveEnginePool { livePreviewEngine() } }
    // Muted ExoPlayer engine for the Home hero preview. The last argument lets it ask whether mpv is
    // already streaming, so a one-session provider isn't locked out by the hero preview.
    // Resolved lazily inside the lambda to keep this free of a construction-order dependency.
    single { HeroPreviewEngine(androidContext(), get(), get(), streamInUse = { get<OwnTVPlayer>().hasActiveStream }) }
    // Every engine above, so Home / the screensaver / memory pressure reach all of them in one call —
    // the Multiview pool included, which the hand-kept list in MainActivity used to miss.
    single {
        tv.own.myowntv.player.PlaybackEngines(
            player = get(),
            livePreview = get(),
            pool = get(),
            heroPreview = get(),
        )
    }
    // Whether the app can be seen and heard: the activity started and the television still connected.
    // MainActivity feeds it and frees / restores the engines on its changes.
    single { tv.own.myowntv.core.player.PlaybackVisibilityWatch(androidContext()) }
    // A screen of another app that OwnTV opens itself: no picture-in-picture, the same stop as Home.
    single {
        val engines = get<tv.own.myowntv.player.PlaybackEngines>()
        tv.own.myowntv.features.pip.AppHandOff(
            holdWindow = get<tv.own.myowntv.features.pip.PictureInPicture>()::holdWindow,
            stopPlayback = engines::onAppBackgrounded,
            restorePlayback = engines::onAppForegrounded,
        )
    }
    // Audio focus (duck-don't-pause) + the system MediaSession, driven by whichever engine is playing.
    single { tv.own.myowntv.player.PlaybackSession(androidContext()) }
    // Ends itself when the session detaches; what "stop" means is assigned by the shell.
    single { tv.own.myowntv.player.ScreenOff(androidContext()) }
    single { tv.own.myowntv.player.SleepTimer(active = get<tv.own.myowntv.player.PlaybackSession>().active, screenOff = get(), itemEnd = get<OwnTVPlayer>()) }
    // Bridges the playing item to the OpenSubtitles search. Bound here rather than with the rest of
    // the subtitle stack because it takes the player; it follows the engine to :player-core.
    single { tv.own.myowntv.core.subtitles.SubtitleController(get(), get(), get(), get()) }
}
