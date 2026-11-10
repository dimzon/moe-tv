package tv.own.owntv.core.live

/**
 * Whether the built-in All channels list stays in the user's category list. Favorites and History
 * always stay. Both apps filter the same list with this, so the Live TV categories, the guide's
 * category list, the player's and Multiview's all agree.
 */
data class LiveBuiltIns(val showAll: Boolean = true) {
    fun shows(key: LiveKey): Boolean = when (key) {
        LiveKey.All -> showAll
        else -> true
    }
}

/**
 * Where Live TV lands when nothing else is chosen — at start, after a playlist switch, or when the
 * list it was on is hidden: All channels while the list holds it, else the first category that
 * opens without a PIN, else Favorites, which is always there.
 *
 * [items] is the category list as shown, [sealed] says which entries need a PIN first.
 */
fun <T> liveLandingKey(items: List<T>, key: (T) -> LiveKey, sealed: (T) -> Boolean): LiveKey =
    items.firstOrNull { key(it) == LiveKey.All }?.let(key)
        ?: items.firstOrNull { !sealed(it) && (key(it) is LiveKey.Folder || key(it) is LiveKey.Custom) }?.let(key)
        ?: LiveKey.Favorites
