package tv.own.myowntv.features.shell.components

import tv.own.myowntv.ui.theme.mpx

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Text
import tv.own.myowntv.ui.components.longPressMenuGuard
import tv.own.myowntv.ui.components.MyOwnTVIcon
import tv.own.myowntv.ui.components.MyOwnTVPopup
import tv.own.myowntv.ui.components.trapAllFocusExit

/**
 * Long-press quick actions for a category (move / hide / lock / restore). Lock with PIN or Remove lock
 * is offered when [locked] is set, saying which of the two the category is. Restore is offered when
 * [resetLabel] is set and asks first, in a popup of its own listing [resetLines] (null while they are
 * still being worked out, empty when there is nothing to undo) — it has no undo of its own.
 */
@Composable
fun CategoryContextMenu(
    categoryName: String,
    canHide: Boolean,
    canMove: Boolean = true,
    onHide: () -> Unit,
    onMove: () -> Unit,
    onDismiss: () -> Unit,
    resetLabel: String? = null,
    resetLines: List<String>? = null,
    onReset: () -> Unit = {},
    locked: Boolean? = null,
    onToggleLock: () -> Unit = {},
) {
    val focus = remember { FocusRequester() }
    var confirmingReset by remember { mutableStateOf(false) }
    // Every entry here organises the list, so each asks for the Settings PIN first when its
    // long-press switch is on.
    val gate: tv.own.myowntv.features.settings.lock.MenuChangeGate = org.koin.compose.koinInject()
    // The menu steps aside while the confirmation is up, and takes focus back when it returns.
    if (!confirmingReset) {
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    androidx.activity.compose.BackHandler { onDismiss() }
    // The long-press menu look, as the channel and settings-row menus draw it.
    tv.own.myowntv.ui.components.MyOwnTVPopup(onDismissRequest = onDismiss, stageLayout = true) {
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxSize().background(tv.own.myowntv.ui.theme.StageColors.Scrim).longPressMenuGuard(),
            contentAlignment = Alignment.Center,
        ) {
            tv.own.myowntv.ui.stage.StageMenu(Modifier.width(520.mpx).trapAllFocusExit().focusGroup()) {
                tv.own.myowntv.ui.stage.StageMenuHeader(title = categoryName, subtitle = null)
                if (canMove) {
                    tv.own.myowntv.ui.stage.StageMenuItem(
                        stringResource(tv.own.myowntv.R.string.content_move), onClick = { gate.run(onMove) },
                        icon = MyOwnTVIcon.MOVE, modifier = Modifier.focusRequester(focus),
                    )
                }
                if (canHide) {
                    tv.own.myowntv.ui.stage.StageMenuItem(
                        stringResource(tv.own.myowntv.R.string.common_hide), onClick = { gate.run(onHide) },
                        icon = MyOwnTVIcon.EYE_OFF, modifier = if (!canMove) Modifier.focusRequester(focus) else Modifier,
                    )
                }
                if (locked != null) {
                    tv.own.myowntv.ui.stage.StageMenuItem(
                        stringResource(if (locked) tv.own.myowntv.R.string.settings_customize_remove_lock else tv.own.myowntv.R.string.content_category_lock),
                        onClick = { gate.run(onToggleLock) }, icon = MyOwnTVIcon.LOCK,
                        modifier = if (!canMove && !canHide) Modifier.focusRequester(focus) else Modifier,
                    )
                }
                if (resetLabel != null) {
                    tv.own.myowntv.ui.stage.StageMenuItem(
                        resetLabel, onClick = { gate.run { confirmingReset = true } },
                        icon = MyOwnTVIcon.REFRESH,
                        modifier = if (!canMove && !canHide && locked == null) Modifier.focusRequester(focus) else Modifier,
                    )
                }
                tv.own.myowntv.ui.stage.StageMenuItem(stringResource(tv.own.myowntv.R.string.common_cancel), onClick = onDismiss, icon = MyOwnTVIcon.CLOSE)
            }
        }
    }
    }
    if (confirmingReset) {
        RestoreConfirmPopup(
            categoryName = categoryName,
            restoreLabel = resetLabel,
            lines = resetLines,
            onRestore = onReset,
            onDismiss = { confirmingReset = false },
        )
    }
}

/**
 * What restoring a category will change, asked before it does. A popup of its own rather than a swap
 * inside the menu: a freshly opened popup hands focus to its first button before the highlight is
 * listening, which is exactly how the menu itself opens — swapping content in place left the one
 * remaining button focused but not drawn as such.
 */
@Composable
private fun RestoreConfirmPopup(
    categoryName: String,
    restoreLabel: String?,
    lines: List<String>?,
    onRestore: () -> Unit,
    onDismiss: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    LaunchedEffect(Unit) {
        withFrameNanos { }
        focusManager.clearFocus(force = true)
        withFrameNanos { }
        runCatching { focus.requestFocus() }
    }
    androidx.activity.compose.BackHandler { onDismiss() }
    tv.own.myowntv.ui.components.MyOwnTVPopup(onDismissRequest = onDismiss, stageLayout = true) {
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxSize().background(tv.own.myowntv.ui.theme.StageColors.Scrim).longPressMenuGuard(),
            contentAlignment = Alignment.Center,
        ) {
            tv.own.myowntv.ui.stage.StageMenu(Modifier.width(520.mpx).trapAllFocusExit().focusGroup()) {
                tv.own.myowntv.ui.stage.StageMenuHeader(title = categoryName, subtitle = null)
                Text(
                    stringResource(tv.own.myowntv.R.string.content_category_restore_question),
                    style = tv.own.myowntv.ui.theme.stageText(18, 600),
                    color = tv.own.myowntv.ui.theme.StageColors.MenuItemText,
                    modifier = Modifier.padding(start = 14.mpx, end = 14.mpx, top = 8.mpx, bottom = 12.mpx),
                )
                lines?.forEach { line ->
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.padding(start = 22.mpx, end = 14.mpx, bottom = 10.mpx),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.mpx),
                    ) {
                        androidx.compose.foundation.layout.Box(
                            Modifier.size(6.mpx).clip(CircleShape)
                                .background(tv.own.myowntv.ui.theme.StageColors.Muted),
                        )
                        Text(line, style = tv.own.myowntv.ui.theme.stageText(17, 500), color = tv.own.myowntv.ui.theme.StageColors.Muted)
                    }
                }
                // Room between what will change and what to do about it.
                androidx.compose.foundation.layout.Spacer(Modifier.height(18.mpx))
                // Restoring an already-default category changes nothing — that is success, so the
                // button is offered from the start, while the preview lines are still loading.
                tv.own.myowntv.ui.stage.StageMenuItem(
                    restoreLabel ?: stringResource(tv.own.myowntv.R.string.common_reset), onClick = onRestore,
                    icon = MyOwnTVIcon.REFRESH, modifier = Modifier.focusRequester(focus),
                )
                tv.own.myowntv.ui.stage.StageMenuItem(
                    stringResource(tv.own.myowntv.R.string.common_cancel), onClick = onDismiss,
                    icon = MyOwnTVIcon.CLOSE,
                )
            }
        }
    }
}
