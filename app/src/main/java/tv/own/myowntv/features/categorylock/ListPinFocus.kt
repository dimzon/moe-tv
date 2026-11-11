package tv.own.myowntv.features.categorylock
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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.delay

/** How long a category the PIN opened may take to list its first item before focus stops waiting. */
private const val LIST_WAIT_MS = 3_000L

/** Frames the first item of the opened category gets to take focus once it is listed (about half a second). */
private const val FIRST_ITEM_FRAMES = 30

/**
 * Where focus goes when a PIN prompt asked from a sealed category's Enter PIN button closes. The
 * button sits in the item list, so that is where focus stays: on the first item once the opened
 * category has loaded, or back on the button when the PIN did not open it. A prompt asked from the
 * categories is the screen's to close; when it opened the category from the sheet, the screen hands
 * the wait for the first item back here with [openedFromCategories].
 */
@Stable
internal class ListPinFocus {
    private var fromList = false
    internal var awaitingItems by mutableStateOf(false)
    internal var refocusPin by mutableStateOf(false)

    /** The prompt is being asked from the Enter PIN button. */
    fun askedFromList() { fromList = true }

    /** The category sheet's prompt opened its category: focus moves to the first item once it has loaded. */
    fun openedFromCategories() { awaitingItems = true }

    /** Takes the close of a prompt asked from the button and returns true; any other close is the screen's. */
    fun takeClose(opened: Boolean): Boolean {
        if (!fromList) return false
        fromList = false
        if (opened) awaitingItems = true else refocusPin = true
        return true
    }
}

/**
 * A [ListPinFocus] for a list of [itemCount] items whose Enter PIN button holds [pinFocus], as its first
 * item does once the list has one.
 * [scrollToFirst] brings the first item on screen, the way the screen's own focus moves scroll first.
 * [focusList] is the screen's own way into the list (the remembered item, else the first).
 */
@Composable
internal fun rememberListPinFocus(
    itemCount: Int,
    pinFocus: FocusRequester,
    scrollToFirst: suspend () -> Unit,
    focusList: () -> Unit,
): ListPinFocus {
    val state = remember { ListPinFocus() }
    val toFirst by rememberUpdatedState(scrollToFirst)
    val enterList by rememberUpdatedState(focusList)
    LaunchedEffect(state.awaitingItems, itemCount > 0) {
        if (!state.awaitingItems) return@LaunchedEffect
        // An opened category that stays empty still gets the list's own fallback (its search field).
        if (itemCount == 0) delay(LIST_WAIT_MS)
        if (itemCount > 0) {
            // Not the screen's own way in: it keeps a focus already inside the list (a list counts its
            // search field), and it scrolls to the remembered item, so the first item could
            // take focus while scrolled off screen. The list scrolls to it first instead.
            toFirst()
            focusFirstItem(pinFocus)
        } else {
            enterList()
        }
        // Last: clearing the flag restarts this effect, which cancels whatever is still running in it.
        state.awaitingItems = false
    }
    LaunchedEffect(state.refocusPin) {
        if (!state.refocusPin) return@LaunchedEffect
        withFrameNanos { }
        runCatching { pinFocus.requestFocus() }
        state.refocusPin = false
    }
    return state
}

/** Moves focus to [firstItem] as soon as the list has composed it. */
private suspend fun focusFirstItem(firstItem: FocusRequester) {
    repeat(FIRST_ITEM_FRAMES) {
        withFrameNanos { }
        if (runCatching { firstItem.requestFocus() }.getOrDefault(false)) return
    }
}
