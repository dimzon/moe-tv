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
import tv.own.owntv.core.model.MediaType

/**
 * Membership rows of the user's custom combined categories. Each row pins one content
 * item to a [position] inside a custom category, identified by its stable DataStore key
 * (`"custom:<uuid>"`, `CustomizeKeys.CUSTOM_PREFIX`) as [CustomCategoryMemberEntity.contextKey].
 * The table is modeled on `content_order`: membership defines the SET, while the user's manual
 * reorder WITHIN a custom category still rides the existing `content_order` rows (same contextKey —
 * the browse queries below LEFT JOIN both). See [CustomCategoryMemberEntity].
 */
@Dao
interface CustomCategoryDao {

    // --- write side (mirrors ContentOrderDao) ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<CustomCategoryMemberEntity>)

    @Query("DELETE FROM custom_category_members WHERE mediaType = :type AND contextKey = :contextKey")
    suspend fun clearContext(type: MediaType, contextKey: String)

    /** Every custom-category membership: a restore's Replace, before the backup's are put back. */
    @Query("DELETE FROM custom_category_members")
    suspend fun clearAllMembers()

    /** Context keys that actually have membership rows for this section. */
    @Query("SELECT DISTINCT contextKey FROM custom_category_members WHERE mediaType = :type")
    fun observeContextKeys(type: MediaType): Flow<List<String>>

    /** Replaces a context's entire membership in one transaction (commit point of a Move). */
    @Transaction
    suspend fun replaceContext(type: MediaType, contextKey: String, rows: List<CustomCategoryMemberEntity>) {
        clearContext(type, contextKey)
        if (rows.isNotEmpty()) insertAll(rows)
    }

    /** Highest existing position for a context — append-point for "Move to…". */
    @Query("SELECT COALESCE(MAX(position), -1) FROM custom_category_members WHERE mediaType = :type AND contextKey = :contextKey")
    suspend fun maxPosition(type: MediaType, contextKey: String): Int

    /** Appends one member atomically so two fast Move actions cannot receive the same position. */
    @Transaction
    suspend fun appendItem(type: MediaType, contextKey: String, itemId: Long) {
        insertAll(
            listOf(
                CustomCategoryMemberEntity(
                    mediaType = type,
                    contextKey = contextKey,
                    itemId = itemId,
                    position = maxPosition(type, contextKey) + 1,
                ),
            ),
        )
    }

    /** Does this row already exist? The dry run before a sync counts what is genuinely new. */
    @Query("SELECT EXISTS(SELECT 1 FROM custom_category_members WHERE mediaType = :type AND contextKey = :contextKey AND itemId = :itemId)")
    suspend fun exists(type: MediaType, contextKey: String, itemId: Long): Boolean

    /** The custom categories [itemId] belongs to — a channel kept in one stays visible when its own
     *  provider folder is hidden. */
    @Query("SELECT DISTINCT contextKey FROM custom_category_members WHERE mediaType = :type AND itemId = :itemId")
    suspend fun contextsOf(type: MediaType, itemId: Long): List<String>

    @Query("DELETE FROM custom_category_members WHERE mediaType = :type AND contextKey = :contextKey AND itemId = :itemId")
    suspend fun deleteItem(type: MediaType, contextKey: String, itemId: Long)

    /**
     * Resolvable member counts for the active sources — the "Move to…" dialog's per-row badges.
     * A re-sync temporarily leaves stale membership ids until relinking finishes. Neither kind of
     * row is visible in the category, so neither may inflate the badge.
     */
    @Query(
        "SELECT contextKey, COUNT(*) AS count FROM custom_category_members " +
            "WHERE mediaType = :type AND contextKey IN (:contextKeys) AND " +
            "itemId IN (SELECT id FROM channels WHERE sourceId IN (:sourceIds)) " +
            "GROUP BY contextKey",
    )
    fun observeCountsByContexts(
        type: MediaType,
        contextKeys: List<String>,
        sourceIds: List<Long>,
    ): Flow<List<CustomCategoryCount>>

    /** [observeCountsByContexts] without the items filed under [excluded] (locked categories). */
    @Query(
        "SELECT contextKey, COUNT(*) AS count FROM custom_category_members " +
            "WHERE mediaType = :type AND contextKey IN (:contextKeys) AND " +
            "itemId IN (SELECT id FROM channels WHERE sourceId IN (:sourceIds) " +
            "AND (remoteId IN (SELECT channelHexId FROM channel_category WHERE categoryId NOT IN (:excluded)) " +
            "OR remoteId NOT IN (SELECT channelHexId FROM channel_category))) " +
            "GROUP BY contextKey",
    )
    fun observeCountsByContextsExcluding(
        type: MediaType,
        contextKeys: List<String>,
        sourceIds: List<Long>,
        excluded: List<Long>,
    ): Flow<List<CustomCategoryCount>>

    /** Everything, for Backup & Restore / re-sync snapshotting. */
    @Query("SELECT * FROM custom_category_members")
    suspend fun getAllOnce(): List<CustomCategoryMemberEntity>

    /** Stable customization keys for every live member of one custom category. Used before delete
     *  to restore items that had been moved out of their provider folder. */
    @Query(
        "SELECT CAST(c.sourceId AS TEXT) || ':' || COALESCE(c.remoteId, c.name) " +
            "FROM custom_category_members m INNER JOIN channels c ON m.mediaType = 'LIVE' AND m.itemId = c.id " +
            "WHERE m.mediaType = 'LIVE' AND m.contextKey = :contextKey",
    )
    suspend fun stableItemKeys(contextKey: String): List<String>

    /**
     * Membership rows tied to one source, joined to stable content keys — the per-source re-sync
     * snapshot. Mirrors [ContentOrderDao.exportRowsForSource]: without this, a resync renumbered
     * the content ids and every Move the user had made was silently lost.
     */
    @Query(
        "SELECT m.mediaType AS mediaType, m.itemId AS itemId, " +
            "m.contextKey AS contextKey, m.position AS position, " +
            "c.sourceId AS sourceId, c.remoteId AS remoteId, c.name AS name " +
            "FROM custom_category_members m " +
            "JOIN channels c ON m.mediaType = 'LIVE' AND m.itemId = c.id " +
            "WHERE c.sourceId = :sourceId",
    )
    suspend fun exportRowsForSource(sourceId: Long): List<CustomCategoryMemberExportRow>

    /**
     * Snapshot-scoped orphan drop (as FavoriteDao.purgeSnapshotOrphan) for many ids at once. This
     * table has no index that starts with (mediaType, itemId), so a one-row form scans every row
     * for every snapshot row — quadratic, and hours with tens of thousands of rows while
     * it holds the writer.
     */
    @Query(
        "DELETE FROM custom_category_members WHERE mediaType = :type AND itemId IN (:itemIds) AND (" +
            "mediaType != 'LIVE' OR itemId NOT IN (SELECT id FROM channels))",
    )
    suspend fun purgeSnapshotOrphans(type: MediaType, itemIds: List<Long>)

    /**
     * Drops membership rows whose content row no longer exists — content is clear-then-insert on
     * every sync, so an item's itemId goes stale. Called after a re-sync's relink
     * (UserDataResolver), mirroring FavoriteDao.purgeOrphans.
     */
    @Query("DELETE FROM custom_category_members WHERE mediaType != 'LIVE' OR itemId NOT IN (SELECT id FROM channels)")
    suspend fun purgeOrphans()

    // --- browse side: paging / count / search (mirrors the ChannelDao manual-order joins) ---

    /**
     * Paged content of a custom category in rail order. Membership defines the SET (INNER JOIN);
     * the user's manual reorder within the category rides content_order with the same contextKey
     * (LEFT JOIN, matching [ContentOrderDao]'s position semantics); items without an explicit
     * order row fall back to membership position (insertion order), or A–Z when [alpha]. A moved
     * row keeps its saved position in either order.
     */
    @Query(
        "SELECT c.*, o.position AS pos, m.position AS mp FROM channels c " +
        "INNER JOIN custom_category_members m ON m.itemId = c.id AND m.mediaType = 'LIVE' AND m.contextKey = :contextKey " +
        "LEFT JOIN content_order o ON o.itemId = c.id AND o.mediaType = 'LIVE' AND o.contextKey = :contextKey " +
        "WHERE c.sourceId IN (:sourceIds)",
    )
    suspend fun baseChannelsManual(contextKey: String, sourceIds: List<Long>): List<ManualOrderRow>

    /** Live count of a custom category's in-scope items — the rail count badge. Live-only. */
    @Query(
        "SELECT COUNT(*) FROM custom_category_members m " +
            "JOIN channels c ON m.mediaType = 'LIVE' AND m.itemId = c.id " +
            "WHERE m.mediaType = :type AND m.contextKey = :contextKey " +
            "AND c.sourceId IN (:sourceIds)",
    )
    fun countMembers(type: MediaType, contextKey: String, sourceIds: List<Long>): Flow<Int>

    /** [countMembers] without the items filed under [excluded] (locked categories). Live-only. */
    @Query(
        "SELECT COUNT(*) FROM custom_category_members m " +
            "JOIN channels c ON m.mediaType = 'LIVE' AND m.itemId = c.id " +
            "WHERE m.mediaType = :type AND m.contextKey = :contextKey " +
            "AND c.sourceId IN (:sourceIds) " +
            "AND (c.remoteId IN (SELECT channelHexId FROM channel_category WHERE categoryId NOT IN (:excluded)) " +
            "OR c.remoteId NOT IN (SELECT channelHexId FROM channel_category))",
    )
    fun countMembersExcluding(type: MediaType, contextKey: String, sourceIds: List<Long>, excluded: List<Long>): Flow<Int>

    /** Content ids in one custom category. The Guide already holds its bounded channel window in
     *  memory, so this small projection lets it apply a custom-category filter without another
     *  content-table load. */
    @Query("SELECT itemId FROM custom_category_members WHERE mediaType = :type AND contextKey = :contextKey")
    suspend fun itemIds(type: MediaType, contextKey: String): List<Long>

    /** Bounded snapshot of a custom category in rail order — the Move session's in-memory reorder. */
    suspend fun snapshotChannels(contextKey: String, sourceIds: List<Long>, alpha: Boolean, limit: Int): List<ChannelEntity> =
        ManualOrdering.order(baseChannelsManual(contextKey, sourceIds), alpha).take(limit)

    /** In-category search (mirrors ChannelDao.searchInCategory), for custom categories. */
    @Query(
        "SELECT c.* FROM channels c " +
            "INNER JOIN custom_category_members m ON m.itemId = c.id AND m.mediaType = 'LIVE' AND m.contextKey = :contextKey " +
            "WHERE c.sourceId IN (:sourceIds) AND c.name LIKE '%' || :query || '%' " +
            "ORDER BY m.position, c.sortOrder, c.name",
    )
    fun searchChannels(query: String, contextKey: String, sourceIds: List<Long>): PagingSource<Int, ChannelEntity>
}

/** One custom category's member count, for the Move to… dialog badges. */
data class CustomCategoryCount(val contextKey: String, val count: Int)

/**
 * One membership row joined to its content row's stable identity, for the per-source re-sync
 * snapshot ([CustomCategoryDao.exportRowsForSource]). Mirrors [ContentOrderExportRow]; the shape is
 * identical because the table is modeled on content_order.
 */
data class CustomCategoryMemberExportRow(
    val mediaType: MediaType,
    val itemId: Long,
    val contextKey: String,
    val position: Int,
    val sourceId: Long,
    val remoteId: String?,
    val name: String?,
)
