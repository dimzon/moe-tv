package tv.own.myowntv.features.shell
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
import tv.own.owntv.core.profile.*
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
                    live = liveVm.previewChannel.value?.categoryId,
                    liveInPlayer = inPlayer(),
                    vod = null,
                    tiles = grid()?.tiles?.map { it.channel?.categoryId }.orEmpty(),
                )
            },
            stopLive = { shown, _ ->
                if (shown && inPlayer()) exit()
                liveVm.stopPreview()
                liveVm.previewEngine.discardBackgroundRestore()
            },
            stopVod = {},
            stopTiles = { sealed ->
                grid()?.let { state -> sealedTileIndices(state.tiles.map { it.channel?.categoryId }, sealed).forEach(state::clear) }
            },
        )
        onDispose { shellVm.sealedPlayback = null }
    }
}
