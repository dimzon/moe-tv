package tv.own.owntv.core.player

/**
 * The stable identity everything remembered per played item — the audio-only choice, a per-item zoom,
 * volume or A/V-sync offset, the picked track languages — is keyed on.
 *
 * These stores originally keyed on the item's stream URL, which is stable for M3U and Xtream but
 * **never** for Stalker/MAC portals: `StreamUrlResolver.resolve()` asks the portal for a URL at play
 * time and gets back a fresh, token-bearing, single-use one — so the URL stored when the choice was
 * made could never match the URL of the next play, and the remembered setting silently did nothing on
 * every Stalker item.
 *
 * `sourceId` + media type + provider `remoteId` is carried by every content entity of every source
 * type and survives re-syncs (Room ids do not). Returns null when the row has no `remoteId` (some
 * hand-made M3U rows), in which case callers keep using the stream URL — which for those rows is
 * exactly as stable as it always was.
 *
 * Both key shapes coexist in the same stored set (old data still carries URL keys): reads try the
 * stable key, then the legacy URL, and stores that migrate rewrite a legacy hit under the stable key.
 * That also keeps backups compatible in both directions — a key an older build doesn't recognise is
 * simply never matched.
 */
fun contentKey(sourceId: Long, mediaType: String, remoteId: String?): String? =
    remoteId?.takeIf { it.isNotBlank() }?.let { "$sourceId:$mediaType:$it" }

/** The playlist a [contentKey] belongs to, or `-1` for a legacy stream-URL key. */
fun sourceIdOfContentKey(key: String): Long = key.substringBefore(':').toLongOrNull() ?: -1L

/** The media type a [contentKey] names (live channels are `LIVE`), or null for a stream-URL key. */
fun mediaTypeOfContentKey(key: String): String? =
    if (sourceIdOfContentKey(key) < 0) null else key.substringAfter(':').substringBefore(':').takeIf { it.isNotEmpty() }
