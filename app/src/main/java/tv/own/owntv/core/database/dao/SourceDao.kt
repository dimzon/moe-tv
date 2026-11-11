package tv.own.owntv.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import tv.own.owntv.core.database.entity.SourceEntity

@Dao
interface SourceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(source: SourceEntity): Long

    @Update
    suspend fun update(source: SourceEntity)

    @Delete
    suspend fun delete(source: SourceEntity)

    @Query("SELECT * FROM sources WHERE id = :id")
    suspend fun getById(id: Long): SourceEntity?

    @Query("SELECT * FROM sources ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<SourceEntity>>

    @Query("UPDATE sources SET lastSyncAt = :timestamp WHERE id = :id")
    suspend fun markSynced(id: Long, timestamp: Long)

    /** Per-playlist "Pre-buffer" override in seconds; `-1` follows the global setting. */
    @Query("UPDATE sources SET livePrerollSecs = :secs WHERE id = :id")
    suspend fun updateLivePreroll(id: Long, secs: Int)

    /** Per-playlist Live latency override (a `LiveLatency` name); `null` follows the global setting. The
     *  seconds are written alongside so a CUSTOM choice can never land without its value. */
    @Query("UPDATE sources SET liveLatencyMode = :mode, liveLatencyCustomSecs = :customSecs WHERE id = :id")
    suspend fun updateLiveLatency(id: Long, mode: String?, customSecs: Int)

    /** Per-playlist "Give up after" in seconds (0 = never); `null` follows the global setting. */
    @Query("UPDATE sources SET liveTuneTimeoutSecs = :secs WHERE id = :id")
    suspend fun updateLiveTuneTimeout(id: Long, secs: Int?)

    /** Per-playlist "Clean channel names" and the user's own words to remove (`null` = none). Takes
     *  effect at the playlist's next sync, which rewrites the names it changes. */
    @Query("UPDATE sources SET cleanChannelNames = :enabled, channelNameStrip = :strip WHERE id = :id")
    suspend fun updateChannelNameCleanup(id: Long, enabled: Boolean, strip: String?)

    // --- sources are global: no profile links (the profile tables are gone) ---

    @Query("SELECT id FROM sources")
    suspend fun allSourceIds(): List<Long>

    @Query("SELECT * FROM sources ORDER BY createdAt ASC")
    suspend fun getAllOnce(): List<SourceEntity>

    /** The built-in RuTube source, if it has been created yet (v54). */
    @Query("SELECT * FROM sources WHERE type = 'RUTUBE' ORDER BY createdAt ASC LIMIT 1")
    suspend fun rutubeSource(): SourceEntity?
}
