package tv.own.myowntv.features.shell.components

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.delay

/** The single root Back decision, in remote-key priority order. */
internal enum class SettingsRootBackAction {
    /** A modal/menu owns Back; its specialised handler must stay in control. */
    DEFER_TO_OVERLAY,
    CLEAR_SEARCH,
    CLOSE_SEARCH,
    RETURN_TO_QUICK,
    EXIT_ROOT,
    /** No Settings root callback is installed, so the shell keeps its own handler. */
    NONE,
}

internal data class SettingsRootBackState(
    val modalOpen: Boolean = false,
    val menuOpen: Boolean = false,
    val searchQuery: String = "",
    val searchMode: Boolean = false,
    val searchFromMore: Boolean = false,
    val quickReturn: Boolean = false,
    val canExitRoot: Boolean = true,
)

/** Pure so every overlapping state can be covered by a small table-driven JVM test. */
internal fun resolveSettingsRootBack(state: SettingsRootBackState): SettingsRootBackAction = when {
    state.modalOpen || state.menuOpen -> SettingsRootBackAction.DEFER_TO_OVERLAY
    state.searchQuery.isNotBlank() -> SettingsRootBackAction.CLEAR_SEARCH
    state.searchMode -> SettingsRootBackAction.CLOSE_SEARCH
    state.quickReturn -> SettingsRootBackAction.RETURN_TO_QUICK
    state.canExitRoot -> SettingsRootBackAction.EXIT_ROOT
    else -> SettingsRootBackAction.NONE
}

/**
 * Replaces the competing root Back handlers. Dialogs, the row menu and panel editors keep their
 * deeper handlers; this one is disabled while any of them is open.
 */
@Composable
internal fun SettingsRootBackHandler(
    state: SettingsRootBackState,
    onClearSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onReturnToQuick: () -> Unit,
    onExit: () -> Unit,
) {
    val action = resolveSettingsRootBack(state)
    BackHandler(enabled = action != SettingsRootBackAction.DEFER_TO_OVERLAY && action != SettingsRootBackAction.NONE) {
        when (action) {
            SettingsRootBackAction.CLEAR_SEARCH -> onClearSearch()
            SettingsRootBackAction.CLOSE_SEARCH -> onCloseSearch()
            SettingsRootBackAction.RETURN_TO_QUICK -> onReturnToQuick()
            SettingsRootBackAction.EXIT_ROOT -> onExit()
            SettingsRootBackAction.DEFER_TO_OVERLAY,
            SettingsRootBackAction.NONE -> Unit
        }
    }
}

/**
 * After unpinning, prefer the row that slides up into the removed row's place, then the row above
 * when the last row was removed. Null means Quick is empty and focus must fall back outside it.
 */
internal fun quickReturnKeyAfterUnpin(pinnedKeys: List<String>, removedKey: String): String? {
    val index = pinnedKeys.indexOf(removedKey)
    if (index < 0) return pinnedKeys.firstOrNull()
    return pinnedKeys.getOrNull(index + 1) ?: pinnedKeys.getOrNull(index - 1)
}

/** Restores a row after its hold-OK menu closes, with explicit fallbacks when that row was removed. */
@Composable
internal fun SettingsMenuFocusRestore(
    request: Int,
    menuOpen: Boolean,
    returnKey: String?,
    hasRows: Boolean,
    returnFocus: FocusRequester,
    rowsFocus: FocusRequester,
    searchFocus: FocusRequester,
    onReturnConsumed: () -> Unit,
) {
    LaunchedEffect(request) {
        if (request == 0 || menuOpen) return@LaunchedEffect
        // An unattached requester returns false, not an exception. Try the neighbour across frames,
        // then the first row; an empty Quick has neither, so the search field is the explicit fallback.
        val targets = buildList {
            if (returnKey != null) add(returnFocus)
            if (hasRows) add(rowsFocus)
            add(searchFocus)
        }
        delay(60)
        requestFocusAcrossFrames(targets) { it.requestFocus() }
        // Never carry a removed/stale row key into the next menu or directional entry.
        onReturnConsumed()
    }
}

/**
 * FocusRequesters can be unattached while a menu tears down: requestFocus then returns false rather
 * than throwing. Retry each target across frames before trying the next explicit fallback.
 */
internal suspend fun <T> requestFocusAcrossFrames(
    targets: List<T>,
    maxFramesPerTarget: Int = SETTINGS_FOCUS_FRAMES,
    waitForNextFrame: suspend () -> Unit = { withFrameNanos { } },
    requestFocus: (T) -> Boolean,
): Boolean {
    if (maxFramesPerTarget <= 0) return false
    for (target in targets) {
        repeat(maxFramesPerTarget) {
            waitForNextFrame()
            if (runCatching { requestFocus(target) }.getOrDefault(false)) return true
        }
    }
    return false
}

private const val SETTINGS_FOCUS_FRAMES = 10
