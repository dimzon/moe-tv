package tv.own.myowntv.player
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import tv.own.myowntv.R
import tv.own.owntv.core.ui.findActivity
import kotlin.math.roundToInt

/** How long the mismatched stream must play before the suggestion appears — long enough that it never
 *  interrupts zapping, short enough that the judder is still on screen when it's read. */
private const val SETTLE_MS = 8_000L

/**
 * The one-time "your TV is at 60 Hz and this stream is 25 fps" suggestion.
 *
 * When the decoder draws straight to the SurfaceView the app does not control scan-out timing — frames
 * go to the decoder's surface and the display presents them on its own cadence. 25 fps on a fixed 60 Hz
 * output therefore becomes an uneven 2:3 pulldown (visible judder) while 50 fps maps cleanly. That is a
 * property of the hardware path, not something OwnTV can smooth over, and **Auto frame rate is the only
 * cure** — which ships off by default, so the affected user has no way to know it exists.
 *
 * Deliberately conservative, because an unsolicited dialog over a playing video is intrusive:
 * - full-screen only, and only while Auto frame rate is OFF;
 * - only when the display really is on a rate that isn't a multiple of the stream's, *and* a matching
 *   mode exists at the current resolution ([FrameRateController.betterRefreshRateFor]). On a panel with
 *   nothing but 60 Hz there is nothing to offer, so nothing is said;
 * - only after [SETTLE_MS] of continuous playback at that frame rate;
 * - shown **once ever**, whichever way it is answered.
 */
@Composable
fun AutoFrameRatePrompt(
    fps: Float?,
    afrEnabled: Boolean,
    alreadyPrompted: Boolean,
    onEnable: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var offer by remember { mutableStateOf<Pair<Float, Float>?>(null) } // current Hz to target Hz

    LaunchedEffect(fps, afrEnabled, alreadyPrompted) {
        offer = null
        if (afrEnabled || alreadyPrompted) return@LaunchedEffect
        // Below Android 12 the display never tells us which rates it can reach without blanking, so
        // enabling AFR there trades judder for a black screen at every switch — Settings actively warns
        // against it. Volunteering the suggestion on that hardware would be recommending both ways at
        // once, so the offer is simply never made; the setting is still there for anyone who wants it.
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return@LaunchedEffect
        val rate = fps ?: return@LaunchedEffect
        if (rate <= 0f) return@LaunchedEffect
        val activity = context.findActivity() ?: return@LaunchedEffect
        val target = FrameRateController.betterRefreshRateFor(activity, rate) ?: return@LaunchedEffect
        val current = FrameRateController.currentRefreshRate(activity) ?: return@LaunchedEffect
        delay(SETTLE_MS) // cancelled by any fps change, so zapping never triggers it
        offer = current to target
    }

    val (currentHz, targetHz) = offer ?: return
    tv.own.myowntv.ui.stage.StageConfirm(
        title = stringResource(R.string.player_frame_rate_prompt_title),
        body = stringResource(
            R.string.player_frame_rate_prompt_description,
            fps?.roundToInt() ?: 0,
            currentHz.roundToInt(),
            targetHz.roundToInt(),
        ),
        cancel = stringResource(R.string.settings_not_now),
        confirm = stringResource(R.string.player_frame_rate_turn_on),
        onConfirm = onEnable,
        onCancel = onDismiss,
        eyebrow = null,
    )
}
