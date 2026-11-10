package tv.own.owntv.core.stalker

import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType

/**
 * Turns a stored item URL into a playable URL at play time. M3U/Xtream already store a
 * final URL, so they pass through unchanged. Stalker stores the portal `cmd` (not playable) — this
 * resolves it via `create_link` every call (Stalker links are short-lived, so never cache), with a
 * one-shot token re-handshake on expiry. Keep the stored `cmd` as the item's identity everywhere;
 * only the value handed to the player engine comes from here.
 *
 * **Live URL expiry:** a Stalker resolved URL dies after ~2-4h, so playback's
 * auto-reconnect must NOT replay the dead URL for Stalker. Instead the engine calls back into a
 * [ReconnectUrlProvider] (installed by `LiveViewModel` only for a Stalker source) to mint a fresh
 * URL before retrying. M3U/Xtream have no provider → the stored URL is replayed as before.
 */
class StreamUrlResolver(
    private val stalkerAuth: StalkerAuthManager,
    private val stalkerClient: StalkerClient,
    /** RuTube mints its live HLS URL at tune time (`play/options`); null in callers that never play it. */
    private val rutube: tv.own.owntv.core.sync.RutubeApi? = null,
) {
    /**
     * True when this source needs play-time resolution (so callers can skip the work entirely).
     * Stalker stores a portal `cmd`; RuTube stores a `rutube://{sourceId}/{videoId}` marker.
     */
    fun needsResolve(source: SourceEntity?): Boolean =
        source?.type == SourceType.STALKER || source?.type == SourceType.RUTUBE

    /**
     * Resolve [storedUrl] to a playable URL. For Stalker this is a fresh `create_link`; for anything
     * else it returns [storedUrl] untouched. Throws on a portal/auth failure so the caller can show
     * an error instead of handing a dead URL to the engine.
     *
     * Every call is a FRESH resolve (never cached) — this is what the reconnect paths call
     * ("resolve fresh"). For direct-play portals the fresh URL equals the stored one
     * (their embedded token is static); for placeholder-cmd portals it's a brand-new `create_link`.
     */
    suspend fun resolve(source: SourceEntity, storedUrl: String): String {
        if (source.type == SourceType.RUTUBE) return resolveRutube(storedUrl)
        if (source.type != SourceType.STALKER) return storedUrl
        // Many portals embed the full playable URL right in the channel cmd — play it as-is. Only a
        // placeholder cmd (localhost / no query) needs create_link (which on the former kind blanks
        // the stream id → HTTP 405).
        val direct = StalkerClient.stripCmdPrefix(storedUrl)
        if (StalkerClient.isDirectPlayUrl(direct)) return direct
        val mac = source.mac?.let { StalkerClient.canonicalizeMac(it) } ?: return direct
        val creds = source.stalkerCredentials(mac)
        return stalkerAuth.withAuthRetry(creds) { session ->
            stalkerClient.createLink(
                session.apiBase, mac, session.token, creds.userAgent, storedUrl,
            )
        }
    }

    /**
     * A RuTube channel's stored `rutube://{sourceId}/{videoId}` marker → the live HLS URL the
     * official client would play. Fresh every call: play/options URLs expire, and the
     * reconnect path calls back here to mint a new one.
     */
    private suspend fun resolveRutube(storedUrl: String): String {
        val api = rutube ?: throw java.io.IOException("RuTube playback resolver is not configured")
        val videoId = storedUrl.removePrefix("rutube://").substringAfter('/').takeIf { it.isNotBlank() }
            ?: throw java.io.IOException("RuTube channel has no video id: $storedUrl")
        return when (val result = api.playUrl(videoId)) {
            is tv.own.owntv.core.sync.RutubePlayback.Ready -> result.url
            is tv.own.owntv.core.sync.RutubePlayback.Unavailable ->
                throw java.io.IOException("RuTube: ${result.reason.name}${result.detail?.let { " ($it)" } ?: ""}")
        }
    }

    /**
     * The channel's now/next programmes via the portal's per-channel `get_short_epg`
     * (the Stalker counterpart of Xtream's short-EPG fallback in `LiveViewModel.loadEpg`). Throws on
     * portal/auth failure — the caller treats that as "no guide data".
     */
    suspend fun shortEpg(source: SourceEntity, channelRemoteId: String, size: Int = 8): List<StalkerClient.ShortEpgEntry> {
        require(source.type == SourceType.STALKER) { "shortEpg is Stalker-only" }
        val mac = source.mac?.let { StalkerClient.canonicalizeMac(it) }
            ?: throw java.io.IOException("Stalker source ${source.id} has no valid MAC")
        val creds = source.stalkerCredentials(mac)
        return stalkerAuth.withAuthRetry(creds) { session ->
            stalkerClient.getShortEpg(session.apiBase, mac, session.token, creds.userAgent, channelRemoteId, size)
        }
    }
}

/**
 * Installed on a playback engine when the current item's URL can't simply be replayed on reconnect
 * (Stalker live: its `create_link` URL expires in a couple of hours). Before retrying, the engine
 * awaits [freshUrl]; a null return means "replay the stored URL as-is" (also the default when no
 * provider is set — i.e. M3U/Xtream, whose URLs are stable forever).
 *
 * The engine calls this on its MAIN scope (ExoPlayer is single-threaded) and treats the returned
 * value as the URL to (re)load. A throw means resolution failed — the engine surfaces its normal
 * reconnect-exhausted / error state.
 */
fun interface ReconnectUrlProvider {
    /** Return a fresh playable URL, or null to replay the current (non-expiring) URL as-is. */
    suspend fun freshUrl(): String?
}
