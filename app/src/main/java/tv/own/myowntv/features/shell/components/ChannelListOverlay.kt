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

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import org.koin.androidx.compose.koinViewModel
import tv.own.myowntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.i18n.HorizontalDirection
import tv.own.owntv.core.i18n.horizontalDirection
import tv.own.owntv.core.parser.XtEpgEntry
import tv.own.myowntv.features.home.relativeLastWatchedLabel
import tv.own.myowntv.features.live.LiveStageRow
import tv.own.myowntv.features.live.liveNumberWidth
import tv.own.owntv.core.live.ProviderTags
import tv.own.myowntv.features.settings.SettingsViewModel
import tv.own.myowntv.ui.components.chNavPaging
import tv.own.myowntv.ui.components.jumpLazyListTo
import tv.own.myowntv.ui.stage.PlaylistMark
import tv.own.myowntv.ui.stage.StageKeyHints
import tv.own.myowntv.ui.stage.stageGlass
import tv.own.myowntv.ui.stage.stageSelectedBar
import tv.own.myowntv.ui.theme.StageColors
import tv.own.myowntv.ui.theme.StageRadii
import tv.own.myowntv.ui.theme.gradientWash
import tv.own.myowntv.ui.theme.mpx
import tv.own.myowntv.ui.theme.stageAccent
import tv.own.myowntv.ui.theme.stageText

/**
 * A channel list that slides in over the playing video, as a Stage glass sheet 20 from the screen edge.
 * Two instances exist in the player: Left opens the playing channel's own provider category (anchored
 * left), Right opens the watch history (anchored right, [watchedAt] set). Rows are the Live TV
 * list's own [LiveStageRow]; the playing channel carries the accent bar. Browse with the D-pad, OK
 * switches channel, Back — or pushing outwards past the list edge — closes it, all without leaving
 * full-screen. The current channel is focused first; in the history list it is the newest row, so focus
 * starts on the row after it and OK there really changes channel.
 * The remote's paging keys (CH+/CH−, rewind/fast-forward by default) page through it as in the Live list.
 */
@Composable
fun ChannelListOverlay(
    channels: List<ChannelEntity>,
    currentId: String?,
    onSelect: (ChannelEntity) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    nowProgrammes: Map<String, XtEpgEntry> = emptyMap(),
    nowPlaying: Map<String, String> = emptyMap(),
    favoriteIds: Set<String> = emptySet(),
    playlistMarks: Map<String, PlaylistMark> = emptyMap(),
    title: String? = null,
    alignEnd: Boolean = false,
    showNumbers: Boolean = true,
    /** History only: channel id → when it was last watched, shown at the row's end. */
    watchedAt: Map<String, Long>? = null,
    onOpenCategories: (() -> Unit)? = null,
) {
    val history = watchedAt != null
    val playingIndex = remember(channels, currentId) { channels.indexOfFirst { it.id == currentId } }
    val currentIndex = remember(channels, playingIndex, history) {
        when {
            history && playingIndex == 0 && channels.size > 1 -> 1
            else -> playingIndex.coerceAtLeast(0)
        }
    }
    BackHandler { onDismiss() }

    val shade = tv.own.myowntv.ui.theme.stageTone(Color(2, 5, 6), Color(244, 247, 245))
    Box(
        modifier = modifier
            .fillMaxSize()
            // Darken only the side the sheet is on, so the picture stays visible beside it.
            .gradientWash(
                vertical = false,
                *(if (alignEnd) arrayOf(0.38f to Color.Transparent, 0.62f to shade.copy(alpha = 0.45f), 1f to shade.copy(alpha = 0.72f))
                else arrayOf(0f to shade.copy(alpha = 0.72f), 0.38f to shade.copy(alpha = 0.45f), 0.62f to Color.Transparent)),
            ),
    ) {
        ChannelListPanel(
            channels = channels,
            currentId = currentId,
            entryIndex = currentIndex,
            onSelect = onSelect,
            nowProgrammes = nowProgrammes,
            nowPlaying = nowPlaying,
            favoriteIds = favoriteIds,
            playlistMarks = playlistMarks,
            title = title,
            showNumbers = showNumbers,
            watchedAt = watchedAt,
            outward = if (alignEnd) HorizontalDirection.END else HorizontalDirection.START,
            // Pushing outward from the Start panel opens categories when they are wired; pushing outward
            // from the End panel closes it. Back always closes.
            onOutward = if (!alignEnd && onOpenCategories != null) onOpenCategories else onDismiss,
            // Outward is ◀ on the left sheet, ▶ on the right one (Multiview's picker goes back to categories).
            outwardHint = when {
                onOpenCategories != null -> (if (alignEnd) "▶" else "◀") to stringResource(R.string.content_category_browser_title)
                alignEnd -> "▶" to stringResource(R.string.content_close)
                else -> null
            },
            modifier = Modifier
                .align(if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(horizontal = 20.mpx, vertical = 22.mpx)
                .fillMaxHeight()
                .width(if (history) 760.mpx else 720.mpx),
        )
    }
}

/**
 * The glass list itself — heading, "Now playing" line, the rows and the key hints — shared by
 * [ChannelListOverlay] and the two-pane [ChannelBrowserOverlay]. [entryIndex] is the row focus lands on;
 * with [autoFocus] the list takes focus as it opens, otherwise it waits for [focusTarget] to be asked
 * and scrolls back to [entryIndex] whenever [channels] changes (a new category on the right).
 * Pushing towards [outward] calls [onOutward]; the caller owns Back.
 */
@Composable
internal fun ChannelListPanel(
    channels: List<ChannelEntity>,
    currentId: String?,
    entryIndex: Int,
    onSelect: (ChannelEntity) -> Unit,
    outward: HorizontalDirection,
    onOutward: () -> Unit,
    outwardHint: Pair<String, String>?,
    modifier: Modifier = Modifier,
    nowProgrammes: Map<String, XtEpgEntry> = emptyMap(),
    nowPlaying: Map<String, String> = emptyMap(),
    favoriteIds: Set<String> = emptySet(),
    playlistMarks: Map<String, PlaylistMark> = emptyMap(),
    title: String? = null,
    showNumbers: Boolean = true,
    watchedAt: Map<String, Long>? = null,
    focusTarget: FocusRequester = remember { FocusRequester() },
    autoFocus: Boolean = true,
    onFocusChannel: (ChannelEntity) -> Unit = {},
) {
    val layoutDirection = LocalLayoutDirection.current
    val history = watchedAt != null
    val playingIndex = remember(channels, currentId) { channels.indexOfFirst { it.id == currentId } }
    // One number column for the overlay too, sized by the longest number in this list.
    val numberFontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    val numberWidth = remember(channels, numberFontScale) { liveNumberWidth(channels.map { it.number }, numberFontScale) }
    val listState = rememberLazyListState()
    // The row that owns [focusTarget]: the entry row at first, then wherever paging jumped to.
    var targetIndex by remember(channels, entryIndex) { mutableIntStateOf(entryIndex) }
    var focusedIndex by remember(channels, entryIndex) { mutableIntStateOf(entryIndex) }
    var listFocused by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val settingsVm: SettingsViewModel = koinViewModel()
    val chNavEnabled by settingsVm.chNavEnabled.collectAsStateWithLifecycle()
    val chNavUpSkip by settingsVm.chNavUpSkip.collectAsStateWithLifecycle()
    val chNavDownSkip by settingsVm.chNavDownSkip.collectAsStateWithLifecycle()
    LaunchedEffect(if (autoFocus) Unit else channels) {
        runCatching { listState.scrollToItem(entryIndex) }
        if (autoFocus) runCatching { focusTarget.requestFocus() }
    }

    val accent = stageAccent.accent
    Column(
        modifier = modifier
            .stageGlass(StageRadii.Sheet, overContent = true)
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key.horizontalDirection(layoutDirection) == outward) {
                    onOutward()
                    true
                } else false
            }
            .padding(horizontal = 16.mpx, vertical = 26.mpx),
    ) {
        // "Catch-up · 978 channels        Live TV", as the Live TV header and category sheet have it.
        Row(
            Modifier.fillMaxWidth().padding(start = 14.mpx, end = 14.mpx),
            horizontalArrangement = Arrangement.spacedBy(16.mpx),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.mpx),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title ?: stringResource(R.string.content_channel_overlay_title),
                    style = stageText(26, 800), color = StageColors.Text,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    "· " + pluralStringResource(R.plurals.content_live_channel_count, channels.size, channels.size),
                    style = stageText(18, 500), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Text(stringResource(R.string.common_nav_live_tv), style = stageText(17, 400), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val playing = channels.getOrNull(playingIndex)
        val subLine = when {
            history -> stringResource(R.string.content_history_overlay_hint)
            playing != null -> listOfNotNull(
                stringResource(R.string.shell_now_playing),
                playing.number?.toString()?.takeIf { showNumbers },
                ProviderTags.parse(playing.name).name,
            ).joinToString(" · ")
            else -> null
        }
        subLine?.let {
            Text(
                it, style = stageText(16, 400), color = StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 14.mpx, end = 14.mpx, top = 4.mpx, bottom = 12.mpx),
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .onFocusChanged { listFocused = it.hasFocus }
                .chNavPaging(
                    enabled = chNavEnabled,
                    upSkip = chNavUpSkip,
                    downSkip = chNavDownSkip,
                    isFocused = { listFocused },
                    lastIndex = { channels.lastIndex },
                    currentTargetIndex = { focusedIndex },
                    onJumpToIndex = { idx ->
                        targetIndex = idx
                        scope.jumpLazyListTo(listState, idx) { focusTarget.requestFocus() }
                    },
                ),
            // Room for the focus ring, which the list would otherwise clip.
            contentPadding = PaddingValues(horizontal = 4.mpx, vertical = 4.mpx),
            verticalArrangement = Arrangement.spacedBy(2.mpx),
        ) {
            itemsIndexed(channels, key = { _, ch -> ch.id }) { index, ch ->
                val isPlaying = index == playingIndex
                val nowMs = remember { System.currentTimeMillis() }
                LiveStageRow(
                    channel = ch,
                    name = ProviderTags.parse(ch.name),
                    now = nowProgrammes[ch.id],
                    nowTitle = nowPlaying[ch.id],
                    isFavorite = ch.id in favoriteIds,
                    showNumber = showNumbers,
                    numberWidth = numberWidth,
                    onFocus = { focusedIndex = index; onFocusChannel(ch) },
                    onClick = { onSelect(ch) },
                    onLongClick = {},
                    trailing = watchedAt?.let { times ->
                        {
                            Text(
                                if (isPlaying) stringResource(R.string.shell_now_playing)
                                else times[ch.id]?.let { relativeLastWatchedLabel(it, nowMs) }.orEmpty(),
                                style = stageText(15, if (isPlaying) 700 else 500),
                                color = if (isPlaying) accent else StageColors.Dim,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    modifier = Modifier
                        .then(if (isPlaying) Modifier.stageSelectedBar(accent, 22.mpx) else Modifier)
                        .then(if (index == targetIndex) Modifier.focusRequester(focusTarget) else Modifier),
                )
            }
        }
        StageKeyHints(
            listOfNotNull(
                stringResource(R.string.common_ok) to stringResource(R.string.content_key_watch),
                outwardHint,
                "CH±" to stringResource(R.string.content_key_page),
                stringResource(R.string.common_back) to stringResource(R.string.content_close),
            ),
            textSize = 16,
            modifier = Modifier.padding(start = 14.mpx, top = 14.mpx),
        )
    }
}
