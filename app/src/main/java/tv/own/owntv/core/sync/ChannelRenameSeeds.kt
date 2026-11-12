package tv.own.owntv.core.sync

/**
 * The shipped rename dictionary: RuTube hex videoId → the display name the channel carries in
 * every list. Mirrored into `channel_rename` by [RuTubeSyncer] on every sync, so a dictionary
 * change reaches already-stored rows without waiting for their content to change.
 *
 * To add an entry: read the channel's hex id (the natural key of `channels.remoteId` — a
 * `SELECT remoteId, name FROM channels` on any synced install), then add one line below. The
 * user's own Customize renames keep applying on top of whatever lands here, so a curated name is
 * a better default, never a cage.
 */
object ChannelRenameSeeds {
    val map: Map<String, String> = emptyMap()

    /** Author prefixes stripped from the provider name before any naming (see [cleanProviderName]). */
    val trimPrefixes = listOf(
        "Телеканалы мира онлайн · ",
        "Mobotix Webcams Russia · ",
        "ТВОЙ КИНОТЕАТР | MOVIE TIME CINEMA · ",
    )

    /** Advertising tails stripped the same way. */
    val trimSuffixes = listOf(
        " | КРУГЛОСУТОЧНЫЙ ПРЯМОЙ ЭФИР",
    )

    /**
     * Strips the curated prefixes/tails from the provider's channel name. A name that is nothing
     * but a prefix keeps the original — an empty display name would be worse than a long one.
     */
    fun cleanProviderName(raw: String): String {
        var name = raw
        for (prefix in trimPrefixes) {
            if (name.startsWith(prefix)) name = name.removePrefix(prefix)
        }
        for (suffix in trimSuffixes) {
            if (name.endsWith(suffix)) name = name.removeSuffix(suffix)
        }
        return name.ifBlank { raw }
    }
}
