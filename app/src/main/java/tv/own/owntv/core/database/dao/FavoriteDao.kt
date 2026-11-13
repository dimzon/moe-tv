package tv.own.owntv.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import tv.own.owntv.core.database.entity.FavoriteEntity

/** Write side of favorites; the favorite channel list lives in the channel DAO (joins). */
@Dao
interface FavoriteDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(favorite: FavoriteEntity)

    /**
     * The per-row LWW apply: [add] inserts when the row is absent, and this moves `addedAt` — the
     * row's LWW stamp — forward **only when the incoming copy is newer**, so an older copy arriving
     * after a newer local re-favorite never takes the row back. Runs inside the resolver's transaction.
     */
    @Query("UPDATE favorites SET addedAt = :at WHERE itemId = :itemId AND addedAt < :at")
    suspend fun bumpIfNewer(itemId: String, at: Long)

    @Query("DELETE FROM favorites WHERE itemId = :itemId")
    suspend fun remove(itemId: String)

    /** Every favorite: a restore's Replace, before the backup's are put back. */
    @Query("DELETE FROM favorites")
    suspend fun clearAll()

    /**
     * Deletes the row only when it is older than [at] — the local-sync merge rule for an
     * incoming deletion. A row the user re-created after the other device deleted it is newer,
     * and survives. Returns the number of rows removed, so the sync summary can count it.
     */
    @Query("DELETE FROM favorites WHERE itemId = :itemId AND addedAt <= :at")
    suspend fun removeIfOlderThan(itemId: String, at: Long): Int

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE itemId = :itemId)")
    fun isFavorite(itemId: String): Flow<Boolean>

    /** When it was favorited — the dry run compares it against an incoming deletion. */
    @Query("SELECT addedAt FROM favorites WHERE itemId = :itemId")
    suspend fun addedAt(itemId: String): Long?

    /** Does this row already exist? The dry run before a sync counts what is genuinely new. */
    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE itemId = :itemId)")
    suspend fun exists(itemId: String): Boolean

    @Query("SELECT COUNT(*) FROM favorites")
    fun count(): Flow<Int>

    /** Favorite item keys (hex remoteIds), so lists can mark rows without a per-row query. */
    @Query("SELECT itemId FROM favorites")
    fun observeFavoriteIds(): Flow<List<String>>

    /** Everything, for Backup & Restore. */
    @Query("SELECT * FROM favorites")
    suspend fun getAllOnce(): List<FavoriteEntity>

    /** The row is an orphan when its channel no longer exists. Keys are the stable hex. */
    @Query("DELETE FROM favorites WHERE itemId = :itemId AND itemId NOT IN (SELECT remoteId FROM channels)")
    suspend fun purgeSnapshotOrphan(itemId: String)

    /**
     * Drops favorites whose channel no longer exists. Called after a re-sync — with the hex PK a
     * favorite's key survives every sync, so this only fires when the provider dropped the channel.
     */
    @Query("DELETE FROM favorites WHERE itemId NOT IN (SELECT remoteId FROM channels)")
    suspend fun purgeOrphans()
}
