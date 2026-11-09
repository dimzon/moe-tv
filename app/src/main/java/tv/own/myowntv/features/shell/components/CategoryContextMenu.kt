package tv.own.myowntv.features.shell.components
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
import tv.own.owntv.player.ffmpeg.*

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
    val nothing = lines?.isEmpty() == true
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    // Again when the lines arrive: the first button changes from Restore to Cancel (or back).
    LaunchedEffect(lines?.isEmpty()) {
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
                    stringResource(
                        if (nothing) tv.own.myowntv.R.string.content_category_restore_nothing
                        else tv.own.myowntv.R.string.content_category_restore_question,
                    ),
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
                // Restore is only offered once the lines are known and there is something to undo.
                if (!lines.isNullOrEmpty()) {
                    tv.own.myowntv.ui.stage.StageMenuItem(
                        restoreLabel ?: stringResource(tv.own.myowntv.R.string.common_reset), onClick = onRestore,
                        icon = MyOwnTVIcon.REFRESH, modifier = Modifier.focusRequester(focus),
                    )
                }
                tv.own.myowntv.ui.stage.StageMenuItem(
                    stringResource(tv.own.myowntv.R.string.common_cancel), onClick = onDismiss,
                    icon = MyOwnTVIcon.CLOSE,
                    modifier = if (lines.isNullOrEmpty()) Modifier.focusRequester(focus) else Modifier,
                )
            }
        }
    }
}
