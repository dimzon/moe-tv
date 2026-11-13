package tv.own.owntv.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * The app has a single user (profiles were removed with the live-only redesign) and is live-only:
 * schema v6 dropped `mediaType` from every table. `itemId` is the channel's hex remoteId, so
 * referential cleanup of these rows is handled in the repository layer.
 */

@Entity(tableName = "favorites")
data class FavoriteEntity(
    /** The channel's hex remoteId — the natural key, no surrogate id. */
    @PrimaryKey val itemId: String,
    val addedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "watch_history",
    primaryKeys = ["itemId", "deviceId"],
    indices = [
        Index("watchedAt"),
    ],
)
data class WatchHistoryEntity(
    val itemId: String,
    val watchedAt: Long = System.currentTimeMillis(),
    /**
     * The device that wrote the row ([tv.own.owntv.core.sync.local.DeviceIdentity].id hash). One row
     * per item **per device**, so two household devices that watched the same channel keep their own
     * timestamp instead of overwriting each other's. `""` = the id could not be read; it is a real
     * key value, never "another device". The sync payload carries it verbatim (`dev`).
     */
    val deviceId: String = "",
)

@Entity(
    tableName = "content_order",
    indices = [
        Index("contextKey"),
        Index(value = ["contextKey", "itemId"], unique = true),
    ],
)
data class ContentOrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** A category stable key (CustomizeKeys.category) for a folder, or [FAV_CONTEXT] for Favorites. */
    val contextKey: String,
    val itemId: String,
    val position: Int,
    /**
     * LWW stamp (epoch ms) for the per-row last-write-wins merge: every local Move refreshes it
     * ([tv.own.owntv.core.database.dao.ContentOrderDao.replaceContext]), and the sync apply only
     * replaces a row whose incoming copy is **newer** ([tv.own.owntv.core.database.dao.ContentOrderDao.bumpIfNewer]).
     * 0 = a row that predates the column (or an old container's timestamp-less order record) —
     * such a row is inserted when absent but never wins against a stamped one.
     */
    @androidx.room.ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
) {
    companion object {
        /** Sentinel [contextKey] for the per-section Favorites list. */
        const val FAV_CONTEXT = "__fav__"
    }
}

/**
 * Per-item playback preferences: the zoom/aspect mode and the volume level the user last chose
 * while watching one specific channel. Both are nullable and a null means "no per-item choice —
 * follow the global default in Settings", which is also why there is no row at all until the user
 * changes something in the player.
 *
 * [contentKey] is the channel's stable identity — the hex remoteId from
 * [tv.own.owntv.core.player.contentKey], falling back to the stream URL for rows with no provider
 * id. It survives re-syncs on its own, so these rows need no snapshot/relink pass.
 */
@Entity(
    tableName = "playback_prefs",
    primaryKeys = ["contentKey"],
)
data class PlaybackPrefsEntity(
    /** [tv.own.owntv.core.player.contentKey] result, or the stream URL when the row has none. */
    val contentKey: String,
    /** A [tv.own.owntv.player.ZoomMode] name; null = follow the global default zoom. */
    val zoomMode: String? = null,
    /** Volume percent (0–150, the shared boost ceiling); null = follow the global default volume. */
    val volumeBoost: Int? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    /** The audio track language last chosen here; null = follow the preference. */
    val audioLang: String? = null,
    /** The subtitle track language last chosen here; `""` = subtitles off. */
    val subtitleLang: String? = null,
)

/**
 * What a **stream** needs (v44). [playback_prefs][PlaybackPrefsEntity] is what the *person* prefers —
 * zoom and volume; the facts here belong to the stream and the device's output: whether it is sound
 * only, its lip-sync correction.
 *
 * Keyed by [tv.own.owntv.core.player.contentKey] like every per-item store. The per-stream engine
 * pin it also used to carry is gone with the second engine (v56); what is left is the audio-only
 * choice and the lip-sync correction. Schema v5 dropped `sourceId` — there is one source.
 */
@Entity(
    tableName = "playback_quirks",
)
data class PlaybackQuirkEntity(
    @PrimaryKey val contentKey: String,
    val audioOnly: Boolean? = null,
    /** A/V-sync offset in ms (-5000..5000, positive = audio delayed); null = follow the global default. */
    val audioDelayMs: Int? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * Membership rows of the user's custom combined categories. One row pins one content
 * item (channel/movie/series) to a [position] inside a custom category, identified by its stable
 * DataStore key — `CustomizeKeys` with the `"custom:"` prefix, e.g. `"custom:1b2f…"` — in
 * [contextKey]. Modeled EXACTLY on [ContentOrderEntity]: the same (mediaType, contextKey,
 * itemId) uniqueness, the same position semantics (a custom category's rails JOIN this table and
 * order by [position] first), so Move works inside custom categories through the identical
 * content_order machinery (the custom category's own order rows live HERE, not in content_order).
 *
 * The membership itself is Room (not DataStore) on purpose: the browse rails need to JOIN
 * `custom_category_members` against channels/movies/series, and DataStore string keys can't be
 * JOINed. The catalog tables are clear-then-insert on every sync, so [itemId] is volatile — these
 * rows are snapshotted with stable keys and re-attached after a sync by UserDataResolver, exactly
 * like [ContentOrderEntity] and favorites/history.
 */
@Entity(
    tableName = "custom_category_members",
    indices = [
        Index("contextKey"),
        Index(value = ["contextKey", "itemId"], unique = true),
    ],
)
data class CustomCategoryMemberEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** The custom category's stable key (`"custom:<uuid>"`, see CustomizeKeys.CUSTOM_PREFIX). */
    val contextKey: String,
    val itemId: String,
    val position: Int,
)

/**
 * A user data row the user deleted, remembered so the deletion survives a sync (v36).
 *
 * Local sync merges — it never clobbers — so without this a favorite removed on the phone simply
 * looks *absent* there and *present* on the TV, and the next merge helpfully puts it back. A
 * tombstone makes the absence deliberate: "this was deleted at this moment", which the merge can
 * compare against the other device's timestamp the same way it compares two live rows.
 *
 * [identity] is NOT the volatile `itemId`. It is the same stable content key
 * [tv.own.owntv.core.backup.UserDataResolver] exports into a backup — the channel's hex remoteId
 * and name (plus the custom-category key for an order removal, and the start time for a reminder)
 * as a canonically-ordered JSON object — because the row being deleted has to be recognisable on
 * a device whose ids are entirely different, and across the clear-then-insert of a re-sync on this one.
 *
 * Only genuine user deletions are recorded. Housekeeping deletes — the orphan purges after a
 * re-sync — must never write one: that would turn "your playlist was refreshed" into "delete this
 * on every other device too".
 */
@Entity(
    tableName = "user_data_tombstones",
    indices = [
        Index(value = ["kind", "identity"], unique = true),
        Index("deletedAt"),
    ],
)
data class UserDataTombstoneEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /**
     * Which list the row was deleted from: `fav`, `order` or `prog` — the kinds whose deletions
     * travel in the sync payload (see [tv.own.owntv.core.backup.UserDataResolver.TOMBSTONE_KINDS]).
     * Rows of the retired `his`/`member` kinds simply stop being written and age out of the prune.
     */
    val kind: String,
    /** Canonical stable content key JSON; see [tv.own.owntv.core.backup.UserDataResolver.canonicalIdentity]. */
    val identity: String,
    val deletedAt: Long = System.currentTimeMillis(),
)
