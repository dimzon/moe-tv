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

import androidx.compose.foundation.layout.size
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import tv.own.myowntv.BuildConfig
import tv.own.myowntv.R
import tv.own.myowntv.features.categorylock.PinPrompt
import tv.own.myowntv.features.settings.LocalSyncScreen
import tv.own.myowntv.features.settings.SettingsViewModel
import tv.own.myowntv.features.settings.lock.SettingsPinViewModel
import tv.own.owntv.core.settings.guardsPage
import tv.own.owntv.player.PlaybackErrorLog
import tv.own.myowntv.ui.components.MyOwnTVIcon
import tv.own.myowntv.ui.stage.StageGroupItem
import tv.own.myowntv.ui.stage.StageKeyHints
import tv.own.myowntv.ui.stage.StageSurface
import tv.own.myowntv.ui.stage.stageGlass
import tv.own.myowntv.ui.theme.StageColors
import tv.own.myowntv.ui.theme.mpx
import tv.own.myowntv.ui.theme.stageText

/** The sheet's items, top to bottom. */
private enum class MoreItem { SETTINGS, FAVORITES, HISTORY, LOCAL_SYNC, ERROR_LOG, ABOUT }

/** The pages that ask for the Settings PIN when it is on. */
private val PIN_PAGES = setOf(MoreItem.LOCAL_SYNC)

/**
 * More: the glass section sheet, and one page on the right
 * for the item chosen with OK. Two layers, never three: ▲ ▼ move focus, OK selects the item (focus stays), ▶ enters the page, Back returns to
 * the sheet, Back again leaves More. Nothing opens full screen any more — Settings is the one door out,
 * through its group cards.
 */
@Composable
fun MoreScreen(
    onOpenSettings: (group: Int?, search: Boolean) -> Unit,
    /** ▶ / OK on a Favourites or History row: the same path Home uses, straight to the channel. */
    onPlayChannel: (String) -> Unit,
    onChildFocused: () -> Unit,
    onEntryHook: (((() -> Boolean)?) -> Unit)? = null,
    /** Back from Settings: land on the Settings page's first card instead of the rail. */
    restoreFocus: Boolean = false,
    onRestored: () -> Unit = {},
    modifier: Modifier = Modifier,
    vm: MoreCountsViewModel = koinViewModel(),
    settingsVm: SettingsViewModel = koinViewModel(),
    pinVm: SettingsPinViewModel = koinViewModel(),
) {
    // Local sync and Developer can restore or copy settings, so the Settings PIN
    // guards them too; one unlock holds until More is left.
    val pinLock by pinVm.lock.collectAsStateWithLifecycle()
    var pinPagesUnlocked by remember { mutableStateOf(false) }
    var askPinFor by remember { mutableStateOf<MoreItem?>(null) }
    val pinShut: (MoreItem) -> Boolean = { it in PIN_PAGES && pinLock.guardsPage(pinPagesUnlocked) }
    var chosen by rememberSaveable { mutableStateOf(MoreItem.SETTINGS) }
    // A guarded page left chosen on an earlier visit stays shut until the PIN is entered again.
    val selected by rememberUpdatedState(if (pinShut(chosen)) MoreItem.SETTINGS else chosen)
    val itemFocus = remember { MoreItem.entries.associateWith { FocusRequester() } }
    // What OK / ▶ focuses on the page: a fixed target, or the page's own choice (its first row).
    val pageTarget = remember { FocusRequester() }
    var pageEntry by remember { mutableStateOf<(() -> Boolean)?>(null) }
    var pageFocused by remember { mutableStateOf(false) }
    val enterPage: () -> Boolean = { pageEntry?.invoke() ?: runCatching { pageTarget.requestFocus() }.getOrDefault(false) }
    val backToSheet: () -> Unit = { runCatching { itemFocus.getValue(selected).requestFocus() } }
    // Like the rail: ▲ ▼ move focus, OK selects the item (its page shows, focus stays on the item),
    // ▶ goes into the page that is shown.
    val choose: (MoreItem) -> Unit = { item ->
        when {
            !pinShut(item) -> if (item != selected) { chosen = item; pageEntry = null }
            pinLock.loaded -> askPinFor = item
        }
    }

    // The rail's ▶ lands on the item the sheet was last on.
    DisposableEffect(onEntryHook) {
        onEntryHook?.invoke { runCatching { itemFocus.getValue(selected).requestFocus() }.getOrDefault(false) }
        onDispose { onEntryHook?.invoke(null) }
    }
    LaunchedEffect(restoreFocus) {
        if (!restoreFocus) return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        if (!enterPage()) runCatching { itemFocus.getValue(selected).requestFocus() }
        onRestored()
    }
    // Composed before the page, so a page's own Back (a step popup, a dialog) still wins over it.
    BackHandler(enabled = pageFocused) { backToSheet() }

    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    val pinned by settingsVm.quickPinnedKeys.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var logVersion by remember { mutableIntStateOf(0) }
    val logCount by produceState(0, logVersion) { value = withContext(Dispatchers.IO) { PlaybackErrorLog.read(context).size } }

    Box(modifier.fillMaxSize().onFocusChanged { if (it.hasFocus) onChildFocused() }) {
        // --- The sheet (`.gsheet`, 440 wide at 24 / 24 / 24): the full height.
        Column(
            Modifier
                .padding(start = 24.mpx, top = 24.mpx, bottom = 24.mpx)
                .width(440.mpx)
                .fillMaxHeight()
                .stageGlass(32.mpx, overContent = true)
                .padding(horizontal = 16.mpx, vertical = 26.mpx)
                // ◀ back from the page lands on the item that page belongs to.
                .focusProperties { onEnter = { runCatching { itemFocus.getValue(selected).requestFocus() } } }
                .onPreviewKeyEvent { e ->
                    e.type == KeyEventType.KeyDown && e.key == Key.DirectionRight && enterPage()
                }
                .focusGroup(),
        ) {
            val item = @Composable { it: MoreItem, icon: MyOwnTVIcon, label: String, value: String?, warn: Boolean ->
                StageGroupItem(
                    text = label, icon = icon, value = value, warn = warn,
                    selected = selected == it,
                    onClick = { choose(it) },
                    modifier = Modifier.focusRequester(itemFocus.getValue(it)),
                )
            }
            item(MoreItem.SETTINGS, MyOwnTVIcon.SETTINGS, stringResource(R.string.common_nav_settings), null, false)
            GroupLabel(stringResource(R.string.more_group_your_things))
            item(MoreItem.FAVORITES, MyOwnTVIcon.FAVORITE, stringResource(R.string.content_category_favorites), favorites.total.toString(), false)
            item(MoreItem.HISTORY, MyOwnTVIcon.HISTORY, stringResource(R.string.content_category_history), history.total.toString(), false)
            GroupLabel(stringResource(R.string.more_group_keep_safe))
            item(MoreItem.LOCAL_SYNC, MyOwnTVIcon.PHONE, stringResource(R.string.local_sync_title), stringResource(if (sync.listening) R.string.common_on else R.string.common_off), false)
            GroupLabel(stringResource(R.string.settings_app_group))
            item(MoreItem.ERROR_LOG, MyOwnTVIcon.WARNING, stringResource(R.string.settings_playback_error_log), logCount.toString(), false)
            item(MoreItem.ABOUT, MyOwnTVIcon.INFO, stringResource(R.string.settings_about), BuildConfig.VERSION_NAME, false)
            Spacer(Modifier.weight(1f))
            StageKeyHints(
                listOf(
                    stringResource(R.string.common_ok) to stringResource(R.string.content_key_select),
                    "▶" to stringResource(R.string.content_key_enter),
                    stringResource(R.string.common_back) to stringResource(R.string.content_key_menu),
                ),
                Modifier.padding(start = 14.mpx),
                textSize = 15,
            )
        }

        // --- The page: everything right of the sheet, below the top-right cluster.
        Box(
            Modifier
                .padding(start = 520.mpx, top = 128.mpx, end = 64.mpx)
                .fillMaxSize()
                .onFocusChanged { pageFocused = it.hasFocus },
        ) {
            val setEntry: (() -> Boolean) -> Unit = { pageEntry = it }
            when (selected) {
                MoreItem.SETTINGS -> SettingsPage(
                    entry = pageTarget, pinned = pinned.size,
                    onOpenGroup = { onOpenSettings(it, false) },
                    onSearch = { onOpenSettings(null, true) },
                )
                MoreItem.FAVORITES -> FavouritesPage(
                    vm = vm, setEntry = setEntry,
                    onPlayChannel = onPlayChannel,
                )
                MoreItem.HISTORY -> HistoryPage(
                    vm = vm, setEntry = setEntry,
                    onClearHistory = { settingsVm.clearWatchHistory() },
                    onPlayChannel = onPlayChannel,
                )
                MoreItem.LOCAL_SYNC -> LocalSyncScreen(entry = pageTarget, modifier = Modifier.fillMaxSize())
                MoreItem.ERROR_LOG -> ErrorLogPage(setEntry = setEntry, onCountChanged = { logVersion++ })
                MoreItem.ABOUT -> AboutPage(vm = vm, entry = pageTarget, onOpenLanguage = { onOpenSettings(7, false) })
            }
        }
    }
    val pinItem = askPinFor
    val pin = pinLock.pin
    if (pinItem != null && pin != null) {
        PinPrompt(
            pin = pin,
            door = tv.own.owntv.core.categorylock.PinDoor.SETTINGS,
            onUnlock = { pinPagesUnlocked = true; askPinFor = null; chosen = pinItem; pageEntry = null },
            onDismiss = { askPinFor = null },
        )
    }
}

/** `.gsheet .gl`: 12.5/800 +0.13em in dim, upper-cased in the user's own locale. */
@Composable
private fun GroupLabel(text: String) {
    Text(
        text.uppercase(androidx.compose.ui.platform.LocalConfiguration.current.locales[0]),
        style = stageText(12.5f, 800, 0.13.em), color = StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 16.mpx, end = 16.mpx, top = 16.mpx, bottom = 6.mpx),
    )
}
