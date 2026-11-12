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
}
