package tv.own.owntv.core.player

import tv.own.owntv.core.database.dao.PlaybackPrefsDao
import tv.own.owntv.core.database.entity.PlaybackPrefsEntity
import tv.own.owntv.core.settings.SettingsRepository

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
    /** Kept for the DI wiring; the store no longer reads anything from settings (it did the active
     *  profile before the profiles went away). */
    private val settings: SettingsRepository,
    private val quirks: tv.own.owntv.core.database.dao.PlaybackQuirkDao,
) {
    /**
     * v44 housekeeping, once per process and safe to repeat: rows learn their playlist from their key,
     * and each leftover audio delay moves to the stream's shared row — the most recently set one
     * winning where rows disagreed — and is emptied from the old column as it goes, so a delay the
     * user later clears cannot come back.
     */
    private val copy = CopyOnce(
        isDone = { false },
        markDone = {},
        copy = {
            runCatching {
                dao.fillSourceIds()
                val moved = dao.legacyAudioDelays()
                moved.forEach { row ->
                    quirks.setAudioDelay(
                        row.contentKey,
                        sourceIdOfContentKey(row.contentKey),
                        mediaTypeOfContentKey(row.contentKey) ?: "LIVE",
                        row.audioDelayMs,
                    )
                }
                if (moved.isNotEmpty()) {
                    dao.clearAudioDelayColumn()
                    dao.dropEmptyRows()
                }
            }
        },
    )

    /** The remembered values for [contentKey], or null when this item has none. The audio delay is
     *  the stream's, kept in the quirks table; zoom and volume are the row's. */
    suspend fun prefsFor(contentKey: String): PlaybackPrefsEntity? = runCatching {
        copy.ensure()
        val own = dao.get(contentKey)
        val delay = quirks.get(contentKey)?.audioDelayMs
        when {
            own != null -> own.copy(audioDelayMs = delay)
            delay != null -> PlaybackPrefsEntity(contentKey = contentKey, audioDelayMs = delay)
            else -> null
        }
    }.getOrNull()

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
        runCatching {
            copy.ensure()
            quirks.setAudioDelay(contentKey, sourceIdOfContentKey(contentKey), mediaTypeOfContentKey(contentKey) ?: "LIVE", audioDelayMs)
        }
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
        runCatching {
            copy.ensure()
            quirks.clearAudioDelays()
        }
    }

    fun observeAudioDelayCount(): kotlinx.coroutines.flow.Flow<Int> = quirks.observeAudioDelayCount()

    companion object {
        /** Stored subtitle choice meaning "the user turned subtitles off here". */
        const val SUBTITLES_OFF = "off"
    }
}
