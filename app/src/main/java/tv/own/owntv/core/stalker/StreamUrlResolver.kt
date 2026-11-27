package tv.own.owntv.core.stalker

import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.sync.RutubeCaption

/** A playable URL and the sidecars advertised by the same provider response. */
data class ResolvedPlayback(
    val url: String,
    val captions: List<RutubeCaption> = emptyList(),
)

/**
 * Turns a stored item URL into a playable URL at play time. RuTube is the only source that needs
 * it: it stores a `rutube://{sourceId}/{videoId}` marker, not a playable URL — this resolves it via
 * `play/options` on every call (RuTube live URLs expire, so never cache). Keep the stored marker as
 * the item's identity everywhere; only the value handed to the player engine comes from here.
 *
 * **Live URL expiry:** a resolved RuTube URL dies when the minted token expires, so playback's
 * auto-reconnect must NOT replay the dead URL. Instead the engine calls back into a
 * [ReconnectUrlProvider] (installed by `LiveViewModel` for a resolving source) to mint a fresh URL
 * before retrying. Anything else has no provider → the stored URL is replayed as before.
 */
class StreamUrlResolver(
    /** RuTube mints its live HLS URL at tune time (`play/options`); null in callers that never play it. */
    private val rutube: tv.own.owntv.core.sync.RutubeApi? = null,
) {
    /**
     * True when this source needs play-time resolution (so callers can skip the work entirely).
     * RuTube stores a `rutube://{sourceId}/{videoId}` marker.
     */
    fun needsResolve(source: SourceEntity?): Boolean = source?.type == SourceType.RUTUBE

    /**
     * Resolve [storedUrl] to a playable URL. For RuTube this is a fresh `play/options` URL; for
     * anything else it returns [storedUrl] untouched. Throws on a resolution failure so the caller
     * can show an error instead of handing a dead URL to the engine.
     *
     * Every call is a FRESH resolve (never cached) — this is what the reconnect paths call
     * ("resolve fresh").
     */
    suspend fun resolve(source: SourceEntity, storedUrl: String): String =
        resolvePlayback(source, storedUrl).url

    /** Resolve the URL and its captions from one provider answer. RuTube's `play/options` carries
     *  both; calling it twice made every tune wait for two sequential network requests and opened a
     *  cancellation window between recording the new channel and actually playing it. */
    suspend fun resolvePlayback(source: SourceEntity, storedUrl: String): ResolvedPlayback {
        if (source.type != SourceType.RUTUBE) return ResolvedPlayback(storedUrl)
        val api = rutube ?: throw java.io.IOException("RuTube playback resolver is not configured")
        val videoId = rutubeVideoId(storedUrl)
            ?: throw java.io.IOException("RuTube channel has no video id: $storedUrl")
        return when (val result = api.playUrl(videoId)) {
            is tv.own.owntv.core.sync.RutubePlayback.Ready -> ResolvedPlayback(result.url, result.captions)
            is tv.own.owntv.core.sync.RutubePlayback.Unavailable ->
                throw java.io.IOException("RuTube: ${result.reason.name}${result.detail?.let { " ($it)" } ?: ""}")
        }
    }

    /** The `{videoId}` half of a stored `rutube://{sourceId}/{videoId}` marker. */
    private fun rutubeVideoId(storedUrl: String): String? =
        storedUrl.removePrefix("rutube://").substringAfter('/').takeIf { it.isNotBlank() }

    /**
     * Sidecar subtitles advertised beside a RuTube stream (`play/options` `captions[]` → SRT/VTT URLs,
     * the same sidecars the reference client merges under the video). Empty for non-RuTube sources, an
     * unconfigured client and feeds that advertise nothing — and best-effort throughout: subtitles
     * must never cost the stream, so a missing/failed lookup is simply "no subtitles".
     */
    suspend fun resolveCaptions(
        source: SourceEntity?,
        storedUrl: String,
    ): List<tv.own.owntv.core.sync.RutubeCaption> {
        if (source?.type != SourceType.RUTUBE) return emptyList()
        val api = rutube ?: return emptyList()
        val id = rutubeVideoId(storedUrl) ?: return emptyList()
        return when (val result = api.playUrl(id)) {
            is tv.own.owntv.core.sync.RutubePlayback.Ready -> result.captions
            is tv.own.owntv.core.sync.RutubePlayback.Unavailable -> emptyList()
        }
    }
}

/**
 * Installed on a playback engine when the current item's URL can't simply be replayed on reconnect
 * (RuTube live: its minted URL expires). Before retrying, the engine awaits [freshUrl]; a null return
 * means "replay the stored URL as-is" (also the default when no provider is set — i.e. sources whose
 * URLs are stable forever).
 *
 * The engine calls this on its MAIN scope (ExoPlayer is single-threaded) and treats the returned
 * value as the URL to (re)load. A throw means resolution failed — the engine surfaces its normal
 * reconnect-exhausted / error state.
 */
fun interface ReconnectUrlProvider {
    /** Return a fresh playable URL, or null to replay the current (non-expiring) URL as-is. */
    suspend fun freshUrl(): String?
}
