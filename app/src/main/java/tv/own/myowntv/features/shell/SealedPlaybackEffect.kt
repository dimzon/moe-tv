package tv.own.myowntv.features.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import tv.own.owntv.core.categorylock.PlayingCategories
import tv.own.owntv.core.categorylock.SealedPlaybackGuard
import tv.own.owntv.core.categorylock.sealedTileIndices
import tv.own.myowntv.features.live.LiveViewModel
import tv.own.myowntv.features.multiview.MultiviewState

/**
 * Gives [shellVm] the shell's [SealedPlaybackGuard] while the shell is up. A sealed Live channel leaves
 * the player ([exitPlayer]) and the preview pane, and its engine forgets it. A sealed Multiview tile is
 * emptied, which frees its engine, so the grid does not tune it again on return.
 */
@Composable
internal fun SealedPlaybackEffect(
    shellVm: ShellViewModel,
    liveVm: LiveViewModel,
    multiview: () -> MultiviewState?,
    exitPlayer: () -> Unit,
) {
    val exit by rememberUpdatedState(exitPlayer)
    val grid by rememberUpdatedState(multiview)
    DisposableEffect(shellVm, liveVm) {
        val inPlayer = { shellVm.playerMode.value != PlayerMode.NONE }
        shellVm.sealedPlayback = SealedPlaybackGuard(
            playing = {
                PlayingCategories(
                    live = liveVm.previewChannel.value?.let { tv.own.owntv.core.database.ChannelMembership.primaryCat(it) },
                    liveInPlayer = inPlayer(),
                    vod = null,
                    tiles = grid()?.tiles?.map { it.channel?.let { c -> tv.own.owntv.core.database.ChannelMembership.primaryCat(c) } }.orEmpty(),
                )
            },
            stopLive = { shown, _ ->
                if (shown && inPlayer()) exit()
                liveVm.stopPreview()
                liveVm.previewEngine.discardBackgroundRestore()
            },
            stopVod = {},
            stopTiles = { sealed ->
                grid()?.let { state -> sealedTileIndices(state.tiles.map { it.channel?.let { c -> tv.own.owntv.core.database.ChannelMembership.primaryCat(c) } }, sealed).forEach(state::clear) }
            },
        )
        onDispose { shellVm.sealedPlayback = null }
    }
}
