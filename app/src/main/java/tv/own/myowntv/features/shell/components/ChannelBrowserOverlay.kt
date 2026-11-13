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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import tv.own.myowntv.R
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.i18n.HorizontalDirection
import tv.own.owntv.core.i18n.horizontalDirection
import tv.own.owntv.core.live.LiveKey
import tv.own.owntv.core.parser.XtEpgEntry
import tv.own.myowntv.features.live.LiveCategories
import tv.own.myowntv.features.live.LiveRailItem
import tv.own.myowntv.features.live.liveCategoryEntries
import tv.own.myowntv.features.settings.SettingsViewModel
import tv.own.myowntv.ui.components.chNavPaging
import tv.own.myowntv.ui.stage.PlaylistMark
import tv.own.myowntv.ui.theme.gradientWash
import tv.own.myowntv.ui.theme.mpx

/**
 * The full-screen channel browser, opened by a second Left in the in-player channel list: Live TV's
 * category sheet on the left, the chosen category's channels on the right, over the playing video.
 *
 * Focus starts on the categories, on the category of the list the player zaps in. There ▲▼ move, OK
 * shows that category's channels on the right (focus stays on the categories) and ▶ goes into the
 * list. In the list ▲▼ move, OK plays the channel and closes, ◀ goes back to the categories. Back
 * closes both panes from either one. Browsing never tunes: only the channel finally picked does, and
 * only then does CH± take the list it was picked from.
 *
 * Widths are the category sheet's 440 and the channel list's 720; on a narrower screen the list gives
 * way first, down to 560. One wash darkens the side the panes are on.
 */
@Composable
fun ChannelBrowserOverlay(
    railItems: List<LiveRailItem>,
    railCounts: Map<LiveKey, Int>,
    shownKey: LiveKey?,
    channels: List<ChannelEntity>,
    channelsTitle: String,
    playingId: String?,
    entryChannelId: () -> String?,
    onPickCategory: (LiveKey) -> Unit,
    onFocusChannel: (ChannelEntity) -> Unit,
    onWatch: (ChannelEntity) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    nowProgrammes: Map<String, XtEpgEntry> = emptyMap(),
    nowPlaying: Map<String, String> = emptyMap(),
    favoriteIds: Set<String> = emptySet(),
    playlistMarks: Map<String, PlaylistMark> = emptyMap(),
    showNumbers: Boolean = true,
) {
    val layoutDirection = LocalLayoutDirection.current
    val (entries, groupsHeading) = liveCategoryEntries(railItems, railCounts)
    val selectedIndex = remember(entries, shownKey) { entries.indexOfFirst { it.item.key == shownKey }.coerceAtLeast(0) }
    val categoryListState = rememberLazyListState()
    val categoryFocus = remember { FocusRequester() }
    val channelFocus = remember { FocusRequester() }
    var focusRow by remember { mutableStateOf<Int?>(null) }
    var focusedCategory by remember(selectedIndex) { mutableIntStateOf(selectedIndex) }
    var categoriesFocused by remember { mutableStateOf(false) }
    var typing by remember { mutableStateOf(false) }
    // The row the channel list is entered on — the one last focused in this category, else the playing one.
    val entryIndex = remember(channels) {
        val id = entryChannelId()
        channels.indexOfFirst { it.id == id }.coerceAtLeast(0)
    }
    val settingsVm: SettingsViewModel = koinViewModel()
    val chNavEnabled by settingsVm.chNavEnabled.collectAsStateWithLifecycle()
    val chNavUpSkip by settingsVm.chNavUpSkip.collectAsStateWithLifecycle()
    val chNavDownSkip by settingsVm.chNavDownSkip.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        runCatching { categoryFocus.requestFocus() }
        withFrameNanos { }
        if (selectedIndex < FIRST_SCREEN_ROWS) runCatching { categoryListState.scrollToItem(0) }
    }
    BackHandler { onDismiss() }

    val shade = tv.own.myowntv.ui.theme.stageTone(Color(2, 5, 6), Color(244, 247, 245))
    Box(
        modifier
            .fillMaxSize()
            .gradientWash(false, 0f to shade.copy(alpha = 0.78f), 0.55f to shade.copy(alpha = 0.5f), 0.8f to Color.Transparent),
    ) {
        Row(Modifier.align(Alignment.CenterStart).fillMaxHeight().padding(horizontal = 20.mpx, vertical = 22.mpx)) {
            LiveCategories(
                entries = entries,
                selectedIndex = selectedIndex,
                groupsHeading = groupsHeading,
                sheet = true,
                listState = categoryListState,
                onSelect = { onPickCategory(entries[it].item.key) },
                onLongSelect = {},
                onNavigateRight = { runCatching { channelFocus.requestFocus() } },
                focusRequester = categoryFocus,
                focusRowIndex = focusRow,
                onRowFocused = { focusRow = null },
                onRowFocus = { focusedCategory = it },
                onSearchEditingChange = { typing = it },
                modifier = Modifier
                    .width(440.mpx)
                    .fillMaxHeight()
                    .onFocusChanged { categoriesFocused = it.hasFocus }
                    .onPreviewKeyEvent { e ->
                        if (typing || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (e.key.horizontalDirection(layoutDirection)) {
                            // Nothing lies beyond the categories: Back is the way out.
                            HorizontalDirection.START -> true
                            HorizontalDirection.END -> { runCatching { channelFocus.requestFocus() }; true }
                            else -> false
                        }
                    }
                    .chNavPaging(
                        enabled = chNavEnabled,
                        upSkip = chNavUpSkip,
                        downSkip = chNavDownSkip,
                        isFocused = { categoriesFocused },
                        lastIndex = { entries.lastIndex },
                        currentTargetIndex = { focusedCategory },
                        onJumpToIndex = { focusRow = it },
                    ),
            )
            Spacer(Modifier.width(16.mpx))
            ChannelListPanel(
                channels = channels,
                currentId = playingId,
                entryIndex = entryIndex,
                onSelect = onWatch,
                outward = HorizontalDirection.START,
                onOutward = { runCatching { categoryFocus.requestFocus() } },
                outwardHint = "◀" to stringResource(R.string.content_category_browser_title),
                nowProgrammes = nowProgrammes,
                nowPlaying = nowPlaying,
                favoriteIds = favoriteIds,
                playlistMarks = playlistMarks,
                title = channelsTitle,
                showNumbers = showNumbers,
                focusTarget = channelFocus,
                autoFocus = false,
                onFocusChannel = onFocusChannel,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .widthIn(min = 560.mpx, max = 720.mpx)
                    .fillMaxHeight()
                    // ▶ has nowhere to go from the list; kept here so focus cannot leave the panes.
                    .onPreviewKeyEvent { e ->
                        e.type == KeyEventType.KeyDown && e.key.horizontalDirection(layoutDirection) == HorizontalDirection.END
                    },
            )
        }
    }
}

/** Rows that fit on the sheet's first screen below the search field, the fixed entries included. */
private const val FIRST_SCREEN_ROWS = 10
