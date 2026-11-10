package tv.own.owntv.core.network

/**
 * Per-channel HTTP request headers.
 *
 * M3U playlists carry per-entry HTTP options — `#EXTVLCOPT:http-user-agent`, `#EXTHTTP:{"cookie":…}`,
 * `#KODIPROP:inputstream.adaptive.stream_headers`, or the `url|User-Agent=…&Referer=…` pipe suffix.
 * Restreams and token-protected CDNs routinely require them, and without them the provider answers 403.
 *
 * Stored on [tv.own.owntv.core.database.entity.ChannelEntity.httpHeaders] as one `Key: Value` per line
 * — deliberately not JSON: it is exactly what an HTTP request looks like, needs no parser dependency,
 * and is human-readable in a backup file.
 */
object StreamHeaders {

    /** Canonical spellings for the handful of headers playlists actually use, so `referer`,
     *  `Referrer` and `REFERER` all end up as one entry rather than three. */
    private val CANONICAL = mapOf(
        "user-agent" to "User-Agent",
        "referer" to "Referer",
        "referrer" to "Referer", // the misspelling VLC itself uses (`http-referrer`)
        "origin" to "Origin",
        "cookie" to "Cookie",
        "authorization" to "Authorization",
        "x-forwarded-for" to "X-Forwarded-For",
    )

    /** Header names we refuse to carry: the transport layer owns them, and a playlist that sets one
     *  breaks the request rather than fixing it. */
    private val RESERVED = setOf("host", "content-length", "connection", "transfer-encoding", "range")

    /** Canonical name, or null when the name is empty or reserved. */
    fun canonicalName(raw: String): String? {
        val name = raw.trim()
        if (name.isEmpty() || name.any { it == ':' || it == '\n' || it == '\r' }) return null
        val lower = name.lowercase()
        if (lower in RESERVED) return null
        return CANONICAL[lower] ?: name
    }

    /** `Key: Value` per line → map. Blank/malformed lines are skipped. */
    fun decode(serialized: String?): Map<String, String> {
        val text = serialized?.takeIf { it.isNotBlank() } ?: return emptyMap()
        val out = LinkedHashMap<String, String>(4)
        text.lineSequence().forEach { line ->
            val sep = line.indexOf(':')
            if (sep <= 0) return@forEach
            val name = canonicalName(line.substring(0, sep)) ?: return@forEach
            val value = line.substring(sep + 1).trim()
            if (value.isNotEmpty()) out[name] = value
        }
        return out
    }

    /** Map → `Key: Value` per line, or null when there is nothing to store. */
    fun encode(headers: Map<String, String>): String? =
        headers.entries
            .mapNotNull { (k, v) ->
                val name = canonicalName(k) ?: return@mapNotNull null
                val value = v.trim().replace('\n', ' ').replace('\r', ' ')
                if (value.isEmpty()) null else "$name: $value"
            }
            .takeIf { it.isNotEmpty() }
            ?.joinToString("\n")

    /** The per-channel User-Agent, which overrides the per-source one when present. */
    fun userAgentOf(headers: Map<String, String>): String? =
        headers.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }?.value?.takeIf { it.isNotBlank() }
}
