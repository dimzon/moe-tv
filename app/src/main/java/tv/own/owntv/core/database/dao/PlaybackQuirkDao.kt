package tv.own.owntv.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import tv.own.owntv.core.database.entity.PlaybackQuirkEntity

/**
 * What a stream needs, global for the whole device — see [PlaybackQuirkEntity].
 *
 * Each value is written on its own and never clears the others: correcting a channel's lip-sync must
 * not forget that it is sound only. A row whose values have all gone back to null is dropped.
 */
@Dao
interface PlaybackQuirkDao {
    @Query("SELECT * FROM playback_quirks WHERE contentKey = :contentKey LIMIT 1")
    suspend fun get(contentKey: String): PlaybackQuirkEntity?

    /** Creates the row if it is missing, leaving an existing one exactly as it is. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfMissing(row: PlaybackQuirkEntity)

    @Query("UPDATE playback_quirks SET audioOnly = :audioOnly, updatedAt = :now WHERE contentKey = :contentKey")
    suspend fun updateAudioOnly(contentKey: String, audioOnly: Boolean?, now: Long)

    @Query("UPDATE playback_quirks SET audioDelayMs = :audioDelayMs, updatedAt = :now WHERE contentKey = :contentKey")
    suspend fun updateAudioDelay(contentKey: String, audioDelayMs: Int?, now: Long)

    @Query("DELETE FROM playback_quirks WHERE audioOnly IS NULL AND audioDelayMs IS NULL")
    suspend fun dropEmptyRows()

    /** Only a sound-only item is recorded; false forgets it. */
    @Transaction
    suspend fun setAudioOnly(contentKey: String, sourceId: Long, mediaType: String, audioOnly: Boolean) {
        if (audioOnly) insertIfMissing(PlaybackQuirkEntity(contentKey, sourceId, mediaType))
        updateAudioOnly(contentKey, audioOnly.takeIf { it }, System.currentTimeMillis())
        if (!audioOnly) dropEmptyRows()
    }

    /** [audioDelayMs] null forgets it and returns the item to the global setting. */
    @Transaction
    suspend fun setAudioDelay(contentKey: String, sourceId: Long, mediaType: String, audioDelayMs: Int?) {
        if (audioDelayMs != null) insertIfMissing(PlaybackQuirkEntity(contentKey, sourceId, mediaType))
        updateAudioDelay(contentKey, audioDelayMs, System.currentTimeMillis())
        if (audioDelayMs == null) dropEmptyRows()
    }

    @Query("SELECT contentKey FROM playback_quirks WHERE audioOnly = 1")
    fun observeAudioOnly(): Flow<List<String>>

    @Transaction
    suspend fun clearAudioDelays() {
        clearAudioDelayColumn()
        dropEmptyRows()
    }

    @Query("UPDATE playback_quirks SET audioDelayMs = NULL")
    suspend fun clearAudioDelayColumn()

    @Query("SELECT COUNT(*) FROM playback_quirks WHERE audioDelayMs IS NOT NULL")
    fun observeAudioDelayCount(): Flow<Int>

    /** A deleted playlist takes what was remembered for its items with it. */
    @Query("DELETE FROM playback_quirks WHERE sourceId = :sourceId")
    suspend fun deleteBySource(sourceId: Long)

    /** Everything, for Backup & Restore. */
    @Query("SELECT * FROM playback_quirks")
    suspend fun getAllOnce(): List<PlaybackQuirkEntity>

    @Query("SELECT COUNT(*) FROM playback_quirks")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<PlaybackQuirkEntity>)
}
