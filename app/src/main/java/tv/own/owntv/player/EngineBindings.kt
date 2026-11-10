package tv.own.owntv.player

import org.koin.android.ext.koin.androidContext
import org.koin.core.scope.Scope

/*
 * How both apps build the live engine they bind. `:player-core` ships no Koin module — each app still
 * decides how many engines it holds and when — but the constructor call lives here once, with named
 * arguments, so neither app carries a copy whose `get()` calls silently depend on parameter order.
 */

/** One ExoPlayer live engine: the preview pane / live engine, or a Multiview tile from the pool. */
fun Scope.livePreviewEngine(): LivePreviewEngine = LivePreviewEngine(
    context = androidContext(),
    streamingHttp = get(),
    diagnostics = get(),
    settings = get(),
    connectivity = get(),
    playbackPrefs = get(),
)
