package tv.own.owntv.core.player

import tv.own.owntv.core.database.dao.PlaybackPrefsDao
import tv.own.owntv.core.database.entity.PlaybackPrefsEntity

/**
 * Remembers the zoom/aspect mode, the volume and the A/V-sync offset the user last chose for one
 * specific channel, so a stream that needs 130% volume, a 4:3 channel the user prefers cropped, or a
 * channel whose audio runs 200 ms early, comes back that way next time.
 *
 * Keyed by [contentKey] when the row carries a provider id, the stream URL when it doesn't — which is
 * why a re-sync doesn't lose these: the same item computes the same key again. The app has one user,
 * so the row is simply *the* choice for that item.
 *
 * Nothing is stored until the user actually changes something in the player. A missing row, or a
 * null column in an existing one, means "follow the global default in Settings".
 */
class PlaybackPrefsStore(
    private val dao: PlaybackPrefsDao,
    private val quirks: tv.own.owntv.core.database.dao.PlaybackQuirkDao,
) {
    /** The remembered zoom/volume/track values for [contentKey], or null when this item has none. */
    suspend fun prefsFor(contentKey: String): PlaybackPrefsEntity? =
        runCatching { dao.get(contentKey) }.getOrNull()

    /** The stream's remembered A/V-sync offset, kept in `playback_quirks`, or null. */
    suspend fun audioDelayFor(contentKey: String): Int? =
        runCatching { quirks.get(contentKey)?.audioDelayMs }.getOrNull()

    /** Remember a deliberate zoom choice ([tv.own.owntv.player.ZoomMode] name). */
    suspend fun rememberZoom(contentKey: String, zoomMode: String) {
        runCatching { dao.setZoom(contentKey, zoomMode) }
    }

    /** Remember a deliberate volume change (percent, 0–150). */
    suspend fun rememberVolume(contentKey: String, volume: Int) {
        runCatching { dao.setVolume(contentKey, volume) }
    }

    /** Remember a deliberate A/V-sync offset in ms; null forgets it and returns to the global value. */
    suspend fun rememberAudioDelay(contentKey: String, audioDelayMs: Int?) {
        runCatching { quirks.setAudioDelay(contentKey, audioDelayMs) }
    }

    /**
     * The track languages last picked for [trackKey] (a channel), as (audio, subtitle); either may be
     * null. Subtitle may be [SUBTITLES_OFF].
     */
    suspend fun tracksFor(trackKey: String): Pair<String?, String?>? = runCatching {
        dao.get(trackKey)
            ?.takeIf { it.audioLang != null || it.subtitleLang != null }
            ?.let { it.audioLang to it.subtitleLang }
    }.getOrNull()

    /** Remember a deliberate audio-track pick by its language. */
    suspend fun rememberAudioLang(trackKey: String, lang: String) {
        runCatching { dao.setAudioLang(trackKey, lang) }
    }

    /** Remember a deliberate subtitle pick by its language, or [SUBTITLES_OFF]. */
    suspend fun rememberSubtitleLang(trackKey: String, lang: String) {
        runCatching { dao.setSubtitleLang(trackKey, lang) }
    }

    /** Settings escape hatch: forget every item's zoom. Volumes are kept. */
    suspend fun clearZoom() {
        runCatching { dao.clearZoom() }
    }

    /** Settings escape hatch: forget every item's volume. Zoom modes are kept. */
    suspend fun clearVolume() {
        runCatching { dao.clearVolume() }
    }

    /** How many items currently remember a zoom / a volume — the two Settings rows' chips. */
    fun observeZoomCount(): kotlinx.coroutines.flow.Flow<Int> = dao.observeZoomCount()

    fun observeVolumeCount(): kotlinx.coroutines.flow.Flow<Int> = dao.observeVolumeCount()

    /** Settings escape hatch: forget every item's audio delay. */
    suspend fun clearAudioDelay() {
        runCatching { quirks.clearAudioDelays() }
    }

    fun observeAudioDelayCount(): kotlinx.coroutines.flow.Flow<Int> = quirks.observeAudioDelayCount()

    companion object {
        /** Stored subtitle choice meaning "the user turned subtitles off here". */
        const val SUBTITLES_OFF = "off"
    }
}
