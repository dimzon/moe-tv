package tv.own.myowntv.ui.components
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
import tv.own.owntv.core.profile.*
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
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import tv.own.owntv.core.menu.MenuLayout
import tv.own.owntv.core.menu.arrange
import tv.own.owntv.core.model.ContentMenu
import tv.own.owntv.core.settings.SettingsRepository

/**
 * One action in a long-press content menu — Live channel, movie, series or episode.
 *
 * The four menus used to be hand-written columns of buttons wrapped in `if`s, which meant their order
 * was the source order and nothing could be rearranged. They now build a list of these instead, and
 * render it in list order, so a later release can sort that list without touching any menu.
 *
 * [key] is a stable identifier and is deliberately NOT derived from the label: labels are translated,
 * and several of them toggle between two texts ("Add to favourites" / "Remove from favourites").
 *
 * [group] only spaces the menu out: a divider (or a gap, depending on the menu) is drawn wherever the
 * group changes between two neighbouring actions. An empty group simply produces no divider.
 */
data class MenuAction(
    val key: String,
    val label: String,
    val icon: MyOwnTVIcon? = null,
    val destructive: Boolean = false,
    val group: Int = 0,
    val onClick: () -> Unit,
)

/** A menu's actions as the popup draws them, and whether to draw its group headings. */
data class ArrangedMenu(val actions: List<MenuAction>, val headings: Boolean)

/**
 * [actions] in the order the user arranged [menu] into, without the ones they hid. The menus read the
 * setting themselves rather than have it threaded down through four screens and their view models.
 *
 * The rule itself is core's, so both apps arrange the same saved keys the same way. Group headings
 * are drawn only in the shipped order: a heading names a run of related actions, and a rearranged
 * menu has none.
 */
@Composable
fun arranged(menu: ContentMenu, actions: List<MenuAction>): ArrangedMenu {
    val settings: SettingsRepository = koinInject()
    val layout by remember(menu) { settings.menuLayout(menu) }.collectAsStateWithLifecycle(MenuLayout())
    val gate: tv.own.myowntv.features.settings.lock.MenuChangeGate = koinInject()
    // The organising actions ask for the Settings PIN first when its long-press switch is on.
    val guarded = actions.map { action ->
        if (action.key in tv.own.owntv.core.categorylock.GUARDED_MENU_ACTIONS) action.copy(onClick = { gate.run(action.onClick) }) else action
    }
    return ArrangedMenu(arrange(menu, guarded, layout) { it.key }, layout.isShippedOrder(menu))
}
