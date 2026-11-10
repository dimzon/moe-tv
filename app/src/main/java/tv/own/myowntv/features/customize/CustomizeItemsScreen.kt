package tv.own.myowntv.features.customize
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

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.Text
import tv.own.myowntv.R
import tv.own.myowntv.ui.components.MyOwnTVIcon
import tv.own.myowntv.ui.theme.mpx
import tv.own.myowntv.features.settings.StageFullPage
import tv.own.myowntv.features.settings.StageSettingRow
import tv.own.myowntv.features.settings.SettingValue
import tv.own.myowntv.features.settings.SettingHelp
import tv.own.myowntv.features.settings.StageAction
import tv.own.myowntv.features.settings.StageActionColumn
import tv.own.myowntv.features.settings.SpanHelpBlock
import tv.own.myowntv.features.settings.PickerDialog
import tv.own.myowntv.features.settings.SettingsViewModel
import tv.own.myowntv.ui.components.chNavPaging
import tv.own.myowntv.ui.components.jumpLazyListTo
import tv.own.myowntv.ui.components.TextInputDialog
import tv.own.myowntv.ui.components.trapVerticalFocusExit
import tv.own.owntv.core.customize.MoveKind
import tv.own.owntv.core.customize.SpanSelector

/**
 * Category items screen — shows every channel in a category, including hidden
 * ones (marked "Hidden"), with hide/show, rename, reorder controls and span selection.
 *
 * Reached from [CustomizeScreen] by pressing OK on a category name.
 */
@Composable
fun CustomizeItemsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val parentVm: CustomizeViewModel = koinViewModel()
    val vm: CustomizeItemsViewModel = koinViewModel()
    val selectedCategory by parentVm.selectedCategory.collectAsStateWithLifecycle()
    val catInfo by vm.catInfo.collectAsStateWithLifecycle()
    val rangeAnchorKey by vm.rangeAnchorKey.collectAsStateWithLifecycle()
    val rangeMode by vm.rangeMode.collectAsStateWithLifecycle()
    val rangeEndKey by vm.rangeEndKey.collectAsStateWithLifecycle()
    val rangeSelectedKeys by vm.rangeSelectedKeys.collectAsStateWithLifecycle()
    val visibilityFilter by vm.visibilityFilter.collectAsStateWithLifecycle()

    // Propagate the category info from the parent ViewModel into the items ViewModel.
    val ctx = parentVm.ctxForItems()
    LaunchedEffect(selectedCategory) {
        val row = selectedCategory
        if (row != null && ctx != null) {
            vm.open(
                CustomizeItemsViewModel.CatInfo(
                    categoryId = ctx.categoryId,
                    contextKey = row.key,
                    mediaType = ctx.mediaType,
                    sourceIds = ctx.sourceIds,
                )
            )
        } else {
            vm.close()
        }
    }

    if (selectedCategory == null) return

    val items = vm.items.collectAsLazyPagingItems()
    val settingsVm: SettingsViewModel = koinViewModel()
    val chNavEnabled by settingsVm.chNavEnabled.collectAsStateWithLifecycle()
    val chNavUpSkip by settingsVm.chNavUpSkip.collectAsStateWithLifecycle()
    val chNavDownSkip by settingsVm.chNavDownSkip.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var listPaneFocused by remember { mutableStateOf(false) }
    var focusedItemIndex by remember { mutableIntStateOf(0) }
    var renaming by remember { mutableStateOf<CustomizeItemRow?>(null) }
    var showFilterPicker by remember { mutableStateOf(false) }
    // The item whose Hide button was clicked to close a range — opens the Show/Hide/Cancel prompt.
    var rangeEnd by remember { mutableStateOf<CustomizeItemRow?>(null) }
    // The item the "Move to…" dialog is moving; creatingCategory swaps the dialog for the
    // new-category name prompt.
    var movingItem by remember { mutableStateOf<CustomizeItemRow?>(null) }
    var creatingCategory by remember { mutableStateOf(false) }
    val backFocus = remember { FocusRequester() }
    val filterFocus = remember { FocusRequester() }
    val rowFocusers = remember { mutableMapOf<String, FocusRequester>() }
    val actionsFocus = remember { FocusRequester() }
    // Focus the row that opened a dialog (rename / move) when it closes (a dialog close can land
    // focus on the screen's first focusable otherwise).
    var dialogReturn by tv.own.myowntv.ui.components.rememberDialogFocusRestore(
        anyDialogOpen = showFilterPicker || renaming != null || rangeEnd != null || movingItem != null || creatingCategory,
    )
    // Focus the first row once the screen opens (rows arrive via paging, so wait for them).
    var firstLanding by remember { mutableStateOf(true) }

    BackHandler { if (rangeAnchorKey != null) vm.cancelRange() else onBack() }

    LaunchedEffect(items.itemCount) {
        if (firstLanding && items.itemCount > 0) {
            firstLanding = false
            kotlinx.coroutines.delay(60)
            items[0]?.let { rowFocusers[it.key] }?.let { runCatching { it.requestFocus() } }
        } else if (firstLanding && items.itemCount == 0) {
            // Empty custom categories are valid. Their screen still needs a deterministic focus
            // owner while Paging is empty (and the first row takes over later if data arrives).
            kotlinx.coroutines.delay(60)
            runCatching { backFocus.requestFocus() }
        } else if (!firstLanding && focusedItemIndex >= items.itemCount) {
            // The focused row left the list (removed from this category): its actions must not stay
            // in the panel, and focus must not stay on them.
            kotlinx.coroutines.delay(60)
            if (items.itemCount == 0) {
                runCatching { backFocus.requestFocus() }
            } else {
                items[items.itemCount - 1]?.let { rowFocusers[it.key] }?.let { runCatching { it.requestFocus() } }
            }
        }
    }

    // Customize › the category. Its tool row above the list, the items as rows and the panel
    // with the focused item's actions and the span help.
    val about = stringResource(R.string.settings_customize_channels_description)
    StageFullPage(
        parents = listOf(stringResource(R.string.settings_customize_title)),
        settingsRoot = false,
        title = selectedCategory!!.displayName,
        count = "",
        onBack = onBack,
        modifier = modifier,
        handleBack = false,
        toolbar = {
            Spacer(Modifier.weight(1f))
            tv.own.myowntv.ui.stage.StageTool(
                stringResource(
                    R.string.settings_customize_filter_button,
                    stringResource(
                        when (visibilityFilter) {
                            CustomizeVisibilityFilter.ALL -> R.string.settings_customize_filter_all
                            CustomizeVisibilityFilter.VISIBLE -> R.string.settings_customize_filter_visible
                            CustomizeVisibilityFilter.HIDDEN -> R.string.settings_customize_filter_hidden
                        },
                    ),
                ),
                onClick = { dialogReturn = filterFocus; showFilterPicker = true },
                icon = MyOwnTVIcon.EYE_OFF, boxed = true,
                modifier = Modifier.focusRequester(filterFocus).focusRequester(backFocus),
            )
        },
        list = { pos ->
            // The page's own panel: with the last row gone nothing else replaces its actions.
            val pagePanel = tv.own.myowntv.features.settings.LocalSettingsPanel.current
            val emptied = !firstLanding && items.itemCount == 0
            LaunchedEffect(emptied) { if (emptied) pagePanel?.help = null }
            Column(pos) {
                if (rangeAnchorKey != null) {
                    StageSettingRow(
                        icon = MyOwnTVIcon.LAYERS,
                        title = when {
                            rangeMode == SpanSelector.Mode.HIDE -> stringResource(R.string.settings_customize_range_hide_start)
                            rangeMode == SpanSelector.Mode.RENAME -> stringResource(R.string.settings_customize_range_rename_start)
                            rangeEndKey == null -> stringResource(R.string.settings_customize_range_move_start)
                            else -> pluralStringResource(R.plurals.settings_customize_move_items_selected, rangeSelectedKeys.size, rangeSelectedKeys.size)
                        },
                        desc = null,
                        value = SettingValue.Action(stringResource(R.string.common_cancel)),
                        onClick = { vm.cancelRange() },
                        marked = true,
                    )
                    Spacer(Modifier.height(6.mpx))
                }
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(6.mpx),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 40.mpx),
                    modifier = Modifier
                        .fillMaxSize()
                        .trapVerticalFocusExit()
                        .onFocusChanged { listPaneFocused = it.hasFocus }
                        .chNavPaging(
                            enabled = chNavEnabled,
                            upSkip = chNavUpSkip,
                            downSkip = chNavDownSkip,
                            isFocused = { listPaneFocused },
                            lastIndex = { items.itemCount - 1 },
                            currentTargetIndex = { focusedItemIndex },
                            onJumpToIndex = { idx ->
                                scope.jumpLazyListTo(listState, idx) {
                                    items[idx]?.let { rowFocusers[it.key] }?.let { runCatching { it.requestFocus() } }
                                }
                            },
                        ),
                ) {
                    items(
                        count = items.itemCount,
                        key = items.itemKey { it.key },
                        contentType = items.itemContentType(),
                    ) { index ->
                        val row = items[index] ?: return@items
                        val inMoveRange = rangeAnchorKey != null && rangeMode == SpanSelector.Mode.MOVE
                        val inRenameRange = rangeAnchorKey != null && rangeMode == SpanSelector.Mode.RENAME
                        val isInSpan = row.key in rangeSelectedKeys || renaming?.key == row.key
                        ItemRow(
                            row = row,
                            about = about,
                            inRangeMode = rangeAnchorKey != null && rangeMode == SpanSelector.Mode.HIDE,
                            inRenameRange = inRenameRange,
                            isInSpan = isInSpan,
                            focusRequester = remember(row.key) { rowFocusers.getOrPut(row.key) { FocusRequester() } },
                            upFocusRequester = backFocus.takeIf { index == 0 },
                            actionsFocus = actionsFocus,
                            keepPanel = index == focusedItemIndex,
                            onRowFocused = { focusedItemIndex = index },
                            // While a move span is active every arrow acts on the whole block, not this row.
                            onMoveUp = { if (inMoveRange) vm.moveRange(row, MoveKind.UP) else vm.move(row, up = true) },
                            onMoveDown = { if (inMoveRange) vm.moveRange(row, MoveKind.DOWN) else vm.move(row, up = false) },
                            onMoveTop = { if (inMoveRange) vm.moveRange(row, MoveKind.TOP) else vm.moveToEdge(row, top = true) },
                            onMoveBottom = { if (inMoveRange) vm.moveRange(row, MoveKind.BOTTOM) else vm.moveToEdge(row, top = false) },
                            onMoveLongPress = { dialogReturn = rowFocusers[row.key]; vm.beginMoveRange(row) },
                            onRename = { dialogReturn = rowFocusers[row.key]; renaming = row },
                            onRenameLongPress = { dialogReturn = rowFocusers[row.key]; vm.beginRenameRange(row) },
                            onPickRenameEnd = {
                                if (row.key == rangeAnchorKey) {
                                    vm.cancelRange()
                                } else {
                                    dialogReturn = rowFocusers[row.key]
                                    // No active span (anchor vanished?) — fall back to the single rename.
                                    if (vm.finishRenameRange(row) == null) renaming = row
                                }
                            },
                            onMove = { dialogReturn = rowFocusers[row.key]; movingItem = row },
                            onRemoveFromCategory = if (catInfo?.isCustom == true) ({ vm.removeFromCategory(row) }) else null,
                            onToggleHidden = { vm.setItemHidden(row, !row.hidden) },
                            onHideLongPress = { dialogReturn = rowFocusers[row.key]; vm.beginRange(row) },
                            onPickRangeEnd = {
                                if (row.key == rangeAnchorKey) vm.cancelRange()
                                else {
                                    dialogReturn = rowFocusers[row.key]
                                    rangeEnd = row
                                }
                            },
                        )
                    }
                }
            }
        },
    )

    if (showFilterPicker) {
        PickerDialog(
            title = stringResource(R.string.settings_customize_filter_title),
            options = listOf(
                CustomizeVisibilityFilter.ALL.name to stringResource(R.string.settings_customize_filter_all),
                CustomizeVisibilityFilter.VISIBLE.name to stringResource(R.string.settings_customize_filter_visible),
                CustomizeVisibilityFilter.HIDDEN.name to stringResource(R.string.settings_customize_filter_hidden),
            ),
            selected = visibilityFilter.name,
            onSelect = { value ->
                CustomizeVisibilityFilter.entries.firstOrNull { it.name == value }
                    ?.let(vm::setVisibilityFilter)
                scope.launch { listState.scrollToItem(0) }
                showFilterPicker = false
            },
            onDismiss = { showFilterPicker = false },
        )
    }

    renaming?.let { row ->
        TextInputDialog(
            title = stringResource(R.string.content_rename_channel),
            initial = row.displayName,
            hint = stringResource(R.string.settings_customize_rename_item_hint, row.originalName),
            onConfirm = { vm.renameItem(row, it.takeIf { t -> t.isNotBlank() }); renaming = null },
            onDismiss = { renaming = null },
        )
    }

    rangeEnd?.let { row ->
        val count = vm.keysInRange(row)?.size ?: 0
        ItemsRangeHideDialog(
            count = count,
            onHide = { vm.applyRange(row, hidden = true); rangeEnd = null },
            onShow = { vm.applyRange(row, hidden = false); rangeEnd = null },
            onDismiss = { vm.cancelRange(); rangeEnd = null },
        )
    }

    // Bulk rename: choice popup, rule builder, review, restore-confirm, refusal.
    // Its popups restore D-pad focus to the row that opened the flow when the whole flow closes.
    BulkRenameFlow(vm.bulk, returnFocus = dialogReturn)

    // Move to…: pick a combined category; "＋ New category…" swaps this dialog for the
    // name prompt, then the move dialog re-opens with the fresh category listed.
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
        movingItem?.let { row ->
            MoveToCategoryDialog(
                moveTargets = moveTargets,
                originName = selectedCategory?.displayName ?: stringResource(R.string.settings_customize_this_category),
                onNewCategory = { creatingCategory = true },
                onMove = { targetId, keepInOrigin ->
                    vm.moveTo(row, targetId, keepInOrigin)
                    movingItem = null
                },
                onDismiss = { movingItem = null },
            )
        }
    }
}

/** Confirms a range select over ITEMS: hide or show every item in the chosen span (or cancel). */
@Composable
private fun ItemsRangeHideDialog(count: Int, onHide: () -> Unit, onShow: () -> Unit, onDismiss: () -> Unit) {
    val hideFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { hideFocus.requestFocus() } }
    BackHandler { onDismiss() }
    tv.own.myowntv.ui.stage.StagePopup(
        onDismiss = onDismiss,
        title = stringResource(R.string.settings_customize_hide_show_items),
        body = pluralStringResource(R.plurals.settings_customize_selected_items, count, count),
        width = 864.mpx,
        buttons = {
            tv.own.myowntv.ui.stage.StagePopupButton(stringResource(R.string.common_cancel), onClick = onDismiss, primary = false)
            tv.own.myowntv.ui.stage.StagePopupButton(stringResource(R.string.common_show), onClick = onShow, primary = false)
            tv.own.myowntv.ui.stage.StagePopupButton(stringResource(R.string.common_hide), onClick = onHide, modifier = Modifier.focusRequester(hideFocus))
        },
    )
}

@Composable
private fun ItemRow(
    row: CustomizeItemRow,
    /** The page's explanation, shown in the panel. */
    about: String,
    inRangeMode: Boolean,
    inRenameRange: Boolean,
    isInSpan: Boolean,
    focusRequester: FocusRequester,
    upFocusRequester: FocusRequester?,
    actionsFocus: FocusRequester,
    keepPanel: Boolean,
    onRowFocused: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMoveTop: () -> Unit,
    onMoveBottom: () -> Unit,
    onMoveLongPress: () -> Unit,
    onRename: () -> Unit,
    onRenameLongPress: () -> Unit,
    onPickRenameEnd: () -> Unit,
    // "Move to…": send this item into a user's combined category.
    onMove: () -> Unit,
    // Only in a custom category: take the item out of it alone.
    onRemoveFromCategory: (() -> Unit)?,
    onToggleHidden: () -> Unit,
    onHideLongPress: () -> Unit,
    onPickRangeEnd: () -> Unit,
) {
    val meta = listOfNotNull(
        row.hidden.takeIf { it }?.let { stringResource(R.string.settings_customize_hidden) },
        row.renamed.takeIf { it }?.let { stringResource(R.string.settings_customize_item_was, row.originalName) },
    ).joinToString(stringResource(R.string.settings_customize_metadata_separator))
    // A held OK on a Move, Rename or Hide action anchors a span; a normal press on a second row picks its end.
    val actions = listOfNotNull(
        StageAction(MyOwnTVIcon.PAGE_TOWARD_FIRST, stringResource(R.string.settings_customize_move_top), onMoveTop, onMoveLongPress),
        StageAction(MyOwnTVIcon.CHEVRON_UP, stringResource(R.string.settings_row_menu_move_up), onMoveUp, onMoveLongPress),
        StageAction(MyOwnTVIcon.CHEVRON_DOWN, stringResource(R.string.settings_row_menu_move_down), onMoveDown, onMoveLongPress),
        StageAction(MyOwnTVIcon.PAGE_TOWARD_LAST, stringResource(R.string.settings_customize_move_bottom), onMoveBottom, onMoveLongPress),
        // Channels get a per-row Rename (or a renamed span by holding OK).
        StageAction(MyOwnTVIcon.PENCIL, stringResource(R.string.settings_customize_rename), { if (inRenameRange) onPickRenameEnd() else onRename() }, onRenameLongPress),
        StageAction(MyOwnTVIcon.FOLDER, stringResource(R.string.settings_customize_move_to), onMove),
        onRemoveFromCategory?.let { StageAction(MyOwnTVIcon.CLOSE, stringResource(R.string.content_remove_from_category), it) },
        StageAction(
            MyOwnTVIcon.EYE_OFF,
            stringResource(if (row.hidden) R.string.common_show else R.string.common_hide),
            { if (inRangeMode) onPickRangeEnd() else onToggleHidden() },
            onHideLongPress,
        ),
    )
    StageSettingRow(
        icon = MyOwnTVIcon.LIVE_TV,
        title = row.displayName,
        desc = meta.ifBlank { null },
        value = if (row.hidden) SettingValue.Custom {
            androidx.tv.material3.Text(stringResource(R.string.settings_customize_hidden), style = tv.own.myowntv.ui.theme.stageText(18, 700), color = tv.own.myowntv.ui.theme.StageColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else null,
        onClick = { runCatching { actionsFocus.requestFocus() } },
        marked = isInSpan,
        keepPanel = keepPanel,
        help = SettingHelp(
            title = row.displayName,
            text = about,
            hints = listOf(
                stringResource(R.string.common_ok) to stringResource(R.string.settings_key_actions),
                stringResource(R.string.common_back) to stringResource(R.string.settings_customize_title),
            ),
            extra = { StageActionColumn(actions, focusRequester, actionsFocus) },
            footer = { SpanHelpBlock() },
        ),
        modifier = Modifier
            .focusRequester(focusRequester)
            .then(if (upFocusRequester != null) Modifier.upTo(upFocusRequester) else Modifier)
            .focusProperties { right = actionsFocus }
            .onFocusChanged { if (it.isFocused) onRowFocused() },
    )
}
