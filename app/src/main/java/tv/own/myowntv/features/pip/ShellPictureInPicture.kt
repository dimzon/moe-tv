package tv.own.myowntv.features.pip
import tv.own.owntv.*
import tv.own.owntv.core.*
import tv.own.owntv.core.backup.*
import tv.own.owntv.core.brand.*
import tv.own.owntv.core.catalog.*
import tv.own.owntv.core.categorylock.*
import tv.own.owntv.core.companion.*
import tv.own.owntv.core.content.*
import tv.own.owntv.core.customize.*
import tv.own.owntv.core.database.*
import tv.own.owntv.core.database.dao.*
import tv.own.owntv.core.database.entity.*
import tv.own.owntv.core.di.*
import tv.own.owntv.core.download.*
import tv.own.owntv.core.drm.*
import tv.own.owntv.core.epg.*
import tv.own.owntv.core.home.*
import tv.own.owntv.core.i18n.*
import tv.own.owntv.core.launcher.*
import tv.own.owntv.core.live.*
import tv.own.owntv.core.menu.*
import tv.own.owntv.core.metadata.*
import tv.own.owntv.core.model.*
import tv.own.owntv.core.nav.*
import tv.own.owntv.core.network.*
import tv.own.owntv.core.parser.*
import tv.own.owntv.core.player.*
import tv.own.owntv.core.profile.*
import tv.own.owntv.core.recording.*
import tv.own.owntv.core.reminder.*
import tv.own.owntv.core.repository.*
import tv.own.owntv.core.settings.*
import tv.own.owntv.core.setup.*
import tv.own.owntv.core.sports.*
import tv.own.owntv.core.sports.model.*
import tv.own.owntv.core.stalker.*
import tv.own.owntv.core.storage.*
import tv.own.owntv.core.subtitles.*
import tv.own.owntv.core.sync.*
import tv.own.owntv.core.sync.local.*
import tv.own.owntv.core.sync.work.*
import tv.own.owntv.core.theme.*
import tv.own.owntv.core.timeshift.*
import tv.own.owntv.core.trending.*
import tv.own.owntv.core.tv.*
import tv.own.owntv.core.ui.*
import tv.own.owntv.core.ui.stage.*
import tv.own.owntv.core.update.*
import tv.own.owntv.core.util.*
import tv.own.owntv.core.weather.*
import tv.own.owntv.player.*

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import org.koin.compose.koinInject
import tv.own.owntv.player.PlaybackEngine
import kotlin.math.roundToInt

/**
 * What the shell needs from picture-in-picture: [inWindow] is true while the activity is in the small
 * window, where the shell shows only the video (no HUD, mini-player chrome, subtitles or prompts).
 * [source] goes on the player's box, so the window opens from where the video is.
 */
internal data class ShellPictureInPictureState(val inWindow: Boolean, val source: Modifier)

/**
 * The shell's half of picture-in-picture: keeps [PictureInPicture.offer] in step with what is on
 * screen and reports where the player is laid out.
 *
 * [onReturnToApp] runs when the user brings the window back to full screen. Closing the window also
 * leaves picture-in-picture, but then the activity has already stopped; that case is told apart by the
 * lifecycle and left to `onStop`, which releases the players.
 */
@Composable
internal fun rememberShellPictureInPicture(
    playerShown: Boolean,
    multiview: Boolean,
    engine: PlaybackEngine,
    videoAspect: StateFlow<Float?>,
    onReturnToApp: () -> Unit,
): ShellPictureInPictureState {
    val pip = koinInject<PictureInPicture>()
    val optionOn by pip.enabled.collectAsStateWithLifecycle()
    val inWindow by pip.inWindow.collectAsStateWithLifecycle()
    val playing by engine.isPlaying.collectAsStateWithLifecycle()
    val buffering by engine.buffering.collectAsStateWithLifecycle()
    val audioOnlyMedia by engine.audioOnlyMedia.collectAsStateWithLifecycle()
    val audioMode by engine.audioOnly.collectAsStateWithLifecycle()
    val aspect by videoAspect.collectAsStateWithLifecycle()
    val conditions = PipConditions(
        optionOn = optionOn,
        supported = pip.supported,
        playerShown = playerShown,
        multiview = multiview,
        audioOnly = audioOnlyMedia || audioMode,
        playing = playing || buffering,
    )
    val offer = if (PipPolicy.eligible(conditions)) PipPolicy.aspect(aspect) else null
    LaunchedEffect(offer) { pip.offer(offer) }
    DisposableEffect(pip) { onDispose { pip.offer(null) } }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val returnToApp by rememberUpdatedState(onReturnToApp)
    LaunchedEffect(pip, lifecycle) {
        pip.inWindow.drop(1).filter { !it }.collect {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) returnToApp()
        }
    }
    val source = remember(pip) {
        Modifier.onGloballyPositioned { coordinates ->
            // In the window the player fills it; only the full-screen layout says where the window opens from.
            if (pip.inWindow.value) return@onGloballyPositioned
            val r = coordinates.boundsInWindow()
            pip.onPlayerBounds(PipBounds(r.left.roundToInt(), r.top.roundToInt(), r.right.roundToInt(), r.bottom.roundToInt()))
        }
    }
    return ShellPictureInPictureState(inWindow, source)
}
