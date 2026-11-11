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

import tv.own.myowntv.ui.theme.hudOnAccent
import tv.own.myowntv.ui.theme.hudAccent
import tv.own.myowntv.ui.theme.hudShade
import tv.own.myowntv.ui.theme.hudInk
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import tv.own.myowntv.R
import tv.own.myowntv.ui.stage.StageFocus
import tv.own.myowntv.ui.stage.StageSurface
import tv.own.myowntv.ui.components.MyOwnTVIcon
import tv.own.myowntv.ui.theme.animationsOn
import tv.own.myowntv.ui.theme.ownTvTween

/**
 * Shared HUD primitives: the button vocabulary. Split out of [PlayerHud] — behaviour unchanged;
 * the declarations the other HUD files reach for are `internal` rather than file-private for that
 * reason alone.
 */

// ---------------- Buttons ----------------

/** Focus on the player's chrome: the accent that reads over video, ringed in [ring]. */
@Composable
internal fun hudFocus(ring: Color) = tv.own.myowntv.ui.theme.StageAccent(hudAccent, hudOnAccent, ring)

@Composable
internal fun CircleButton(icon: MyOwnTVIcon, size: Int, primary: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    // Focus fills the button with the accent and rings it in a white hairline. The glyph takes
    // onAccentOnVideo rather than a fixed dark, so a deep custom accent still gets a readable icon —
    // that role is already derived from the seed's luminance, which is the check this needs.
    StageSurface(
        onClick = onClick,
        radius = (size / 2f).dp,
        modifier = modifier.size(size.dp),
        focusStyle = StageFocus.FILLED,
        idle = Modifier.background(if (primary) hudInk.copy(alpha = 0.95f) else hudInk.copy(alpha = 0.10f), CircleShape),
        accent = hudFocus(ring = hudInk),
        contentAlignment = Alignment.Center,
    ) { focused ->
        MyOwnTVIcon(
            icon,
            tint = when {
                focused -> hudOnAccent
                primary -> tv.own.myowntv.ui.theme.stageTone(Color(0xFF0E1513), Color.White)
                else -> hudInk
            },
            filled = true,
            modifier = Modifier.size((size * 0.42f).dp),
        )
    }
}

/** The transport buttons live in one frosted capsule instead of floating loose over the video. */
@Composable
internal fun TransportCapsule(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(44.dp))
            .background(hudShade(0.55f))
            .border(1.dp, hudInk.copy(alpha = 0.10f), RoundedCornerShape(44.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .focusGroup(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/**
 * The dock: one container holding band A (the instrument) over band B (the tools). Same fill and rim
 * as [TransportCapsule] at the dock's own 22 dp radius, so the two read as one material.
 */
@Composable
internal fun Dock(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    // No clip: the fill and rim already take the shape.
    Column(
        modifier
            .background(hudShade(0.55f), RoundedCornerShape(22.dp))
            .border(1.dp, hudInk.copy(alpha = 0.10f), RoundedCornerShape(22.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp),
        content = content,
    )
}

/**
 * The live state: a red badge with a pulsing dot at the live edge. It sits at the right end of the
 * dock's band A — live status belongs beside the picture it describes. There is no "behind" state
 * any more: live is the only thing the app plays, and there is nothing to rewind.
 */
@Composable
internal fun LiveStateBadge() {
    // NEVER hand ownTvTween() to infiniteRepeatable. With Animations = Off it is a 0 ms tween, and
    // Compose's VectorizedInfiniteRepeatableSpec divides the play time by the iteration duration to
    // find the current repeat — 0 ms means a divide-by-zero on the main thread on the very next frame.
    // That shipped in 4.2.3 and crashed full-screen Live TV for every user with animations turned off.
    // Reduce animations is honoured by skipping the transition entirely instead.
    val pulse = if (!animationsOn) {
        1f
    } else {
        val transition = rememberInfiniteTransition(label = "liveDot")
        transition.animateFloat(
            initialValue = 1f, targetValue = 0.25f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "liveDotAlpha",
        ).value
    }
    Row(
        Modifier.clip(RoundedCornerShape(7.dp)).background(Color(0xFFDC3232).copy(alpha = 0.85f)).padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        MyOwnTVIcon(
            MyOwnTVIcon.LIVE_DOT,
            tint = Color.White.copy(alpha = pulse),
            filled = true,
            modifier = Modifier.size(10.dp),
        )
        Text(
            stringResource(R.string.player_live),
            style = tv.own.myowntv.ui.theme.stageText(20, 500).copy(textDirection = TextDirection.Content),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The label a focused tool grows to show, and the value that follows it (a track count, a zoom
 *  name). Growth is horizontal and in place: because each cluster is anchored to its own
 *  screen edge, the button can only push its unvisited neighbours toward the centre gap, never the
 *  ones the user has already walked past. Capped and ellipsised as a last-resort backstop — every
 *  translation is written to the 14-character ceiling, so this should never actually bite. */
@Composable
private fun ExpandingLabel(visible: Boolean, label: String, value: String?, tint: Color) {
    AnimatedVisibility(
        visible = visible,
        enter = expandHorizontally(ownTvTween(140)) + fadeIn(ownTvTween(140)),
        exit = shrinkHorizontally(ownTvTween(140)) + fadeOut(ownTvTween(140)),
    ) {
        Row(
            Modifier.padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                label,
                style = tv.own.myowntv.ui.theme.stageText(25, 600),
                color = tint,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = TOOL_LABEL_MAX_DP.dp),
            )
            // The count that overlapped the icon's corner while collapsed reads as plain text here.
            if (value != null) {
                Text(value, style = tv.own.myowntv.ui.theme.stageText(25, 600), color = tint.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Backstop width for a label written to the 14-character ceiling. */
private const val TOOL_LABEL_MAX_DP = 132

@Composable
internal fun CtrlButton(
    icon: MyOwnTVIcon,
    badge: Int? = null,
    active: Boolean = false,
    // The favourite heart stays coral here instead of taking the accent, so one colour means
    // "favourite" everywhere in the app — browse rows, posters and the player alike.
    activeTint: Color = hudAccent,
    label: String,
    // Shown after the label while focused, in place of the count: the current setting a press changes.
    value: String? = null,
    // OK held. Null = a hold is a press.
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    StageSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        radius = 12.dp,
        // A square 44 dp while collapsed; focus lets it grow sideways to fit its label.
        modifier = Modifier.height(44.dp).widthIn(min = 44.dp),
        // Focus as light: the accent rim, a wash of it and a soft glow, all from the accent that reads over video.
        focusStyle = StageFocus.FX,
        accent = hudFocus(ring = hudAccent),
        contentAlignment = Alignment.Center,
    ) { focused ->
        val pad by animateDpAsState(if (focused) 12.dp else 0.dp, ownTvTween(140), label = "toolPadding")
        val tint = if (active) activeTint else if (focused) hudInk else hudInk.copy(alpha = 0.78f)
        Row(Modifier.padding(horizontal = pad), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                MyOwnTVIcon(icon, tint = tint, filled = true, modifier = Modifier.size(22.dp))
                // Collapsed, the count sits on the icon's corner; expanded it moves inline after the
                // label instead, where it no longer covers a third of the glyph.
                if (badge != null && !focused) {
                    Box(
                        Modifier.align(Alignment.TopEnd).size(15.dp).clip(CircleShape).background(hudAccent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(stringResource(R.string.common_number_grouped, badge), style = tv.own.myowntv.ui.theme.stageText(20, 500), color = hudOnAccent, fontWeight = FontWeight.Bold)
                    }
                }
            }
            ExpandingLabel(focused, label, value ?: badge?.let { stringResource(R.string.common_number_grouped, it) }, tint)
        }
    }
}

