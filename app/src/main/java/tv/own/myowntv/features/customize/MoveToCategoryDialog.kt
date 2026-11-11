package tv.own.myowntv.features.customize
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

import tv.own.myowntv.ui.theme.StageColors
import tv.own.myowntv.ui.theme.stageText
import androidx.compose.foundation.layout.heightIn
import tv.own.myowntv.ui.components.MyOwnTVIcon
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Text
import tv.own.myowntv.R
import tv.own.myowntv.ui.theme.mpx

/**
 * One destination row in the "Move to…" dialog: a user-created custom category plus how
 * many items it currently holds. Exposed here (not in the view models) so both Customize
 * screens share the same dialog and the same target model.
 */
data class MoveTarget(val id: String, val displayName: String, val count: Int)

/**
 * Shared "Move to…" dialog: pick one of the user's custom combined categories (or create
 * a new one via [onNewCategory], which swaps this dialog for a name prompt at the call site), decide
 * whether the item stays in its origin as well, then Move. The Move button is disabled until a target
 * is selected. Scrim/trap/BackHandler match [RangeHideDialog] so D-pad focus and Back behave like the
 * other dialogs; the "＋ New category…" row owns the initial focus.
 */
@Composable
fun MoveToCategoryDialog(
    moveTargets: List<MoveTarget>,
    originName: String,
    onNewCategory: () -> Unit,
    onMove: (targetId: String, keepInOrigin: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedTarget by remember { mutableStateOf<String?>(null) }
    var keepInOrigin by remember { mutableStateOf(false) }
    val newCatFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { newCatFocus.requestFocus() } }
    BackHandler { onDismiss() }
    tv.own.myowntv.ui.stage.StagePopup(
        onDismiss = onDismiss,
        title = stringResource(R.string.settings_move_category_title),
        body = stringResource(R.string.settings_move_category_description, originName),
        width = 880.mpx,
        buttons = {
            tv.own.myowntv.ui.stage.StagePopupButton(stringResource(R.string.common_cancel), onClick = onDismiss, primary = false)
            tv.own.myowntv.ui.stage.StagePopupButton(
                stringResource(R.string.settings_move_category_action),
                onClick = { selectedTarget?.let { onMove(it, keepInOrigin) } },
                enabled = selectedTarget != null,
            )
        },
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.mpx),
            // Do not trap the list's vertical exit: Down from its last destination must
            // reach the keep-in-origin toggle and the footer buttons. The popup already
            // prevents focus from escaping.
            modifier = Modifier.heightIn(max = 420.mpx),
        ) {
            item(key = "new") {
                tv.own.myowntv.ui.stage.StagePopupOption(
                    title = stringResource(R.string.settings_move_category_new),
                    onClick = onNewCategory,
                    modifier = Modifier.focusRequester(newCatFocus),
                    leading = { tv.own.myowntv.ui.stage.StagePopupIcon(MyOwnTVIcon.ADD) },
                )
            }
            items(moveTargets, key = { it.id }) { target ->
                val selected = selectedTarget == target.id
                tv.own.myowntv.ui.stage.StagePopupOption(
                    title = target.displayName,
                    onClick = { selectedTarget = target.id },
                    chosen = selected,
                    leading = { f -> tv.own.myowntv.ui.stage.StagePopupRadio(selected, f) },
                    trailing = { Text(stringResource(R.string.common_number_grouped, target.count), style = stageText(18, 700), color = StageColors.Muted) },
                )
            }
        }
        tv.own.myowntv.ui.stage.StagePopupDivider()
        // "Keep in origin" — ticked = copy (item stays in its provider folder / favorites).
        tv.own.myowntv.ui.stage.StagePopupOption(
            title = stringResource(R.string.settings_move_category_keep, originName),
            onClick = { keepInOrigin = !keepInOrigin },
            leading = { f -> tv.own.myowntv.ui.stage.StagePopupCheck(keepInOrigin, f) },
        )
    }
}
