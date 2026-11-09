package tv.own.myowntv.features.shell.components
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
import tv.own.owntv.player.ffmpeg.*

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import tv.own.myowntv.ui.theme.LocalGlass
import tv.own.myowntv.ui.theme.drawRadialGlow

/**
 * Setup-wizard-style radiance for the solid shell. It is a single lightweight Canvas overlay: no
 * blur or per-card work; the glow itself is one small cached gradient image. Glass mode already gets
 * depth from its wallpaper/frost and skips this layer entirely.
 */
@Composable
fun SolidAmbientBackdrop(
    glowEnabled: Boolean,
    pulseEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val glass = LocalGlass.current
    if (!glowEnabled || glass.enabled || tv.own.myowntv.ui.theme.LocalStagePalette.current.isLight) return

    val primary = tv.own.myowntv.ui.theme.stageAccent.accent
    val transition = if (pulseEnabled) rememberInfiniteTransition(label = "solidAmbientPulse") else null
    val ringScale = transition?.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6_000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "solidAmbientRingScale",
    )
    val ringPresence = transition?.animateFloat(
        initialValue = 0.38f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6_000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "solidAmbientRingPresence",
    )
    val scale by ringScale ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    val presence by ringPresence ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(1f) }

    Canvas(modifier = modifier) {
        val center = Offset(size.width * 0.54f, size.height * 0.45f)
        val glowRadius = size.minDimension * 0.46f
        // A cached gradient image, not a brush: a screen-sized gradient is the costliest draw on
        // low-end TV GPUs, and the pulse below redraws this every frame (see GradientTextures.kt).
        drawRadialGlow(
            colors = listOf(
                primary.copy(alpha = 0.14f),
                primary.copy(alpha = 0.052f),
                Color.Transparent,
            ),
            center = center,
            radius = glowRadius,
        )
        if (pulseEnabled) {
            drawCircle(
                color = primary.copy(alpha = 0.20f * presence),
                radius = size.minDimension * 0.34f * scale,
                center = center,
                style = Stroke(width = 1.dp.toPx()),
            )
        }
    }
}
