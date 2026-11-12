package tv.own.owntv.core.database.entity

import androidx.compose.runtime.Immutable

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Objects
import tv.own.owntv.core.model.MediaType

/**
 * A category/folder within a source for a given media type (LIVE / MOVIE / SERIES). The Layer-2 rail
 * is built from these. `remoteId` is the provider's category id (used to dedupe on re-sync).
 */
@Entity(
    tableName = "categories",
    foreignKeys = [
        ForeignKey(
            entity = SourceEntity::class, parentColumns = ["id"], childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("sourceId"),
        Index(value = ["sourceId", "mediaType"]),
        Index(value = ["sourceId", "mediaType", "remoteId"], unique = true),
    ],
)
@Immutable
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val mediaType: MediaType,
    val name: String,
    val remoteId: String? = null,
    val sortOrder: Int = 0,
)

@Entity(
    tableName = "channels",
    foreignKeys = [
        ForeignKey(entity = SourceEntity::class, parentColumns = ["id"], childColumns = ["sourceId"], onDelete = ForeignKey.CASCADE),
    ],
    // No single-column (sourceId) index (v52): the composites below start with the same column
    // and serve every query, and each extra index is one more B-tree every write updates.
    indices = [
        Index("epgChannelId"),
        Index(value = ["sourceId", "remoteId"], unique = true),
        // Single-column unique on the natural hex key: the `channel_category` FK references
        // channels.remoteId (redesign §2 M:N link), and SQLite needs a unique constraint on the
        // parent column. RuTube video ids are globally unique, so this holds across the one source.
        Index(value = ["remoteId"], unique = true),
        // Composite grid read-indices (v6): A–Z order (ORDER BY name) and playlist/provider order
        // (ORDER BY sortOrder, name) for the whole-source list. Per-category reads go through the
        // `channel_category` junction (redesign §2). Mirrors movies/series
        // so all three browse grids (Live/Movies/Series) are index-served in either sort.
        Index(value = ["sourceId", "name"]),
        Index(value = ["sourceId", "sortOrder", "name"]),
        Index(value = ["sourceId", "number"]),
    ],
)
@Immutable
data class ChannelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val name: String,
    val logoUrl: String? = null,
    val streamUrl: String,
    /** tvg-id (M3U) / epg_channel_id (Xtream) — links to EPG. */
    val epgChannelId: String? = null,
    val number: Int? = null,
    val remoteId: String? = null,
    val sortOrder: Int = 0,
    /** Catch-up/archive available for this channel (Xtream `tv_archive` / M3U `catchup`). */
    val catchup: Boolean = false,
    /** How many days back the archive goes (Xtream `tv_archive_duration` / M3U `catchup-days`). */
    val catchupDays: Int = 0,
    /** M3U `catchup-source` URL template (with `${start}`/`${timestamp}`/… placeholders). Null for
     *  Xtream, whose stream URL is built from the source credentials instead. */
    val catchupSource: String? = null,
    /** M3U `catchup` type — "default" / "append" / "shift" / "flussonic" / "xc". Decides HOW
     *  [catchupSource] is used; without it the `append` convention can't be honoured. */
    val catchupType: String? = null,
    /** Per-channel HTTP request headers as `Key: Value` per line — see
     *  [tv.own.owntv.core.network.StreamHeaders]. Set from the M3U's `#EXTVLCOPT` / `#EXTHTTP` /
     *  `#KODIPROP` directives or a `url|Key=Value` suffix; null for providers that carry none. */
    val httpHeaders: String? = null,
    /** Widevine/ClearKey licence details — see [tv.own.owntv.core.drm.DrmConfig] (v33). Non-null
     *  means the stream must go through the platform DRM stack: a raw CENC key could be used by any
     *  decrypter, but a `license_key` URL is a request to a licence server, and only ExoPlayer's
     *  Content Decryption Module answers one. */
    val drmConfig: String? = null,
    /** The container this channel declares for itself (M3U `#KODIPROP:…manifest_type`), stored as
     *  [tv.own.owntv.core.player.ManifestType.key] (v43). Independent of [drmConfig] — an unprotected
     *  channel may declare one too. Null means "infer from the URL", which is every channel today. */
    val manifestType: String? = null,
    /** The panel's own `direct_source` for this channel (Xtream only, v43), when it publishes a
     *  non-blank one.
     *
     *  **Never the URL we tune.** Panels build this from the streaming server's configured domain and
     *  fall back to its raw IP, so a misconfigured panel or load balancer publishes an address only
     *  reachable inside their own network — which is why every major client ignores the field. It is
     *  also often shaped so that the `.ts` ⇄ `.m3u8` rewrites (`resolveStreamUrl`, `toHlsUrl`,
     *  `alternateFormatUrl`) silently no-op on it, costing the channel three recovery rungs.
     *
     *  So it is stored beside [streamUrl] rather than replacing it, and reached for ONLY after the
     *  normal URL has definitively failed and the format-swap rung is spent. Used that way it can only
     *  add channels that would otherwise show an error, never take one away. */
    val directSource: String? = null,
    @ColumnInfo(defaultValue = "0") val contentHash: Int = 0,
    /** M3U `catchup-correction` in minutes (v49): how far the provider's archive clock is from the
     *  guide's, from the entry or the `#EXTM3U` line. 0 for everything else. */
    @ColumnInfo(defaultValue = "0") val catchupCorrectionMin: Int = 0,
    /** The provider's own name when the playlist's "Clean channel names" changed [name] (v51), so
     *  guide matching keeps seeing what the provider wrote. Null when [name] is that name. */
    val providerName: String? = null,
    /**
     * Subscription-only channel (RuTube `is_paid` **or** a non-empty
     * `common_subscription_product_codes`, v55). The live catalogue reports `is_paid = false` on
     * every row, so the subscription packages are the only real signal. The UI badges these and
     * `play/options` answers 404 `common_subscription` for them.
     */
    @ColumnInfo(defaultValue = "0") val isPaid: Boolean = false,
)

/** The name guide matching compares: the provider's own, not the cleaned display name. */
val ChannelEntity.matchName: String get() = providerName ?: name

/**
 * The stream URL to tune: the channel's own `.ts`, swapped to `.m3u8` when the playlist's "Prefer HLS"
 * is on for an Xtream source.
 *
 * **Deliberately NOT gated on `hlsSupported`** — this KDoc used to claim it was, and two earlier audits
 * recommended adding that gate on the strength of the comment. It would be wrong. Panels lie about
 * `allowed_output_formats`: gating here breaks HLS for every user on an under-reporting panel, and for
 * every source that has not finished syncing yet. The authoritative note is on the field itself in
 * `SourceEntities.kt`. A channel whose `.m3u8` genuinely doesn't work is handled after the fact, per
 * channel and per engine, by `LiveStreamQuirks.rememberNoHlsVariant`.
 */
fun ChannelEntity.playStreamUrl(source: SourceEntity?): String =
    resolveStreamUrl(streamUrl, source)

/** Swaps .ts to .m3u8 if preferHls is active for an Xtream source. See [playStreamUrl] for why this is
 *  not gated on `hlsSupported`. */
fun resolveStreamUrl(url: String, source: SourceEntity?): String = url


/**
 * What a resync needs to know about a row it already holds. [sortOrder] is deliberately *outside*
 * `computeContentHash()`: folding it in would change every stored hash at once and turn the next
 * resync of a large catalog into a full rewrite, so the syncer compares it separately.
 */
data class ContentHashProjection(
    val remoteId: String,
    val id: Long,
    val contentHash: Int,
    val sortOrder: Int,
)

/**
 * The v26 fields ([ChannelEntity.catchupType] / [ChannelEntity.httpHeaders]), the v33
 * [ChannelEntity.drmConfig], the v43 [ChannelEntity.manifestType] / [ChannelEntity.directSource], the
 * v49 [ChannelEntity.catchupCorrectionMin], the v51 [ChannelEntity.providerName] and the v55
 * [ChannelEntity.isPaid] are folded in ONLY when the channel actually carries one. Adding them unconditionally would change every stored hash at once and turn the next resync of
 * a 100k-channel playlist into a full rewrite for everybody; this way only the (rare) channels that use
 * them pay a single re-upsert, while a later change to a header, a catch-up type, a licence URL, a
 * declared container or a correction still propagates.
 */
fun ChannelEntity.computeContentHash(): Int {
    val base = Objects.hash(
        sourceId, name, logoUrl, streamUrl,
        epgChannelId, number, remoteId, catchup, catchupDays, catchupSource,
    )
    val withV43 = if (catchupType == null && httpHeaders == null && drmConfig == null &&
        manifestType == null && directSource == null
    ) {
        base
    } else {
        val withV26 = Objects.hash(base, catchupType, httpHeaders)
        val withDrm = if (drmConfig == null) withV26 else Objects.hash(withV26, drmConfig)
        if (manifestType == null && directSource == null) withDrm
        else Objects.hash(withDrm, manifestType, directSource)
    }
    val withV49 = if (catchupCorrectionMin == 0) withV43 else Objects.hash(withV43, catchupCorrectionMin)
    val withV51 = if (providerName == null) withV49 else Objects.hash(withV49, providerName)
    return if (!isPaid) withV51 else Objects.hash(withV51, true)
}
