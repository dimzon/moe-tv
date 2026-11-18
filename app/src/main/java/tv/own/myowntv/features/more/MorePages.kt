package tv.own.myowntv.features.more
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

import tv.own.myowntv.ui.theme.stageTone
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import tv.own.myowntv.BuildConfig
import tv.own.myowntv.R
import tv.own.owntv.core.i18n.SupportedLocales
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.update.UpdateManager
import tv.own.myowntv.features.home.ChannelPlate
import tv.own.myowntv.features.home.OnNowCard
import tv.own.myowntv.features.settings.SettingsViewModel
import tv.own.myowntv.features.settings.VideoGroup
import tv.own.myowntv.features.settings.videoGroupCount
import tv.own.myowntv.features.shell.components.ClearHistoryDialog
import tv.own.myowntv.features.shell.components.FORUM_LINK
import tv.own.myowntv.features.shell.components.GITHUB_REPO
import tv.own.myowntv.features.shell.components.SITE_LINK
import tv.own.myowntv.features.shell.components.playbackDisplayName
import tv.own.myowntv.features.update.UpdateDialog
import tv.own.owntv.player.PlaybackErrorLog
import tv.own.myowntv.player.displayText
import tv.own.myowntv.ui.components.BrandMark
import tv.own.myowntv.ui.components.MoveOrderOverlay
import tv.own.myowntv.ui.components.Wordmark
import tv.own.myowntv.ui.components.rememberAppliedIcon
import tv.own.myowntv.ui.components.MyOwnTVIcon
import tv.own.myowntv.ui.components.MyOwnTVPopup
import tv.own.myowntv.ui.components.displayText
import tv.own.myowntv.ui.components.longPressMenuGuard
import tv.own.myowntv.ui.components.trapAllFocusExit
import tv.own.myowntv.ui.stage.StageFocus
import tv.own.myowntv.ui.stage.StageMenu
import tv.own.myowntv.ui.stage.StageMenuHeader
import tv.own.myowntv.ui.stage.StageMenuItem
import tv.own.myowntv.ui.stage.StageSurface
import tv.own.myowntv.ui.stage.StageTile
import tv.own.myowntv.ui.stage.StageTool
import tv.own.myowntv.ui.stage.stageFocusLook
import tv.own.myowntv.ui.theme.StageColors
import tv.own.myowntv.ui.theme.mpx
import tv.own.myowntv.ui.theme.mpxSp
import tv.own.myowntv.ui.theme.stageAccent
import tv.own.myowntv.ui.theme.stageText
import tv.own.myowntv.ui.format.formatBestDateTime
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

// ---------------------------------------------------------------------------------------------
// Shared parts
// ---------------------------------------------------------------------------------------------

/** "Today 21:10", "Tomorrow 06:00", "Thu 2 Oct 20:15" — the guide's own day words. */
@Composable
private fun feedDayWhen(atMs: Long): String =
    tv.own.myowntv.features.epg.guideDayLabel(tv.own.myowntv.features.epg.localMidnight(atMs)) + " " +
        tv.own.myowntv.ui.format.rememberBestDateFormatter("jm")(atMs)

/** "Favourites" 42/800 −1, then whatever sits beside it (the segmented control, a count, tools). */
@Composable
private fun PageHead(title: String, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = 60.mpx), horizontalArrangement = Arrangement.spacedBy(22.mpx), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = stageText(42, 800, (-1).mpxSp), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing()
    }
}

/** `.tool.box` on a focused row ("▶ Resume", "✕ Remove"): 44 high, 16/700. */
data class RowAction(val text: String, val icon: MyOwnTVIcon, val onClick: () -> Unit)

/**
 * A `.dl` row (History, paired devices, the error log): radius 24, padding 22, gap 24. The row itself
 * holds focus and takes OK; its [actions] show only while it is focused, to its right, so ▶ reaches them
 * and ▲ ▼ move between rows. [extra] draws under the line (a progress bar, the raw error).
 */
@Composable
internal fun StageActionRow(
    height: Dp,
    focus: FocusRequester?,
    onClick: () -> Unit,
    leading: @Composable (focused: Boolean) -> Unit,
    title: String,
    line: String?,
    actions: List<RowAction> = emptyList(),
    titleSize: Int = 24,
    trailing: (@Composable () -> Unit)? = null,
    extra: @Composable (focused: Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var hasFocus by remember { mutableStateOf(false) }
    Box(
        modifier
            .padding(bottom = 10.mpx)
            .fillMaxWidth()
            .heightIn(min = height)
            .onFocusChanged { hasFocus = it.hasFocus }
            .then(if (hasFocus) Modifier.stageFocusLook(StageFocus.FX, 24.mpx) else Modifier)
            .focusGroup()
            .padding(horizontal = 22.mpx, vertical = 10.mpx),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.mpx), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
                    .clickable(interactionSource = null, indication = null, onClick = onClick),
                horizontalArrangement = Arrangement.spacedBy(24.mpx),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leading(hasFocus)
                Column(Modifier.weight(1f)) {
                    Text(title, style = stageText(titleSize, 700), color = if (hasFocus) StageColors.FxText else StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (line != null) Text(line, style = stageText(17, 400), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.mpx))
                    extra(hasFocus)
                }
                trailing?.invoke()
            }
            if (hasFocus) actions.forEach { a ->
                StageSurface(
                    onClick = a.onClick, radius = 15.mpx, modifier = Modifier.height(44.mpx),
                    idle = Modifier.background(StageColors.ControlFill, RoundedCornerShape(15.mpx)),
                ) { focused ->
                    val c = if (focused) stageAccent.onAccent else StageColors.Text
                    Row(Modifier.padding(horizontal = 16.mpx), horizontalArrangement = Arrangement.spacedBy(9.mpx), verticalAlignment = Alignment.CenterVertically) {
                        MyOwnTVIcon(a.icon, c, Modifier.size(18.mpx), filled = a.icon == MyOwnTVIcon.PLAY)
                        Text(a.text, style = stageText(16, 700), color = c, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** What a page says when its list is empty. */
@Composable
private fun EmptyNote(title: String, body: String? = null) {
    Column(Modifier.padding(top = 34.mpx)) {
        Text(title, style = stageText(24, 800), color = StageColors.Text)
        if (body != null) Text(body, style = stageText(18, 500), color = StageColors.Muted, modifier = Modifier.padding(top = 8.mpx))
    }
}

// ---------------------------------------------------------------------------------------------
// Settings: Search and the twelve group cards
// ---------------------------------------------------------------------------------------------

/**
 * The twelve Settings groups, in the order of `SettingsGroup`: a card's index is the group it opens.
 *
 * The corner badge: video groups ([video]) count live from the player catalogue — the same number
 * the group's own pane heads with. Root groups use the hand-written [count]; their rows are a
 * static list in SettingsScreen. Quick has neither and shows the pinned count.
 */
private data class SettingsGroupCard(
    val icon: MyOwnTVIcon,
    val nameRes: Int,
    val summaryRes: Int,
    val count: Int?,
    val video: VideoGroup? = null,
)

private val SettingsCards = listOf(
    SettingsGroupCard(MyOwnTVIcon.SPARKLE, R.string.settings_group_quick, R.string.settings_group_summary_quick, null),
    SettingsGroupCard(MyOwnTVIcon.EPG, R.string.settings_part_guide_catchup, R.string.settings_epg_guide_days, 4),
    SettingsGroupCard(MyOwnTVIcon.PALETTE, R.string.settings_group_appearance, R.string.settings_card_appearance, 9),
    SettingsGroupCard(MyOwnTVIcon.GRID, R.string.settings_group_layout, R.string.settings_card_layout, 10),
    SettingsGroupCard(MyOwnTVIcon.MOVIES, R.string.settings_group_content_metadata, R.string.settings_card_content, 3),
    SettingsGroupCard(MyOwnTVIcon.PLAY_CIRCLE, R.string.settings_vp_cat_player, R.string.settings_card_player, null, VideoGroup.PLAYER),
    SettingsGroupCard(MyOwnTVIcon.EXPAND, R.string.settings_vp_cat_picture, R.string.settings_card_picture, null, VideoGroup.PICTURE),
    SettingsGroupCard(MyOwnTVIcon.HEADPHONES, R.string.settings_group_sound_subtitles, R.string.settings_card_sound, null, VideoGroup.SOUND),
    SettingsGroupCard(MyOwnTVIcon.LIVE_TV, R.string.settings_live_tv, R.string.settings_card_live, null, VideoGroup.LIVE),
    SettingsGroupCard(MyOwnTVIcon.REC, R.string.settings_group_watching_recording, R.string.settings_card_watching, null, VideoGroup.WATCHING),
    SettingsGroupCard(MyOwnTVIcon.NETWORK, R.string.settings_group_network, R.string.settings_card_network, 2),
    SettingsGroupCard(MyOwnTVIcon.INFO, R.string.settings_group_app, R.string.settings_card_app, 8),
)

/** The badge in a card's corner, kept in step with what the group's pane actually shows. */
@Composable
private fun cardBadge(card: SettingsGroupCard, settingsVm: SettingsViewModel, pinned: Int): Int = when {
    card.video != null -> videoGroupCount(card.video, settingsVm) + if (card.video == VideoGroup.WATCHING) 4 else 0
    card.count != null -> card.count
    else -> pinned
}

/** The card last opened, so Back from its group lands on it again rather than on the search field. */
private var lastOpenedCard: Int? = null

@Composable
internal fun SettingsPage(
    entry: FocusRequester,
    settingsVm: SettingsViewModel,
    pinned: Int,
    onOpenGroup: (Int) -> Unit,
    onSearch: () -> Unit,
) {
    val back = lastOpenedCard
    Column(Modifier.fillMaxSize().focusGroup()) {
        PageHead(stringResource(R.string.common_nav_settings)) {
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.more_settings_pin_hint), style = stageText(17, 500), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        StageSurface(
            onClick = { lastOpenedCard = null; onSearch() }, radius = 18.mpx,
            modifier = Modifier.padding(top = 26.mpx).fillMaxWidth().height(60.mpx).then(if (back == null) Modifier.focusRequester(entry) else Modifier),
            focusStyle = StageFocus.FX,
            idle = Modifier.background(StageColors.ControlFill, RoundedCornerShape(18.mpx)),
        ) { focused ->
            Row(Modifier.padding(horizontal = 20.mpx), horizontalArrangement = Arrangement.spacedBy(14.mpx), verticalAlignment = Alignment.CenterVertically) {
                MyOwnTVIcon(MyOwnTVIcon.SEARCH, if (focused) StageColors.Text else StageColors.Dim, Modifier.size(19.mpx))
                Text(stringResource(R.string.more_settings_search), style = stageText(19, 500), color = if (focused) StageColors.Text else StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(Modifier.padding(top = 22.mpx), verticalArrangement = Arrangement.spacedBy(20.mpx)) {
            SettingsCards.chunked(3).forEach { row ->
                // 150 at the default font; a larger app font grows the row rather than cutting the
                // summary, and the row's cards keep one height.
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(20.mpx)) {
                    row.forEach { card ->
                        val index = SettingsCards.indexOf(card)
                        StageTile(
                            modifier = Modifier.weight(1f).heightIn(min = 150.mpx).fillMaxHeight().then(if (index == back) Modifier.focusRequester(entry) else Modifier),
                            onClick = { lastOpenedCard = index; onOpenGroup(index) },
                            padding = 0.mpx,
                        ) { focused ->
                            Column(Modifier.padding(horizontal = 26.mpx, vertical = 24.mpx)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    MyOwnTVIcon(card.icon, if (focused) StageColors.Text else StageColors.Muted, Modifier.size(28.mpx))
                                    Spacer(Modifier.weight(1f))
                                    Text(cardBadge(card, settingsVm, pinned).toString(), style = stageText(17, 700), color = StageColors.Dim)
                                }
                                Text(stringResource(card.nameRes), style = stageText(24, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 14.mpx))
                                Text(stringResource(card.summaryRes), style = stageText(16, 500), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.mpx))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Favourites
// ---------------------------------------------------------------------------------------------

@Composable
internal fun FavouritesPage(
    vm: MoreCountsViewModel,
    /** Hands More what OK / ▶ on the sheet does: focus the first card. */
    setEntry: (() -> Boolean) -> Unit,
    onPlayChannel: (String) -> Unit,
) {
    val counts by vm.favorites.collectAsStateWithLifecycle()
    val lists by vm.favouriteLists.collectAsStateWithLifecycle()
    val move by vm.move.collectAsStateWithLifecycle()
    // Hold OK: the item whose small menu is open, and the card to return to when it closes.
    var menu by remember { mutableStateOf<Triple<MediaType, String, String>?>(null) }
    val itemFocus = remember { HashMap<Pair<MediaType, String>, FocusRequester>() }
    fun focusOf(type: MediaType, id: String) = itemFocus.getOrPut(type to id) { FocusRequester() }
    var returnTo by remember { mutableStateOf<FocusRequester?>(null) }
    LaunchedEffect(menu, move) {
        if (menu == null && move == null) returnTo?.let { withFrameNanos { }; runCatching { it.requestFocus() }; returnTo = null }
    }

    // OK / ▶ from the sheet lands on the first favourite channel, as drawn.
    val firstTarget = lists.live.firstOrNull()?.let { focusOf(MediaType.LIVE, it.id) }

    Column(Modifier.fillMaxSize().focusGroup()) {
        PageHead(stringResource(R.string.content_category_favorites))
        androidx.compose.runtime.SideEffect { setEntry { firstTarget?.let { f -> runCatching { f.requestFocus() }.getOrDefault(false) } ?: false } }
        if (counts.total == 0) {
            EmptyNote(stringResource(R.string.more_fav_empty_title), stringResource(R.string.more_fav_empty_body))
            return@Column
        }
        if (lists.live.isNotEmpty()) {
            RowHead(stringResource(R.string.common_nav_live_tv), stringResource(R.string.home_row_on_now), top = 34.mpx)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(20.mpx), contentPadding = PaddingValues(end = 24.mpx)) {
                items(lists.live, key = { it.id }) { ch ->
                    val f = focusOf(MediaType.LIVE, ch.id)
                    OnNowCard(
                        channel = ch, programme = lists.onNow[ch.id]?.now, now = lists.now,
                        onClick = { onPlayChannel(ch.id) },
                        onLongClick = { returnTo = f; menu = Triple(MediaType.LIVE, ch.id, ch.name) },
                        width = 420.mpx, oneLine = true,
                        modifier = Modifier.focusRequester(f),
                    )
                }
            }
        }
    }

    menu?.let { (type, id, name) ->
        val close = { menu = null }
        MyOwnTVPopup(onDismissRequest = close, stageLayout = true) {
            val first = remember { FocusRequester() }
            LaunchedEffect(Unit) { withFrameNanos { }; runCatching { first.requestFocus() } }
            BackHandler { close() }
            // Opened by a held OK: the guard keeps its repeats off the first item (Remove).
            Box(Modifier.fillMaxSize().background(stageTone(Color.Black.copy(alpha = 0.45f), StageColors.Scrim)).longPressMenuGuard().trapAllFocusExit().focusGroup(), contentAlignment = Alignment.Center) {
                StageMenu(Modifier.width(440.mpx)) {
                    StageMenuHeader(name, null)
                    StageMenuItem(stringResource(R.string.content_remove_favourite), onClick = { vm.removeFavourite(type, id); close() }, icon = MyOwnTVIcon.FAVORITE, modifier = Modifier.focusRequester(first))
                    StageMenuItem(stringResource(R.string.content_move), onClick = { close(); vm.startMove(type, id) }, icon = MyOwnTVIcon.MOVE)
                }
            }
        }
    }
    move?.let { m ->
        MoveOrderOverlay(
            title = stringResource(R.string.content_move),
            itemNames = m.names,
            activeIndex = m.index,
            onMove = vm::move,
            onCommit = vm::commitMove,
            onCancel = vm::cancelMove,
        )
    }
}

/** `.rowh`: a 26/800 heading with an optional dim 17/700 word ("Live TV  On now"), 16 above the row. */
@Composable
private fun RowHead(title: String, small: String?, top: Dp) {
    Row(Modifier.padding(top = top, bottom = 16.mpx), horizontalArrangement = Arrangement.spacedBy(14.mpx)) {
        Text(title, style = stageText(26, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.alignByBaseline())
        if (small != null) Text(small, style = stageText(17, 700), color = StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.alignByBaseline())
    }
}

// ---------------------------------------------------------------------------------------------
// History
// ---------------------------------------------------------------------------------------------

@Composable
internal fun HistoryPage(
    vm: MoreCountsViewModel,
    setEntry: (() -> Boolean) -> Unit,
    onClearHistory: (MediaType?) -> Unit,
    onPlayChannel: (String) -> Unit,
) {
    val counts by vm.history.collectAsStateWithLifecycle()
    val rows = vm.historyLists.collectAsStateWithLifecycle().value.live
    var showClear by remember { mutableStateOf(false) }
    val clearFocus = remember { FocusRequester() }
    val rowFocus = remember { HashMap<String, FocusRequester>() }
    fun focusOf(id: String) = rowFocus.getOrPut(id) { FocusRequester() }
    var clearWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(showClear) { if (showClear) clearWasOpen = true else if (clearWasOpen) { clearWasOpen = false; withFrameNanos { }; runCatching { clearFocus.requestFocus() } } }

    val sep = stringResource(R.string.content_epg_bits_separator)

    Column(Modifier.fillMaxSize().focusGroup()) {
        PageHead(stringResource(R.string.content_category_history)) {
            Spacer(Modifier.weight(1f))
            if (counts.total > 0) StageTool(stringResource(R.string.settings_clear_history), onClick = { showClear = true }, icon = MyOwnTVIcon.TRASH, danger = true, modifier = Modifier.focusRequester(clearFocus))
        }
        androidx.compose.runtime.SideEffect { setEntry { rows.firstOrNull()?.let { r -> runCatching { focusOf(r.id).requestFocus() }.getOrDefault(false) } ?: false } }
        if (rows.isEmpty()) {
            EmptyNote(stringResource(R.string.more_history_empty))
            return@Column
        }
        LazyColumn(Modifier.padding(top = 26.mpx).fillMaxSize(), contentPadding = PaddingValues(bottom = 40.mpx)) {
            items(rows, key = { it.id }) { e ->
                val line = listOf(stringResource(R.string.common_nav_live_tv), feedDayWhen(e.watchedAt)).joinToString(sep)
                StageActionRow(
                    height = 118.mpx,
                    focus = focusOf(e.id),
                    onClick = { onPlayChannel(e.id) },
                    leading = {
                        Box(Modifier.size(176.mpx, 99.mpx).clip(RoundedCornerShape(14.mpx)).background(tv.own.myowntv.ui.stage.stageStillBack), contentAlignment = Alignment.Center) {
                            // The plate fills the whole placeholder: a smaller tile inside it read as
                            // a gray frame drawn around the logo.
                            if (e.channel != null) ChannelPlate(e.channel, Modifier.fillMaxSize())
                            else MyOwnTVIcon(MyOwnTVIcon.LIVE_TV, StageColors.Dim, Modifier.size(36.mpx))
                        }
                    },
                    title = e.title,
                    line = line,
                    actions = listOf(
                        RowAction(stringResource(R.string.content_key_watch), MyOwnTVIcon.PLAY) { onPlayChannel(e.id) },
                        RowAction(stringResource(R.string.settings_customize_remove), MyOwnTVIcon.CLOSE) {
                            // The removed row takes its focus with it: the next one takes over.
                            val i = rows.indexOf(e)
                            vm.removeHistory(e)
                            (rows.getOrNull(i + 1) ?: rows.getOrNull(i - 1))?.let { n -> runCatching { focusOf(n.id).requestFocus() } }
                        },
                    ),
                )
            }
        }
    }
    if (showClear) {
        ClearHistoryDialog(onClear = { t -> onClearHistory(t); showClear = false }, onDismiss = { showClear = false })
    }
}

// ---------------------------------------------------------------------------------------------
// Error log
// ---------------------------------------------------------------------------------------------

@Composable
internal fun ErrorLogPage(setEntry: (() -> Boolean) -> Unit, onCountChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var exportPath by remember { mutableStateOf<String?>(null) }
    var exportFailed by remember { mutableStateOf(false) }
    val entries by produceState<List<PlaybackErrorLog.Entry>?>(initialValue = null, refresh) {
        value = withContext(Dispatchers.IO) { PlaybackErrorLog.read(context) }
    }
    val exportFocus = remember { FocusRequester() }
    val firstRow = remember { FocusRequester() }
    val sep = stringResource(R.string.content_epg_bits_separator)

    Column(Modifier.fillMaxSize().focusGroup()) {
        PageHead(stringResource(R.string.settings_playback_error_log)) {
            Text((entries?.size ?: 0).toString(), style = stageText(22, 700), color = StageColors.Muted)
            Spacer(Modifier.weight(1f))
            // Export also carries the live diagnostics ring, so it stays even when the list is empty.
            StageTool(stringResource(R.string.settings_export), boxed = true, icon = MyOwnTVIcon.DOWNLOADS, modifier = Modifier.focusRequester(exportFocus), onClick = {
                scope.launch {
                    val path = withContext(Dispatchers.IO) { PlaybackErrorLog.export(context) }
                    exportPath = path
                    exportFailed = path == null
                }
            })
            if (!entries.isNullOrEmpty()) StageTool(stringResource(R.string.settings_clear_log), icon = MyOwnTVIcon.TRASH, danger = true, onClick = {
                PlaybackErrorLog.clear(context)
                exportPath = null
                exportFailed = false
                refresh++
                onCountChanged()
                runCatching { exportFocus.requestFocus() }
            })
        }
        androidx.compose.runtime.SideEffect { setEntry { runCatching { (if (entries.isNullOrEmpty()) exportFocus else firstRow).requestFocus() }.getOrDefault(false) } }
        Text(stringResource(R.string.settings_playback_error_description_full), style = stageText(17, 500), color = StageColors.Muted, modifier = Modifier.padding(top = 10.mpx))
        exportPath?.let { Text(stringResource(R.string.settings_backup_saved_to, it), style = stageText(16, 500), color = stageAccent.accent, modifier = Modifier.padding(top = 8.mpx)) }
        if (exportFailed) Text(stringResource(R.string.settings_backup_export_error), style = stageText(16, 500), color = StageColors.Danger, modifier = Modifier.padding(top = 8.mpx))
        val list = entries
        when {
            list == null -> Unit
            list.isEmpty() -> EmptyNote(stringResource(R.string.settings_no_playback_errors))
            else -> LazyColumn(Modifier.padding(top = 22.mpx).fillMaxSize(), contentPadding = PaddingValues(bottom = 40.mpx)) {
                // atMs is millisecond precision and several entries can land in the same millisecond
                // from the same engine — without the index the key would collide and LazyColumn throws.
                itemsIndexed(list, key = { i, e -> e.atMs.toString() + e.engine + i.toString() }) { i, e ->
                    val kind = when (e.kind) {
                        PlaybackErrorLog.Kind.ERROR -> stringResource(R.string.settings_playback_kind_error) to StageColors.Danger
                        PlaybackErrorLog.Kind.EVENT -> stringResource(R.string.settings_playback_kind_event) to StageColors.TagText
                        PlaybackErrorLog.Kind.REPORT -> stringResource(R.string.settings_playback_kind_report) to StageColors.Warn
                    }
                    StageActionRow(
                        height = 96.mpx,
                        focus = if (i == 0) firstRow else null,
                        onClick = {},
                        titleSize = 21,
                        leading = {
                            Text(
                                kind.first.uppercase(androidx.compose.ui.platform.LocalConfiguration.current.locales[0]),
                                style = stageText(13, 800, 0.05.em), color = kind.second, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(min = 72.mpx)
                                    .background(if (kind.second == StageColors.TagText) StageColors.Ink.copy(alpha = 0.08f) else kind.second.copy(alpha = 0.16f), RoundedCornerShape(6.mpx))
                                    .padding(horizontal = 7.mpx, vertical = 3.mpx),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        },
                        title = e.reason?.displayText() ?: e.legacyReason ?: e.engine.playbackDisplayName(),
                        line = listOfNotNull(
                            e.engine.playbackDisplayName(),
                            stringResource(R.string.settings_live),
                            e.mediaSpec()?.displayText() ?: e.spec,
                        ).joinToString(sep),
                        trailing = { Text(formatBestDateTime(context, "dMMM", e.atMs), style = stageText(17, 500), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        extra = { focused ->
                            if (focused) {
                                e.raw?.let { Text(it, style = stageText(15, 500), color = StageColors.Dim, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.mpx)) }
                                Text(stringResource(R.string.settings_device_details, e.model, e.android), style = stageText(15, 500), color = StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.mpx))
                            }
                        },
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// About
// ---------------------------------------------------------------------------------------------

@Composable
internal fun AboutPage(vm: MoreCountsViewModel, entry: FocusRequester, onOpenLanguage: () -> Unit) {
    val context = LocalContext.current
    val manager: UpdateManager = koinInject()
    val updateState by manager.state.collectAsStateWithLifecycle()
    val checkedAt by vm.lastUpdateCheckAt.collectAsStateWithLifecycle()
    var showUpdate by remember { mutableStateOf(false) }
    val updateFocus = remember { FocusRequester() }
    var updateWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(showUpdate) { if (showUpdate) updateWasOpen = true else if (updateWasOpen) { updateWasOpen = false; withFrameNanos { }; runCatching { updateFocus.requestFocus() } } }
    val sep = stringResource(R.string.content_epg_bits_separator)
    val accent = stageAccent.accent

    Column(Modifier.fillMaxSize().focusGroup()) {
        Row(horizontalArrangement = Arrangement.spacedBy(40.mpx), verticalAlignment = Alignment.CenterVertically) {
            // The app's own icon (150) and the wordmark (300).
            BrandMark(rememberAppliedIcon(), 150.mpx)
            Column {
                Wordmark(300.mpx)
                Row(Modifier.padding(top = 16.mpx), horizontalArrangement = Arrangement.spacedBy(14.mpx), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE), style = stageText(22, 700), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    // Only what a real check said: no tag before one has run.
                    val tag = when (updateState) {
                        UpdateManager.State.UpToDate -> stringResource(R.string.more_about_up_to_date)
                        is UpdateManager.State.Available -> stringResource(R.string.update_available)
                        else -> null
                    }
                    tag?.let {
                        Text(it.uppercase(androidx.compose.ui.platform.LocalConfiguration.current.locales[0]), style = stageText(13, 800, 0.05.em), color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.background(accent.copy(alpha = 0.2f), RoundedCornerShape(6.mpx)).padding(horizontal = 7.mpx, vertical = 3.mpx))
                    }
                }
                Text(
                    listOfNotNull(
                        checkedAt?.let { stringResource(R.string.more_about_checked, feedDayWhen(it)) },
                        stringResource(R.string.more_about_license),
                    ).joinToString(sep),
                    style = stageText(17, 500), color = StageColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.mpx),
                )
            }
        }
        Text(stringResource(R.string.more_about_description), style = stageText(18, 500).copy(lineHeight = 29.mpxSp), color = stageTone(Color(0xFFD3DCD8), StageColors.Body),
            modifier = Modifier.padding(top = 26.mpx).widthIn(max = 860.mpx))
        Text(stringResource(R.string.settings_contributions), style = stageText(16, 500), color = StageColors.Dim,
            modifier = Modifier.padding(top = 10.mpx, bottom = 24.mpx).widthIn(max = 860.mpx))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.mpx)) {
            AboutQrCard(
                url = "https://$SITE_LINK/",
                title = stringResource(R.string.more_about_download_page),
                shown = SITE_LINK,
                qrDescription = stringResource(R.string.more_about_download_qr),
                accent = accent,
                modifier = Modifier.weight(1f),
            )
            AboutQrCard(
                url = "https://$FORUM_LINK",
                title = stringResource(R.string.more_about_forum),
                shown = FORUM_LINK,
                qrDescription = stringResource(R.string.more_about_forum_qr),
                accent = accent,
                modifier = Modifier.weight(1f),
            )
        }
        Text(stringResource(R.string.more_about_scan), style = stageText(15.5f, 500), color = StageColors.Muted,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.mpx))
        Row(Modifier.padding(top = 22.mpx), horizontalArrangement = Arrangement.spacedBy(10.mpx)) {
            // The link is for reading: a TV has no browser to open it in.
            Row(
                Modifier.height(44.mpx).background(StageColors.ControlFill, RoundedCornerShape(15.mpx)).padding(horizontal = 16.mpx),
                horizontalArrangement = Arrangement.spacedBy(9.mpx), verticalAlignment = Alignment.CenterVertically,
            ) {
                MyOwnTVIcon(MyOwnTVIcon.NETWORK, StageColors.Text, Modifier.size(18.mpx))
                Text(GITHUB_REPO, style = stageText(16, 700), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val languages = SupportedLocales.all.count { it.packaged }
            StageTool(pluralStringResource(R.plurals.more_about_languages, languages, languages), onClick = onOpenLanguage, icon = MyOwnTVIcon.LIST, boxed = true, modifier = Modifier.focusRequester(entry))
            StageTool(stringResource(R.string.settings_check_updates), onClick = { showUpdate = true }, icon = MyOwnTVIcon.REFRESH, boxed = true, modifier = Modifier.focusRequester(updateFocus))
        }
    }
    if (showUpdate) {
        UpdateDialog(onDismiss = { showUpdate = false }, checkOnOpen = true)
    }
}

/**
 * One "scan this" card on About: a QR rendered from [url] itself, the label and the link in readable
 * form. A TV has no browser to open the link in, so the address stays a label and the QR is the door.
 */
@Composable
private fun AboutQrCard(
    url: String,
    title: String,
    shown: String,
    qrDescription: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    StageTile(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.mpx), verticalAlignment = Alignment.CenterVertically) {
            // Generated from the link itself, so the QR can never encode an old address.
            val qr = remember(url) { CompanionLink.renderQr(url, sizePx = 512) }
            if (qr != null) {
                Image(
                    bitmap = qr.asImageBitmap(),
                    contentDescription = qrDescription,
                    modifier = Modifier.size(150.mpx).clip(RoundedCornerShape(14.mpx)).background(Color.White),
                )
            }
            Column {
                Text(title, style = stageText(22, 800), color = StageColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(shown, style = stageText(17, 500), color = accent, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.mpx))
            }
        }
    }
}
