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

import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Frames the closed sheet and the list get to settle before the list's focus is checked. */
private const val SETTLE_FRAMES = 2

/**
 * Focus for BACK out of the category sheet. [focusCurrent] moves it to the item the list was on, at
 * once. A lock from the sheet's menu can have emptied that list, so the item is gone (or goes once the
 * list reloads): when the list pane does not hold focus ([listFocused]) a few frames later,
 * [enterList] takes the screen's own way in, which lands on a locked category's Enter PIN button, else
 * the first item, else the search field.
 */
internal fun focusListAfterSheet(
    scope: CoroutineScope,
    focusCurrent: () -> Boolean,
    listFocused: () -> Boolean,
    enterList: () -> Unit,
) {
    focusCurrent()
    scope.launch {
        repeat(SETTLE_FRAMES) { withFrameNanos { } }
        if (!listFocused()) enterList()
    }
}
