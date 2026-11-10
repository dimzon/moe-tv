package tv.own.owntv.core.settings

import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.network.StreamHeaders

/**
 * The v44 per-playlist overrides, read the same way everywhere.
 *
 * Every column is nullable and `null` means "follow the global setting", so each function here returns
 * null for "no override" and the caller falls back to its own global value. An unreadable stored value
 * (a name from a newer build, say) also reads as no override rather than as a guess.
 */
object SourceOverrides {

    /** [source]'s own "Give up after" in seconds (0 = never), or null when it follows the global one. */
    fun liveTuneTimeoutSecsOf(source: SourceEntity?): Int? =
        source?.liveTuneTimeoutSecs?.coerceIn(0, MAX_TUNE_TIMEOUT_SECS)

    /**
     * [itemHeaders] (a stream's own `Key: Value` block) with [source]'s Referer added. A Referer the
     * stream declares itself wins — the playlist entry is the more specific statement.
     */
    fun headersWithReferer(itemHeaders: String?, source: SourceEntity?): String? {
        val referer = source?.httpReferer?.trim()?.takeIf { it.isNotEmpty() } ?: return itemHeaders
        val headers = StreamHeaders.decode(itemHeaders)
        if (headers.keys.any { it.equals("Referer", ignoreCase = true) }) return itemHeaders
        return StreamHeaders.encode(headers + ("Referer" to referer))
    }

    /** Same upper bound as the global "Give up after" (see `SettingsRepository.liveTuneTimeoutSecs`). */
    const val MAX_TUNE_TIMEOUT_SECS = 60
}
