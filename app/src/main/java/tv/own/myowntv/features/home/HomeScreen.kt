package tv.own.myowntv.features.home
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

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.own.myowntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.model.HomeLiveRowMode
import tv.own.owntv.core.model.HomeRow
import tv.own.myowntv.ui.components.BrandLockup
import tv.own.myowntv.ui.components.MyOwnTVIcon
import tv.own.myowntv.ui.stage.StageStill
import tv.own.myowntv.ui.theme.StageColors
import tv.own.myowntv.ui.theme.mpx

/*
 * Home, Stage (hero, rows). Live TV only: Keep watching shows the last-watched channels, and under it
 * the live rails — Recent and Favourite channels. The focused card is the same composable
 * wherever the list sits.
 */

/** The row gap; the headings' design positions below already include it. */
private val RowGap = 40.mpx

/** Where the rows start when there is no hero: just below the top-right cluster. */
private val NoHeroTop = 110.mpx

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onPlayChannel: (channelId: String, zapChannels: List<ChannelEntity>) -> Unit,
    onChildFocused: () -> Unit,
    restoreFocus: Boolean = false,
    onRestored: () -> Unit = {},
    previewEnabled: Boolean = true,
    firstRowFocusRequester: FocusRequester? = null,
    /** The content's left edge: beside the resting capsule (150), or 64 inside a docked rail's reserve. */
    contentStart: Dp = 150.mpx,
    /**
     * Hands the shell Home's own way in, for ▶ out of the rail: the control that last had focus, or Play.
     * (A plain request on the content area drills to the nearest card instead.) Null on leaving.
     */
    onEntryHook: (((() -> Boolean)?) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val heroPreviewEngine = koinInject<HeroPreviewEngine>()
    val engineState by heroPreviewEngine.state.collectAsStateWithLifecycle()
    val isPreviewActive by vm.isPreviewActive.collectAsStateWithLifecycle()
    val lastInteractionMs by vm.lastHeroInteractionMs.collectAsStateWithLifecycle()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val homeScope = rememberCoroutineScope()
    val heroFocus = remember { FocusRequester() }
    val fallbackFocus = remember { FocusRequester() }
    val rowFirstFocusRequesters = remember { HomeRow.entries.associateWith { FocusRequester() } }
    // A Keep watching card plays its preview only after 3 s of continuous focus (the dwell below).
    var previewIndex by remember { mutableStateOf(-1) }
    var focusedHeroIndex by remember { mutableStateOf(-1) }
    val orderedRows = state.config.visibleOrder
    val heroVisible = HomeRow.HERO in orderedRows
    val hasNonHeroContent = orderedRows.any { it != HomeRow.HERO && rowHasData(it, state) }
    val showHeroFallback = heroVisible && state.heroCards.isEmpty() && !hasNonHeroContent
    val renderRows = orderedRows.filter { rowCanRender(it, state, showHeroFallback) }
    val firstDataRow = renderRows.firstOrNull { it != HomeRow.HERO && rowHasData(it, state) }
    val showAllHiddenState = orderedRows.isEmpty()
    val showEmptyState = orderedRows.isNotEmpty() && renderRows.isEmpty()
    val rowFocusRequester: (HomeRow) -> FocusRequester? = { row ->
        if (row == renderRows.firstOrNull() && firstRowFocusRequester != null) {
            firstRowFocusRequester
        } else when (row) {
            HomeRow.HERO -> when {
                state.heroCards.isNotEmpty() -> heroFocus
                showHeroFallback -> fallbackFocus
                else -> null
            }
            else -> rowFirstFocusRequesters[row]
        }
    }
    // The control that last had focus on Home, so ▶ out of the rail comes back to exactly it (rail
    // carry-over). Compose's own restorer cannot follow a card inside a lazy row inside a lazy column.
    var lastFocus by remember { mutableStateOf<FocusRequester?>(null) }
    val track: (FocusRequester) -> Unit = { lastFocus = it }
    val latestEntry by androidx.compose.runtime.rememberUpdatedState(
        lastFocus ?: renderRows.firstOrNull()?.let(rowFocusRequester),
    )
    DisposableEffect(onEntryHook) {
        onEntryHook?.invoke { latestEntry?.let { runCatching { it.requestFocus() }.getOrDefault(false) } ?: false }
        onDispose { onEntryHook?.invoke(null) }
    }

    val onNonHeroFocused = remember(vm, heroPreviewEngine) {
        {
            vm.setHeroFocused(false)
            heroPreviewEngine.stop()
            previewIndex = -1
            onChildFocused()
        }
    }

    LaunchedEffect(focusedHeroIndex) {
        if (focusedHeroIndex != -1) return@LaunchedEffect
        // Focus moves between cards very quickly (old loses focus before new gains). Debounce the
        // "left the row" signal so the preview state does not flap while navigating within the row.
        kotlinx.coroutines.delay(40L)
        if (focusedHeroIndex != -1) return@LaunchedEffect
        vm.setHeroFocused(false)
        heroPreviewEngine.stop()
        previewIndex = -1
    }

    // Dwell-to-preview: only after 3 s of uninterrupted focus, so quick D-pad sweeps never start a video.
    LaunchedEffect(focusedHeroIndex) {
        val index = focusedHeroIndex
        if (index < 0) return@LaunchedEffect
        kotlinx.coroutines.delay(3_000L)
        if (focusedHeroIndex == index) previewIndex = index
    }

    LaunchedEffect(previewEnabled) {
        vm.setPreviewEnabled(previewEnabled)
        if (!previewEnabled) vm.stopPreview()
    }

    // The engine is an app-scoped singleton; make sure the preview can't outlive the Home screen.
    DisposableEffect(heroPreviewEngine) {
        onDispose { heroPreviewEngine.stop() }
    }

    LaunchedEffect(orderedRows, state.heroCards, state.recentLive, state.favoriteLive, state.recentGuide, state.favoriteGuide, restoreFocus) {
        if (orderedRows.isEmpty()) {
            if (restoreFocus) onRestored()
            return@LaunchedEffect
        }

        val targetRow = when {
            restoreFocus && heroVisible && state.heroCards.isNotEmpty() -> HomeRow.HERO
            restoreFocus && showHeroFallback -> HomeRow.HERO
            restoreFocus -> firstDataRow
            else -> null
        }
        val targetIndex = targetRow?.let { renderRows.indexOf(it) } ?: 0
        runCatching { listState.scrollToItem(targetIndex.coerceAtLeast(0)) }

        // Only pull focus INTO the Home content when returning from the player (restoreFocus). On a cold
        // start or a tab switch, leave focus on the rail's Home item so the nav is immediately navigable.
        if (restoreFocus) {
            kotlinx.coroutines.delay(60)
            val focusTarget = when {
                heroVisible && state.heroCards.isNotEmpty() -> rowFocusRequester(HomeRow.HERO)
                showHeroFallback -> rowFocusRequester(HomeRow.HERO)
                firstDataRow != null -> rowFocusRequester(firstDataRow)
                else -> null
            }
            if (focusTarget != null) runCatching { focusTarget.requestFocus() }
            onRestored()
        }
    }

    // While the first load runs, nothing but the Stage background behind: not the empty state (which
    // would look wrong, and vanish, for a user who does have history), and no placeholder blocks.
    if (state.isLoading) return
    if (showAllHiddenState) {
        AllRowsHiddenState(modifier = modifier.fillMaxSize())
        return
    }
    if (showEmptyState) {
        EmptyHomeState(modifier = modifier.fillMaxSize())
        return
    }

    val hero = state.heroCards.getOrNull(state.activeHeroIndex)
    // Scroll only as far as the focused row needs to be fully on screen (focus on Favourite
    // channels leaves Keep watching where it is). The TV default instead pins the focused item a
    // third of the way down, which slid the whole page up on every ▼. The hero itself never scrolls.
    val homeBringIntoViewSpec = remember {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
                val trailing = offset + size - containerSize
                return when {
                    offset >= 0f && trailing <= 0f -> 0f
                    offset < 0f || size > containerSize -> offset
                    else -> trailing
                }
            }
        }
    }
    // The rows inside read the spec above too, and flush to the row's own edge the last card (scaled up,
    // with its ring) ran off the screen. Across a row, keep the card inside the row's padding instead:
    // [contentStart] before it, the 64 every row ends with after it.
    val density = LocalDensity.current
    val rowBringIntoViewSpec = remember(contentStart, density) {
        val before = with(density) { contentStart.toPx() }
        val after = with(density) { 64.mpx.toPx() }
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
                val leading = offset - before
                val trailing = offset + size - (containerSize - after)
                return when {
                    leading >= 0f && trailing <= 0f -> 0f
                    leading < 0f || size > containerSize - before - after -> leading
                    else -> trailing
                }
            }
        }
    }

    // The Stage page itself is painted by the shell, full width, so it also runs under a docked rail.
    Box(modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalBringIntoViewSpec provides homeBringIntoViewSpec) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .onFocusChanged { if (it.hasFocus) onChildFocused() }
                    // ▶ out of the rail comes back to the card it left from (rail carry-over): every level
                    // of the list remembers its focused child.
                    .focusGroup(),
                state = listState,
                contentPadding = PaddingValues(top = NoHeroTop, bottom = 48.mpx),
                verticalArrangement = Arrangement.spacedBy(RowGap),
            ) {
                itemsIndexed(renderRows, key = { _, row -> row.name }) { index, row ->
                    val firstItemFocusRequester = rowFocusRequester(row)
                    // With no hero above it, the first row is the top of the page: back at it, show the page
                    // from the top. Bringing the focused card into view alone stopped as soon as the card
                    // showed, leaving the row's heading (Keep watching) cut off above the screen.
                    val topRow = index == 0
                    val toTop = Modifier.onFocusChanged { if (it.hasFocus) homeScope.launch { listState.animateScrollToItem(0) } }
                    val rowModifier = if (topRow) toTop else Modifier
                    // Focus entering a row also brings the whole row into view, heading included: the
                    // focused card alone stopped the list as soon as the card showed, so coming back up
                    // left each row's heading cut off above the screen.
                    val rowArea = remember { BringIntoViewRequester() }
                    Column(
                        if (topRow) Modifier else Modifier
                            .bringIntoViewRequester(rowArea)
                            .onFocusChanged { if (it.hasFocus) homeScope.launch { rowArea.bringIntoView() } },
                    ) {
                    CompositionLocalProvider(LocalBringIntoViewSpec provides rowBringIntoViewSpec) {
                        when (row) {
                            HomeRow.HERO -> if (state.heroCards.isNotEmpty()) {
                                KeepWatchingRow(
                                    title = row.displayTitle(),
                                    items = state.heroCards,
                                    start = contentStart,
                                    previewIndex = previewIndex,
                                    engine = heroPreviewEngine,
                                    engineState = engineState,
                                    firstFocusRequester = firstItemFocusRequester ?: heroFocus,
                                    onCardFocusChanged = { index, hasFocus ->
                                        if (hasFocus) {
                                            if (previewIndex != index) {
                                                heroPreviewEngine.stop() // stop the previous card's video first
                                                previewIndex = -1 // the dwell timer starts it again after 3 s
                                            }
                                            focusedHeroIndex = index
                                            vm.onHeroUserNavigate(index)
                                            vm.setHeroFocused(true)
                                            onChildFocused()
                                        } else if (focusedHeroIndex == index) {
                                            focusedHeroIndex = -1
                                        }
                                    },
                                    onPlay = { card -> onPlayChannel(card.channel.id, state.recentLive) },
                                    track = track,
                                    modifier = rowModifier,
                                )
                            } else {
                                HeroFallbackPane(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = contentStart).then(rowModifier),
                                    focusRequester = firstItemFocusRequester ?: fallbackFocus,
                                    onChildFocused = onNonHeroFocused,
                                )
                            }

                            HomeRow.RECENT_CHANNELS -> HomeLiveRow(
                                title = row.displayTitle(),
                                mode = state.config.recentLiveMode,
                                channels = state.recentLive,
                                guide = state.recentGuide,
                                onChannelClick = onPlayChannel,
                                onFocus = onNonHeroFocused,
                                firstItemFocusRequester = firstItemFocusRequester,
                                start = contentStart,
                                track = track,
                                modifier = rowModifier,
                            )

                            HomeRow.FAVORITE_CHANNELS -> HomeLiveRow(
                                title = row.displayTitle(),
                                mode = state.config.favoriteLiveMode,
                                channels = state.favoriteLive,
                                guide = state.favoriteGuide,
                                onChannelClick = onPlayChannel,
                                onFocus = onNonHeroFocused,
                                firstItemFocusRequester = firstItemFocusRequester,
                                start = contentStart,
                                track = track,
                                modifier = rowModifier,
                            )

                            else -> Unit // Favourite/Continue movie & series rails are gone (VOD removed).
                        }
                    }
                    }
                }
            }
        }
    }

    // The preview starts after the card has settled, so decoder setup does not compete with the focus lift.
    LaunchedEffect(isPreviewActive, hero, previewIndex, focusedHeroIndex, lastInteractionMs) {
        if (!isPreviewActive || hero == null || previewIndex < 0 ||
            focusedHeroIndex != previewIndex
        ) {
            heroPreviewEngine.stop()
            return@LaunchedEffect
        }

        val scheduledIndex = previewIndex
        val scheduledHero = hero
        val interactionStamp = lastInteractionMs

        heroPreviewEngine.stop()
        kotlinx.coroutines.delay(520L)
        if (!isPreviewActive || interactionStamp != lastInteractionMs) return@LaunchedEffect
        if (focusedHeroIndex != scheduledIndex || previewIndex != scheduledIndex) return@LaunchedEffect
        if (scheduledHero != state.heroCards.getOrNull(state.activeHeroIndex)) return@LaunchedEffect

        vm.startPreview(scheduledHero)
    }
}

private fun rowHasData(row: HomeRow, state: HomeUiState): Boolean = when (row) {
    HomeRow.HERO -> state.heroCards.isNotEmpty()
    HomeRow.RECENT_CHANNELS -> when (state.config.recentLiveMode) {
        HomeLiveRowMode.CARDS -> state.recentLive.isNotEmpty()
        HomeLiveRowMode.ON_NOW -> state.recentGuide.hasContent
    }
    HomeRow.FAVORITE_CHANNELS -> when (state.config.favoriteLiveMode) {
        HomeLiveRowMode.CARDS -> state.favoriteLive.isNotEmpty()
        HomeLiveRowMode.ON_NOW -> state.favoriteGuide.hasContent
    }
    else -> false // Trending, favourite/continue movie & series rails: VOD is gone, they never have data.
}

private fun rowCanRender(row: HomeRow, state: HomeUiState, showHeroFallback: Boolean): Boolean =
    when (row) {
        HomeRow.HERO -> state.heroCards.isNotEmpty() || showHeroFallback
        else -> rowHasData(row, state)
    }

/**
 * Reports this control to Home when it takes focus, as the place ▶ out of the rail returns to. Pass the
 * control's own [requester] when it already has one; otherwise one is made for it.
 */
@Composable
internal fun Modifier.tracked(requester: FocusRequester? = null, track: (FocusRequester) -> Unit): Modifier {
    val own = remember { FocusRequester() }
    return (if (requester == null) focusRequester(own) else this)
        .onFocusChanged { if (it.hasFocus) track(requester ?: own) }
}

/**
 * Keep watching: the channels last watched, 16:9 stills 336 × 189. After 3 s on a card
 * its preview plays inside the still (the card does not widen, nothing is drawn over it).
 */
@Composable
private fun KeepWatchingRow(
    title: String,
    items: List<HomeHeroCard>,
    start: Dp,
    previewIndex: Int,
    engine: HeroPreviewEngine,
    engineState: HeroPreviewEngine.State,
    firstFocusRequester: FocusRequester,
    onCardFocusChanged: (index: Int, hasFocus: Boolean) -> Unit,
    onPlay: (HomeHeroCard) -> Unit,
    track: (FocusRequester) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        HomeRowHeader(title, small = items.size.toString(), start = start)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(20.mpx),
            contentPadding = PaddingValues(start = start, end = 64.mpx),
            modifier = Modifier.focusRestorer().focusGroup(),
        ) {
            itemsIndexed(items, key = { _, card -> card.channel.id }) { index, card ->
                val still = homeStill(card.channel.logoUrl)
                StageStill(
                    title = card.channel.name,
                    line = relativeLastWatchedLabel(card.watchedAt, System.currentTimeMillis()),
                    onClick = { onPlay(card) },
                    width = 336.mpx,
                    height = 189.mpx,
                    titleSize = 19,
                    lineSize = 15,
                    modifier = Modifier
                        .then(if (index == 0) Modifier.focusRequester(firstFocusRequester) else Modifier)
                        .tracked(track = track)
                        .onFocusChanged { onCardFocusChanged(index, it.hasFocus) },
                ) {
                    // The video sits under the still, which lifts off once frames arrive. (A TextureView
                    // hidden at alpha 0 is never drawn, so it never gets the surface the engine needs.)
                    if (index == previewIndex) PreviewTexture(engine, Modifier.fillMaxSize())
                    if (index != previewIndex || engineState != HeroPreviewEngine.State.PLAYING) StillArtwork(still)
                }
            }
        }
    }
}

@Composable
private fun StillArtwork(still: HomeStill) {
    when (still) {
        is HomeStill.Picture -> AsyncImage(model = still.url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        is HomeStill.Logo -> Box(Modifier.fillMaxSize().background(tv.own.myowntv.ui.stage.stageStillBack), contentAlignment = Alignment.Center) {
            tv.own.myowntv.ui.components.ChannelLogoTile(
                logoUrl = still.url,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.mpx)),
                imageModifier = Modifier.fillMaxSize().padding(8.mpx),
                fill = Color.Transparent,
            ) {}
        }
        HomeStill.None -> Box(Modifier.fillMaxSize().background(tv.own.myowntv.ui.stage.stageStillBack), contentAlignment = Alignment.Center) {
            MyOwnTVIcon(MyOwnTVIcon.PLAY_CIRCLE, tint = StageColors.Dim, modifier = Modifier.size(40.mpx))
        }
    }
}

/**
 * The preview's video. A TextureView, not a SurfaceView: the focused card is lifted by 1.08 and clipped
 * to its corners, and only a TextureView is drawn with the card — a SurfaceView would sit unscaled and
 * square behind it.
 */
@Composable
private fun PreviewTexture(engine: HeroPreviewEngine, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextureView(ctx).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    private var surface: Surface? = null
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                        surface = Surface(st).also(engine::setSurface)
                    }
                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) = Unit
                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                        engine.setSurface(null)
                        surface?.release()
                        surface = null
                        return true
                    }
                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                }
            }
        },
    )
}

@Composable
internal fun relativeLastWatchedLabel(lastEngagementAt: Long, nowMs: Long): String {
    val elapsedMs = nowMs - lastEngagementAt
    if (elapsedMs < 60_000L) return stringResource(R.string.home_last_watched_now)

    val elapsedMinutes = elapsedMs / 60_000L
    if (elapsedMinutes < 60L) {
        return pluralStringResource(R.plurals.home_last_watched_minutes, elapsedMinutes.toInt(), elapsedMinutes.toInt())
    }

    val elapsedHours = elapsedMinutes / 60L
    if (elapsedHours < 24L) {
        return pluralStringResource(R.plurals.home_last_watched_hours, elapsedHours.toInt(), elapsedHours.toInt())
    }

    val elapsedDays = elapsedHours / 24L
    return pluralStringResource(R.plurals.home_last_watched_days, elapsedDays.toInt(), elapsedDays.toInt())
}

@Composable
private fun HeroFallbackPane(
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester,
    onChildFocused: () -> Unit,
) {
    Box(
        modifier = modifier
            .aspectRatio(16f / 9f)
            .focusRequester(focusRequester)
            .focusable()
            .onFocusChanged { if (it.hasFocus) onChildFocused() }
            .clip(RoundedCornerShape(20.dp))
            .background(tv.own.myowntv.ui.theme.StageColors.GlassOff)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BrandLockup(markSize = 72)
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.home_no_preview),
                style = tv.own.myowntv.ui.theme.stageText(29, 400),
                color = tv.own.myowntv.ui.theme.StageColors.Muted,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.home_continue_empty),
                style = tv.own.myowntv.ui.theme.stageText(25, 400),
                color = tv.own.myowntv.ui.theme.StageColors.Muted,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EmptyHomeState(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .focusProperties { canFocus = false }
            .background(tv.own.myowntv.ui.theme.StageColors.GlassOff),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BrandLockup(markSize = 84)
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.home_start_watching),
                style = tv.own.myowntv.ui.theme.stageText(40, 600),
                color = tv.own.myowntv.ui.theme.StageColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.home_continue_empty),
                style = tv.own.myowntv.ui.theme.stageText(29, 400),
                color = tv.own.myowntv.ui.theme.StageColors.Muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AllRowsHiddenState(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .focusProperties { canFocus = false }
            .background(tv.own.myowntv.ui.theme.StageColors.GlassOff),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BrandLockup(markSize = 84)
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.home_no_rows),
                style = tv.own.myowntv.ui.theme.stageText(40, 600),
                color = tv.own.myowntv.ui.theme.StageColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.home_enable_rows),
                style = tv.own.myowntv.ui.theme.stageText(29, 400),
                color = tv.own.myowntv.ui.theme.StageColors.Muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
