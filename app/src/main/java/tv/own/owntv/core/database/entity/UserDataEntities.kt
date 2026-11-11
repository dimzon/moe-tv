package tv.own.owntv.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import tv.own.owntv.core.model.MediaType

/*
 * The app has a single user (profiles were removed with the live-only redesign), so every user-data
 * table is global. `itemId` is the local id of a channel and is disambiguated by `mediaType`, so
 * referential cleanup of these rows is handled in the repository layer.
 */

@Entity(
    tableName = "favorites",
    indices = [
        Index(value = ["mediaType", "itemId"], unique = true),
    ],
)
data class FavoriteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaType: MediaType,
    val itemId: Long,
    val addedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "watch_history",
    indices = [
        Index("watchedAt"),
        Index(value = ["mediaType", "itemId"], unique = true),
    ],
)
data class WatchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaType: MediaType,
    val itemId: Long,
    val watchedAt: Long = System.currentTimeMillis(),
    /**
     * The device that wrote the row ([tv.own.owntv.core.sync.local.DeviceIdentity].id hash) — the
     * foundation of the cross-device "recently watched in this household" rail: after a sync merges
     * another device's rows in, the rail can attribute and tune them. Nullable on rows written
     * before the column existed, or where the id could not be read; the sync payload carries it
     * verbatim (`dev`), and an apply writes it as it came.
     */
    val deviceId: String? = null,
)

@Entity(
    tableName = "content_order",
    indices = [
        Index(value = ["mediaType", "contextKey"]),
        Index(value = ["mediaType", "contextKey", "itemId"], unique = true),
    ],
)
data class ContentOrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** LIVE / MOVIE / SERIES — never EPISODE (episodes aren't reorderable). */
    val mediaType: MediaType,
    /** A category stable key (CustomizeKeys.category) for a folder, or [FAV_CONTEXT] for Favorites. */
    val contextKey: String,
    val itemId: Long,
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
 * while watching one specific channel, film or episode. Both are nullable and a null means "no
 * per-item choice — follow the global default in Settings", which is also why there is no row at
 * all until the user changes something in the player.
 *
 * [contentKey] is deliberately NOT the volatile Room `itemId` that favorites/history use. It is the
 * stable identity — `sourceId:mediaType:remoteId` from
 * [tv.own.owntv.core.player.contentKey], falling back to the stream URL for rows with no provider
 * id — exactly like the per-item stores and the subtitle tables. That key survives the clear-then-insert of a re-sync on
 * its own, so unlike `content_order` these rows need no snapshot/relink pass: after a re-sync the
 * same film computes the same key and finds its own row again.
 */
@Entity(
    tableName = "playback_prefs",
    primaryKeys = ["contentKey"],
    indices = [Index("sourceId")],
)
data class PlaybackPrefsEntity(
    /** [tv.own.owntv.core.player.contentKey] result, or the stream URL when the row has none. */
    val contentKey: String,
    /** A [tv.own.owntv.player.ZoomMode] name; null = follow the global default zoom. */
    val zoomMode: String? = null,
    /** Volume percent (0–150, the shared boost ceiling); null = follow the global default volume. */
    val volumeBoost: Int? = null,
    /**
     * A/V-sync offset in ms (-5000..5000, positive = audio delayed); null = follow the global default.
     *
     * Per item because lip-sync error belongs to the **stream**, not to the user: one badly-muxed film
     * or one channel whose provider mis-times its audio needs the correction, and carrying that same
     * correction onto the next, correctly-muxed item would break it.
     */
    val audioDelayMs: Int? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    /**
     * The playlist [contentKey] belongs to, so deleting a playlist deletes its rows.
     * `-1` = unknown — a legacy stream-URL key, or a row written before v44.
     */
    @androidx.room.ColumnInfo(defaultValue = "-1") val sourceId: Long = -1,
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
 * choice and the lip-sync correction. [sourceId] lets a playlist's rows go with it.
 */
@Entity(
    tableName = "playback_quirks",
    indices = [Index("sourceId")],
)
data class PlaybackQuirkEntity(
    @PrimaryKey val contentKey: String,
    /** `-1` = unknown (a legacy stream-URL key). */
    val sourceId: Long,
    /**
     * `LIVE`, `MOVIE` or `EPISODE` — the kind of item, recorded by whoever wrote the row. The key
     * alone cannot say: an item with no provider id is keyed by its stream URL. Live rows are what
     * the surviving stores write; the older VOD kinds still sit in rows carried from before.
     */
    val mediaType: String,
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
        Index(value = ["mediaType", "contextKey"]),
        Index(value = ["mediaType", "contextKey", "itemId"], unique = true),
    ],
)
data class CustomCategoryMemberEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** LIVE / MOVIE / SERIES — never EPISODE (episodes aren't added to custom categories). */
    val mediaType: MediaType,
    /** The custom category's stable key (`"custom:<uuid>"`, see CustomizeKeys.CUSTOM_PREFIX). */
    val contextKey: String,
    val itemId: Long,
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
 * [tv.own.owntv.core.backup.UserDataResolver] exports into a backup — source id, provider remote id
 * and name (or show + season/episode) as a canonically-ordered JSON object — because the row being
 * deleted has to be recognisable on a device whose ids are entirely different, and across the
 * clear-then-insert of a re-sync on this one.
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
