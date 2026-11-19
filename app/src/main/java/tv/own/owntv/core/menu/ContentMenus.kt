package tv.own.owntv.core.menu

import androidx.annotation.StringRes
import tv.own.myowntv.R
import tv.own.owntv.core.model.ContentMenu

/**
 * One arrangeable action in the long-press channel menu.
 *
 * [key] is a stable identifier and is deliberately NOT derived from the label: labels are translated,
 * and several of them flip between two texts. [labelRes] is the *neutral* wording of an action that
 * flips — the menu says "Remove from favourites" on a favourite, this catalogue always says "Add to
 * favourites", because it describes what can appear rather than what a particular item shows.
 */
data class MenuActionRef(
    val key: String,
    @param:StringRes val labelRes: Int,
    /** Only the television draws it; the phone's Customize list leaves it out. */
    val tvOnly: Boolean = false,
)

/** [menu]'s actions as the phone's Customize page lists them: the catalogue without TV-only ones. */
fun phoneCatalogue(menu: ContentMenu): List<MenuActionRef> = catalogue(menu).filterNot { it.tvOnly }

/**
 * What *can* appear in the channel menu, in its shipped order.
 *
 * Here rather than in an app because the saved order is user data keyed by these strings: a second
 * copy of the key list in the second app is a rename away from silently dropping a user's
 * arrangement. Both apps build their menus from this and both arrange them with [arrange].
 *
 * The order is the one the popup is grouped in (WATCH, CHANNEL, GUIDE DATA, ORGANISE), so the list
 * Settings shows as "Default" is exactly what the popup shows. The labels are the popup's own where
 * it has a neutral one.
 */
fun catalogue(menu: ContentMenu): List<MenuActionRef> = when (menu) {
    ContentMenu.LIVE -> LIVE_ACTIONS
}

private val LIVE_ACTIONS = listOf(
    // The channel's Program details page (its day, each programme with what it can do). TV only:
    // the phone shows a channel's schedule on its own channel page.
    MenuActionRef("programme_details", R.string.content_programme_details, tvOnly = true),
    // Keep this channel for the Multiview grid. It belongs in the list rather than being added by an
    // app, because this list is what both apps arrange by and what Customize lets the user reorder
    // and hide — a key that is not here is dropped by the arrangement.
    MenuActionRef("add_to_multiview", R.string.multiview_add_to),
    // Pin this channel as a card in the Android TV launcher's home row. TV-only: a phone launcher
    // cannot show a Live Channels card, so the phone's Customize list must not offer the key.
    MenuActionRef("home_screen", R.string.content_add_home_screen, tvOnly = true),
    MenuActionRef("play_external", R.string.content_play_external_short),
    MenuActionRef("favourite", R.string.content_add_favourite),
    MenuActionRef("rename", R.string.content_rename),
    MenuActionRef("hide", R.string.content_hide_channel),
    MenuActionRef("remove_history", R.string.content_remove_history),
    MenuActionRef("epg_offset", R.string.content_epg_time_offset),
    MenuActionRef("move", R.string.content_move),
    MenuActionRef("move_to_category", R.string.content_move_to_category),
    // Only offered inside a category the user created; it takes the channel out of that one alone.
    MenuActionRef("remove_from_category", R.string.content_remove_from_category),
)

/**
 * How the user set up one menu: the saved [order] (empty = as shipped) and the actions they [hidden],
 * the same way.
 */
data class MenuLayout(
    val order: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
) {
    /** Whether the user changed anything Settings shows: the order or what is hidden. */
    fun isCustom(menu: ContentMenu): Boolean = hidden.isNotEmpty() || !isShippedOrder(menu)

    /**
     * Whether the actions come out in the shipped order. Only then do the popups draw their group
     * headings: a heading describes a run of related actions, and a rearranged list has none.
     */
    fun isShippedOrder(menu: ContentMenu): Boolean {
        // A TV-only action the saved order has never heard of (an order saved on the phone, or before
        // the action existed) does not count as a rearrangement.
        val keys = catalogue(menu).filter { !it.tvOnly || it.key in order }.map { it.key }
        return order.isEmpty() || applyMenuOrder(keys, order) { it } == keys
    }
}

/**
 * The actions of [menu] a popup shows, in the user's order: [items] in the saved order — the
 * catalogue's when nothing is saved, so a popup built in any order still matches Settings —
 * without the hidden ones.
 */
fun <T> arrange(menu: ContentMenu, items: List<T>, layout: MenuLayout, key: (T) -> String): List<T> {
    val order = layout.order.ifEmpty { catalogue(menu).map { it.key } }
    return applyMenuOrder(items, order, key).filter { key(it) !in layout.hidden }
}

/**
 * Put [items] into the user's saved [order].
 *
 * Two rules, and both matter more than they look:
 * - a saved key that no longer exists is ignored, so an order saved by an older release never drops
 *   an action or crashes;
 * - an item the saved order has never heard of keeps its shipped position relative to the other
 *   unknown ones and is appended, so an action added by a *newer* release still shows up for someone
 *   who arranged their menu months ago.
 *
 * An empty [order] returns [items] unchanged, which is the shipped order.
 */
fun <T> applyMenuOrder(items: List<T>, order: List<String>, key: (T) -> String): List<T> {
    if (order.isEmpty()) return items
    val byKey = items.associateBy(key)
    return order.mapNotNull { byKey[it] } + items.filterNot { key(it) in order }
}
