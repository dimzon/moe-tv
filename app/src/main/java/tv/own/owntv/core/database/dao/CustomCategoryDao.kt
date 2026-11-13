package tv.own.owntv.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import tv.own.owntv.core.database.ManualOrderRow
import tv.own.owntv.core.database.ManualOrdering
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.CustomCategoryMemberEntity

/**
 * Membership rows of the user's custom combined categories. Each row pins one channel to a
 * [position] inside a custom category, identified by its stable DataStore key
 * (`"custom:<uuid>"`, `CustomizeKeys.CUSTOM_PREFIX`) as [CustomCategoryMemberEntity.contextKey].
 * The table is modeled on `content_order`: membership defines the SET, while the user's manual
 * reorder WITHIN a custom category still rides the existing `content_order` rows (same contextKey —
 * the browse queries below LEFT JOIN both). See [CustomCategoryMemberEntity].
 *
 * Schema v6 dropped `mediaType`: the app is live-only, so membership is (contextKey, itemId).
 */
@Dao
interface CustomCategoryDao {

    // --- write side (mirrors ContentOrderDao) ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<CustomCategoryMemberEntity>)

    @Query("DELETE FROM custom_category_members WHERE contextKey = :contextKey")
    suspend fun clearContext(contextKey: String)

    /** Every custom-category membership: a restore's Replace, before the backup's are put back. */
    @Query("DELETE FROM custom_category_members")
    suspend fun clearAllMembers()

    /** Context keys that actually have membership rows. */
    @Query("SELECT DISTINCT contextKey FROM custom_category_members")
    fun observeContextKeys(): Flow<List<String>>

    /** Replaces a context's entire membership in one transaction (commit point of a Move). */
    @Transaction
    suspend fun replaceContext(contextKey: String, rows: List<CustomCategoryMemberEntity>) {
        clearContext(contextKey)
        if (rows.isNotEmpty()) insertAll(rows)
    }

    /** Highest existing position for a context — append-point for "Move to…". */
    @Query("SELECT COALESCE(MAX(position), -1) FROM custom_category_members WHERE contextKey = :contextKey")
    suspend fun maxPosition(contextKey: String): Int

    /** Appends one member atomically so two fast Move actions cannot receive the same position. */
    @Transaction
    suspend fun appendItem(contextKey: String, itemId: String) {
        insertAll(
            listOf(
                CustomCategoryMemberEntity(
                    contextKey = contextKey,
                    itemId = itemId,
                    position = maxPosition(contextKey) + 1,
                ),
            ),
        )
    }

    /** Does this row already exist? The dry run before a sync counts what is genuinely new. */
    @Query("SELECT EXISTS(SELECT 1 FROM custom_category_members WHERE contextKey = :contextKey AND itemId = :itemId)")
    suspend fun exists(contextKey: String, itemId: String): Boolean

    /** The custom categories [itemId] belongs to — a channel kept in one stays visible when its own
     *  provider folder is hidden. */
    @Query("SELECT DISTINCT contextKey FROM custom_category_members WHERE itemId = :itemId")
    suspend fun contextsOf(itemId: String): List<String>

    @Query("DELETE FROM custom_category_members WHERE contextKey = :contextKey AND itemId = :itemId")
    suspend fun deleteItem(contextKey: String, itemId: String)

    /**
     * Resolvable member counts — the "Move to…" dialog's per-row badges. Keys are the stable hex,
     * so a count only drops when the provider actually dropped the channel.
     */
    @Query(
        "SELECT contextKey, COUNT(*) AS count FROM custom_category_members " +
            "WHERE contextKey IN (:contextKeys) AND itemId IN (SELECT remoteId FROM channels) " +
            "GROUP BY contextKey",
    )
    fun observeCountsByContexts(contextKeys: List<String>): Flow<List<CustomCategoryCount>>

    /** [observeCountsByContexts] without the items filed under [excluded] (locked categories). */
    @Query(
        "SELECT contextKey, COUNT(*) AS count FROM custom_category_members " +
            "WHERE contextKey IN (:contextKeys) AND itemId IN (SELECT remoteId FROM channels " +
            "WHERE remoteId IN (SELECT channelHexId FROM channel_category WHERE categoryId NOT IN (:excluded)) " +
            "OR remoteId NOT IN (SELECT channelHexId FROM channel_category)) " +
            "GROUP BY contextKey",
    )
    fun observeCountsByContextsExcluding(
        contextKeys: List<String>,
        excluded: List<Long>,
    ): Flow<List<CustomCategoryCount>>

    /** Everything, for Backup & Restore / re-sync snapshotting. */
    @Query("SELECT * FROM custom_category_members")
    suspend fun getAllOnce(): List<CustomCategoryMemberEntity>

    /** Stable customization keys for every live member of one custom category. Used before delete
     *  to restore items that had been moved out of their provider folder. */
    @Query(
        "SELECT remoteId FROM custom_category_members m INNER JOIN channels c ON m.itemId = c.remoteId " +
            "WHERE m.contextKey = :contextKey",
    )
    suspend fun stableItemKeys(contextKey: String): List<String>

    /** Snapshot-scoped orphan drop (as FavoriteDao.purgeSnapshotOrphan) for many keys at once. */
    @Query(
        "DELETE FROM custom_category_members WHERE itemId IN (:itemIds) " +
            "AND itemId NOT IN (SELECT remoteId FROM channels)",
    )
    suspend fun purgeSnapshotOrphans(itemIds: List<String>)

    /** Drops membership rows whose channel no longer exists. */
    @Query("DELETE FROM custom_category_members WHERE itemId NOT IN (SELECT remoteId FROM channels)")
    suspend fun purgeOrphans()

    // --- browse side: paging / count / search (mirrors the ChannelDao manual-order joins) ---

    /**
     * Paged content of a custom category in rail order. Membership defines the SET (INNER JOIN);
     * the user's manual reorder within the category rides content_order with the same contextKey
     * (LEFT JOIN, matching [ContentOrderDao]'s position semantics); items without an explicit
     * order row fall back to membership position (insertion order), or A–Z when [alpha].
     */
    @Query(
        "SELECT c.*, o.position AS pos, m.position AS mp FROM channels c " +
        "INNER JOIN custom_category_members m ON m.itemId = c.remoteId AND m.contextKey = :contextKey " +
        "LEFT JOIN content_order o ON o.itemId = c.remoteId AND o.contextKey = :contextKey",
    )
    suspend fun baseChannelsManual(contextKey: String): List<ManualOrderRow>

    /** Live count of a custom category's in-scope items — the rail count badge. */
    @Query(
        "SELECT COUNT(*) FROM custom_category_members m " +
            "JOIN channels c ON m.itemId = c.remoteId " +
            "WHERE m.contextKey = :contextKey",
    )
    fun countMembers(contextKey: String): Flow<Int>

    /** [countMembers] without the items filed under [excluded] (locked categories). */
    @Query(
        "SELECT COUNT(*) FROM custom_category_members m " +
            "JOIN channels c ON m.itemId = c.remoteId " +
            "WHERE m.contextKey = :contextKey " +
            "AND (c.remoteId IN (SELECT channelHexId FROM channel_category WHERE categoryId NOT IN (:excluded)) " +
            "OR c.remoteId NOT IN (SELECT channelHexId FROM channel_category))",
    )
    fun countMembersExcluding(contextKey: String, excluded: List<Long>): Flow<Int>

    /** Content keys in one custom category. */
    @Query("SELECT itemId FROM custom_category_members WHERE contextKey = :contextKey")
    suspend fun itemIds(contextKey: String): List<String>

    /** Bounded snapshot of a custom category in rail order — the Move session's in-memory reorder. */
    suspend fun snapshotChannels(contextKey: String, alpha: Boolean, limit: Int): List<ChannelEntity> =
        ManualOrdering.order(baseChannelsManual(contextKey), alpha).take(limit)

    /** In-category search (mirrors ChannelDao.searchInCategory), for custom categories. */
    @Query(
        "SELECT c.* FROM channels c " +
            "INNER JOIN custom_category_members m ON m.itemId = c.remoteId AND m.contextKey = :contextKey " +
            "WHERE c.name LIKE '%' || :query || '%' " +
            "ORDER BY m.position, c.sortOrder, c.name",
    )
    fun searchChannels(query: String, contextKey: String): PagingSource<Int, ChannelEntity>
}

/** One custom category's member count, for the Move to… dialog badges. */
data class CustomCategoryCount(val contextKey: String, val count: Int)
