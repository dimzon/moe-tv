package tv.own.owntv.core.live

/** Which set of channels a Live TV list is showing. */
sealed interface LiveKey {
    data object Favorites : LiveKey
    data object History : LiveKey
    data object All : LiveKey
    data class Folder(val id: Long) : LiveKey
    /** A user-created combined category; [id] is its "custom:<uuid>" customization key. */
    data class Custom(val id: String) : LiveKey
}

// Persistence for the "remember last category" toggles. Both apps read and write the same stored
// string, so the encoding lives here: a second copy is a typo away from an app forgetting where the
// other one left the user.
fun LiveKey.serialize(): String = when (this) {
    LiveKey.Favorites -> "FAV"
    LiveKey.History -> "HIST"
    LiveKey.All -> "ALL"
    is LiveKey.Folder -> "FOLDER:$id"
    is LiveKey.Custom -> "CUSTOM:$id"
}

fun parseLiveKey(s: String): LiveKey? = when {
    s == "FAV" -> LiveKey.Favorites
    s == "HIST" -> LiveKey.History
    s == "ALL" -> LiveKey.All
    s.startsWith("FOLDER:") -> s.removePrefix("FOLDER:").toLongOrNull()?.let { LiveKey.Folder(it) }
    s.startsWith("CUSTOM:") -> LiveKey.Custom(s.removePrefix("CUSTOM:"))
    else -> null
}
