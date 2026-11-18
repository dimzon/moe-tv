package tv.own.myowntv.features.shell.components
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
import tv.own.owntv.core.player.*
import tv.own.owntv.core.reminder.*
import tv.own.owntv.core.repository.*
import tv.own.owntv.core.settings.*
import tv.own.owntv.core.setup.*
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

import androidx.compose.ui.draw.clipToBounds
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import tv.own.myowntv.R
import tv.own.owntv.core.nav.MainSection
import tv.own.owntv.core.settings.SettingsRepository.NavLength
import tv.own.owntv.core.settings.SettingsRepository.NavSize
import tv.own.myowntv.ui.components.BrandMark
import tv.own.myowntv.ui.components.MyOwnTVIcon
import tv.own.myowntv.ui.components.Wordmark
import tv.own.myowntv.ui.components.rememberAppliedIcon
import tv.own.myowntv.ui.stage.StageFocus
import tv.own.myowntv.ui.stage.StageSurface
import tv.own.myowntv.ui.stage.drawBoxShadow
import tv.own.myowntv.ui.stage.stageGlass
import tv.own.myowntv.ui.theme.StageColors
import tv.own.myowntv.ui.theme.mpx
import tv.own.myowntv.ui.theme.ownTvTween
import tv.own.myowntv.ui.theme.stageAccent
import tv.own.myowntv.ui.theme.stageText
import java.text.NumberFormat

/** What the Stage rail shows right now: the Navigation setting plus where focus is (see MyOwnTVShell). */
enum class RailState {
    /** A 7 px accent glow at the screen edge; the items stay focusable so ◀ can still reach them. */
    HIDDEN,
    /** The 84 px icon capsule: Floating at rest, or Size = Compact. */
    CAPSULE,
    /** The chosen Size over a scrim: the floating rail while it holds focus. */
    OPEN,
    /** The chosen Size without the scrim: Navigation = Docked. */
    PINNED,
}

/**
 * What the rail's "Now Playing" item shows: the docked channel's logo, or the equalizer while Audio
 * Mode is running (there is no picture to stand for then).
 */
data class NowPlayingRail(val logoUrl: String?, val audioMode: Boolean, val playing: Boolean)

/** The rail order: Search first, the Guide right after Live TV, More last. */
private val StageRailOrder = listOf(
    MainSection.SEARCH, MainSection.HOME, MainSection.LIVE_TV, MainSection.EPG,
)

/** The rail's width with names: Normal 280 · Wide 340 · Extra wide 400; Compact is the 84 capsule. */
private val NavSize.openWidth get() = when (this) {
    NavSize.COMPACT -> 84
    NavSize.NORMAL -> 280
    NavSize.WIDE -> 340
    NavSize.EXTRA_WIDE -> 400
}

/** The space a docked rail takes from the content, from the screen edge: 28 + 84 capsule, 24 + width with names. */
fun railDockedReserve(size: NavSize) =
    if (size == NavSize.COMPACT) (28 + 84 + 16).mpx else (24 + size.openWidth + 16).mpx

/**
 * Where content starts below the top-right cluster (34 + its 68 height + a gap) on screens that have not
 * been redrawn for Stage yet. Each screen's own phase takes this away as it gets the full canvas.
 */
val StageContentTop = 110.mpx

/**
 * The Stage navigation rail: a floating glass capsule, the rail at its
 * [size] over a scrim or docked, or only the accent glow at the edge. [count] is the number beside an
 * item (Wide and up) and [detail] the line under its name (Extra wide), null for none. [length] Full
 * runs the rail the height of the screen. Entering the rail from the content always lands on the
 * selected section, and ▶ out of it returns to exactly where the content was ([contentFocusRequester]).
 */
@Composable
fun StageRail(
    state: RailState,
    size: NavSize,
    length: NavLength,
    selected: MainSection,
    visibleSections: Set<MainSection>,
    onSelect: (MainSection) -> Unit,
    count: (MainSection) -> Int?,
    detail: @Composable (MainSection) -> String?,
    selectedItemFocusRequester: FocusRequester,
    contentFocusRequester: FocusRequester,
    /** A screen's own way in, when it has one (Home returns to the exact control left from). */
    enterContent: (() -> Boolean)? = null,
    onFocused: () -> Unit,
    nowPlaying: NowPlayingRail?,
    onNowPlaying: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val open = (state == RailState.OPEN || state == RailState.PINNED) && size != NavSize.COMPACT
    val counts = size >= NavSize.WIDE
    val details = size == NavSize.EXTRA_WIDE
    val full = length == NavLength.FULL
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val scope = rememberCoroutineScope()
    var hasFocus by remember { mutableStateOf(false) }
    // Search, Settings and More all land on a rail item: Search has its own, Settings lives behind More.
    val focusSection = when {
        selected == MainSection.SETTINGS || selected == MainSection.MORE -> MainSection.MORE
        selected == MainSection.SEARCH || selected in visibleSections -> selected
        else -> StageRailOrder.firstOrNull { it in visibleSections } ?: MainSection.MORE
    }
    val items = StageRailOrder.filter { it == MainSection.SEARCH || it in visibleSections } + MainSection.MORE

    // Slide in from the edge (320 ms, or a snap with animations off) when the rail leaves HIDDEN. A rail
    // that opens from the capsule already on screen widens in place instead: sliding it in from off-screen
    // made the capsule vanish and come back, a visible double take.
    val slide = remember { Animatable(0f) }
    val slideSpec: androidx.compose.animation.core.AnimationSpec<Float> = ownTvTween(320)
    val capsuleWidth = 84.mpx
    val openWidth = size.openWidth.mpx
    // The width the names are laid out at. A compact rail that widens on focus is handed Compact again the
    // moment it starts to close, and its rows measured at 84 squeezed the icons for the whole narrowing; the
    // last width with names stays until the rail is back in its capsule.
    var namesWidth by remember { mutableStateOf(maxOf(openWidth, NavSize.NORMAL.openWidth.mpx)) }
    if (openWidth > capsuleWidth) namesWidth = openWidth
    val hidden = state == RailState.HIDDEN
    val railWidth = remember { Animatable(if (open) openWidth else capsuleWidth, Dp.VectorConverter) }
    val widthSpec: androidx.compose.animation.core.AnimationSpec<Dp> = ownTvTween(320)
    var wasHidden by remember { mutableStateOf(hidden) }
    LaunchedEffect(open, hidden, openWidth) {
        val target = if (open) openWidth else capsuleWidth
        // In or out of HIDDEN the rail only slides, at its final width, exactly as before: growing while
        // it slides in would be two motions at once.
        if (hidden || wasHidden) railWidth.snapTo(target) else launch { railWidth.animateTo(target, widthSpec) }
        wasHidden = hidden
        slide.animateTo(if (hidden) -1f else 0f, slideSpec)
    }
    // Names stay laid out until the width has closed back to the capsule, so collapsing mirrors opening
    // instead of re-centring the icons in a rail that is still wide. Derived, so the rail recomposes when
    // this flips, not on every frame of the widening.
    val shown by remember(open, capsuleWidth) { derivedStateOf { open || railWidth.value > capsuleWidth } }
    // Names fade in over the second half of the widening (and out over the first half of the narrowing),
    // so a rail too narrow for them never shows them squeezed down to an ellipsis. A lambda, read in
    // each label's draw layer only.
    val namesAlpha: () -> Float = {
        ((railWidth.value - capsuleWidth) / (namesWidth - capsuleWidth).coerceAtLeast(1.mpx) * 2f - 1f).coerceIn(0f, 1f)
    }

    Box(modifier.fillMaxSize()) {
        if (state == RailState.OPEN && open) {
            Box(Modifier.fillMaxSize().background(StageColors.Scrim))
        }
        if (state == RailState.HIDDEN) RailEdgeGlow(Modifier.align(Alignment.CenterStart))

        val edge = if (shown) 24.mpx else 28.mpx
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = edge)
                .then(if (full) Modifier.padding(vertical = 24.mpx).fillMaxHeight() else Modifier)
                // The animated width is read here, in layout, so each frame of the widening re-lays the
                // rail out without recomposing it.
                .layout { measurable, constraints ->
                    val w = railWidth.value.roundToPx().coerceIn(constraints.minWidth, constraints.maxWidth)
                    val placeable = measurable.measure(constraints.copy(minWidth = w, maxWidth = w))
                    layout(w, placeable.height) { placeable.place(0, 0) }
                }
                .graphicsLayer {
                    val travel = (railWidth.value + edge).toPx()
                    translationX = slide.value * travel * (if (rtl) -1f else 1f)
                }
                // Fades as it slides out, so its shadow never lingers at the edge once hidden.
                .alpha(if (state == RailState.HIDDEN) (1f + slide.value).coerceIn(0f, 1f) else 1f)
                .onFocusChanged {
                    // Every entry from the content lands on the selected section, not on whatever item
                    // happens to line up; moves inside the rail do not re-trigger this.
                    val entered = it.hasFocus && !hasFocus
                    hasFocus = it.hasFocus
                    if (it.hasFocus) onFocused()
                    if (entered) scope.launch { runCatching { selectedItemFocusRequester.requestFocus() } }
                }
                .focusProperties {
                    onExit = {
                        val out = if (rtl) FocusDirection.Left else FocusDirection.Right
                        if (requestedFocusDirection == out && enterContent != null) {
                            // Focus cannot be moved from inside a focus change: stop this one, and let
                            // the screen place focus itself on the next frame.
                            cancelFocusChange()
                            scope.launch {
                                withFrameNanos { }
                                if (!enterContent()) runCatching { contentFocusRequester.requestFocus() }
                            }
                        } else if (requestedFocusDirection == out && runCatching { contentFocusRequester.requestFocus() }.getOrDefault(false)) {
                            cancelFocusChange()
                        }
                    }
                }
                // ▶ always leaves for the content. The open rail can cover the content's left
                // column (More's sheet), and then the D-pad search finds nothing to its right and focus
                // simply stayed in the rail.
                .onKeyEvent { e ->
                    val out = if (rtl) Key.DirectionLeft else Key.DirectionRight
                    if (e.key != out) return@onKeyEvent false
                    if (e.type == KeyEventType.KeyDown) scope.launch {
                        if (enterContent?.invoke() != true) runCatching { contentFocusRequester.requestFocus() }
                    }
                    true
                }
                .focusGroup()
                .stageGlass(if (shown) 34.mpx else 42.mpx, overContent = true)
                // The open rail keeps the capsule's vertical sizes (16 padding, 58 rows, the same gaps), so
                // opening and closing only change the width: the rail never grows or shrinks in height.
                .padding(horizontal = if (shown) 16.mpx else 0.mpx, vertical = 16.mpx)
                // While it widens, the rows are laid out at the open rail's width and clipped to the
                // current one, so the icons are never squeezed in a rail still too narrow for them.
                .clipToBounds()
                .layout { measurable, constraints ->
                    val innerWidth = if (shown) (namesWidth - 32.mpx).roundToPx().coerceAtLeast(0) else null
                    val placeable = measurable.measure(
                        constraints.copy(minWidth = innerWidth ?: constraints.minWidth, maxWidth = innerWidth ?: constraints.maxWidth),
                    )
                    layout(constraints.maxWidth, placeable.height) { placeable.place(0, 0) }
                },
            horizontalAlignment = if (shown) Alignment.Start else Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.mpx),
        ) {
            RailBrand(shown, namesAlpha)
            if (nowPlaying != null) {
                RailItem(
                    icon = if (nowPlaying.audioMode) MyOwnTVIcon.EQ else MyOwnTVIcon.PLAY,
                    label = stringResource(R.string.shell_now_playing),
                    trailing = if (nowPlaying.audioMode && counts) stringResource(R.string.shell_now_playing_audio) else null,
                    detail = null,
                    open = shown,
                    namesAlpha = namesAlpha,
                    active = false,
                    accentIcon = true,
                    onClick = onNowPlaying,
                )
                RailSeparator(shown)
            }
            items.forEach { section ->
                RailItem(
                    icon = section.stageIcon,
                    label = stringResource(section.labelRes),
                    trailing = if (counts) count(section)?.let { NumberFormat.getIntegerInstance(LocalConfiguration.current.firstLocaleCompat()).format(it) } else null,
                    detail = if (details) detail(section) else null,
                    open = shown,
                    namesAlpha = namesAlpha,
                    active = section == selected ||
                        (section == MainSection.MORE && selected == MainSection.SETTINGS),
                    onClick = { onSelect(section) },
                    modifier = if (section == focusSection) Modifier.focusRequester(selectedItemFocusRequester) else Modifier,
                )
            }
        }
    }
}

private val MainSection.stageIcon: MyOwnTVIcon
    get() = when (this) {
        MainSection.SEARCH -> MyOwnTVIcon.SEARCH
        MainSection.HOME -> MyOwnTVIcon.HOME
        MainSection.LIVE_TV -> MyOwnTVIcon.LIVE_TV
        MainSection.EPG -> MyOwnTVIcon.EPG
        MainSection.MORE, MainSection.SETTINGS -> MyOwnTVIcon.TILES
    }

/** `.edge`: 7 × 150 at the screen edge, accent, glowing 22 px at 80% and 60 px at 35%, 90% opaque. */
@Composable
private fun RailEdgeGlow(modifier: Modifier) {
    val accent = stageAccent.accent
    Box(
        modifier
            .size(7.mpx, 150.mpx)
            .alpha(0.9f)
            .drawBehind {
                val r = 7.mpx.toPx()
                drawBoxShadow(accent.copy(alpha = 0.35f), 60.mpx.toPx(), r)
                drawBoxShadow(accent.copy(alpha = 0.8f), 22.mpx.toPx(), r)
                // border-radius: 0 7 7 0 — square against the screen edge, round on the inside.
                drawRoundRect(accent, cornerRadius = CornerRadius(r))
                drawRect(accent, size = size.copy(width = size.width / 2f))
            },
    )
}

/** The mark (50), with the wordmark beside it when the rail is open (`.brandrow`, 0 10 18 padding). */
@Composable
private fun ColumnScope.RailBrand(open: Boolean, namesAlpha: () -> Float) {
    val icon = rememberAppliedIcon()
    if (!open) {
        BrandMark(icon, 50.mpx, Modifier.padding(bottom = 12.mpx))
        return
    }
    Row(
        Modifier.padding(start = 10.mpx, end = 10.mpx, bottom = 12.mpx),
        horizontalArrangement = Arrangement.spacedBy(14.mpx),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandMark(icon, 50.mpx)
        // The wordmark, 130 wide (`.brandrow`, `wordmarkC()`).
        Box(Modifier.graphicsLayer { alpha = namesAlpha() }) { Wordmark(130.mpx) }
    }
}

@Composable
private fun RailSeparator(open: Boolean) {
    Box(
        Modifier
            .then(if (open) Modifier.fillMaxWidth().padding(horizontal = 14.mpx, vertical = 8.mpx) else Modifier.padding(vertical = 8.mpx).width(34.mpx))
            .height(1.mpx)
            .background(StageColors.Ink.copy(alpha = 0.12f)),
    )
}

/**
 * `.frail .it`: a 58 × 58 icon tile in the capsule, a 60 high row with its label, [detail] line under it
 * and count when open.
 * Active = accent icon on accent 14%, plus the glowing dot outside the capsule; focused = FILLED.
 */
@Composable
private fun RailItem(
    icon: MyOwnTVIcon,
    label: String,
    trailing: String?,
    detail: String?,
    open: Boolean,
    namesAlpha: () -> Float,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentIcon: Boolean = false,
) {
    val a = stageAccent
    val r = 20.mpx
    StageSurface(
        onClick = onClick,
        radius = r,
        // At least 60, not exactly: with a large font size the name and its detail line need more, and a
        // fixed height cut the detail line off (#7.2).
        modifier = modifier.then(if (open) Modifier.fillMaxWidth().heightIn(min = 58.mpx) else Modifier.size(58.mpx)),
        idle = if (active) Modifier.background(a.accent.copy(alpha = 0.14f), RoundedCornerShape(r)) else Modifier,
        contentAlignment = if (open) Alignment.CenterStart else Alignment.Center,
    ) { focused ->
        val iconTint = when {
            focused -> a.onAccent
            active || accentIcon -> a.accent
            else -> StageColors.NavText
        }
        if (!open) {
            MyOwnTVIcon(icon, iconTint, Modifier.size(27.mpx))
            if (active && !focused) RailActiveDot(Modifier.align(Alignment.CenterEnd).offset(x = 9.mpx))
            return@StageSurface
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.mpx, vertical = 6.mpx),
            horizontalArrangement = Arrangement.spacedBy(18.mpx),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MyOwnTVIcon(icon, iconTint, Modifier.size(26.mpx))
            Column(Modifier.weight(1f).graphicsLayer { alpha = namesAlpha() }) {
                Text(
                    label,
                    style = stageText(21, 600),
                    color = if (focused) a.onAccent else if (active) a.accent else StageColors.Text,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (detail != null) {
                    // `.dt`: 14/500 dim, 2 px under the name; on the focused (filled) row on-accent at 80%.
                    Text(
                        detail,
                        style = stageText(14, 500),
                        color = if (focused) a.onAccent.copy(alpha = 0.8f) else StageColors.Dim,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.mpx),
                    )
                }
            }
            if (trailing != null) {
                Text(trailing, style = stageText(16, 600), color = if (focused) a.onAccent else StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.graphicsLayer { alpha = namesAlpha() })
            }
        }
    }
}

/** `.it.on::after`: 6 px, 9 px outside the capsule's item, glowing 10 px. */
@Composable
private fun RailActiveDot(modifier: Modifier) {
    val accent = stageAccent.accent
    Box(
        modifier
            .size(6.mpx)
            .drawBehind {
                drawBoxShadow(accent, 10.mpx.toPx(), size.minDimension / 2f)
                drawCircle(accent)
            },
    )
}


