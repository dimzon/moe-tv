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
import tv.own.owntv.core.parser.*
import tv.own.owntv.core.player.*
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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import tv.own.myowntv.R
import tv.own.owntv.core.customize.CategoryRestorePlan
import tv.own.owntv.core.customize.categoryRestoreSummary
import tv.own.owntv.core.live.LiveKey
import tv.own.owntv.core.categorylock.SealedCategoryAction
import tv.own.myowntv.features.live.LiveRailItem

/**
 * The long-press menu of a category in the Live TV, Movies and Series lists, wired to what each item
 * does to [item]. [onClose] runs after every action but an open Move, and on Back. Lock with PIN / Remove lock goes to
 * [onToggleLock]. Only provider folders can be locked: every other list finds a locked item by its
 * provider folder, so a lock on a custom category would guard that one list and nothing else. A custom
 * one locked already (by a restored backup) still offers Remove lock. On a sealed category Hide, Move
 * and Restore go to [onSealedAction], which asks the PIN first.
 */
@Composable
internal fun RailCategoryMenu(
    item: LiveRailItem,
    name: String,
    restorePlan: suspend (LiveKey) -> CategoryRestorePlan,
    onHide: (LiveKey) -> Unit,
    onMove: (LiveKey) -> Unit,
    onRestore: (LiveKey) -> Unit,
    onToggleLock: (LiveKey) -> Unit,
    onSealedAction: (LiveKey, SealedCategoryAction) -> Unit,
    onClose: () -> Unit,
) {
    val real = item.key is LiveKey.Folder || item.key is LiveKey.Custom
    val plan by produceState<CategoryRestorePlan?>(null, item.key) { value = restorePlan(item.key) }
    val guarded: (SealedCategoryAction, (LiveKey) -> Unit) -> Unit = { action, run ->
        if (item.sealed) onSealedAction(item.key, action) else run(item.key)
        onClose()
    }
    CategoryContextMenu(
        categoryName = name,
        canHide = real,
        canMove = real,
        onHide = { guarded(SealedCategoryAction.HIDE, onHide) },
        // The menu stays up until the move list is loaded, then hands over to it: the screen closes
        // it in the frame the list opens. A sealed category asks the PIN first, so that path closes.
        onMove = { if (item.sealed) guarded(SealedCategoryAction.MOVE, onMove) else onMove(item.key) },
        // Always offered: the plan is read in the background and only fills in the confirmation.
        resetLabel = stringResource(if (item.key is LiveKey.Custom) R.string.content_category_restore_custom else R.string.content_category_restore_playlist),
        resetLines = plan?.let { categoryRestoreSummary(it, LocalContext.current.resources) },
        onReset = { guarded(SealedCategoryAction.RESTORE, onRestore) },
        onDismiss = onClose,
        locked = when (item.key) {
            is LiveKey.Folder -> item.locked
            is LiveKey.Custom -> true.takeIf { item.locked }
            else -> null
        },
        onToggleLock = { onToggleLock(item.key); onClose() },
    )
}
