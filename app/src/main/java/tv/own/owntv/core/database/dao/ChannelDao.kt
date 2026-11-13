package tv.own.owntv.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import tv.own.owntv.core.database.ManualOrderRow
import tv.own.owntv.core.database.ManualOrdering
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.ChannelNumberProjection
import tv.own.owntv.core.database.entity.GuideChannelName

/** One category's current channel count for a source (re-sync delta check). */
data class CategoryItemCount(val categoryId: Long, val itemCount: Int)

/** A channel plus its category name, for richer global-search results ("category · #number"). */
data class ChannelSearchResult(
    @Embedded val channel: ChannelEntity,
    val categoryName: String?,
)

/** A channel plus when it was last watched — for the Home screen's recent/continue rows. */
data class ChannelWithWatchedAt(
    @Embedded val channel: ChannelEntity,
    val watchedAt: Long,
)

/**
 * Live TV channels. Big lists are exposed as [PagingSource]; totals come from indexed COUNT queries
 * (per the plan's count requirements). FTS search joins via `channels_fts.rowid = channels.remoteId`
 * (the PK is the rowid of an ordinary rowid table). Favorites/history join the user-data tables by
 * the same hex key — there is no local row id to churn on a re-sync any more.
 */
@Dao
interface ChannelDao {
    /** Batch insert; the sync layer calls this in chunks (~500) inside a transaction. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(channels: List<ChannelEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(channels: List<ChannelEntity>)

    @Update
    suspend fun updateAll(channels: List<ChannelEntity>)

    @Query("SELECT * FROM channels WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getById(remoteId: String): ChannelEntity?

    // --- Stable-key lookups (Backup & Restore resolution; the key never changes on re-sync) ---
    @Query("SELECT * FROM channels WHERE remoteId = :remoteId LIMIT 1")
    suspend fun findByRemote(remoteId: String): ChannelEntity?

    @Query("SELECT * FROM channels WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): ChannelEntity?

    /** Channels whose own guide id is one of [epgKeys] — Search's "On TV". */
    @Query("SELECT * FROM channels WHERE remoteId IN (:epgKeys)")
    suspend fun byEpgKeys(epgKeys: List<String>): List<ChannelEntity>

    /** Channels a stored manual guide match may name, by its key's tail: the provider id, or the name. */
    @Query("SELECT * FROM channels WHERE remoteId IN (:tails) OR name IN (:tails)")
    suspend fun byRemoteIdsOrNames(tails: List<String>): List<ChannelEntity>

    /** Every channel — drives bulk EPG auto-matching. */
    @Query("SELECT * FROM channels ORDER BY sortOrder ASC, name ASC LIMIT :limit")
    suspend fun allForSources(limit: Int): List<ChannelEntity>

    /** Every guide key the channels reference — the set of EPG ids the user's channels actually
     *  use, used to filter the bulk EPG sync down from the whole feed. */
    @Query("SELECT DISTINCT remoteId FROM channels")
    suspend fun allEpgChannelIds(): List<String>

    /**
     * Every channel as a guide candidate: its guide key (== remoteId) and the name the user sees.
     * The "Match EPG" picker and auto-match read this instead of a separate guide-channel table —
     * with one source the guide key IS the catalogue key, so names come from the catalogue.
     */
    @Query("SELECT remoteId AS epgChannelId, name AS displayName FROM channels LIMIT :limit")
    suspend fun guideChannelNames(limit: Int): List<GuideChannelName>

    /**
     * Channels that actually HAVE programmes in the window — so the guide never wastes its row limit
     * on channels without data. The guide key IS the hex remoteId, matching epg_programmes.epgChannelId.
     */
    @Query(
        "SELECT * FROM channels " +
            "WHERE (:query = '' OR name LIKE '%' || :query || '%') " +
            "AND remoteId IN (SELECT DISTINCT epgChannelId FROM epg_programmes) " +
            "ORDER BY number ASC, name ASC LIMIT :limit",
    )
    suspend fun channelsWithGuide(query: String, limit: Int): List<ChannelEntity>

    /** Every channel, same order — the TV Guide with Show › Channels without guide on. */
    @Query(
        "SELECT * FROM channels " +
            "WHERE (:query = '' OR name LIKE '%' || :query || '%') ORDER BY number ASC, name ASC LIMIT :limit",
    )
    suspend fun channelsForGuide(query: String, limit: Int): List<ChannelEntity>

    /** Channels matching these remoteIds — to resolve smart-matched channels in bulk. */
    @Query("SELECT * FROM channels WHERE remoteId IN (:remoteIds)")
    suspend fun findByRemoteIds(remoteIds: List<String>): List<ChannelEntity>

    @Query("SELECT remoteId FROM channels")
    suspend fun remoteIdsForSource(): List<String>

    /** Any one channel — the "Test HLS support" probe needs a channel to ask about. */
    @Query("SELECT remoteId FROM channels LIMIT 1")
    suspend fun anyRemoteId(): String?

    /** Prune scope for the per-category sync fallback. */
    @Query("SELECT remoteId FROM channels WHERE remoteId IN (SELECT channelHexId FROM channel_category WHERE categoryId IN (:categoryIds))")
    suspend fun remoteIdsInCategories(categoryIds: List<Long>): List<String>

    /**
     * Write only the position of a row whose content did not change. The whole point is what it
     * does *not* touch — one measured resync had 1,397 real changes producing 255,775 pointless
     * rewrites when the update wrote every column.
     */
    @Query("UPDATE channels SET sortOrder = :sortOrder WHERE remoteId = :remoteId")
    suspend fun setSortOrder(remoteId: String, sortOrder: Int)

    /** One transaction for the whole batch; without it each statement pays its own commit. */
    @Transaction
    suspend fun applyPositionMoves(remoteIds: List<String>, sortOrders: List<Int>) {
        for (i in remoteIds.indices) setSortOrder(remoteIds[i], sortOrders[i])
    }

    /** Every channel in playlist order, with its number — what `PlaylistNumbering` works from. */
    @Query("SELECT remoteId, sortOrder, number FROM channels ORDER BY sortOrder ASC")
    suspend fun numbersForSource(): List<ChannelNumberProjection>

    /** Sets only the number, for the same reason [setSortOrder] sets only the position. */
    @Query("UPDATE channels SET number = :number WHERE remoteId = :remoteId")
    suspend fun setNumber(remoteId: String, number: Int?)

    @Transaction
    suspend fun applyNumbers(remoteIds: List<String>, numbers: List<Int?>) {
        for (i in remoteIds.indices) setNumber(remoteIds[i], numbers[i])
    }

    // --- Direct tune (channel-number lookup) ---
    /** All channels whose provider number matches [number]. Non-unique because provider data can
     *  contain duplicates; the caller applies visibility and zap-context policy. */
    @Query("SELECT * FROM channels WHERE number = :number ORDER BY sortOrder ASC, name ASC")
    suspend fun findByNumber(number: Int): List<ChannelEntity>

    /** Bounded window of channels in provider order AFTER [afterSortOrder]/[afterRemoteId] within a category.
     *  Combined with [channelsBeforeCategory] to build a neighbourhood around a tuned channel.
     *  Ordered by (sortOrder, remoteId) to match the cursor predicate — avoids mismatch when sortOrder ties. */
    @Query(
        "SELECT * FROM channels WHERE remoteId IN (SELECT channelHexId FROM channel_category WHERE categoryId = :categoryId) " +
            "AND (sortOrder > :afterSortOrder OR (sortOrder = :afterSortOrder AND remoteId > :afterRemoteId)) " +
            "ORDER BY sortOrder ASC, remoteId ASC LIMIT :limit",
    )
    suspend fun channelsAfterCategory(categoryId: Long, afterSortOrder: Int, afterRemoteId: String, limit: Int): List<ChannelEntity>

    /** Bounded window of channels in provider order BEFORE [beforeSortOrder]/[beforeRemoteId] within a category. */
    @Query(
        "SELECT * FROM channels WHERE remoteId IN (SELECT channelHexId FROM channel_category WHERE categoryId = :categoryId) " +
            "AND (sortOrder < :beforeSortOrder OR (sortOrder = :beforeSortOrder AND remoteId < :beforeRemoteId)) " +
            "ORDER BY sortOrder DESC, remoteId DESC LIMIT :limit",
    )
    suspend fun channelsBeforeCategory(categoryId: Long, beforeSortOrder: Int, beforeRemoteId: String, limit: Int): List<ChannelEntity>

    /** Bounded window of channels in provider order AFTER [afterSortOrder]/[afterRemoteId]. */
    @Query(
        "SELECT * FROM channels WHERE (sortOrder > :afterSortOrder OR (sortOrder = :afterSortOrder AND remoteId > :afterRemoteId)) " +
            "ORDER BY sortOrder ASC, remoteId ASC LIMIT :limit",
    )
    suspend fun channelsAfterSource(afterSortOrder: Int, afterRemoteId: String, limit: Int): List<ChannelEntity>

    /** Bounded window of channels in provider order BEFORE [beforeSortOrder]/[beforeRemoteId]. */
    @Query(
        "SELECT * FROM channels WHERE (sortOrder < :beforeSortOrder OR (sortOrder = :beforeSortOrder AND remoteId < :beforeRemoteId)) " +
            "ORDER BY sortOrder DESC, remoteId DESC LIMIT :limit",
    )
    suspend fun channelsBeforeSource(beforeSortOrder: Int, beforeRemoteId: String, limit: Int): List<ChannelEntity>

    @Query("DELETE FROM channels WHERE remoteId IN (:remoteIds)")
    suspend fun deleteByRemoteIds(remoteIds: List<String>)

    // --- Browsing (each list has a playlist-order and an A–Z variant; the sort chip picks one) ---
    @Query("SELECT * FROM channels WHERE remoteId IN (SELECT channelHexId FROM channel_category WHERE categoryId = :categoryId) ORDER BY sortOrder ASC, name ASC")
    fun pagingByCategory(categoryId: Long): PagingSource<Int, ChannelEntity>

    @Query("SELECT * FROM channels WHERE remoteId IN (SELECT channelHexId FROM channel_category WHERE categoryId = :categoryId) ORDER BY name ASC")
    fun pagingByCategoryAlpha(categoryId: Long): PagingSource<Int, ChannelEntity>

    @Query("SELECT * FROM channels ORDER BY name ASC")
    fun pagingAll(): PagingSource<Int, ChannelEntity>

    @Query("SELECT * FROM channels ORDER BY sortOrder ASC, name ASC")
    fun pagingAllOriginal(): PagingSource<Int, ChannelEntity>

    // --- Manual order (Move) — LEFT JOIN content_order; a row with a saved position keeps it, the rest
    //     follow A–Z or the provider/playlist order around it. ---
    /** The manual-order inputs for one folder: each channel with its saved position (null = never
     *  moved) and membership position (0 here). The ordering itself is computed in Kotlin
     *  ([tv.own.owntv.core.database.ManualOrdering]). Browse pages it with
     *  [tv.own.owntv.core.database.ManualOrderPagingSource]; the Move session uses [snapshotByCategoryManual]. */
    @Query(
        "SELECT c.*, o.position AS pos, 0 AS mp FROM channels c " +
        "LEFT JOIN content_order o ON o.itemId = c.remoteId AND o.contextKey = :contextKey " +
        "WHERE c.remoteId IN (SELECT channelHexId FROM channel_category WHERE categoryId = :categoryId)",
    )
    suspend fun baseByCategoryManual(categoryId: Long, contextKey: String): List<ManualOrderRow>

    /**
     * A tick that re-emits on any write to the tables the manual order reads (channels, content_order,
     * custom_category_members). Room's @Query PagingSource used to invalidate itself on those writes;
     * the Kotlin-sorted [tv.own.owntv.core.database.ManualOrderPagingSource] has no such hook, so the
     * browse Pager combines this flow to rebuild the source when the order changes. The value is never
     * read — only its emissions matter.
     */
    @Query(
        "SELECT (SELECT COUNT(*) FROM channels) " +
        "+ (SELECT COUNT(*) FROM content_order) " +
        "+ (SELECT COUNT(*) FROM custom_category_members)",
    )
    fun observeManualVersion(): Flow<Int>


    @Query(
        "SELECT c.* FROM channels c " +
            "INNER JOIN favorites f ON f.itemId = c.remoteId " +
            "LEFT JOIN content_order o ON o.itemId = c.remoteId AND o.contextKey = :contextKey " +
            "ORDER BY (CASE WHEN o.position IS NULL THEN 1 ELSE 0 END), o.position, f.addedAt DESC",
    )
    fun pagingFavoritesManual(contextKey: String): PagingSource<Int, ChannelEntity>

    /** Bounded snapshot of a folder in manual order, for the Move session's in-memory reorder. */
    suspend fun snapshotByCategoryManual(categoryId: Long, contextKey: String, alpha: Boolean, limit: Int): List<ChannelEntity> =
        ManualOrdering.order(baseByCategoryManual(categoryId, contextKey), alpha).take(limit)

    /** Bounded snapshot across the catalogue, in provider order — the in-player zap /
     *  channel-list fallback for a channel that has no category of its own. */
    @Query("SELECT * FROM channels ORDER BY sortOrder ASC, name ASC LIMIT :limit")
    suspend fun snapshotAll(limit: Int): List<ChannelEntity>

    /** Bounded snapshot of Favorites in manual order, for the Move session's in-memory reorder. */
    @Query(
        "SELECT c.* FROM channels c " +
            "INNER JOIN favorites f ON f.itemId = c.remoteId " +
            "LEFT JOIN content_order o ON o.itemId = c.remoteId AND o.contextKey = :contextKey " +
            "ORDER BY (CASE WHEN o.position IS NULL THEN 1 ELSE 0 END), o.position, f.addedAt DESC LIMIT :limit",
    )
    suspend fun snapshotFavoritesManual(contextKey: String, limit: Int): List<ChannelEntity>

    // --- Counts ---
    @Query("SELECT COUNT(*) FROM channels WHERE remoteId IN (SELECT channelHexId FROM channel_category WHERE categoryId = :categoryId)")
    fun countByCategory(categoryId: Long): Flow<Int>

    /** [countByCategory] for every category at once — the counts beside each Live TV group. */
    @Query(
        "SELECT cc.categoryId AS categoryId, COUNT(*) AS itemCount FROM channel_category cc " +
            "JOIN channels c ON c.remoteId = cc.channelHexId GROUP BY cc.categoryId",
    )
    fun observeCountsByCategory(): Flow<List<CategoryItemCount>>

    @Query("SELECT COUNT(*) FROM channels")
    fun countAll(): Flow<Int>

    /** "All Channels" count with hidden categories excluded (matches the filtered ALL list). */
    @Query(
        "SELECT COUNT(*) FROM channels " +
            "WHERE remoteId IN (SELECT channelHexId FROM channel_category WHERE categoryId NOT IN (:excludedCategoryIds)) " +
            "OR remoteId NOT IN (SELECT channelHexId FROM channel_category)",
    )
    fun countAllExcluding(excludedCategoryIds: List<Long>): Flow<Int>

    // --- Search (FTS4) ---
    @Query(
        "SELECT * FROM channels " +
            "WHERE rowid IN (SELECT rowid FROM channels_fts WHERE channels_fts MATCH :query) ORDER BY name ASC",
    )
    fun search(query: String): PagingSource<Int, ChannelEntity>

    // --- Inline folder-scoped search (substring LIKE, matches the user's expectation) ---
    @Query("SELECT * FROM channels WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchAll(query: String): PagingSource<Int, ChannelEntity>

    @Query("SELECT * FROM channels WHERE remoteId IN (SELECT channelHexId FROM channel_category WHERE categoryId = :categoryId) AND name LIKE '%' || :query || '%' ORDER BY sortOrder ASC, name ASC")
    fun searchInCategory(query: String, categoryId: Long): PagingSource<Int, ChannelEntity>

    /** Bounded list for global search (across the catalogue). */
    @Query("SELECT * FROM channels WHERE name LIKE '%' || :query || '%' ORDER BY name ASC LIMIT :limit")
    suspend fun searchList(query: String, limit: Int): List<ChannelEntity>

    /** Global search with the channel's category name joined in — lets results show "category · #number"
     *  so near-identical feeds (e.g. several "ABC") are distinguishable. */
    @Query(
        "SELECT c.*, (SELECT GROUP_CONCAT(cat.name, ' · ') FROM channel_category cc " +
            "JOIN categories cat ON cat.id = cc.categoryId WHERE cc.channelHexId = c.remoteId) AS categoryName " +
            "FROM channels c " +
            "WHERE c.name LIKE '%' || :query || '%' " +
            "ORDER BY c.name ASC LIMIT :limit",
    )
    suspend fun searchListDetailed(query: String, limit: Int): List<ChannelSearchResult>

    /** FTS-backed variant of [searchListDetailed] for global as-you-type search. */
    @Query(
        "SELECT c.*, (SELECT GROUP_CONCAT(cat.name, ' · ') FROM channel_category cc " +
            "JOIN categories cat ON cat.id = cc.categoryId WHERE cc.channelHexId = c.remoteId) AS categoryName " +
            "FROM channels c " +
            "WHERE c.rowid IN (SELECT rowid FROM channels_fts WHERE channels_fts MATCH :ftsQuery) " +
            "ORDER BY c.name ASC LIMIT :limit",
    )
    suspend fun searchListDetailedFts(ftsQuery: String, limit: Int): List<ChannelSearchResult>

    @Query(
        "SELECT c.* FROM channels c INNER JOIN favorites f ON f.itemId = c.remoteId " +
            "WHERE c.name LIKE '%' || :query || '%' ORDER BY f.addedAt DESC",
    )
    fun searchFavorites(query: String): PagingSource<Int, ChannelEntity>

    @Query(
        "SELECT c.* FROM channels c INNER JOIN watch_history h ON h.itemId = c.remoteId " +
            "WHERE c.name LIKE '%' || :query || '%' GROUP BY c.remoteId ORDER BY MAX(h.watchedAt) DESC",
    )
    fun searchHistory(query: String): PagingSource<Int, ChannelEntity>

    // --- Favorites / History ---
    @Query(
        "SELECT c.* FROM channels c " +
            "INNER JOIN favorites f ON f.itemId = c.remoteId " +
            "ORDER BY f.addedAt DESC",
    )
    fun pagingFavorites(): PagingSource<Int, ChannelEntity>

    @Query(
        "SELECT c.* FROM channels c " +
            "INNER JOIN favorites f ON f.itemId = c.remoteId " +
            "ORDER BY LOWER(c.name) ASC",
    )
    fun favoritesListAlpha(): Flow<List<ChannelEntity>>

    // Counts via the same join the list uses, so the badge can't show favorites whose channel went
    // stale before the prune catches up.
    @Query("SELECT COUNT(*) FROM favorites f INNER JOIN channels c ON c.remoteId = f.itemId")
    fun countFavorites(): Flow<Int>

    /** History rail count, joined to channels so it follows the same visibility as the list. */
    @Query(
        "SELECT COUNT(DISTINCT h.itemId) FROM watch_history h INNER JOIN channels c ON c.remoteId = h.itemId",
    )
    fun countHistory(): Flow<Int>

    /** [countFavorites] without the channels filed under [excluded] (locked categories). */
    @Query(
        "SELECT COUNT(*) FROM favorites f INNER JOIN channels c ON c.remoteId = f.itemId " +
            "WHERE (c.remoteId IN (SELECT channelHexId FROM channel_category WHERE categoryId NOT IN (:excluded)) OR c.remoteId NOT IN (SELECT channelHexId FROM channel_category))",
    )
    fun countFavoritesExcluding(excluded: List<Long>): Flow<Int>

    /** [countHistory] without the channels filed under [excluded] (locked categories). */
    @Query(
        "SELECT COUNT(DISTINCT h.itemId) FROM watch_history h INNER JOIN channels c ON c.remoteId = h.itemId " +
            "WHERE (c.remoteId IN (SELECT channelHexId FROM channel_category WHERE categoryId NOT IN (:excluded)) OR c.remoteId NOT IN (SELECT channelHexId FROM channel_category))",
    )
    fun countHistoryExcluding(excluded: List<Long>): Flow<Int>

    @Query(
        "SELECT c.* FROM channels c " +
            "INNER JOIN watch_history h ON h.itemId = c.remoteId " +
            "GROUP BY c.remoteId ORDER BY MAX(h.watchedAt) DESC",
    )
    fun pagingHistory(): PagingSource<Int, ChannelEntity>

    /** Recently-watched row at the top of Live TV. */
    @Query(
        "SELECT c.* FROM channels c " +
            "INNER JOIN watch_history h ON h.itemId = c.remoteId " +
            "GROUP BY c.remoteId ORDER BY MAX(h.watchedAt) DESC LIMIT :limit",
    )
    fun recentlyWatched(limit: Int): Flow<List<ChannelEntity>>

    @Query(
        "SELECT c.*, MAX(h.watchedAt) AS watchedAt FROM channels c " +
            "INNER JOIN watch_history h ON h.itemId = c.remoteId " +
            "GROUP BY c.remoteId ORDER BY watchedAt DESC LIMIT :limit",
    )
    fun recentlyWatchedWithTimestamp(limit: Int): Flow<List<ChannelWithWatchedAt>>
}
