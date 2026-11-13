package tv.own.owntv.core.database.entity

import androidx.compose.runtime.Immutable

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import tv.own.owntv.core.database.SingleSource
import tv.own.owntv.core.model.MediaType

/**
 * A category/folder for a media type (LIVE / MOVIE / SERIES). The Layer-2 rail is built from these.
 * `remoteId` is the provider's category id (used to dedupe on re-sync). Schema v5 dropped
 * `sourceId` — there is one built-in source, so a category is simply (mediaType, remoteId).
 */
@Entity(
    tableName = "categories",
    indices = [
        Index("mediaType"),
        Index(value = ["mediaType", "remoteId"], unique = true),
    ],
)
@Immutable
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaType: MediaType,
    val name: String,
    val remoteId: String? = null,
    val sortOrder: Int = 0,
)

@Entity(
    tableName = "channels",
    // One built-in RuTube source, one natural key. Everything the old wide row carried (row id,
    // sourceId, stream marker, EPG id, catch-up/DRM/headers…) is either the hex key itself or a
    // constant — they live on as code-level synonyms below, so the ~700 read sites keep compiling
    // while the table carries only what RuTube actually publishes.
    indices = [
        Index(value = ["name"]),
        Index(value = ["sortOrder", "name"]),
        Index(value = ["number"]),
    ],
)
@Immutable
data class ChannelEntity(
    /** The provider's natural key (RuTube hex videoId) — the row's PK and the universal key for
     *  junction membership, renames, EPG and every user-data reference. Survives re-syncs. */
    @PrimaryKey val remoteId: String,
    val name: String,
    val logoUrl: String? = null,
    val number: Int? = null,
    val sortOrder: Int = 0,
) {
    /** Former row id — now just the natural key. Call sites that keyed user data on it key on the hex. */
    val id: String get() = remoteId

    /** tvg-id (M3U) / epg_channel_id (Xtream) — links to EPG; for RuTube it IS the videoId. */
    val epgChannelId: String get() = remoteId

    /** The tune-time marker `rutube://{videoId}` — resolved to a live HLS URL by the built-in helper. */
    val streamUrl: String get() = "rutube://$remoteId"

    /** Single-source world: every channel belongs to the one built-in source (see [SingleSource]). */
    val sourceId: Long get() = SingleSource.id

    val catchup: Boolean get() = false
    val catchupDays: Int get() = 0
    val catchupSource: String? get() = null
    val catchupType: String? get() = null
    val catchupCorrectionMin: Int get() = 0
    val httpHeaders: String? get() = null
    val drmConfig: String? get() = null
    val manifestType: String? get() = null
    val directSource: String? get() = null
    val providerName: String? get() = null
    val isPaid: Boolean get() = false
}

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



