package tv.own.owntv.core.player

/**
 * The stable identity everything remembered per played item — the audio-only choice, a per-item zoom,
 * volume or A/V-sync offset, the picked track languages — is keyed on.
 *
 * The key is the channel's hex remoteId: it is the natural key of `channels` and survives every
 * re-sync and every reinstall. Returns null when the item has no provider id; callers then fall back
 * to the stream URL, which for those rows is the only identity there is.
 */
fun contentKey(remoteId: String?): String? = remoteId?.takeIf { it.isNotBlank() }
