package tv.own.owntv.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import tv.own.owntv.core.database.entity.ReminderEntity

/**
 * Programme reminders. The sync payload carries them as the `prog` kind (identity
 * (epgChannelId, startMs), per-row LWW on [ReminderEntity.updatedAt]) — see [ReminderEntity].
 */
@Dao
interface ReminderDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reminder: ReminderEntity): Long

    @Query("DELETE FROM programme_reminders WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM programme_reminders WHERE id = :id")
    suspend fun getById(id: Long): ReminderEntity?

    /**
     * The sync-identity lookup: one reminder per programme per epg channel. `channelId` is local
     * and goes stale on a re-sync, so the payload (and the merge) key on `epgChannelId` instead.
     */
    @Query(
        "SELECT * FROM programme_reminders WHERE epgChannelId = :epgChannelId AND startMs = :startMs LIMIT 1",
    )
    suspend fun getByEpgAndStart(epgChannelId: String, startMs: Long): ReminderEntity?

    /**
     * Deletes the reminder only when it is older than [at] — the merge rule for an incoming
     * deletion (`prog` tombstone). A reminder re-set locally after the other device dismissed it is
     * newer and survives. Returns the number of rows removed.
     */
    @Query(
        "DELETE FROM programme_reminders WHERE epgChannelId = :epgChannelId AND startMs = :startMs " +
            "AND updatedAt <= :at",
    )
    suspend fun removeIfOlderThan(epgChannelId: String, startMs: Long, at: Long): Int

    /** Soonest first — the guide marks these with a bell. */
    @Query("SELECT * FROM programme_reminders ORDER BY startMs ASC")
    fun observeAll(): Flow<List<ReminderEntity>>

    /** All of them as they change; which are due is decided against the clock (ReminderSchedule.due). */
    @Query("SELECT * FROM programme_reminders ORDER BY startMs ASC")
    suspend fun all(): List<ReminderEntity>

    @Query("DELETE FROM programme_reminders WHERE stopMs <= :now")
    suspend fun deleteEnded(now: Long): Int
}
