package tv.own.myowntv.features.search
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
import tv.own.owntv.core.profile.*
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

import tv.own.myowntv.ui.theme.stageTone
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import tv.own.myowntv.R
import tv.own.owntv.core.content.SearchIntent
import tv.own.owntv.core.content.SearchKind
import tv.own.owntv.core.content.ProgrammeSearchResult
import tv.own.owntv.core.content.SearchResults
import tv.own.owntv.core.database.dao.ChannelSearchResult
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.epg.displayLogoUrl
import tv.own.myowntv.features.live.LivePlate
import tv.own.myowntv.features.live.LiveStagePane
import tv.own.myowntv.features.live.LiveViewModel
import tv.own.owntv.core.live.ProviderTags
import tv.own.myowntv.features.settings.dotSeparator
import tv.own.myowntv.ui.components.MyOwnTVIcon
import tv.own.myowntv.ui.format.localizedInteger
import tv.own.myowntv.ui.stage.PlaylistMark
import tv.own.myowntv.ui.stage.StageKeyHints
import tv.own.myowntv.ui.stage.StagePlaylistMark
import tv.own.myowntv.ui.stage.StageRow
import tv.own.myowntv.ui.stage.StageSurface
import tv.own.myowntv.ui.stage.StageTab
import tv.own.myowntv.ui.stage.StageTag
import tv.own.myowntv.ui.stage.StageTool
import tv.own.myowntv.ui.stage.stageGlass
import tv.own.myowntv.ui.theme.StageColors
import tv.own.myowntv.ui.theme.mpx
import tv.own.myowntv.ui.theme.mpxSp
import tv.own.myowntv.ui.theme.stageAccent
import tv.own.myowntv.ui.theme.stageText
import tv.own.myowntv.ui.format.rememberSystemTimeFormatter

@Composable
private fun SearchIntent.displayLabel(): String = stringResource(
    when (this) {
        SearchIntent.CONTINUE -> R.string.search_continue
        SearchIntent.CHANNELS -> R.string.search_channels
    },
)

@Composable
private fun SearchKind.label(): String = stringResource(
    when (this) {
        SearchKind.LIVE -> R.string.common_nav_live_tv
        SearchKind.TV_SHOWS -> R.string.search_on_tv
    },
)

/** The kinds the live-only screen offers as filter chips. */
private val searchableKinds = listOf(SearchKind.LIVE, SearchKind.TV_SHOWS)

/** One line of the results column: a group heading, a result, or the "All n channels" row. */
private sealed interface Entry {
    val key: String
    data class Heading(val text: String, override val key: String) : Entry
    data class Channel(val row: ChannelSearchResult) : Entry { override val key = "c${row.channel.id}" }
    data class Programme(val found: ProgrammeSearchResult) : Entry {
        override val key = "p${found.programme.id}-${found.channel.id}"
    }
    data class AllChannels(val count: Int) : Entry { override val key = "all-channels" }
}

/** With several kinds shown: the first four channels, then the "All n channels" row, so the rest stay on screen. */
private const val ALL_TAB_CHANNELS = 4

/**
 * Global search in the Stage design: the title band, the field, tabs over grouped
 * results on the left, and on the right the focused result — Live TV's own preview pane for a channel.
 * Search only shows: OK goes to the channel, where tuning and every option already live.
 * Back clears the search first, then leaves.
 */
@Composable
fun SearchScreen(
    /** OK on a result goes to it: the channel in Live TV. */
    onGoToChannel: (ChannelEntity) -> Unit,
    onChildFocused: () -> Unit,
    /** The channel panel is Live TV's own: its preview engine, guide data and Preview setting. */
    liveVm: LiveViewModel,
    /** False while the fullscreen or mini player owns the picture. */
    previewEnabled: Boolean,
    vm: SearchViewModel = koinViewModel(),
    returnToHomeOnBack: Boolean = false,
    onReturnToHome: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val curated by vm.curatedResults.collectAsStateWithLifecycle()
    val intent by vm.intent.collectAsStateWithLifecycle()
    val recent by vm.recentSearches.collectAsStateWithLifecycle()
    val kinds by vm.kinds.collectAsStateWithLifecycle()
    val marks by vm.playlistMarks.collectAsStateWithLifecycle()
    val nowPlaying by liveVm.nowPlaying.collectAsStateWithLifecycle()
    val nowNext by liveVm.nowNext.collectAsStateWithLifecycle()
    val livePreviewSetting by liveVm.livePreviewEnabled.collectAsStateWithLifecycle()
    val singleSessionBlocked by liveVm.previewBlockedSingleSession.collectAsStateWithLifecycle()

    val fieldFocus = remember { FocusRequester() }
    val filterFocus = remember { searchableKinds.associateWith { FocusRequester() } }
    val jumpFocus = remember { FocusRequester() }
    val rowFocus = remember { HashMap<String, FocusRequester>() }
    fun requesterFor(key: String) = rowFocus.getOrPut(key) { FocusRequester() }

    val searching = query.trim().length >= 2
    val showingResults = searching || intent != null
    val shown = if (searching) results.only(kinds) else curated
    // The screen is live TV only: the movie/series lists core's reader still fills are never shown.
    val liveResults = shown.channels.isNotEmpty() || shown.programmes.isNotEmpty()
    // One filter button on lists that kind alone, uncapped; otherwise the kinds are grouped under headings.
    val singleKind = searching && kinds.size == 1
    val entries = searchEntries(shown, grouped = !singleKind, capChannels = searching && !singleKind)
    // ▼ from the field and ▲ out of the results land on the first button that is on, else the first.
    val filterEntry = filterFocus.getValue(kinds.filter { it in searchableKinds }.minOrNull() ?: searchableKinds.first())
    var filterFocused by remember { mutableStateOf<SearchKind?>(null) }

    // The focused row drives the right side; nothing focused yet = the first result.
    var focusedKey by remember { mutableStateOf<String?>(null) }
    val active = entries.firstOrNull { it.key == focusedKey && it.isResult() } ?: entries.firstOrNull { it.isResult() }
    // The preview plays only while the user is on a channel row (or its schedule), never just because
    // a channel is the first result under the field.
    var listHasFocus by remember { mutableStateOf(false) }
    val activeChannel = active?.channel()?.takeIf { showingResults && listHasFocus }
    val previewOn = previewEnabled && livePreviewSetting

    // A channel shows Live TV's preview: the same guide lookup, and the same 700 ms settle before playing.
    LaunchedEffect(activeChannel?.id, previewOn) {
        val ch = activeChannel
        if (ch == null) { liveVm.stopPreview(); return@LaunchedEffect }
        liveVm.onChannelFocused(ch)
        if (!previewOn) return@LaunchedEffect
        delay(700)
        liveVm.playPreview(ch)
    }
    // The "now on" part of a channel row's line (the cache the Live TV list fills).
    val channelRows = shown.channels.map { it.channel }
    val formatTime = rememberSystemTimeFormatter()
    LaunchedEffect(channelRows.map { it.id }) { liveVm.ensureNowPlaying(channelRows) }

    // Back clears an active query/intent (returning to the launcher) before leaving the screen.
    BackHandler(enabled = returnToHomeOnBack || query.isNotBlank() || intent != null) {
        vm.setQuery("")
        vm.setIntent(null)
        vm.clearKinds()
        if (returnToHomeOnBack) onReturnToHome()
        else runCatching { fieldFocus.requestFocus() }
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Back from a "Go to" lands on the row it left from; otherwise the field.
    LaunchedEffect(Unit) {
        val back = vm.lastFocusKey
        if (back != null) {
            repeat(30) {
                val idx = entries.indexOfFirst { it.key == back }
                if (idx >= 0) {
                    runCatching { listState.scrollToItem(idx) }
                    withFrameNanos { }
                    if (runCatching { requesterFor(back).requestFocus() }.getOrDefault(false)) return@LaunchedEffect
                }
                delay(100)
            }
        }
        runCatching { fieldFocus.requestFocus() }
    }
    // An intent's list replaces the launcher under the cursor: land on its first row.
    LaunchedEffect(intent, entries.isNotEmpty()) {
        if (intent != null && !searching) entries.firstOrNull { it !is Entry.Heading }?.let { e ->
            withFrameNanos { }
            runCatching { requesterFor(e.key).requestFocus() }
        }
    }

    val goTo: (Entry) -> Unit = { e ->
        vm.lastFocusKey = e.key
        vm.rememberCurrentQuery()
        when (e) {
            is Entry.Channel -> onGoToChannel(e.row.channel)
            is Entry.Programme -> onGoToChannel(e.found.channel)
            else -> Unit
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().onFocusChanged { if (it.hasFocus) onChildFocused() }) {
        val screenW = maxWidth
        fun fx(px: Int) = screenW * (px / 1920f)
        val listX = fx(64)
        val listW = fx(846)
        val panelX = fx(944)
        val panelW = fx(912)

        // Title band: "Search" + "“sky” · 12 results".
        val titleModifier = Modifier.padding(start = fx(84), top = 52.mpx).width(fx(826))
        if (showingResults) {
            val total = shown.channels.size + shown.programmes.size
            Column(titleModifier) {
                Text(
                    stringResource(R.string.search_title),
                    style = stageText(46, 800, (-1).mpxSp), color = StageColors.Text,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                val category = if (searching) stringResource(R.string.search_query_quoted, query.trim()) else intent?.displayLabel().orEmpty()
                Text(
                    category + dotSeparator() + pluralStringResource(R.plurals.search_result_count, total, total),
                    style = stageText(19, 500), color = StageColors.Muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.mpx),
                )
            }
        } else {
            Text(stringResource(R.string.search_title), style = stageText(46, 800, (-1).mpxSp), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = titleModifier)
        }
        SearchFieldRow(
            query = query,
            onQueryChange = vm::setQuery,
            fieldFocus = fieldFocus,
            // ▼ lands on a filter button that is on, not whichever one is nearest below.
            focusDown = if (searching) filterEntry else if (!showingResults) jumpFocus else FocusRequester.Default,
            modifier = Modifier.padding(start = listX, top = 118.mpx).width(listW),
        )

        if (!showingResults) {
            Launcher(
                firstFocus = jumpFocus,
                recent = recent,
                onIntent = vm::setIntent,
                onRecent = vm::setQuery,
                onClearRecent = vm::clearRecentSearches,
                modifier = Modifier.padding(start = listX, top = 192.mpx).width(listW),
            )
            LauncherCard(Modifier.padding(start = panelX, top = 200.mpx).width(panelW))
        } else if (!liveResults) {
            NothingFound(
                title = if (searching) stringResource(R.string.search_nothing_found, query.trim())
                else stringResource(R.string.search_nothing_in_list, intent?.displayLabel().orEmpty()),
                modifier = Modifier.padding(start = fx(84), top = if (searching) 300.mpx else 230.mpx).width(fx(820)),
            )
        }

        if (searching) {
            // Filter buttons: OK switches one on or off, several can be on, none on shows everything.
            Row(Modifier.padding(start = fx(84) - 12.mpx, top = 192.mpx), horizontalArrangement = Arrangement.spacedBy(16.mpx)) {
                searchableKinds.forEach { k ->
                    StageTab(
                        label = k.label(),
                        count = localizedInteger(results.count(k)),
                        selected = k in kinds,
                        onClick = { vm.toggleKind(k) },
                        modifier = Modifier
                            .focusRequester(filterFocus.getValue(k))
                            .onFocusChanged { if (it.isFocused) filterFocused = k else if (filterFocused == k) filterFocused = null },
                    )
                }
            }
        }

        if (showingResults && liveResults) {
            val sep = dotSeparator()
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .padding(start = listX, top = if (searching) 252.mpx else 192.mpx, bottom = 80.mpx)
                    .width(listW)
                    .fillMaxHeight()
                    .onFocusChanged { listHasFocus = it.hasFocus }
                    // ▲ out of the results lands on the first button that is on, not the one nearest the row.
                    .focusProperties {
                        onExit = {
                            if (searching && requestedFocusDirection == FocusDirection.Up) {
                                cancelFocusChange()
                                runCatching { filterEntry.requestFocus() }
                            }
                        }
                    }
                    .focusGroup(),
                verticalArrangement = Arrangement.spacedBy(8.mpx),
            ) {
                itemsIndexed(entries, key = { _, e -> e.key }) { i, e ->
                    val rowModifier = Modifier
                        .focusRequester(requesterFor(e.key))
                        .onFocusChanged { if (it.isFocused) focusedKey = e.key }
                    when (e) {
                        is Entry.Heading -> GroupHeading(e.text, Modifier.padding(top = if (i == 0) 0.mpx else 18.mpx))
                        is Entry.AllChannels -> StageTool(
                            text = pluralStringResource(R.plurals.search_all_channels, e.count, e.count),
                            icon = MyOwnTVIcon.LIST,
                            // The first channel keeps its row (same key) with only Live TV on, so focus moves
                            // onto it first rather than up onto the filter buttons.
                            onClick = {
                                scope.launch {
                                    shown.channels.firstOrNull()?.let { first ->
                                        val key = Entry.Channel(first).key
                                        runCatching { listState.scrollToItem(entries.indexOfFirst { it.key == key }.coerceAtLeast(0)) }
                                        withFrameNanos { }
                                        runCatching { requesterFor(key).requestFocus() }
                                    }
                                    vm.showOnly(SearchKind.LIVE)
                                }
                            },
                            modifier = rowModifier.padding(top = 10.mpx),
                        )
                        is Entry.Channel -> {
                            val ch = e.row.channel
                            val name = ProviderTags.parse(ch.name)
                            val line = listOfNotNull(
                                e.row.categoryName?.takeIf { it.isNotBlank() }?.let { ProviderTags.parse(it).name },
                                ch.number?.toString(),
                                nowPlaying[ch.id],
                            ).joinToString(sep)
                            SearchRow(
                                title = name.name, line = AnnotatedString(line), tags = name.tags, mark = marks[ch.sourceId],
                                onClick = { goTo(e) },
                                leading = { LivePlate(ch.displayLogoUrl, 76.mpx, 54.mpx) },
                                modifier = rowModifier,
                            )
                        }
                        is Entry.Programme -> {
                            val ch = e.found.channel
                            val p = e.found.programme
                            SearchRow(
                                title = p.title,
                                line = AnnotatedString(
                                    ProviderTags.parse(ch.name).name + sep +
                                        stringResource(R.string.content_live_time_range_plain, formatTime(p.startMs), formatTime(p.stopMs)),
                                ),
                                tags = emptyList(), mark = marks[ch.sourceId],
                                onClick = { goTo(e) },
                                leading = { LivePlate(ch.displayLogoUrl, 76.mpx, 54.mpx) },
                                modifier = rowModifier,
                            )
                        }
                    }
                }
            }

            // The right side, in one slot (x 944, w 912): Live TV's preview pane for the focused channel.
            val panelModifier = Modifier.padding(start = panelX, top = 120.mpx).width(panelW)
            val activeCh = active?.channel()
            if (activeCh != null) {
                LiveStagePane(
                    channel = activeCh,
                    channelName = ProviderTags.parse(activeCh.name).name,
                    nowNext = nowNext,
                    previewEngine = liveVm.previewEngine,
                    showVideo = previewOn,
                    singleSessionBlocked = singleSessionBlocked,
                    modifier = panelModifier,
                )
            }
        }

        // Key hints, bottom left.
        val hints = buildList {
            val onFilter = filterFocused
            if (onFilter != null) {
                add(stringResource(R.string.common_ok) to stringResource(if (onFilter in kinds) R.string.content_key_unselect else R.string.content_key_select))
            } else if (showingResults && liveResults) {
                add(stringResource(R.string.common_ok) to stringResource(R.string.search_go_to_channel))
            }
            if (showingResults) {
                add("▲" to stringResource(R.string.common_search))
                add(stringResource(R.string.common_back) to stringResource(R.string.common_clear))
            } else {
                add(stringResource(R.string.common_ok) to stringResource(R.string.content_key_select))
                add(stringResource(R.string.common_back) to stringResource(R.string.content_key_menu))
            }
        }
        StageKeyHints(hints, Modifier.align(Alignment.BottomStart).padding(start = fx(84), bottom = 40.mpx))
    }
}

private fun Entry.isResult() = this is Entry.Channel || this is Entry.Programme

/** The channel a row is about — its own, or the one showing the programme. */
private fun Entry.channel(): ChannelEntity? = when (this) {
    is Entry.Channel -> row.channel
    is Entry.Programme -> found.channel
    else -> null
}

/** The rows in display order; [grouped] puts each kind under its heading, else one flat list. */
@Composable
private fun searchEntries(results: SearchResults, grouped: Boolean, capChannels: Boolean): List<Entry> {
    val live = stringResource(R.string.common_nav_live_tv)
    val onTv = stringResource(R.string.search_on_tv)
    val sep = dotSeparator()
    return remember(results, grouped, capChannels, live, onTv, sep) {
        val locale = java.util.Locale.getDefault()
        fun heading(label: String, n: Int, key: String) = Entry.Heading(label.uppercase(locale) + sep + n.toString(), key)
        if (!grouped) {
            results.channels.map { Entry.Channel(it) } + results.programmes.map { Entry.Programme(it) }
        } else {
            buildList {
                if (results.channels.isNotEmpty()) {
                    add(heading(live, results.channels.size, "h-live"))
                    val cap = capChannels && results.channels.size > ALL_TAB_CHANNELS
                    (if (cap) results.channels.take(ALL_TAB_CHANNELS) else results.channels).forEach { add(Entry.Channel(it)) }
                    if (cap) add(Entry.AllChannels(results.channels.size))
                }
                if (results.programmes.isNotEmpty()) {
                    add(heading(onTv, results.programmes.size, "h-ontv"))
                    results.programmes.forEach { add(Entry.Programme(it)) }
                }
            }
        }
    }
}

/** `.sgh`: "LIVE TV · 9", 12.5/800 with wide tracking, dim, 20 in from the rows. */
@Composable
private fun GroupHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = stageText(12.5f, 800, 0.13.em), color = StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(start = 20.mpx),
    )
}

/** `.srow`: 84 high, 18 padding and gap; art, title 21/700, a 16 px muted line, then tags and the playlist mark. Focused = FX. */
@Composable
private fun SearchRow(
    title: String,
    line: AnnotatedString,
    tags: List<String>,
    mark: PlaylistMark?,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Hold OK does nothing here: the item's options live where OK takes you.
    StageRow(onClick = onClick, onLongClick = {}, height = 84.mpx, horizontalPadding = 18.mpx, gap = 18.mpx, modifier = modifier.fillMaxWidth()) { focused ->
        leading()
        Column(Modifier.weight(1f)) {
            Text(title, style = stageText(21, 700), color = if (focused) StageColors.FxText else StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.mpx))
            Text(line, style = stageText(16, 400), color = if (focused) StageColors.FxLine else StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.mpx), verticalAlignment = Alignment.CenterVertically) {
            tags.forEach { StageTag(it) }
            mark?.let { StagePlaylistMark(it) }
        }
    }
}

/** The empty page: JUMP TO (Continue · Channels), RECENT SEARCHES as rows, then Clear. */
@Composable
private fun Launcher(
    firstFocus: FocusRequester,
    recent: List<String>,
    onIntent: (SearchIntent) -> Unit,
    onRecent: (String) -> Unit,
    onClearRecent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    Column(modifier.focusGroup()) {
        GroupHeading(stringResource(R.string.search_jump_to).uppercase(locale), Modifier.padding(top = 8.mpx, bottom = 10.mpx))
        Row(horizontalArrangement = Arrangement.spacedBy(10.mpx)) {
            SearchIntent.entries.forEach { i ->
                val icon = when (i) {
                    SearchIntent.CONTINUE -> MyOwnTVIcon.PLAY
                    SearchIntent.CHANNELS -> MyOwnTVIcon.LIVE_TV
                }
                StageTool(
                    text = i.displayLabel(), icon = icon, onClick = { onIntent(i) },
                    modifier = if (i == SearchIntent.entries.first()) Modifier.focusRequester(firstFocus) else Modifier,
                )
            }
        }
        if (recent.isNotEmpty()) {
            GroupHeading(stringResource(R.string.search_recent).uppercase(locale), Modifier.padding(top = 46.mpx, bottom = 4.mpx))
            Column(verticalArrangement = Arrangement.spacedBy(6.mpx)) {
                recent.forEach { term -> RecentRow(term) { onRecent(term) } }
            }
            StageTool(
                text = stringResource(R.string.search_clear_recent), icon = MyOwnTVIcon.TRASH, onClick = onClearRecent,
                modifier = Modifier.padding(top = 8.mpx),
            )
        }
    }
}

/** `.fcat`: a 52-high flat row, the history glyph and the term in 18.5/600. Focused = FILLED. */
@Composable
private fun RecentRow(term: String, onClick: () -> Unit) {
    val a = stageAccent
    StageSurface(onClick = onClick, radius = 15.mpx, modifier = Modifier.fillMaxWidth().height(52.mpx)) { focused ->
        Row(
            Modifier.padding(horizontal = 14.mpx),
            horizontalArrangement = Arrangement.spacedBy(12.mpx),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MyOwnTVIcon(MyOwnTVIcon.HISTORY, if (focused) a.onAccent else StageColors.Muted, Modifier.size(20.mpx))
            Text(term, style = stageText(18.5f, 600), color = if (focused) a.onAccent else stageTone(Color(0xFFCDD7D3), StageColors.ItemText), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The empty page's glass card: what Search does, and where a result takes you. */
@Composable
private fun LauncherCard(modifier: Modifier = Modifier) {
    Column(modifier.stageGlass(30.mpx).padding(horizontal = 38.mpx, vertical = 34.mpx)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.mpx), verticalAlignment = Alignment.CenterVertically) {
            MyOwnTVIcon(MyOwnTVIcon.SEARCH, stageAccent.accent, Modifier.size(20.mpx))
            Text(stringResource(R.string.search_title), style = stageText(19, 700), color = stageAccent.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            stringResource(R.string.search_card_title), style = stageText(30, 800), color = StageColors.Text,
            modifier = Modifier.padding(top = 14.mpx, bottom = 10.mpx),
        )
        Text(stringResource(R.string.search_card_body), style = stageText(19, 400).copy(lineHeight = (19 * 1.55f).mpxSp), color = StageColors.Muted)
    }
}

/** No results: "Nothing found for “qwzx”" and what search looks in. */
@Composable
private fun NothingFound(title: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = stageText(30, 800), color = StageColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            stringResource(R.string.search_nothing_found_body), style = stageText(19, 400).copy(lineHeight = (19 * 1.55f).mpxSp),
            color = StageColors.Muted, modifier = Modifier.padding(top = 10.mpx),
        )
    }
}
