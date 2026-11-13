package tv.own.owntv.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import tv.own.owntv.core.database.entity.CategoryEntity
import tv.own.owntv.core.database.entity.ChannelEntity

/**
 * The many-to-many link between a channel and the RuTube genres it is broadcast in
 * (`broadcast_category[]`). One channel has many rows here — the target schema §2 of the redesign,
 * which replaces the old single `channels.categoryId` "primary bucket". A channel is a member of
 * every category it appears in; the browse and count queries read this table.
 *
 * Keyed by the channel's **natural id** — the 16-byte hex `videoId` (stored as `channels.remoteId`,
 * the same value as `epgChannelId`) — not the surrogate Room `id`. The hex is stable forever, so a
 * re-sync's clear-then-insert never orphans a membership row and the syncer writes straight from the
 * provider payload with no id remap. The FK targets `channels.remoteId`, which carries a unique index.
 *
 * Both sides cascade: deleting a channel drops its memberships; deleting a category drops its side.
 */
@Entity(
    tableName = "channel_category",
    primaryKeys = ["channelHexId", "categoryId"],
    foreignKeys = [
        ForeignKey(entity = ChannelEntity::class, parentColumns = ["remoteId"], childColumns = ["channelHexId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("categoryId"), Index("channelHexId")],
)
data class ChannelCategoryEntity(
    /** The channel's 16-byte hex provider id (= `channels.remoteId`). */
    val channelHexId: String,
    val categoryId: Long,
)

@Dao
interface ChannelCategoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(rows: List<ChannelCategoryEntity>)

    /** Every membership — cleared and rebuilt on each sync. */
    @Query("DELETE FROM channel_category")
    suspend fun clearAll()

    /** The hex ids of the channels that actually exist in the DB — the set the link may point at. */
    @Query("SELECT remoteId FROM channels")
    suspend fun presentHexIds(): List<String>

    /** The categories a channel (by its hex id) belongs to. */
    @Query("SELECT categoryId FROM channel_category WHERE channelHexId = :hexId")
    suspend fun categoriesOf(hexId: String): List<Long>

    /**
     * Every membership as (hexId, categoryId) rows — the whole link table. At ~250 channels this is
     * a few hundred rows, loaded once to build a `hexId → categories` map so the visibility filters
     * (hidden / sealed) can ask "is any of this channel's categories visible" without a query per row.
     */
    @Query("SELECT channelHexId, categoryId FROM channel_category")
    suspend fun allMemberships(): List<ChannelCategoryEntity>

    /** Reactive view of the whole link table — the membership store observes this to keep a
     *  `hexId → categories` map current for the in-memory visibility filters. */
    @Query("SELECT channelHexId, categoryId FROM channel_category")
    fun observeAll(): kotlinx.coroutines.flow.Flow<List<ChannelCategoryEntity>>

    /**
     * Whether a channel (by hex id) is a member of [categoryId] — the junction replacement for the
     * old `channels.categoryId = :categoryId` equality. The category-lock / hidden / EPG-folder checks
     * must now ask "is this channel in this category", not "is this its category".
     */
    @Query("SELECT EXISTS(SELECT 1 FROM channel_category WHERE channelHexId = :hexId AND categoryId = :categoryId)")
    suspend fun isMember(hexId: String, categoryId: Long): Boolean
}
