package tv.own.owntv.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import tv.own.owntv.core.database.entity.ContentOrderEntity
import tv.own.owntv.core.customize.pinnedAfterMove

/**
 * Write side of the manual item order ("Move up/down"). The ordered content lists are
 * produced by the content DAOs (LEFT JOIN content_order). See [ContentOrderEntity].
 */
@Dao
interface ContentOrderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<ContentOrderEntity>)

    /**
     * The merge write for the sync apply (phase ③), and deliberately not [insertAll]: REPLACE would
     * let the other device's **older** copy of a Move win purely by arriving second. Paired with
     * [bumpIfNewer] — insert when absent, then take the incoming position only if its [ContentOrderEntity.updatedAt]
     * is actually newer.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(row: ContentOrderEntity)

    /** Applies the incoming order row's position+stamp only when it is newer. The `<` is the whole rule. */
    @Query(
        "UPDATE content_order SET position = :position, updatedAt = :updatedAt " +
            "WHERE contextKey = :contextKey AND itemId = :itemId AND updatedAt < :updatedAt",
    )
    suspend fun bumpIfNewer(contextKey: String, itemId: String, position: Int, updatedAt: Long)

    /**
     * Deletes the row only when it is older than [at] — the merge rule for an incoming **deletion**
     * of an order row. A row the user re-moved locally after the other device deleted it is newer
     * and survives. Returns the number of rows removed, so the sync summary can count it.
     */
    @Query(
        "DELETE FROM content_order WHERE contextKey = :contextKey AND itemId = :itemId " +
            "AND updatedAt <= :at",
    )
    suspend fun removeIfOlderThan(contextKey: String, itemId: String, at: Long): Int

    /** The row's LWW stamp, or null when absent — the dry run compares it against an incoming copy. */
    @Query(
        "SELECT updatedAt FROM content_order WHERE contextKey = :contextKey AND itemId = :itemId LIMIT 1",
    )
    suspend fun updatedAt(contextKey: String, itemId: String): Long?

    @Query("DELETE FROM content_order WHERE contextKey = :contextKey")
    suspend fun clearContext(contextKey: String)

    /** Every manual position: a restore's Replace, before the backup's are put back. */
    @Query("DELETE FROM content_order")
    suspend fun clearAll()

    /** Context keys that actually have manual-order rows for this section — the fast
     *  path: folders with no overrides use the plain indexed paging query instead of the
     *  unindexable content_order join-sort (which materializes the whole folder per page). */
    @Query("SELECT DISTINCT contextKey FROM content_order")
    fun observeContextKeys(): Flow<List<String>>

    /**
     * Replaces a context's entire order in one transaction (commit point of a Move), stamping every
     * written row with the current time: [ContentOrderEntity.updatedAt] is the per-row LWW stamp the
     * sync merge compares, and a Move is the newest fact about that list. Callers that build rows
     * themselves (e.g. the favorites Move in the counts screen) are stamped here too, so every
     * write path through the DAO carries a fresh stamp.
     */
    @Transaction
    suspend fun replaceContext(contextKey: String, rows: List<ContentOrderEntity>) {
        clearContext(contextKey)
        if (rows.isNotEmpty()) {
            val now = System.currentTimeMillis()
            insertAll(rows.map { if (it.updatedAt == 0L) it.copy(updatedAt = now) else it })
        }
    }

    /**
     * Saves a Move of one list: [order] is the list as the move left it, [carried] the items the user
     * moved. Only those and the items moved before keep a position — the rest go on following the sort
     * chip around them (see `placePinned`). Favorites has no sort chip, so its whole order is kept.
     */
    @Transaction
    suspend fun saveMove(contextKey: String, order: List<String>, carried: Set<String>) {
        val positions =
            if (contextKey == ContentOrderEntity.FAV_CONTEXT) order.withIndex().associate { (i, id) -> id to i }
            else pinnedAfterMove(order, movedItemIds(contextKey).toSet(), carried)
        replaceContext(
            contextKey,
            positions.map { (id, pos) -> ContentOrderEntity(contextKey = contextKey, itemId = id, position = pos) },
        )
    }

    /** Drops every list order except [keep] (Favorites). Run once: orders saved by older versions held every
     *  item of the list, so nothing in it could follow the sort chip. */
    @Query("DELETE FROM content_order WHERE contextKey != :keep")
    suspend fun dropListOrdersExcept(keep: String)

    /** Does this row already exist? The dry run before a sync counts what is genuinely new. */
    @Query("SELECT EXISTS(SELECT 1 FROM content_order WHERE contextKey = :contextKey AND itemId = :itemId)")
    suspend fun exists(contextKey: String, itemId: String): Boolean

    /** The items of one list that the user moved by hand (they keep a saved position). */
    @Query("SELECT itemId FROM content_order WHERE contextKey = :contextKey")
    suspend fun movedItemIds(contextKey: String): List<String>

    @Query("SELECT * FROM content_order")
    suspend fun getAllOnce(): List<ContentOrderEntity>

    /**
     * Snapshot-scoped orphan drop (as FavoriteDao.purgeSnapshotOrphan) for many ids at once — a
     * one-row form would scan the table once per snapshot row.
     */
    @Query(
        "DELETE FROM content_order WHERE itemId IN (:itemIds) " +
            "AND itemId NOT IN (SELECT remoteId FROM channels)",
    )
    suspend fun purgeSnapshotOrphans(itemIds: List<String>)

    /**
     * Drops order rows whose content row no longer exists — content is clear-then-insert on every sync,
     * so an item's itemId goes stale. Called after a re-sync's relink (UserDataResolver), mirroring
     * FavoriteDao.purgeOrphans. Live-only now.
     */
    @Query("DELETE FROM content_order WHERE itemId NOT IN (SELECT remoteId FROM channels)")
    suspend fun purgeOrphans()
}
