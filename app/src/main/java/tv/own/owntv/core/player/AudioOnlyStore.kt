package tv.own.owntv.core.player

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Channels the user last watched with the picture switched off, so a radio station opens without a
 * video decoder next time and a television channel does not. Self-learning like every per-item store:
 * the user turns the picture off once, and that one channel remembers.
 *
 * Keyed by [contentKey] — the channel's hex remoteId — because channel rows are REPLACE-upserted on
 * every sync, so a Room id or a stream URL would be forgotten on the next refresh.
 *
 * Honoured only while the "Remember per channel" setting is on; the store keeps its entries when that
 * is switched off so turning it back on restores what the user had taught it. Stored in
 * `playback_quirks` and included in backups with the other per-item quirks.
 */
class AudioOnlyStore(
    private val dao: tv.own.owntv.core.database.dao.PlaybackQuirkDao,
) {
    val keys: Flow<Set<String>> = dao.observeAudioOnly().map { it.toSet() }

    /** Was this item last watched without a picture? */
    suspend fun isAudioOnly(contentKey: String): Boolean =
        dao.get(contentKey)?.audioOnly == true

    /** Record — or clear — the sound-only choice for one item. */
    suspend fun set(contentKey: String, audioOnly: Boolean) {
        dao.setAudioOnly(contentKey, audioOnly)
    }
}
