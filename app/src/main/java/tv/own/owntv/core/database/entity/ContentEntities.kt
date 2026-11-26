package tv.own.owntv.core.database.entity

import androidx.compose.runtime.Immutable

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import tv.own.owntv.core.database.SingleSource

/**
 * A category/folder. The Layer-2 rail is built from these. [id] is the provider's category id and
 * the primary key; [alias] is its provider slug ("all", "movies"…), stored for future use but not
 * read yet. Schema v6 dropped `mediaType` — the app is live-only, so a category is simply (id).
 */
@Entity(tableName = "categories")
@Immutable
data class CategoryEntity(
    /** RuTube's numeric category id — the natural key, no surrogate row id. */
    @PrimaryKey val id: Long,
    val name: String,
    /** Provider slug (`all`, `federal`, `movies`…), nullable when the API omits it. */
    val alias: String? = null,
    val sortOrder: Int = 0,
) {
    /** The provider id in the string form used by customization and sync keys. */
    val remoteId: String get() = id.toString()
}

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

    /** The RuTube videoId, which links the channel to its EPG entries. */
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

/** Return the stream URL to tune; the single built-in source needs no source-specific rewrite. */
fun ChannelEntity.playStreamUrl(source: SourceEntity?): String =
    resolveStreamUrl(streamUrl, source)

/** Return the URL unchanged for the single built-in source. */
fun resolveStreamUrl(url: String, source: SourceEntity?): String = url



