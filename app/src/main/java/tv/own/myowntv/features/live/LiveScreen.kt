package tv.own.myowntv.features.live
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

import tv.own.myowntv.ui.components.onDpadLeft
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.focus.FocusRequester as RowFocus
import tv.own.myowntv.ui.theme.mpx
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import androidx.tv.material3.Text
import tv.own.myowntv.R
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.ContentOrderEntity
import tv.own.myowntv.features.categorylock.focusListAfterSheet
import tv.own.myowntv.features.categorylock.rememberListPinFocus
import tv.own.myowntv.features.customize.MoveToCategoryDialog
import tv.own.myowntv.features.settings.SettingsViewModel
import tv.own.owntv.core.settings.PanelSection
import tv.own.myowntv.features.settings.data.computePanelWidths
import tv.own.myowntv.features.settings.rememberPanelShares
import tv.own.myowntv.features.shell.components.RailCategoryMenu
import tv.own.myowntv.ui.components.MoveOrderOverlay
import tv.own.myowntv.ui.components.chNavPaging
import tv.own.myowntv.ui.components.longPressMenuGuard
import tv.own.myowntv.ui.components.trapAllFocusExit
import tv.own.owntv.core.model.ContentMenu
import tv.own.myowntv.ui.components.MenuAction
import tv.own.myowntv.ui.components.arranged
import tv.own.myowntv.ui.components.MyOwnTVIcon
import tv.own.myowntv.ui.components.OwnTVSpinner
import tv.own.myowntv.ui.components.TextInputDialog
import tv.own.myowntv.ui.components.gridFocusTarget
import tv.own.owntv.core.live.LiveKey
import tv.own.owntv.core.live.ProviderTags

/** Live TV: the channel list, the stage beside it and the categories as a sheet or a column. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun LiveScreen(
    onFullscreen: () -> Unit,
    onChildFocused: () -> Unit,
    previewEnabled: Boolean = true,
    restoreFocus: Boolean = false,
    onRestored: () -> Unit = {},
    modifier: Modifier = Modifier,
    /**
     * Pins the list to one folder and takes the category rail away — how More → Favourites and
     * More → History show channels without a second copy of this list existing.
     */
    lockedKey: LiveKey? = null,
    /** Guide view (Live TV tool row): opens the TV Guide until an in-place guide exists. */
    onOpenGuide: () -> Unit = {},
) {
    val vm: LiveViewModel = koinViewModel()
    val lockVm: tv.own.myowntv.features.categorylock.CategoryLockViewModel = koinViewModel()
    // Locking and unlocking are one pair: the pin belongs to this screen's lifetime, not to the view
    // model's. On the television that view model is a single instance shared with the browse section,
    // so a pin left behind froze its category rail. `DisposableEffect` (not `LaunchedEffect`) also
    // means the pin is in place before the first frame, so the list never flashes the wrong folder.
    if (lockedKey != null) {
        val pinned = lockedKey
        DisposableEffect(pinned) {
            vm.lock(pinned)
            onDispose { vm.unlock() }
        }
    }
    val railItems by vm.railItems.collectAsStateWithLifecycle()
    val railFocus = remember { FocusRequester() }
    val selectedKey by vm.selectedKey.collectAsStateWithLifecycle()
    val count by vm.count.collectAsStateWithLifecycle()
    val favoriteIds by vm.favoriteIds.collectAsStateWithLifecycle()
    val homePinnedIds by vm.homePinnedIds.collectAsStateWithLifecycle()
    val showChannelNumbers by vm.showChannelNumbers.collectAsStateWithLifecycle()
    val externalPlayerOn by vm.externalPlayerOn.collectAsStateWithLifecycle()
    val previewChannel by vm.previewChannel.collectAsStateWithLifecycle()
    val previewCategoryName by vm.previewCategoryName.collectAsStateWithLifecycle()
    val previewArmed by vm.previewArmed.collectAsStateWithLifecycle()
    val previewBlockedSingleSession by vm.previewBlockedSingleSession.collectAsStateWithLifecycle()
    val previewBlockedByMemory by vm.previewBlockedByMemory.collectAsStateWithLifecycle()
    val keyedNowNext by vm.keyedNowNext.collectAsStateWithLifecycle()
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
    /** The search field's keyboard is open — ◀ then moves the text cursor, not to Categories. */
    var searchEditing by remember { mutableStateOf(false) }
    val sortMode by vm.sortMode.collectAsStateWithLifecycle()
    val livePreviewSetting by vm.livePreviewEnabled.collectAsStateWithLifecycle()
    val previewOnOk by vm.livePreviewOnOk.collectAsStateWithLifecycle()
    val paneChannel by vm.paneChannel.collectAsStateWithLifecycle()
    val channels = vm.channels.collectAsLazyPagingItems()
    val moveState by vm.moveState.collectAsStateWithLifecycle()
    val categoryMoveState by vm.categoryMoveState.collectAsStateWithLifecycle()

    // Current programme title for each loaded channel (id → title), against the stored guide. Drives
    // the small "now playing" subtitle on each channel row. Channels with no guide are absent from the
    // map → their row shows no second line.
    //
    // An appended page asks only about the channels it added; the view model keeps the rest. This
    // used to re-query every loaded channel on every append and again every 60 seconds, which deep
    // in a large category was a dozen chunked queries a minute to learn nothing new.
    val nowPlaying by vm.nowPlaying.collectAsStateWithLifecycle()
    val loadedChannels = channels.itemSnapshotList.items.filterNotNull()
    // One shared number column for the whole list: a four-digit fallback number must not be clipped
    // just because another row has a short one. Recomputed only when the loaded channels change.
    val numberFontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    val numberWidth = remember(loadedChannels, numberFontScale) {
        liveNumberWidth(loadedChannels.map { it.number }, numberFontScale)
    }
    LaunchedEffect(loadedChannels.size, loadedChannels.firstOrNull()?.id, loadedChannels.lastOrNull()?.id) {
        vm.ensureNowPlaying(loadedChannels)
    }
    // Turnover happens on the minute, so wait for the next one rather than 60s from mount — otherwise
    // rows change late and at different instants from each other.
    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            kotlinx.coroutines.delay(60_000 - (now % 60_000))
            vm.refreshNowPlaying(channels.itemSnapshotList.items.filterNotNull())
        }
    }
    // Preview runs only when the player isn't busy (previewEnabled) AND the user hasn't turned it off.
    val effectivePreview = previewEnabled && livePreviewSetting

    // NOTE: do NOT stop the player when LiveScreen leaves composition — going fullscreen disposes
    // this screen, and stopping here would abort the stream that was just started. Playback is
    // stopped on fullscreen exit (shell BackHandler) instead.

    // In-pane preview: play the focused channel after the focus settles (700ms). Disabled while the
    // fullscreen/mini player owns the surface (previewEnabled=false) to avoid two surfaces fighting.
    LaunchedEffect(previewChannel?.id, effectivePreview, previewArmed, previewOnOk, previewBlockedByMemory) {
        // previewArmed gates the case where the last channel was restored on startup — we don't auto-preview
        // it until the user actually focuses a channel (then it plays normally). Critical pressure keeps
        // focus from starting a new decoder for the rest of the process; OK still plays explicitly.
        if (previewOnOk || !effectivePreview || !previewArmed || previewBlockedByMemory) return@LaunchedEffect
        val ch = previewChannel ?: return@LaunchedEffect
        delay(700)
        vm.playPreview(ch)
    }
    // With "OK previews first" the pane plays only what OK chose (or the channel just watched full
    // screen); this takes it back whenever the pane can play again — back from the player or another tab.
    LaunchedEffect(effectivePreview, previewArmed, previewOnOk) {
        if (!previewOnOk || !effectivePreview || !previewArmed) return@LaunchedEffect
        vm.paneChannel.value?.let(vm::playPreview)
    }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val selFocus = remember { FocusRequester() }
    val firstItemFocus = remember { FocusRequester() }
    // Right from the rail on an empty list: the list's search box, so a search with no results can be cleared.
    val listSearchFocus = remember { FocusRequester() }

    // CH+- key paging: shared settings + a hoisted rail state so the same modifier can page both the
    // category rail and this channel list. Channel-list pane focus is tracked separately from rail
    // focus so chNavPaging only consumes the keys for whichever pane is active.
    val settingsVm: SettingsViewModel = koinViewModel()
    val chNavEnabled by settingsVm.chNavEnabled.collectAsStateWithLifecycle()
    val chNavUpSkip by settingsVm.chNavUpSkip.collectAsStateWithLifecycle()
    val chNavDownSkip by settingsVm.chNavDownSkip.collectAsStateWithLifecycle()
    val rememberLive by settingsVm.rememberLastLive.collectAsStateWithLifecycle()

    // "Remember last item per category": ON → each category keeps its own scroll position via a per-category
    // state map (so A→B→A lands back where you were in A). OFF → reset the shared state to the top whenever
    // the category changes (fixes the cross-category scroll-leak bug).
    // A plain map: writing `getOrPut` during composition must not write snapshot state. The stored
    // LazyListState is itself observable, so scroll changes still recompose — the map only needs to
    // keep one state per category across switches.
    val perCategoryStates = remember { mutableMapOf<LiveKey, androidx.compose.foundation.lazy.LazyListState>() }
    val perCategoryChannelIds = remember { mutableStateMapOf<LiveKey, String>() }
    val effectiveListState =
        if (rememberLive) perCategoryStates.getOrPut(selectedKey) { androidx.compose.foundation.lazy.LazyListState() }
        else listState
    LaunchedEffect(selectedKey, rememberLive) {
        if (!rememberLive) runCatching { listState.scrollToItem(0) }
    }
    val catListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val categoryQuery by vm.categoryQuery.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var channelPaneFocused by remember { mutableStateOf(false) }
    var railPaneFocused by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<ChannelEntity?>(null) }
    var offsettingEpg by remember { mutableStateOf<ChannelEntity?>(null) }
    var contextChannel by remember { mutableStateOf<ChannelEntity?>(null) } // long-press quick menu
    // Multiview: whether the menu offers it at all, how many tiles the grid has, and the confirmation
    // after a channel is kept (null = nothing to say).
    val liveSettings = koinInject<tv.own.owntv.core.settings.SettingsRepository>()
    val multiviewEnabled by liveSettings.multiviewEnabled.collectAsStateWithLifecycle(false)
    val multiviewTiles by liveSettings.multiviewTiles.collectAsStateWithLifecycle(
        tv.own.owntv.core.live.DEFAULT_MULTIVIEW_TILES,
    )
    val multiviewToast = tv.own.myowntv.ui.components.rememberInAppToast()
    // Resolved through resources rather than stringResource: the count is only known inside the click.
    val multiviewRes = androidx.compose.ui.platform.LocalContext.current.resources
    var contextCategory by remember { mutableStateOf<LiveRailItem?>(null) }
    // The rail row a category menu was opened from, kept after the menu closes so the cursor can go
    // back to that exact row. Held as a key, not an index: a Move changes the row's position.
    var contextCategoryKey by remember { mutableStateOf<LiveKey?>(null) }
    var railFocusRow by remember { mutableStateOf<Int?>(null) }
    // The channel the "Move to category…" flow is moving, with the origin captured at
    // menu-open time (the rail can't change under the modal, but capturing is still safer).
    var moveItem by remember { mutableStateOf<ChannelEntity?>(null) }
    var moveOriginKey by remember { mutableStateOf<String?>(null) }
    var moveOriginName by remember { mutableStateOf<String?>(null) }
    var creatingCategory by remember { mutableStateOf(false) }
    // When the long-press menu closes (Cancel, Favourite, Hide) WITHOUT opening another dialog, return focus
    // to the channel it was opened from — otherwise focus falls back to the nav panel.
    var contextMenuOpen by remember { mutableStateOf(false) }
    // Id of the channel the context menu was opened on, plus a dedicated requester bound to that row.
    // The previous restore was racy (delay(60) + selFocus bound to the *previewed* channel): when the
    // menu scrim disposed the focused menu button, Compose auto-restored focus and the category rail's
    // entry-redirect pinned it to the rail before selFocus.requestFocus() ran. Tracking the long-press
    // target by id and binding a dedicated requester makes the restore deterministic.
    var contextChannelId by remember { mutableStateOf<String?>(null) }
    val contextFocus = remember { FocusRequester() }
    var enteringMoveMode by remember { mutableStateOf(false) }
    LaunchedEffect(moveState) { if (moveState != null) enteringMoveMode = false }
    // Bring row [idx] on screen only when it is not already fully there: returning focus to a row
    // that is visible must not yank the list so that row jumps to the top.
    suspend fun revealRow(idx: Int) {
        val info = effectiveListState.layoutInfo
        val row = info.visibleItemsInfo.firstOrNull { it.index == idx }
        val fullyVisible = row != null && row.offset >= info.viewportStartOffset && row.offset + row.size <= info.viewportEndOffset
        if (!fullyVisible) runCatching { effectiveListState.scrollToItem(idx) }
    }
    // One requester per row position, for ▲/▼ stepping. Only composed rows are attached.
    val rowRequesters = remember { HashMap<Int, RowFocus>() }
    fun rowFocus(i: Int): RowFocus = rowRequesters.getOrPut(i) { RowFocus() }
    // Move to row [i]: scroll just enough to show it with one row of room (the edge-scroll rule), wait for
    // it to be laid out, then focus it. Works for a held key: nothing depends on focus search.
    // The row a burst of presses is heading for: every press counts from there, not from the row that
    // still has focus, and the newest press cancels the step still in flight.
    var stepTarget by remember { mutableStateOf<Int?>(null) }
    var stepJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun stepTo(i: Int) {
        stepTarget = i
        stepJob?.cancel()
        stepJob = scope.launch {
            val info = effectiveListState.layoutInfo
            val row = info.visibleItemsInfo.firstOrNull { it.index == i }
            val step = ((info.visibleItemsInfo.firstOrNull()?.size ?: 0) + info.mainAxisItemSpacing).toFloat()
            val delta = when {
                row == null -> if (i > (info.visibleItemsInfo.lastOrNull()?.index ?: 0)) step else -step
                row.offset - step < info.viewportStartOffset -> row.offset - step - info.viewportStartOffset
                row.offset + row.size + step > info.viewportEndOffset -> row.offset + row.size + step - info.viewportEndOffset
                else -> 0f
            }
            if (delta != 0f) runCatching { effectiveListState.scrollBy(delta) }
            if (effectiveListState.layoutInfo.visibleItemsInfo.none { it.index == i }) runCatching { effectiveListState.scrollToItem(i) }
            withFrameNanos { }
            runCatching { rowFocus(i).requestFocus() }
            stepTarget = null
        }
    }
    // Land focus back on the long-pressed channel's row (or a sensible fallback if it's gone).
    suspend fun restoreToContextRow() {
        val targetId = contextChannelId
        if (targetId == null) { runCatching { selFocus.requestFocus() }; return }

        val idx = channels.itemSnapshotList.items.indexOfFirst { it.id == targetId }
        if (idx >= 0) {
            revealRow(idx)
            withFrameNanos { } // wait one frame so the row is laid out and contextFocus is attached
            runCatching { contextFocus.requestFocus() }
        } else {
            // Row is gone (e.g. "Hide channel" removed it) — clear the anchor and land on the first row.
            contextChannelId = null
            runCatching { firstItemFocus.requestFocus() }
        }
    }
    LaunchedEffect(contextChannel) {
        val opened = contextChannel != null
        if (opened) { contextMenuOpen = true; return@LaunchedEffect }
        if (!contextMenuOpen) return@LaunchedEffect
        contextMenuOpen = false
        // A follow-up dialog (rename / EPG offset / move) grabs focus itself — only restore
        // for plain closes (Cancel, Favourite, Hide, Close). Those dialogs restore on their own close.
        if (renaming != null || offsettingEpg != null || enteringMoveMode ||
            moveItem != null || creatingCategory
        ) return@LaunchedEffect
        restoreToContextRow()
    }
    // The context menu closes before the shared Move-to-category dialog opens. Treat both the move
    // picker and its nested New-category prompt as one focus-owning flow, then restore the original
    // row only after the whole flow closes. Otherwise the menu-close effect can steal focus behind
    // the new dialog.
    var moveCategoryWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(moveItem, creatingCategory) {
        if (moveItem != null || creatingCategory) {
            moveCategoryWasOpen = true
        } else if (moveCategoryWasOpen) {
            moveCategoryWasOpen = false
            restoreToContextRow()
        }
    }
    // Rename restoration: re-assert row focus after dialog closes.
    var renameWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(renaming) {
        if (renaming != null) { renameWasOpen = true; return@LaunchedEffect }
        if (!renameWasOpen) return@LaunchedEffect
        renameWasOpen = false
        repeat(5) {
            delay(200)
            restoreToContextRow()
        }
    }
    // Channel reorder restoration.
    var reorderWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(moveState) {
        if (moveState != null) { reorderWasOpen = true; return@LaunchedEffect }
        if (!reorderWasOpen) return@LaunchedEffect
        reorderWasOpen = false
        restoreToContextRow()
    }
    // Same for the EPG-offset dialog: it owns focus while open, so hand it back to the channel row.
    var epgOffsetWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(offsettingEpg) {
        if (offsettingEpg != null) { epgOffsetWasOpen = true; return@LaunchedEffect }
        if (!epgOffsetWasOpen) return@LaunchedEffect
        epgOffsetWasOpen = false
        restoreToContextRow()
    }
    // Category rail focus restoration.
    var contextCategoryWasOpen by remember { mutableStateOf(false) }
    var categoryMoveWasOpen by remember { mutableStateOf(false) }
    // Land on the row the menu was opened from; fall back to the column if that row is gone (Hide).
    fun restoreToContextCategory() {
        val row = contextCategoryKey?.let { k -> railItems.indexOfFirst { it.key == k } }?.takeIf { it >= 0 }
        contextCategoryKey = null
        if (row != null) railFocusRow = row else runCatching { railFocus.requestFocus() }
    }
    LaunchedEffect(contextCategory, categoryMoveState) {
        if (contextCategory != null) contextCategoryWasOpen = true
        if (categoryMoveState != null) categoryMoveWasOpen = true

        if (contextCategory == null && contextCategoryWasOpen && categoryMoveState == null) {
            contextCategoryWasOpen = false
            if (!categoryMoveWasOpen) {
                kotlinx.coroutines.delay(60)
                restoreToContextCategory()
            }
        }
        if (categoryMoveState == null && categoryMoveWasOpen) {
            categoryMoveWasOpen = false
            kotlinx.coroutines.delay(60)
            restoreToContextCategory()
        }
    }
    // Returning from fullscreen: scroll to and focus the channel you were watching (waits for the list to load).
    // Also used by "Startup → Live · Favorites": there's no remembered channel yet, so land on the first row
    // (not the nav panel).
    LaunchedEffect(restoreFocus, channels.itemCount) {
        if (!restoreFocus || channels.itemCount == 0) return@LaunchedEffect
        val ch = previewChannel
        val idx = if (ch != null) channels.itemSnapshotList.items.indexOfFirst { it.id == ch.id } else -1
        if (idx >= 0) {
            revealRow(idx)
            delay(60)
            runCatching { selFocus.requestFocus() }
        } else {
            delay(60)
            runCatching { firstItemFocus.requestFocus() }
        }
        onRestored()
    }
    // Search's "Go to channel": scroll to where the channel sits so that part of the list loads, find
    // its row by id and focus it. The list can still be the old category's for a moment, so focus is
    // re-asserted for a short while; nothing found after ~5 s lands on the first row.
    val reveal by vm.reveal.collectAsStateWithLifecycle()
    LaunchedEffect(reveal, selectedKey) {
        val r = reveal ?: return@LaunchedEffect
        if (selectedKey != r.key) return@LaunchedEffect
        if (rememberLive) perCategoryChannelIds[selectedKey] = r.id
        var found = false
        for (attempt in 0 until 50) {
            if (channels.itemCount > 0) {
                val idx = channels.itemSnapshotList.indexOfFirst { it?.id == r.id }
                if (idx >= 0) {
                    revealRow(idx)
                    withFrameNanos { }
                    if (runCatching { selFocus.requestFocus() }.getOrDefault(false)) found = true
                    if (found && attempt >= 8) break
                } else {
                    runCatching { effectiveListState.scrollToItem(r.position.coerceAtMost(channels.itemCount - 1)) }
                }
            }
            delay(100)
        }
        if (!found) runCatching { firstItemFocus.requestFocus() }
        vm.revealDone()
    }

    val selectedIndex = railItems.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)
    val selectedItem = railItems.getOrNull(selectedIndex)
    val selectedLabel = selectedItem?.displayLabel() ?: stringResource(R.string.content_category_all_channels)

    val liveLayout by vm.liveLayout.collectAsStateWithLifecycle()
    val nowProgrammes by vm.nowProgrammes.collectAsStateWithLifecycle()
    // Separate panels keeps the categories on screen; the Stage layout opens them as a sheet on Left.
    val separate = liveLayout == tv.own.owntv.core.settings.SettingsRepository.LiveLayout.SEPARATE && lockedKey == null
    var categoriesOpen by remember { mutableStateOf(false) }
    var sheetHadFocus by remember { mutableStateOf(false) }
    // The current channel's row. After a long-press that row carries contextFocus instead of selFocus
    // (gridFocusTarget prefers it), so both are tried before falling back to the first row.
    fun focusCurrentRow(): Boolean =
        runCatching { selFocus.requestFocus() }.getOrDefault(false) ||
            runCatching { contextFocus.requestFocus() }.getOrDefault(false) ||
            runCatching { firstItemFocus.requestFocus() }.getOrDefault(false)
    val categoriesVisible = lockedKey == null && (separate || categoriesOpen)
    val railCounts by (if (categoriesVisible) vm.railCounts else remember { kotlinx.coroutines.flow.MutableStateFlow(emptyMap<LiveKey, Int>()) })
        .collectAsStateWithLifecycle()
    // The channel whose Program details page is open; focus returns to its row when it closes.
    var programmeDetails by remember { mutableStateOf<ChannelEntity?>(null) }
    LaunchedEffect(categoriesOpen) {
        if (categoriesOpen) { withFrameNanos { }; runCatching { railFocus.requestFocus() } }
        // However the sheet closed (Back, a pick, ◀ to the rail), the next one starts fresh.
        else sheetHadFocus = false
    }

    // Focus from the categories to the channel list: the remembered channel, else the first row (a
    // locked category's Enter PIN button holds its requester), else the search field.
    fun focusChannelList() {
        val targetId = if (rememberLive) perCategoryChannelIds[selectedKey] ?: previewChannel?.id else previewChannel?.id
        scope.launch {
            if (channels.itemCount > 0) {
                val targetIdx = if (targetId != null) {
                    channels.itemSnapshotList.items.indexOfFirst { it.id == targetId }.takeIf { it >= 0 } ?: 0
                } else 0
                revealRow(targetIdx)
                withFrameNanos { }
                repeat(3) {
                    if (targetId != null && focusCurrentRow()) return@launch
                    if (runCatching { firstItemFocus.requestFocus() }.getOrDefault(false)) return@launch
                    withFrameNanos { }
                }
            } else if (!runCatching { firstItemFocus.requestFocus() }.getOrDefault(false)) {
                runCatching { listSearchFocus.requestFocus() }
            }
        }
    }
    val listPin = rememberListPinFocus(channels.itemCount, firstItemFocus, { revealRow(0) }) { focusChannelList() }
    // Back closes the sheet: one level out, to the current row (or the list's way in when a lock emptied it).
    BackHandler(enabled = categoriesOpen) { categoriesOpen = false; focusListAfterSheet(scope, ::focusCurrentRow, { channelPaneFocused }, ::focusChannelList) }
    // ◀ Categories, from a row, the search field or a locked category's Enter PIN: the sheet (Stage) or the column (Separate).
    fun openCategories() { if (separate) runCatching { railFocus.requestFocus() } else categoriesOpen = true }

    val (categoryEntries, groupsHeading) = liveCategoryEntries(railItems, railCounts)
    val headerLabel = if (selectedItem?.key is LiveKey.Folder || selectedItem?.key is LiveKey.Custom) ProviderTags.parse(selectedLabel).name else selectedLabel

    // CH± in the categories moves the highlight only; OK picks.
    var catFocusIndex by remember { mutableStateOf<Int?>(null) }
    // Browsing the categories (or the search field) keeps the preview playing, like a TV: only picking
    // another channel or leaving Live TV changes it.
    val categoriesModifier = Modifier
        .onFocusChanged {
            railPaneFocused = it.hasFocus
            // The sheet closes when focus leaves it (◀ to the rail) — not while its own menus are open,
            // and not on the "unfocused" report every node gets when it first attaches.
            if (it.hasFocus) sheetHadFocus = true
            else if (sheetHadFocus && categoriesOpen && contextCategory == null && categoryMoveState == null && !lockVm.prompting) {
                sheetHadFocus = false
                categoriesOpen = false
            }
        }
        .chNavPaging(
            enabled = chNavEnabled,
            upSkip = chNavUpSkip,
            downSkip = chNavDownSkip,
            isFocused = { railPaneFocused },
            lastIndex = { railItems.size - 1 },
            currentTargetIndex = { catFocusIndex ?: selectedIndex },
            onJumpToIndex = { idx -> catFocusIndex = idx; railFocusRow = idx },
        )
    val onCategorySelect: (Int) -> Unit = { idx ->
        val item = railItems.getOrNull(idx)
        item?.let { vm.select(it.key) }
        if (item?.sealed == true) lockVm.open(tv.own.owntv.core.model.MediaType.LIVE, item.key)
        else if (categoriesOpen) { categoriesOpen = false; focusChannelList() }
    }
    val onCategoryLongSelect: (Int) -> Unit = { idx ->
        railItems.getOrNull(idx)?.let { item ->
            if (item.key is LiveKey.Folder || item.key is LiveKey.Custom) {
                contextCategory = item
                contextCategoryKey = item.key
            }
        }
    }

    // Manual panel widths (Settings → Layout → Panel widths), mapped onto Stage: Category = the sheet
    // (Stage) or the column (Separate), List and Preview split the rest; Preview 0% = full-width list.
    val panelShares = rememberPanelShares(PanelSection.LIVE, settingsVm)
    // The Stage layout has its own set: the sheet on its own scale, list + preview = 100.
    val panelOn by settingsVm.panelWidthEnabled.getValue(PanelSection.LIVE).collectAsStateWithLifecycle()
    val stageSaved by settingsVm.liveStageWidths.collectAsStateWithLifecycle()
    val stageWidths = stageSaved.takeIf { panelOn && !separate }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onFocusChanged { if (it.hasFocus) onChildFocused() },
    ) {
        // Horizontal geometry is a fraction of the 1920 design width, so every zoom reflows.
        val screenW = maxWidth
        fun fx(px: Int) = screenW * (px / 1920f)
        val margin = fx(64)
        val row = screenW - margin * 2
        val previewShown = if (separate) panelShares?.preview != 0 else stageWidths?.preview != 0
        val liveHints = listOf(
            stringResource(R.string.common_ok) to stringResource(R.string.content_key_watch),
            "◀" to stringResource(R.string.content_category_browser_title),
            "▶" to stringResource(R.string.content_programme_details),
            stringResource(R.string.content_key_hold_ok) to stringResource(R.string.content_key_options),
        )
        val gapCat = fx(26)
        val gapStage = if (separate) fx(30) else fx(34)
        val colW: Dp
        val listW: Dp
        val stageW: Dp
        val sheetW: Dp
        if (separate && panelShares != null) {
            val p = computePanelWidths(panelShares, row, gapCat + if (previewShown) gapStage else 0.dp)
            colW = p.category; listW = p.list; stageW = p.preview; sheetW = 0.dp
        } else if (!separate && stageWidths != null) {
            // The sheet is a share of the screen; the row between the margins is list + preview.
            val rest = (row - if (previewShown) gapStage else 0.dp).coerceAtLeast(1.dp)
            colW = 0.dp
            sheetW = screenW * (stageWidths.sheet / 100f)
            listW = rest * (stageWidths.list / 100f)
            stageW = rest - listW
        } else {
            // The design's geometry.
            colW = fx(350); sheetW = fx(450)
            listW = if (separate) fx(620) else fx(846)
            stageW = if (separate) fx(766) else fx(912)
        }
        val listX = if (separate) margin + colW + gapCat else margin
        val listWidth = if (previewShown) listW else row - (listX - margin)

        if (separate) {
            LiveCategories(
                entries = categoryEntries,
                selectedIndex = selectedIndex,
                groupsHeading = groupsHeading,
                sheet = false,
                listState = catListState,
                onSelect = onCategorySelect,
                onLongSelect = onCategoryLongSelect,
                onNavigateRight = { focusChannelList() },
                focusRequester = railFocus,
                focusRowIndex = railFocusRow,
                onRowFocused = { railFocusRow = null },
                onRowFocus = { catFocusIndex = it },
                searchQuery = categoryQuery,
                onSearchQueryChange = vm::setCategoryQuery,
                modifier = categoriesModifier
                    .padding(start = margin, top = 128.mpx, bottom = 24.mpx)
                    .width(colW)
                    .fillMaxHeight(),
            )
        }

        val targetChannelIdState = remember {
            androidx.compose.runtime.derivedStateOf {
                if (rememberLive) perCategoryChannelIds[selectedKey] ?: previewChannel?.id else previewChannel?.id
            }
        }
        val targetChannelId by targetChannelIdState

        // The list side, dimmed to 40% while the sheet is over it.
        Box(
            Modifier
                .fillMaxSize()
                .then(if (categoriesOpen) Modifier.graphicsLayer { alpha = 0.4f } else Modifier),
        ) {
            LiveHeader(
                category = headerLabel,
                count = count.takeUnless { selectedItem?.sealed == true },
                showChevron = !separate && lockedKey == null,
                modifier = Modifier.padding(start = fx(84), top = 52.mpx).width(listX - fx(84) + listWidth),
            )
            // Header tools + channel list: one focus group.
            Column(
                modifier = Modifier
                    .padding(start = listX, top = 118.mpx)
                    .width(listWidth)
                    .fillMaxHeight()
                    .onFocusChanged { channelPaneFocused = it.hasFocus }
                    .chNavPaging(
                        enabled = chNavEnabled,
                        upSkip = chNavUpSkip,
                        downSkip = chNavDownSkip,
                        isFocused = { channelPaneFocused },
                        longPressEnabled = { selectedKey != LiveKey.All },
                        lastIndex = { channels.itemCount - 1 },
                        currentTargetIndex = {
                            val pc = previewChannel
                            if (pc != null) {
                                val idx = channels.itemSnapshotList.items.indexOfFirst { it.id == pc.id }
                                if (idx >= 0) idx else effectiveListState.firstVisibleItemIndex
                            } else {
                                effectiveListState.firstVisibleItemIndex
                            }
                        },
                        onJumpToIndex = { idx ->
                            val target = channels.itemSnapshotList.items.getOrNull(idx)?.id
                            scope.launch {
                                runCatching { effectiveListState.scrollToItem(idx) }
                                withFrameNanos { }
                                if (target != null) {
                                    val item = channels.itemSnapshotList.items.firstOrNull { it.id == target }
                                    if (item != null) {
                                        // The remembered channel is the focus target, so it moves with the jump;
                                        // focus by row number, which holds whatever the row's other requesters are.
                                        vm.onChannelFocused(item)
                                        if (rememberLive) perCategoryChannelIds[selectedKey] = item.id
                                        withFrameNanos { }
                                        runCatching { rowFocus(idx).requestFocus() }
                                    }
                                } else {
                                    runCatching { firstItemFocus.requestFocus() }
                                }
                            }
                        },
                    )
                    // Entering this pane lands on a channel row, never the search field.
                    .focusProperties {
                        onEnter = {
                            if (targetChannelId == null || !focusCurrentRow()) runCatching { firstItemFocus.requestFocus() }
                        }
                    }
                    // Held Up/Down never escape the list; pinned (More) keeps Up for its tabs.
                    .focusProperties {
                        onExit = {
                            when (requestedFocusDirection) {
                                // The rows handle ◀ and ▶ themselves; from the tool row neither leaves the pane.
                                FocusDirection.Left, FocusDirection.Right -> cancelFocusChange()
                                FocusDirection.Up, FocusDirection.Down -> if (lockedKey == null) cancelFocusChange()
                                else -> Unit
                            }
                        }
                    }
                    .focusGroup(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.mpx),
                    modifier = Modifier.padding(start = 4.mpx).focusGroup(),
                ) {
                    tv.own.myowntv.ui.stage.StageSearchField(
                        query = searchQuery,
                        onQueryChange = vm::setSearchQuery,
                        onEditingChange = { searchEditing = it },
                        placeholder = if (separate) stringResource(R.string.common_search) else stringResource(R.string.content_search_in, headerLabel),
                        // The design's share of the list width, but never more than the two tools leave:
                        // a narrow custom list shrinks the field, not "Guide view".
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .width(if (separate) listWidth * (230f / 620f) else listWidth * (456f / 846f))
                            .focusRequester(listSearchFocus)
                            // ◀ Categories from the search field too, as from the rows (not while typing — a search that found nothing here may be in another category).
                            .onDpadLeft({ !searchEditing && lockedKey == null }) { openCategories() },
                    )
                    tv.own.myowntv.ui.stage.StageTool(
                        text = null,
                        icon = MyOwnTVIcon.SORT,
                        value = if (sortMode == tv.own.owntv.core.settings.SettingsRepository.SortMode.ALPHA) stringResource(R.string.settings_sort_alpha) else stringResource(R.string.content_epg_sort_provider),
                        onClick = vm::toggleSort,
                    )
                    tv.own.myowntv.ui.stage.StageTool(
                        text = stringResource(R.string.content_live_guide_view),
                        icon = MyOwnTVIcon.GRID,
                        onClick = onOpenGuide,
                    )
                }
                Spacer(Modifier.height(20.mpx))
                if (channels.itemCount == 0) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        tv.own.myowntv.features.categorylock.CategoryEmptyState(
                            if (selectedItem?.sealed == true) stringResource(R.string.content_category_locked_placeholder) else if (searchQuery.isNotBlank()) stringResource(R.string.content_no_channels_found, searchQuery.trim()) else stringResource(R.string.content_no_channels_here),
                            sealed = selectedItem?.sealed == true, pinFocus = firstItemFocus, horizontalAlignment = Alignment.CenterHorizontally,
                            onEnterPin = { selectedItem?.let { listPin.askedFromList(); lockVm.open(tv.own.owntv.core.model.MediaType.LIVE, it.key) } },
                            onNavigateLeft = { openCategories() }.takeIf { lockedKey == null },
                        )
                    }
                } else {
                    // The list clips whatever is drawn outside it, which cut the focused row's 44 px glow.
                    // It is laid out [glowRoom] wider at each side and padded back by the same amount,
                    // so the rows sit exactly where they did and the glow has room to show.
                    val glowRoom = 24.mpx
                    // Scroll only as far as the focused row needs to be on screen, as Home does: the TV
                    // default pins focus a third of the way down, so each ▼ moved the highlight a row
                    // and then slid the whole list back under it — a double step.
                    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides edgeScrollSpec) {
                    LazyColumn(
                        state = effectiveListState,
                        verticalArrangement = Arrangement.spacedBy(8.mpx),
                        modifier = Modifier.fillMaxWidth().weight(1f).layout { measurable, constraints ->
                            val extra = glowRoom.roundToPx()
                            val placeable = measurable.measure(
                                constraints.copy(
                                    minWidth = constraints.minWidth + extra * 2, maxWidth = constraints.maxWidth + extra * 2,
                                ),
                            )
                            layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(-extra, 0) }
                        },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = glowRoom, end = glowRoom, bottom = 16.mpx),
                    ) {
                        items(
                            count = channels.itemCount,
                            key = channels.itemKey { it.id },
                            contentType = channels.itemContentType { "channel" },
                        ) { index ->
                            // Paging's get() throws while a category switch has installed an empty
                            // page store but the LazyColumn still measures the previous count;
                            // treat that as the documented out-of-bounds null instead of crashing.
                            val channel = try {
                                channels[index]
                            } catch (_: IndexOutOfBoundsException) {
                                null
                            }
                            if (channel != null) {
                                val isTarget by remember(channel.id) {
                                    androidx.compose.runtime.derivedStateOf { targetChannelIdState.value == channel.id }
                                }
                                val parsed = remember(channel.name) { ProviderTags.parse(channel.name) }
                                LiveStageRow(
                                    channel = channel,
                                    name = parsed,
                                    // The focused row takes the preview's answer only once it is keyed to this
                                    // channel; until then, and for every other row, use its own guide entry.
                                    now = rowProgramme(keyedNowNext, channel.id, nowProgrammes, System.currentTimeMillis()),
                                    nowTitle = nowPlaying[channel.id],
                                    isFavorite = favoriteIds.contains(channel.id),
                                    showNumber = showChannelNumbers,
                                    numberWidth = numberWidth,
                                    modifier = Modifier.gridFocusTarget(
                                        itemId = channel.id, index = index,
                                        contextId = contextChannelId, contextFocus = contextFocus,
                                        selectedId = if (isTarget) channel.id else null, selectedFocus = selFocus,
                                        firstItemFocus = firstItemFocus,
                                    ).focusRequester(rowFocus(index)).onPreviewKeyEvent { e ->
                                        if (e.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                        when (e.key) {
                                            // The remote's Menu key opens the same menu as holding OK.
                                            androidx.compose.ui.input.key.Key.Menu -> { contextChannel = channel; contextChannelId = channel.id; true }
                                            // ◀ Categories: the sheet (Stage) or the column (Separate); pinned lists have none.
                                            androidx.compose.ui.input.key.Key.DirectionLeft -> when {
                                                lockedKey != null -> false
                                                else -> { openCategories(); true }
                                            }
                                            // ▶ or the remote's Info key: this channel's Program details page.
                                            androidx.compose.ui.input.key.Key.DirectionRight, androidx.compose.ui.input.key.Key.Info -> {
                                                contextChannelId = channel.id; programmeDetails = channel; true
                                            }
                                            // ▲/▼ step by row number, not by focus search: a held key outran the
                                            // lazy list at the edge or a page boundary and stopped.
                                            androidx.compose.ui.input.key.Key.DirectionDown -> (stepTarget ?: index).let { from -> from + 1 < channels.itemCount && run { stepTo(from + 1); true } }
                                            androidx.compose.ui.input.key.Key.DirectionUp -> (stepTarget ?: index).let { from -> from > 0 && run { stepTo(from - 1); true } }
                                            else -> false
                                        }
                                    },
                                    onFocus = {
                                        vm.onChannelFocused(channel)
                                        if (rememberLive) perCategoryChannelIds[selectedKey] = channel.id
                                    },
                                    onClick = {
                                        when (okAction(previewOnOk, effectivePreview && previewShown, paneChannel?.id, channel.id)) {
                                            OkAction.PREVIEW -> vm.playPreview(channel)
                                            OkAction.WATCH -> {
                                                if (vm.watchFullscreen(channel, channels.itemSnapshotList.items.filterNotNull()) && !externalPlayerOn) {
                                                    onFullscreen()
                                                }
                                            }
                                        }
                                    },
                                    onLongClick = { contextChannel = channel; contextChannelId = channel.id },
                                )
                            }
                        }
                    }
                    }
                }
                // With the preview off the hints stay under the list, where there is nothing else to hold them.
                if (lockedKey == null && !previewShown) {
                    tv.own.myowntv.ui.stage.StageKeyHints(
                        liveHints,
                        // A clear gap under the list, and 40 from the bottom rather than the design's 26:
                        // TV panels overscan the very edge away.
                        modifier = Modifier.padding(start = 16.mpx, top = 22.mpx, bottom = 40.mpx),
                    )
                }
            }

            // Under the preview, bottom right, so the list runs down to the bottom edge.
            if (lockedKey == null && previewShown) {
                tv.own.myowntv.ui.stage.StageKeyHints(
                    liveHints,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = margin, bottom = 40.mpx),
                )
            }
        }

        // The preview sits outside the dimmed list side: a video surface under a translucent layer goes
        // black, and the show keeps playing while the categories are open.
        if (previewShown) {
            // With "OK previews first" the video and its name plate stay on the channel OK chose while
            // the programme below follows the highlight; before any OK, the highlight as before.
            val videoChannel = if (previewOnOk) paneChannel ?: previewChannel else previewChannel
            LiveStagePane(
                channel = videoChannel,
                channelName = videoChannel?.let { ProviderTags.parse(it.name).name },
                // With "OK previews first" the picture stays on the channel OK chose, but the
                // programme below still follows the highlighted row.
                nowNext = nowNextForChannel(keyedNowNext, previewChannel?.id, System.currentTimeMillis()),
                previewEngine = vm.previewEngine,
                showVideo = effectivePreview && (!previewOnOk || paneChannel != null),
                singleSessionBlocked = previewBlockedSingleSession,
                modifier = Modifier
                    .padding(start = screenW - margin - stageW, top = 120.mpx)
                    .width(stageW),
            )
        }

        if (categoriesOpen) {
            LiveCategories(
                entries = categoryEntries,
                selectedIndex = selectedIndex,
                groupsHeading = groupsHeading,
                sheet = true,
                listState = catListState,
                onSelect = onCategorySelect,
                onLongSelect = onCategoryLongSelect,
                onNavigateRight = { categoriesOpen = false; focusChannelList() },
                focusRequester = railFocus,
                focusRowIndex = railFocusRow,
                onRowFocused = { railFocusRow = null },
                onRowFocus = { catFocusIndex = it },
                searchQuery = categoryQuery,
                onSearchQueryChange = vm::setCategoryQuery,
                modifier = categoriesModifier
                    .padding(start = fx(24), top = 24.mpx, bottom = 24.mpx)
                    .width(sheetW)
                    .fillMaxHeight(),
            )
        }
    }

    programmeDetails?.let { ch ->
        val epgVm: tv.own.myowntv.features.epg.EpgViewModel = koinViewModel()
        tv.own.myowntv.features.epg.ProgrammeDayPage(
            vm = epgVm,
            channel = ch,
            focusStartMs = null,
            onWatch = { if (vm.watchFullscreen(ch, emptyList()) && !externalPlayerOn) onFullscreen() },
            onDismiss = { programmeDetails = null; scope.launch { restoreToContextRow() } },
        )
    }

    renaming?.let { ch ->
        TextInputDialog(
            title = stringResource(R.string.content_rename_channel),
            initial = ch.name,
            hint = stringResource(R.string.content_rename_hint),
            onConfirm = { vm.renameChannel(ch, it.takeIf { t -> t.isNotBlank() }); renaming = null },
            onDismiss = { renaming = null },
        )
    }

    offsettingEpg?.let { ch ->
        EpgOffsetDialog(
            channelName = ch.name,
            currentMinutes = vm.currentEpgShift(ch),
            globalMinutes = vm.globalEpgShift(),
            onSet = { vm.setEpgShift(ch, it) },
            onDismiss = { offsettingEpg = null },
        )
    }

    // Long-press a channel → quick actions.
    contextChannel?.let { ch ->
        ChannelContextMenu(
            channel = ch,
            title = ch.name,
            subtitle = listOfNotNull(headerLabel, vm.sourceNameOf(ch.sourceId)).joinToString(" · "),
            isFavorite = favoriteIds.contains(ch.id),
            isHomePinned = homePinnedIds.contains(ch.id),
            epgShiftMinutes = vm.currentEpgShift(ch),
            canMove = selectedKey is LiveKey.Folder || selectedKey is LiveKey.Custom || selectedKey == LiveKey.Favorites,
            isHistory = selectedKey == LiveKey.History,
            onToggleFavorite = { vm.toggleFavorite(ch); contextChannel = null },
            onToggleHomeScreen = {
                scope.launch {
                    val pinned = vm.toggleHomeScreen(ch)
                    multiviewToast.show(
                        multiviewRes.getString(
                            if (pinned) R.string.content_home_screen_added else R.string.content_home_screen_removed,
                        ),
                    )
                }
                contextChannel = null
            },
            onRename = { renaming = ch; contextChannel = null },
            onHide = { vm.hideChannel(ch); contextChannel = null },
            onEpgOffset = { offsettingEpg = ch; contextChannel = null },
            onDetails = { programmeDetails = ch; contextChannel = null },
            onPlayExternal = { vm.playExternal(ch); contextChannel = null },
            // Only offered once Multiview is switched on. Adding is silent apart from the toast: the
            // grid opens when the user plays a channel, which is the gesture that says "now".
            onAddToMultiview = if (multiviewEnabled) {
                {
                    vm.addToMultiview(ch, multiviewTiles)
                    multiviewToast.show(
                        multiviewRes.getString(
                            R.string.multiview_added,
                            vm.multiviewSelection.value.size,
                            multiviewTiles,
                        ),
                    )
                    contextChannel = null
                }
            } else {
                null
            },
            onMove = { contextChannel = null; enteringMoveMode = true; vm.enterMoveMode(ch, selectedKey) },
            onMoveToCategory = {
                moveOriginKey = when (val k = selectedKey) {
                    is LiveKey.Folder -> vm.folderKey(k.id)
                    is LiveKey.Custom -> k.id
                    LiveKey.Favorites -> ContentOrderEntity.FAV_CONTEXT
                    else -> null
                }
                moveOriginName = railItems.firstOrNull { it.key == selectedKey }?.title
                moveItem = ch
                contextChannel = null
            },
            onRemoveFromHistory = { vm.removeFromHistory(ch.id); contextChannel = null },
            onRemoveFromCategory = (selectedKey as? LiveKey.Custom)?.let { k -> { vm.removeFromCustomCategory(ch, k); contextChannel = null } },
            onDismiss = { contextChannel = null },
        )
    }

    tv.own.myowntv.ui.components.InAppToast(multiviewToast)

    // Move to… a combined category, incl. the "＋ New category…" name prompt.
    val moveTargets by vm.moveTargets.collectAsStateWithLifecycle()
    if (creatingCategory) {
        TextInputDialog(
            title = stringResource(R.string.settings_customize_new_category_title),
            hint = stringResource(R.string.settings_customize_new_category_description),
            confirmLabel = stringResource(R.string.common_create),
            allowBlank = false,
            onConfirm = { vm.createCustomCategory(it); creatingCategory = false },
            onDismiss = { creatingCategory = false },
        )
    } else {
        moveItem?.let { ch ->
            val originKey = moveOriginKey
            if (originKey != null) {
                MoveToCategoryDialog(
                    moveTargets = moveTargets.filterNot { it.id == originKey },
                    originName = moveOriginName ?: stringResource(R.string.settings_customize_this_category),
                    onNewCategory = { creatingCategory = true },
                    onMove = { targetId, keepInOrigin ->
                        vm.moveToCategory(CustomizeKeys.channel(ch), ch.id, originKey, targetId, keepInOrigin)
                        moveItem = null
                    },
                    onDismiss = { moveItem = null },
                )
            }
        }
    }

    // Move mode overlay — intercepts D-pad Up/Down/OK/Back while reordering.
    moveState?.let { ms ->
        MoveOrderOverlay(
            title = stringResource(R.string.content_reorder_channel),
            itemNames = ms.items.map { it.name },
            activeIndex = ms.activeIndex,
            onMove = vm::move,
            onCommit = vm::commitMove,
            onCancel = vm::cancelMove,
        )
    }

    // Category Move mode overlay — intercepts D-pad Up/Down/OK/Back while reordering.
    categoryMoveState?.let { ms ->
        MoveOrderOverlay(
            title = stringResource(R.string.content_move),
            itemNames = ms.items,
            activeIndex = ms.activeIndex,
            onMove = vm::moveCategory,
            onCommit = vm::commitCategoryMove,
            onCancel = vm::cancelCategoryMove,
        )
    }

    // Gone in the same frame the move list opens, so two popups never hold focus at once.
    LaunchedEffect(categoryMoveState != null) { if (categoryMoveState != null) contextCategory = null }
    contextCategory?.takeIf { categoryMoveState == null }?.let { item ->
        RailCategoryMenu(
            item, item.displayLabel(), vm::categoryRestorePlan,
            onHide = vm::hideCategory, onMove = vm::enterCategoryMoveMode, onRestore = vm::restoreCategory,
            onToggleLock = { lockVm.toggleLock(tv.own.owntv.core.model.MediaType.LIVE, it) },
            onSealedAction = { key, action -> lockVm.open(tv.own.owntv.core.model.MediaType.LIVE, key, action) },
            onClose = { contextCategory = null },
        )
    }
    tv.own.myowntv.features.categorylock.CategoryLockPrompts(
        tv.own.owntv.core.model.MediaType.LIVE,
        onAction = { key, action -> action.run(key, vm::hideCategory, vm::enterCategoryMoveMode, vm::restoreCategory) },
    ) { key, opened ->
        if (listPin.takeClose(opened)) Unit
        else if (opened && categoriesOpen) { categoriesOpen = false; focusChannelList(); listPin.openedFromCategories() }
        else railFocusRow = railItems.indexOfFirst { it.key == key }.takeIf { it >= 0 }
    }
}

/**
 * ☰ Channel options: the Stage options menu, 540 wide over the stage, above the scrim. Header
 * = the logo plate, "256 · Sky Cinema Premieren" and "category · playlist"; the actions in the groups
 * WATCH · CHANNEL · GUIDE DATA · ORGANISE with their values (EPG time offset "Global"). The order
 * and visibility are the user's (Settings › Long-press menus ›
 * Live TV); in the shipped order a group label is drawn wherever the group changes, in the user's own
 * order there are none.
 */
@Composable
private fun ChannelContextMenu(
    channel: ChannelEntity,
    title: String,
    subtitle: String?,
    isFavorite: Boolean,
    isHomePinned: Boolean,
    canMove: Boolean,
    isHistory: Boolean,
    epgShiftMinutes: Int?,
    onToggleFavorite: () -> Unit,
    onToggleHomeScreen: () -> Unit,
    onRename: () -> Unit,
    onHide: () -> Unit,
    onEpgOffset: () -> Unit,
    onPlayExternal: () -> Unit,
    // Null unless Multiview is switched on: keep this channel for the grid.
    onAddToMultiview: (() -> Unit)?,
    onMove: () -> Unit,
    // "Move to category…": send this channel into a user's combined category.
    onMoveToCategory: () -> Unit,
    onRemoveFromHistory: () -> Unit,
    // Only inside a custom category: take the channel out of that category alone.
    onRemoveFromCategory: (() -> Unit)?,
    // This channel's Program details page (a TV-only action in core's catalogue).
    onDetails: () -> Unit,
    onDismiss: () -> Unit,
) {
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    androidx.activity.compose.BackHandler { onDismiss() }
    val offsetValue = epgShiftMinutes?.let { liveEpgShiftLabel(it) } ?: stringResource(R.string.content_epg_offset_global_short)
    // Group ids = the four groups, in their order.
    val watch = 0; val chan = 1; val guide = 2; val organise = 3; val details = 4
    val actions = buildList {
        add(MenuAction("programme_details", stringResource(R.string.content_programme_details), MyOwnTVIcon.INFO, group = details, onClick = onDetails))
        if (onAddToMultiview != null) {
            add(MenuAction("add_to_multiview", stringResource(R.string.multiview_add_to), MyOwnTVIcon.MULTIVIEW, group = watch, onClick = onAddToMultiview))
        }
        add(
            MenuAction(
                "home_screen",
                stringResource(if (isHomePinned) R.string.content_remove_home_screen else R.string.content_add_home_screen),
                MyOwnTVIcon.HOME,
                group = watch,
                onClick = onToggleHomeScreen,
            ),
        )
        // Always offered, regardless of the Live TV external-player default — this is the per-channel
        // escape hatch for a stream the in-app live engine can't open.
        add(MenuAction("play_external", stringResource(R.string.content_play_external_short), MyOwnTVIcon.EXTERNAL, group = watch, onClick = onPlayExternal))
        add(MenuAction("favourite", if (isFavorite) stringResource(R.string.content_remove_favourite) else stringResource(R.string.content_add_favourite), MyOwnTVIcon.FAVORITE, group = chan, onClick = onToggleFavorite))
        add(MenuAction("rename", stringResource(R.string.content_rename), MyOwnTVIcon.PENCIL, group = chan, onClick = onRename))
        add(MenuAction("hide", stringResource(R.string.content_hide_channel), MyOwnTVIcon.EYE_OFF, group = chan, onClick = onHide))
        if (isHistory) add(MenuAction("remove_history", stringResource(R.string.content_remove_history), MyOwnTVIcon.HISTORY, group = chan, onClick = onRemoveFromHistory))
        add(MenuAction("epg_offset", stringResource(R.string.content_epg_time_offset), MyOwnTVIcon.CLOCK, group = guide, onClick = onEpgOffset))
        if (canMove) {
            add(MenuAction("move", stringResource(R.string.content_move), MyOwnTVIcon.MOVE, group = organise, onClick = onMove))
            add(MenuAction("move_to_category", stringResource(R.string.content_move_to_category), MyOwnTVIcon.FOLDER, group = organise, onClick = onMoveToCategory))
        }
        if (onRemoveFromCategory != null) {
            add(MenuAction("remove_from_category", stringResource(R.string.content_remove_from_category), MyOwnTVIcon.CLOSE, group = organise, onClick = onRemoveFromCategory))
        }
    }
    val values = mapOf("epg_offset" to offsetValue)
    val groupLabels = listOf(
        stringResource(R.string.content_menu_group_watch),
        stringResource(R.string.content_menu_group_channel),
        stringResource(R.string.content_menu_group_guide_data),
        stringResource(R.string.content_menu_group_organise),
        stringResource(R.string.content_menu_group_details),
    ).map { it.uppercase(androidx.compose.ui.text.intl.Locale.current.platformLocale) }
    tv.own.myowntv.ui.components.MyOwnTVPopup(onDismissRequest = onDismiss, stageLayout = true) {
        BoxWithConstraints(
            Modifier.fillMaxSize().background(tv.own.myowntv.ui.theme.StageColors.Scrim).longPressMenuGuard(),
        ) {
            val w = maxWidth
            tv.own.myowntv.ui.stage.StageMenu(
                Modifier
                    .padding(start = w * (930f / 1920f), top = 118.mpx, bottom = 24.mpx)
                    .width(540.mpx)
                    .verticalScroll(rememberScrollState())
                    .trapAllFocusExit()
                    .focusGroup(),
            ) {
                tv.own.myowntv.ui.stage.StageMenuHeader(
                    title = listOfNotNull(channel.number?.toString(), ProviderTags.parse(channel.name).name).joinToString(" · "),
                    subtitle = subtitle,
                    leading = {
                        LivePlate(channel.logoUrl, 70.mpx, 50.mpx)
                    },
                )
                val menu = arranged(ContentMenu.LIVE, actions)
                var previousGroup: Int? = null
                menu.actions.forEachIndexed { index, action ->
                    if (menu.headings && action.group != previousGroup) tv.own.myowntv.ui.stage.StageGroupLabel(groupLabels[action.group])
                    previousGroup = action.group
                    tv.own.myowntv.ui.stage.StageMenuItem(
                        text = action.label,
                        icon = action.icon,
                        value = values[action.key],
                        onClick = action.onClick,
                        modifier = if (index == 0) Modifier.focusRequester(focus) else Modifier,
                    )
                }
            }
        }
    }
}

/**
 * Per-channel EPG time offset. Providers often hang both the East and the West stream of a network
 * off ONE guide, so one of them runs hours out; this moves that channel's guide only. Shared with the
 * Guide screen (long-press a channel → EPG offset). The change is written on "Done", so stepping
 * through a few hours is a single edit.
 */
@Composable
internal fun EpgOffsetDialog(
    channelName: String,
    currentMinutes: Int?,
    globalMinutes: Int,
    onSet: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    var minutes by remember { mutableStateOf(currentMinutes ?: globalMinutes) }
    val valueFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); runCatching { valueFocus.requestFocus() } }
    tv.own.myowntv.ui.stage.StagePopup(
        onDismiss = onDismiss,
        title = stringResource(R.string.content_epg_time_offset),
        body = stringResource(R.string.content_epg_offset_channel_description, channelName),
        eyebrow = null,
        width = 760.mpx,
        buttons = {
            if (currentMinutes != null) tv.own.myowntv.ui.stage.StageButton(stringResource(R.string.content_epg_offset_use_global), onClick = { onSet(null); onDismiss() }, height = 56.mpx, textSize = 19)
            tv.own.myowntv.ui.stage.StageButton(stringResource(R.string.common_cancel), onClick = onDismiss, height = 56.mpx, textSize = 19)
            tv.own.myowntv.ui.stage.StageButton(stringResource(R.string.common_done), onClick = { onSet(minutes); onDismiss() }, height = 56.mpx, textSize = 19, tinted = true)
        },
    ) {
        // ◀ ▶ shift by half an hour, -12 h … +14 h; Done keeps it.
        tv.own.myowntv.ui.stage.StageSurface(
            onClick = { onSet(minutes); onDismiss() },
            radius = 18.mpx,
            focusStyle = tv.own.myowntv.ui.stage.StageFocus.FX,
            modifier = Modifier.fillMaxWidth().height(84.mpx).focusRequester(valueFocus).onPreviewKeyEvent { e ->
                val d = when (e.key) { Key.DirectionLeft -> -1; Key.DirectionRight -> 1; else -> 0 }
                if (d != 0 && e.type == KeyEventType.KeyDown) minutes = (minutes + d * 30).coerceIn(-12 * 60, 14 * 60)
                d != 0
            },
            contentAlignment = Alignment.Center,
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.mpx), verticalAlignment = Alignment.CenterVertically) {
                tv.own.myowntv.ui.components.MyOwnTVIcon(tv.own.myowntv.ui.components.MyOwnTVIcon.CHEVRON, tv.own.myowntv.ui.theme.StageColors.Muted, Modifier.size(26.mpx).graphicsLayer { rotationZ = 180f })
                Text(liveEpgShiftLabel(minutes), style = tv.own.myowntv.ui.theme.stageText(36, 800), color = tv.own.myowntv.ui.theme.stageAccent.accent, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f))
                tv.own.myowntv.ui.components.MyOwnTVIcon(tv.own.myowntv.ui.components.MyOwnTVIcon.CHEVRON, tv.own.myowntv.ui.theme.StageColors.Muted, Modifier.size(26.mpx))
            }
        }
        Text(
            stringResource(if (currentMinutes == null) R.string.content_epg_offset_following_global else R.string.content_epg_offset_channel_only, liveEpgShiftLabel(globalMinutes)),
            style = tv.own.myowntv.ui.theme.stageText(15, 500), color = tv.own.myowntv.ui.theme.StageColors.Muted, modifier = Modifier.padding(top = 12.mpx),
        )
    }
}

/** Remind me's value: "5 min before", or "At the start" when the reminder comes at the start. */
@Composable
internal fun reminderLeadText(minutes: Int): String =
    if (minutes == 0) stringResource(R.string.settings_reminder_at_start)
    else stringResource(R.string.content_remind_before, stringResource(R.string.player_duration_minutes, minutes))

@Composable
internal fun liveEpgShiftLabel(minutes: Int): String {
    if (minutes == 0) return stringResource(R.string.common_off)
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.firstLocaleCompat() ?: java.util.Locale.US
    val number = java.text.NumberFormat.getIntegerInstance(locale)
    val sign = if (minutes < 0) "−" else "+"
    val absolute = kotlin.math.abs(minutes)
    val hours = absolute / 60
    val remainder = absolute % 60
    return when {
        hours == 0 -> stringResource(R.string.content_epg_shift_minutes, sign, number.format(remainder))
        remainder == 0 -> stringResource(R.string.content_epg_shift_hours, sign, number.format(hours))
        else -> stringResource(
            R.string.content_epg_shift_hours_minutes,
            sign,
            number.format(hours),
            number.format(remainder),
        )
    }
}
