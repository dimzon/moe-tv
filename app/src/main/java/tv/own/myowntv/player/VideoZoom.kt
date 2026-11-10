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

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tv.own.owntv.player.ZoomMode

/**
 * Zoom/aspect sizing for the one video surface, [ExoPreviewSurface] (ExoPlayer live preview +
 * promoted full-screen). The video engine always fills its surface edge-to-edge, so zoom / letterbox /
 * crop is done entirely by **sizing the surface view itself** inside a clipped black box (the approach
 * ExoPlayer/YouTube use). Every Live channel therefore scales by the same math in the pane, the docked
 * mini-player and full screen.
 *
 * Must be called inside a [androidx.compose.foundation.layout.BoxWithConstraints] scope so the caller can
 * pass the incoming [maxWidth]/[maxHeight] (the on-screen container).
 *
 * - [aspect] is the video's display aspect ratio (w/h, PAR-corrected). null/≤0 ⇒ no dimensions yet → fill.
 * - [videoSize] is the video's native pixel (w, h); used only by ORIGINAL (1:1) to render at true size.
 */
private const val ZOOM_CROP_FACTOR = 1.2f

@Composable
fun Modifier.videoZoom(
    zoom: ZoomMode,
    aspect: Float?,
    videoSize: Pair<Int, Int>?,
    maxWidth: Dp,
    maxHeight: Dp,
): Modifier {
    if (aspect == null || aspect <= 0f) {
        // No video dimensions yet — just fill the slot.
        return fillMaxSize()
    }
    val cw = maxWidth
    val ch = maxHeight
    val containerAspect = cw.value / ch.value
    return when (zoom) {
        ZoomMode.STRETCH -> fillMaxSize()
        ZoomMode.ORIGINAL -> {
            val vs = videoSize
            if (vs != null && vs.first > 0 && vs.second > 0) {
                // requiredSize (not size): native pixel dims can exceed the container (e.g. a 4K frame on
                // a smaller output), and a plain size()/width()/height() modifier is clamped back down to
                // the incoming BoxWithConstraints max — it can shrink a box but never grow one past the
                // container. requiredSize ignores that ceiling so the view genuinely overflows and the
                // parent's clipToBounds() does the cropping.
                val density = LocalDensity.current
                with(density) { requiredSize(vs.first.toDp(), vs.second.toDp()) }
            } else {
                aspectRatio(aspect)
            }
        }
        ZoomMode.FILL -> {
            // TV-style "zoom/crop": take the letterboxed FIT box, then scale it up by a fixed factor so
            // it overflows the container on every side; the parent's clipToBounds() crops the excess.
            // Always visible, even when the video's native aspect already matches the container's (unlike
            // a plain aspect-correct "cover", which is a no-op in that case). requiredWidth/Height (not
            // width/height): the whole point is to exceed the container, and plain width()/height() get
            // silently clamped back to the incoming max constraint.
            val fitWidthDriven = aspect >= containerAspect
            val fitW = if (fitWidthDriven) cw.value else ch.value * aspect
            val fitH = if (fitWidthDriven) cw.value / aspect else ch.value
            requiredWidth((fitW * ZOOM_CROP_FACTOR).dp).requiredHeight((fitH * ZOOM_CROP_FACTOR).dp)
        }
        else -> {
            // Target box aspect for the view; the surface stretches to fill it.
            val targetAspect = when (zoom) {
                ZoomMode.FORCE_16_9 -> 16f / 9f
                ZoomMode.FORCE_4_3 -> 4f / 3f
                else -> aspect // FIT keeps the video aspect
            }
            // contain: largest box of targetAspect fitting inside the container.
            val widthDriven = targetAspect >= containerAspect
            if (widthDriven) {
                width(cw).height((cw.value / targetAspect).dp)
            } else {
                height(ch).width((ch.value * targetAspect).dp)
            }
        }
    }
}
