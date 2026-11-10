package tv.own.myowntv.features.settings
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

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.first
import org.koin.androidx.compose.koinViewModel
import tv.own.myowntv.R
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import androidx.compose.ui.res.pluralStringResource
import tv.own.myowntv.ui.theme.mpx
import tv.own.owntv.core.menu.MenuLayout
import tv.own.owntv.core.menu.applyMenuOrder
import tv.own.owntv.core.menu.catalogue
import tv.own.owntv.core.model.ContentMenu
import tv.own.myowntv.ui.components.MyOwnTVIcon
import tv.own.myowntv.ui.components.trapAllFocusExit

@Composable
private fun menuTitle(menu: ContentMenu) = stringResource(
    when (menu) {
        ContentMenu.LIVE -> R.string.common_nav_live_tv
        ContentMenu.MOVIE -> R.string.common_nav_movies
        ContentMenu.SERIES -> R.string.common_nav_series
        ContentMenu.EPISODE -> R.string.content_episodes
    },
)

private fun menuIcon(menu: ContentMenu) = when (menu) {
    ContentMenu.LIVE -> MyOwnTVIcon.LIVE_TV
    ContentMenu.MOVIE -> MyOwnTVIcon.MOVIES
    ContentMenu.SERIES -> MyOwnTVIcon.SERIES
    ContentMenu.EPISODE -> MyOwnTVIcon.LIST_GRID
}

/**
 * Long-press menus — the order of the actions in the Live TV menu, and which of them
 * are shown at all.
 *
 * One row: core still carries the Movies/Series/Episode menu keys, but the screens that opened those
 * menus are gone with VOD, so only the Live TV menu can be arranged here. Opening one shows that
 * menu's actions in the Move overlay, where OK shows or hides an action, holding OK picks it up and
 * Up/Down carries it. Nothing is written until Save, so Cancel and Back always leave the menu exactly
 * as it was. "Close" is not listed because it is pinned to the bottom of every menu, outside the
 * arrangeable part.
 */
@Composable
fun ContentMenuSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val vm: SettingsViewModel = koinViewModel()
    val scrollState = rememberScrollState()
    val rowFocus = remember { ContentMenu.entries.associateWith { FocusRequester() } }
    var openMenu by remember { mutableStateOf<ContentMenu?>(null) }
    LaunchedEffect(Unit) { runCatching { rowFocus.getValue(ContentMenu.LIVE).requestFocus() } }
    // One menu row and Reset; the panel lists the focused menu's actions in their order.
    val about = stringResource(R.string.settings_content_menus_description)
    StageFullPage(
        parents = listOf(stringResource(R.string.settings_group_layout)),
        title = stringResource(R.string.settings_content_menus_title),
        count = pluralStringResource(R.plurals.settings_setting_count, 1, 1),
        onBack = onBack,
        modifier = modifier,
        scroll = scrollState,
    ) {
        listOf(ContentMenu.LIVE).forEach { menu ->
            val layout by remember(menu) { vm.menuLayout(menu) }.collectAsStateWithLifecycle(MenuLayout())
            val refs = catalogue(menu)
            val order = applyMenuOrder(refs.map { it.key }, layout.order) { it }
            val rows = order.mapNotNull { k -> refs.firstOrNull { it.key == k }?.let { it.labelRes to (k in layout.hidden) } }
            val title = menuTitle(menu)
            val value = SettingValue.Opens(stringResource(if (layout.isCustom(menu)) R.string.settings_live_latency_custom else R.string.settings_subtitle_default))
            val hiddenSuffix = stringResource(R.string.content_epg_bits_separator) + stringResource(R.string.settings_hidden)
            StageSettingRow(
                icon = menuIcon(menu),
                title = title,
                desc = null,
                value = value,
                onClick = { openMenu = menu },
                help = SettingHelp(
                    title, about,
                    hints = settingHints(value, pinnable = false),
                    extra = {
                        Column(Modifier.padding(top = 16.mpx), verticalArrangement = Arrangement.spacedBy(4.mpx)) {
                            rows.forEachIndexed { i, (res, hidden) ->
                                Row(Modifier.height(36.mpx), horizontalArrangement = Arrangement.spacedBy(12.mpx), verticalAlignment = Alignment.CenterVertically) {
                                    Text((i + 1).toString(), style = tv.own.myowntv.ui.theme.stageText(17, 700), color = tv.own.myowntv.ui.theme.StageColors.Dim, modifier = Modifier.width(22.mpx))
                                    Text(
                                        if (hidden) stringResource(res) + hiddenSuffix else stringResource(res),
                                        style = tv.own.myowntv.ui.theme.stageText(17, 500),
                                        color = if (hidden) tv.own.myowntv.ui.theme.StageColors.Dim else tv.own.myowntv.ui.theme.StageColors.Text,
                                    )
                                }
                            }
                        }
                    },
                ),
                modifier = Modifier.focusRequester(rowFocus.getValue(menu)),
            )
        }
        val reset = stringResource(R.string.common_reset)
        StageSettingRow(
            icon = MyOwnTVIcon.REFRESH,
            title = reset,
            desc = null,
            value = null,
            onClick = { ContentMenu.entries.forEach { vm.setMenuLayout(it, MenuLayout()) } },
            help = SettingHelp(reset, about, hints = settingHints(null, pinnable = false)),
        )
        // Arranged in the page's panel.
        openMenu?.let { menu ->
            ArrangeMenuOverlay(
                menu = menu,
                onSave = { layout -> vm.setMenuLayout(menu, layout); openMenu = null },
                onCancel = { openMenu = null },
            )
        }
    }

}

/**
 * One menu's actions in the Move overlay — the same panel channels are reordered with, except
 * the item being carried is chosen here rather than handed in: OK on an action shows or hides it,
 * hold OK to pick it up, Up/Down to carry it, OK to put it down. A hidden action stays in the list,
 * dimmed, so it can be shown again.
 */
@Composable
private fun ArrangeMenuOverlay(
    menu: ContentMenu,
    onSave: (MenuLayout) -> Unit,
    onCancel: () -> Unit,
) {
    val vm: SettingsViewModel = koinViewModel()
    val refs = catalogue(menu)
    val shipped = refs.map { it.key }
    // Wait for DataStore's first real value before seeding. An eager empty placeholder would make
    // every reopened editor show the shipped order even when a custom order is already saved.
    var keys by remember(menu) { mutableStateOf(emptyList<String>()) }
    var hidden by remember(menu) { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(menu) {
        val saved = vm.menuLayout(menu).first()
        keys = applyMenuOrder(shipped, saved.order) { it }
        hidden = saved.hidden
    }
    // -1 = nothing picked up; otherwise the index Up/Down carries.
    var picked by remember(menu) { mutableIntStateOf(-1) }
    val pickedFocus = remember { FocusRequester() }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(keys.isEmpty()) { if (keys.isNotEmpty()) { kotlinx.coroutines.delay(60); runCatching { firstFocus.requestFocus() } } }
    // The carried action is a different composable at its new index, so focus has to follow it.
    LaunchedEffect(picked, keys) { if (picked >= 0) runCatching { pickedFocus.requestFocus() } }
    fun move(delta: Int) {
        val to = picked + delta
        if (picked < 0 || to !in keys.indices) return
        keys = keys.toMutableList().apply { add(to, removeAt(picked)) }
        picked = to
    }

    val list: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
        Column(
            // The rows scroll inside the panel; Save and Cancel stay under them, on screen.
            Modifier.weight(1f, fill = false).trapAllFocusExit().focusGroup()
                // Only while an action is picked up: Up/Down carry it instead of moving focus.
                .onPreviewKeyEvent { event ->
                    if (picked < 0 || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionUp -> { move(-1); true }
                        Key.DirectionDown -> { move(1); true }
                        else -> false
                    }
                },
            verticalArrangement = Arrangement.spacedBy(2.mpx),
        ) {
            Text(stringResource(R.string.settings_content_menus_hint), style = tv.own.myowntv.ui.theme.stageText(15, 500), color = tv.own.myowntv.ui.theme.StageColors.Muted, modifier = Modifier.padding(bottom = 4.mpx))
            Text(stringResource(R.string.settings_content_menus_note), style = tv.own.myowntv.ui.theme.stageText(15, 500), color = tv.own.myowntv.ui.theme.StageColors.Dim, modifier = Modifier.padding(bottom = 10.mpx))
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.mpx)) {
            keys.forEachIndexed { index, key ->
                val ref = refs.first { it.key == key }
                ArrangeMenuRow(
                    number = index + 1,
                    label = stringResource(ref.labelRes),
                    picked = picked == index,
                    hidden = key in hidden,
                    onPress = {
                        when {
                            picked == index -> picked = -1
                            picked < 0 -> hidden = if (key in hidden) hidden - key else hidden + key
                        }
                    },
                    onPick = { picked = if (picked == index) -1 else index },
                    modifier = when {
                        picked == index -> Modifier.focusRequester(pickedFocus)
                        picked < 0 && index == 0 -> Modifier.focusRequester(firstFocus)
                        else -> Modifier
                    },
                )
            }
            }
            Row(Modifier.fillMaxWidth().padding(top = 14.mpx), horizontalArrangement = Arrangement.spacedBy(12.mpx, Alignment.End)) {
                tv.own.myowntv.ui.stage.StageButton(stringResource(R.string.common_cancel), onClick = onCancel, height = 52.mpx, textSize = 18)
                tv.own.myowntv.ui.stage.StageButton(stringResource(R.string.common_save), onClick = { onSave(MenuLayout(order = keys.takeIf { it != shipped }.orEmpty(), hidden = hidden)) }, height = 52.mpx, textSize = 18, tinted = true)
            }
        }
    }
    if (panelEditor(onCancel) { list() }) return
    tv.own.myowntv.ui.stage.StagePopup(onDismiss = onCancel, title = menuTitle(menu), scroll = false) { list() }
}

/**
 * One action in the arrange overlay: accent-filled with a move sign while it is picked up, dimmed
 * while hidden, with Shown or Hidden at the end. OK ([onPress]) shows or hides it, or puts down the
 * action being carried; a held OK ([onPick]) picks it up.
 */
@Composable
private fun ArrangeMenuRow(
    number: Int,
    label: String,
    picked: Boolean,
    hidden: Boolean,
    onPress: () -> Unit,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // A held OK raises the long press first and the plain click when the key is finally released,
    // which would pick the action up and immediately put it back down.
    var longAt by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    val a = tv.own.myowntv.ui.theme.stageAccent
    tv.own.myowntv.ui.stage.StageSurface(
        onClick = { if (android.os.SystemClock.uptimeMillis() - longAt > 800) onPress() },
        onLongClick = { longAt = android.os.SystemClock.uptimeMillis(); onPick() },
        radius = 14.mpx,
        focusStyle = tv.own.myowntv.ui.stage.StageFocus.FX,
        // The action being carried stays lit, focused or not.
        idle = if (picked) Modifier.background(a.accent.copy(alpha = 0.22f), RoundedCornerShape(14.mpx)) else Modifier,
        modifier = modifier.fillMaxWidth().height(50.mpx),
    ) { focused ->
        Row(Modifier.padding(horizontal = 14.mpx), horizontalArrangement = Arrangement.spacedBy(12.mpx), verticalAlignment = Alignment.CenterVertically) {
            Text(if (picked) stringResource(R.string.setup_move_indicator) else number.toString(), style = tv.own.myowntv.ui.theme.stageText(17, 700), color = if (picked) a.accent else tv.own.myowntv.ui.theme.StageColors.Dim, modifier = Modifier.width(26.mpx))
            Text(
                label, style = tv.own.myowntv.ui.theme.stageText(18, 600),
                color = when {
                    picked || focused -> tv.own.myowntv.ui.theme.StageColors.Text
                    hidden -> tv.own.myowntv.ui.theme.StageColors.Dim
                    else -> tv.own.myowntv.ui.theme.StageColors.Muted
                },
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(if (hidden) R.string.settings_hidden else R.string.settings_home_shown),
                style = tv.own.myowntv.ui.theme.stageText(16, 600),
                color = if (hidden) tv.own.myowntv.ui.theme.StageColors.Dim else tv.own.myowntv.ui.theme.StageColors.Muted,
            )
        }
    }
}
