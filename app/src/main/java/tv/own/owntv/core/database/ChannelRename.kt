package tv.own.owntv.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * A curated rename: the name [tv.own.owntv.core.database.entity.ChannelEntity] carries in every
 * list, overriding what the provider published (redesign §2 follow-up). Seeded from
 * [tv.own.owntv.core.sync.ChannelRenameSeeds] on every sync; the user's own renames in Customize
 * still apply on top of whatever lands here.
 *
 * Deliberately NO foreign key, keyed by the provider's hex videoId (the natural key of
 * `channels.remoteId`): a rename outlives its channel — a re-sync's clear-then-insert, a removed
 * source — and an orphan row is inert. A channel that comes back with the same videoId is renamed
 * again by [ChannelRenameDao.applyToChannels]. Local row ids would seed nothing (they only exist
 * after the first sync) and would not survive a re-sync's id churn — hence the hex key.
 */
@Entity(tableName = "channel_rename")
data class ChannelRenameEntity(
    @PrimaryKey val remoteId: String,
    val name: String,
)

@Dao
interface ChannelRenameDao {
    /** The shipped dictionary, mirrored wholesale: entries a new build dropped disappear too. */
    @Query("DELETE FROM channel_rename")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<ChannelRenameEntity>)

    /** Clear + insert in one transaction, so a reader never sees a half-mirrored dictionary. */
    @androidx.room.Transaction
    suspend fun replaceAll(rows: List<ChannelRenameEntity>) {
        clear()
        insertAll(rows)
    }

    @Query("SELECT * FROM channel_rename")
    suspend fun all(): List<ChannelRenameEntity>

    /**
     * Rewrite the stored display name of every channel the dictionary names. No-op for orphans
     * (their channel is gone), so deletions never break it; a returning channel is renamed again.
     * Runs on the sync path after the bulk commit — the one place that changes channel data — so a
     * dictionary change with an otherwise identical channel row still reaches `channels.name`.
     */
    @Query(
        "UPDATE channels SET name = (SELECT r.name FROM channel_rename r WHERE r.remoteId = channels.remoteId) " +
            "WHERE remoteId IN (SELECT remoteId FROM channel_rename)",
    )
    suspend fun applyToChannels()
}
