package tv.own.owntv.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import tv.own.owntv.core.database.entity.FavoriteEntity
import tv.own.owntv.core.model.MediaType

/** Write side of favorites; per-type favorite content lists live in the content DAOs (joins). */
@Dao
interface FavoriteDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(favorite: FavoriteEntity)

    /**
     * The per-row LWW apply for favorites (phase ③): [add] inserts when the row is absent, and this
     * moves `addedAt` — the row's LWW stamp — forward **only when the incoming copy is newer**, so an
     * older copy arriving after a newer local re-favorite never takes the row back. Runs inside the
     * resolver's transaction.
     */
    @Query("UPDATE favorites SET addedAt = :at WHERE mediaType = :type AND itemId = :itemId AND addedAt < :at")
    suspend fun bumpIfNewer(type: MediaType, itemId: Long, at: Long)

    @Query("DELETE FROM favorites WHERE mediaType = :type AND itemId = :itemId")
    suspend fun remove(type: MediaType, itemId: Long)

    /** Every favorite: a restore's Replace, before the backup's are put back. */
    @Query("DELETE FROM favorites")
    suspend fun clearAll()

    /**
     * Deletes the row only when it is older than [at] — the local-sync merge rule for an
     * incoming deletion. A row the user re-created after the other device deleted it is newer,
     * and survives. Returns the number of rows removed, so the sync summary can count it.
     */
    @Query("DELETE FROM favorites WHERE mediaType = :type AND itemId = :itemId AND addedAt <= :at")
    suspend fun removeIfOlderThan(type: MediaType, itemId: Long, at: Long): Int

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE mediaType = :type AND itemId = :itemId)")
    fun isFavorite(type: MediaType, itemId: Long): Flow<Boolean>

    /** When it was favorited — the dry run compares it against an incoming deletion. */
    @Query("SELECT addedAt FROM favorites WHERE mediaType = :type AND itemId = :itemId")
    suspend fun addedAt(type: MediaType, itemId: Long): Long?

    /** Does this row already exist? The dry run before a sync counts what is genuinely new. */
    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE mediaType = :type AND itemId = :itemId)")
    suspend fun exists(type: MediaType, itemId: Long): Boolean

    @Query("SELECT COUNT(*) FROM favorites WHERE mediaType = :type")
    fun count(type: MediaType): Flow<Int>

    /** Favorite item ids for a type, so lists can mark rows without a per-row query. */
    @Query("SELECT itemId FROM favorites WHERE mediaType = :type")
    fun observeFavoriteIds(type: MediaType): Flow<List<Long>>

    /** Everything, for Backup & Restore. */
    @Query("SELECT * FROM favorites")
    suspend fun getAllOnce(): List<FavoriteEntity>

    /** User-data rows tied to one source, already joined to stable content keys for fast re-sync snapshots.
     *  Live-only: the only media type whose content rows still exist. */
    @Query(
        "SELECT f.mediaType AS mediaType, f.itemId AS itemId, " +
            "c.sourceId AS sourceId, c.remoteId AS remoteId, c.name AS name, " +
            "f.addedAt AS at " +
            "FROM favorites f " +
            "JOIN channels c ON f.mediaType = 'LIVE' AND f.itemId = c.id " +
            "WHERE c.sourceId = :sourceId",
    )
    suspend fun exportRowsForSource(sourceId: Long): List<UserDataExportRow>

    /** The row is an orphan when its type no longer has content rows at all (the app is live-only)
     *  or its LIVE row is gone. */
    @Query(
        "DELETE FROM favorites WHERE mediaType = :type AND itemId = :itemId AND (" +
            "mediaType != 'LIVE' OR itemId NOT IN (SELECT id FROM channels))",
    )
    suspend fun purgeSnapshotOrphan(type: MediaType, itemId: Long)

    /**
     * Drops favorites whose content row no longer exists — content is clear-then-insert on every sync,
     * so a favorite's itemId goes stale and the join returns nothing while the raw count still showed
     * it. Called after a re-sync's relink (UserDataResolver).
     */
    @Query("DELETE FROM favorites WHERE mediaType != 'LIVE' OR itemId NOT IN (SELECT id FROM channels)")
    suspend fun purgeOrphans()
}
