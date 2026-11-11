package tv.own.myowntv.player
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

import android.content.Context
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.settings.SubtitleStyle
import tv.own.myowntv.ui.theme.asAndroidTypeface

/**
 * Hosts the [LivePreviewEngine]'s ExoPlayer video (a [SurfaceView]) for the Live preview pane AND the
 * promoted full-screen live player — the app's one and only video surface. Zoom/aspect is done by
 * sizing the view itself via [Modifier.videoZoom]: the picture is scaled by the view, never inside the
 * decoder, so every surface behaves identically. The surface is handed to the engine on create and
 * released on destroy. [keepAwake] holds the screen on (TV screensaver off) while actively watching
 * full-screen/PiP.
 */
@Composable
fun ExoPreviewSurface(
    engine: LivePreviewEngine,
    modifier: Modifier = Modifier,
    keepAwake: Boolean = false,
    autoFrameRate: Boolean = false,
    /**
     * Draw through a [TextureView] instead of a [SurfaceView].
     *
     * A SurfaceView is a hole punched in the app's window that the display hardware fills directly.
     * It is the right thing for one full-screen picture — it is what keeps 4K and HDR on their
     * hardware path — but a television has only so many of those hardware video planes, and several
     * have exactly one. A second SurfaceView asking for a plane that does not exist gets no picture,
     * while its audio, which needs no plane at all, carries on. That is precisely the Multiview
     * symptom: one tile with sound and no image.
     *
     * A TextureView is an ordinary view composited by the GPU, so any number of them can draw at
     * once. It costs some GPU and gives up the hardware-overlay path, which is a fair trade for a
     * quarter-screen tile and a bad one for full screen — hence a parameter rather than a change.
     */
    useTextureView: Boolean = false,
) {
    // Only the full-screen live player passes autoFrameRate = true — the in-pane preview must never
    // reconfigure the display while the user is just scrolling the channel list.
    val fps by engine.videoFps.collectAsStateWithLifecycle()
    // Media3 has its own Surface.setFrameRate path, independent of FrameRateController. Keep it off in
    // previews/mini-player and make the full-screen path obey the same AFR setting.
    LaunchedEffect(engine, autoFrameRate) { engine.setAutoFrameRateEnabled(autoFrameRate) }
    AutoFrameRateEffect(fps, autoFrameRate)
    BoxWithConstraints(modifier.background(Color.Black).clipToBounds(), contentAlignment = Alignment.Center) {
        val aspect by engine.videoAspect.collectAsStateWithLifecycle()
        val videoSize by engine.videoSize.collectAsStateWithLifecycle()
        val zoom by engine.zoomMode.collectAsStateWithLifecycle()
        // Keyed on the engine's surface generation: when it releases a 4K decoder it bumps the counter,
        // which drops this SurfaceView and builds a new one. Some hardware decoders only ever accept one
        // 4K codec per Surface — see LivePreviewEngine.recreateSurface.
        val surfaceGeneration by engine.surfaceGeneration.collectAsStateWithLifecycle()
        val viewModifier = Modifier.videoZoom(zoom, aspect, videoSize, maxWidth, maxHeight)
        key(surfaceGeneration) {
            if (useTextureView) {
                AndroidView(
                    modifier = viewModifier,
                    factory = { ctx ->
                        TextureView(ctx).apply {
                            // The Surface is ours to make and ours to release: a TextureView hands out
                            // a SurfaceTexture, not a Surface, and leaking one keeps a decoder alive.
                            var surface: Surface? = null
                            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                                    surface = Surface(texture).also { engine.setSurface(it) }
                                }

                                override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit

                                override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                                    surface?.let { engine.detachSurface(it); it.release() }
                                    surface = null
                                    return true
                                }

                                override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                            }
                        }
                    },
                    update = { it.keepScreenOn = keepAwake },
                )
            } else {
                AndroidView(
                    modifier = viewModifier,
                    factory = { ctx ->
                        SurfaceView(ctx).apply {
                            holder.addCallback(object : SurfaceHolder.Callback {
                                override fun surfaceCreated(holder: SurfaceHolder) = engine.setSurface(holder.surface)
                                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
                                override fun surfaceDestroyed(holder: SurfaceHolder) = engine.detachSurface(holder.surface)
                            })
                        }
                    },
                    update = { it.keepScreenOn = keepAwake },
                )
            }
        }
        // Subtitle overlay — mounted ONLY while subs are on, so 4K live keeps its direct hardware-overlay path.
        // Sized like the video, not like the screen: at any zoom other than Fit the two differ, and
        // mounting this full-screen put the same subtitle in a different place depending on where the
        // surface was composed. The picture is the reference, so a line stays inside the frame the user
        // is actually looking at.
        val subOn by engine.subtitleOn.collectAsStateWithLifecycle()
        val cues by engine.cues.collectAsStateWithLifecycle()
        if (subOn) {
            StyledSubtitleView(cues = cues, modifier = viewModifier)
        }
    }
}

/**
 * Media3's [androidx.media3.ui.SubtitleView] with the user's subtitle appearance applied —
 * the one place live subtitles draw, so they always look the same.
 *
 * Every option is independent, and each one that's left on "Default" (or the whole thing while the
 * master toggle is off) leaves this the plain, unstyled view it has always been. In particular
 * `applyEmbeddedStyles` is only switched off once a color or a background transparency is actually
 * chosen: that discards broadcaster CEA-608/teletext styling, which is the only way an override can
 * take effect — and exactly what is wanted, since the opaque black box comes from the broadcaster.
 * Whichever of the two is still on "Default" falls back to [androidx.media3.ui.CaptionStyleCompat]'s
 * own value rather than to the (now discarded) embedded one.
 *
 * **Position** re-anchors each cue. `bottomPaddingFraction` can't do it: it only moves cues without
 * an explicit `line`, which is precisely what embedded live captions *do* carry. Bitmap cues
 * (PGS/VOBSUB/DVB) are passed through untouched — they're pre-rendered images whose placement is
 * part of the picture.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun StyledSubtitleView(cues: List<androidx.media3.common.text.Cue>, modifier: Modifier = Modifier) {
    val settings = org.koin.compose.koinInject<SettingsRepository>()
    val styleOn by settings.subtitleStyleEnabled.collectAsStateWithLifecycle(initialValue = false)
    val scale by settings.subtitleScaleExo.collectAsStateWithLifecycle(initialValue = SubtitleStyle.SCALE_DEFAULT)
    val font by settings.subtitleFont.collectAsStateWithLifecycle(initialValue = null)
    val colorHex by settings.subtitleColor.collectAsStateWithLifecycle(initialValue = SubtitleStyle.COLOR_DEFAULT)
    val position by settings.subtitlePosition.collectAsStateWithLifecycle(initialValue = SubtitleStyle.Position.DEFAULT)
    val bgOpacity by settings.subtitleBgOpacity.collectAsStateWithLifecycle(initialValue = SubtitleStyle.OPACITY_DEFAULT)

    val customColor = styleOn && SubtitleStyle.hasColor(colorHex)
    val customBackground = styleOn && SubtitleStyle.hasOpacity(bgOpacity)
    val customFont = styleOn && font != null
    val customPosition = if (styleOn) position else SubtitleStyle.Position.DEFAULT
    val textScale = if (styleOn) scale else SubtitleStyle.SCALE_DEFAULT

    AndroidView(
        modifier = modifier,
        factory = { ctx -> androidx.media3.ui.SubtitleView(ctx) },
        update = { view ->
            val stock = androidx.media3.ui.CaptionStyleCompat.DEFAULT
            if (customColor || customBackground || customFont) {
                view.setApplyEmbeddedStyles(false)
                view.setStyle(
                    androidx.media3.ui.CaptionStyleCompat(
                        if (customColor) SubtitleStyle.colorArgb(colorHex) else stock.foregroundColor,
                        if (customBackground) SubtitleStyle.backgroundArgb(bgOpacity) else stock.backgroundColor,
                        android.graphics.Color.TRANSPARENT,
                        androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                        android.graphics.Color.BLACK,
                        if (customFont) font?.asAndroidTypeface(view.context) else null,
                    ),
                )
            } else {
                view.setApplyEmbeddedStyles(true)
                view.setStyle(stock)
            }
            view.setFractionalTextSize(androidx.media3.ui.SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * textScale)
            view.setCues(
                if (customPosition == SubtitleStyle.Position.DEFAULT) cues else cues.map { anchor(it, customPosition) },
            )
        },
    )
}

/** Re-anchor a text cue to one of the six fixed screen positions; bitmap cues pass through as-is. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun anchor(cue: androidx.media3.common.text.Cue, position: SubtitleStyle.Position): androidx.media3.common.text.Cue {
    if (cue.bitmap != null) return cue
    return cue.buildUpon()
        .setLine(SubtitleStyle.lineFraction(position), androidx.media3.common.text.Cue.LINE_TYPE_FRACTION)
        .setLineAnchor(
            if (position.isTop) androidx.media3.common.text.Cue.ANCHOR_TYPE_START
            else androidx.media3.common.text.Cue.ANCHOR_TYPE_END,
        )
        .setPosition(SubtitleStyle.positionFraction(position))
        .setPositionAnchor(
            when {
                position.isLeft -> androidx.media3.common.text.Cue.ANCHOR_TYPE_START
                position.isRight -> androidx.media3.common.text.Cue.ANCHOR_TYPE_END
                else -> androidx.media3.common.text.Cue.ANCHOR_TYPE_MIDDLE
            },
        )
        // Let the box wrap its text: a cue carrying an explicit width would otherwise still span the
        // screen, and left/right would look identical to center.
        .setSize(androidx.media3.common.text.Cue.DIMEN_UNSET)
        .setTextAlignment(
            when {
                position.isLeft -> android.text.Layout.Alignment.ALIGN_NORMAL
                position.isRight -> android.text.Layout.Alignment.ALIGN_OPPOSITE
                else -> android.text.Layout.Alignment.ALIGN_CENTER
            },
        )
        .build()
}
