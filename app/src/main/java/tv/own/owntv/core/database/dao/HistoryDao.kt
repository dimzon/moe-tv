package tv.own.owntv.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import tv.own.owntv.core.database.entity.WatchHistoryEntity
import tv.own.owntv.core.model.MediaType

/** Write side of watch history; the recently-watched content rows live in the content DAOs (joins). */
@Dao
interface HistoryDao {

    /**
     * The local write of "you watched this" (the tuner's debounced record lands here). Stamps the
     * writing device on the way in — [WatchHistoryEntity.deviceId] is what the cross-device
     * "recently watched" rail attributes rows with — and keeps [WatchHistoryEntity.watchedAt] as the
     * per-row LWW stamp. One row per (type, item); REPLACE bumps `watchedAt` via the unique index.
     */
    @Transaction
    suspend fun record(entry: WatchHistoryEntity) {
        insert(entry.copy(deviceId = entry.deviceId ?: HistoryDeviceStamp.deviceId()))
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: WatchHistoryEntity)

    /**
     * The merge write, and deliberately not [record]: a row that arrives from another device must
     * never move `watchedAt` **backwards**. REPLACE did exactly that — the other device's older copy
     * of "you watched this" overwrote the newer local one, so a sync could make a show look less
     * watched than it was. Paired with [bumpIfNewer]: insert when absent, then move the time forward
     * only when the incoming one is actually later. Both run inside the resolver's own transaction.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entry: WatchHistoryEntity)

    /**
     * Moves `watchedAt` forward, never back, adopting the incoming row's [deviceId] when it does —
     * the latest watch happened on that device, whatever was here before. The `<` is the whole rule.
     */
    @Query(
        "UPDATE watch_history SET watchedAt = :at, deviceId = :deviceId " +
            "WHERE mediaType = :type AND itemId = :itemId AND watchedAt < :at",
    )
    suspend fun bumpIfNewer(type: MediaType, itemId: String, at: Long, deviceId: String?)

    @Query("DELETE FROM watch_history WHERE mediaType = :type AND itemId = :itemId")
    suspend fun remove(type: MediaType, itemId: String)

    /**
     * Deletes the row only when it is older than [at] — the local-sync merge rule for an
     * incoming deletion. A row the user re-created after the other device deleted it is newer,
     * and survives. Returns the number of rows removed, so the sync summary can count it.
     */
    @Query("DELETE FROM watch_history WHERE mediaType = :type AND itemId = :itemId AND watchedAt <= :at")
    suspend fun removeIfOlderThan(type: MediaType, itemId: String, at: Long): Int

    @Query("DELETE FROM watch_history")
    suspend fun clear()

    /** Clear just one media type (Live). */
    @Query("DELETE FROM watch_history WHERE mediaType = :type")
    suspend fun clearType(type: MediaType)

    /** When it was last watched — the dry run compares it against an incoming deletion. */
    @Query("SELECT watchedAt FROM watch_history WHERE mediaType = :type AND itemId = :itemId")
    suspend fun watchedAt(type: MediaType, itemId: String): Long?

    /** Does this row already exist? The dry run before a sync counts what is genuinely new. */
    @Query("SELECT EXISTS(SELECT 1 FROM watch_history WHERE mediaType = :type AND itemId = :itemId)")
    suspend fun exists(type: MediaType, itemId: String): Boolean

    @Query("SELECT COUNT(*) FROM watch_history WHERE mediaType = :type")
    fun count(type: MediaType): Flow<Int>

    /** The single most-recently-watched item (any type) — drives the top-bar Continue chip. */
    @Query("SELECT * FROM watch_history ORDER BY watchedAt DESC LIMIT 1")
    fun observeMostRecent(): Flow<WatchHistoryEntity?>

    /** The most-recently-watched item of one type — the TV rail's details line (Last channel, Resume …). */
    @Query("SELECT * FROM watch_history WHERE mediaType = :type ORDER BY watchedAt DESC LIMIT 1")
    fun observeMostRecentOfType(type: MediaType): Flow<WatchHistoryEntity?>

    /** Every history row, so a "clear" can record each deletion before it happens. */
    @Query("SELECT * FROM watch_history")
    suspend fun getAll(): List<WatchHistoryEntity>

    @Query("SELECT * FROM watch_history WHERE mediaType = :type")
    suspend fun getAllOfType(type: MediaType): List<WatchHistoryEntity>

    /** Everything, for Backup & Restore. */
    @Query("SELECT * FROM watch_history")
    suspend fun getAllOnce(): List<WatchHistoryEntity>

    /** The row is an orphan when its type no longer has content rows at all (the app is live-only)
     *  or its LIVE row is gone. */
    @Query(
        "DELETE FROM watch_history WHERE mediaType = :type AND itemId = :itemId AND (" +
            "mediaType != 'LIVE' OR itemId NOT IN (SELECT remoteId FROM channels))",
    )
    suspend fun purgeSnapshotOrphan(type: MediaType, itemId: String)

    /** Drops history rows orphaned by a re-sync (see FavoriteDao.purgeOrphans). */
    @Query("DELETE FROM watch_history WHERE mediaType != 'LIVE' OR itemId NOT IN (SELECT remoteId FROM channels)")
    suspend fun purgeOrphans()
}

/**
 * The device id new watch-history rows are stamped with ([HistoryDao.record]). Read once per
 * process through the application context registered with Koin — the DAO layer has no Context of
 * its own, and [tv.own.owntv.core.sync.local.DeviceIdentity.of] needs one to read `ANDROID_ID`.
 *
 * Rows written before Koin is up (or in tests without it) keep `deviceId = null`, which the payload
 * and the merge treat as "this device, id unknown" — never as a different device.
 */
internal object HistoryDeviceStamp {
    @Volatile
    private var cached: String? = null

    /** The id hash, or null while it cannot be read. A successful read is cached for the process. */
    fun deviceId(): String? {
        cached?.let { return it }
        val id = runCatching {
            val context = org.koin.core.context.GlobalContext.getOrNull()?.getOrNull<android.content.Context>()
                ?: return null
            tv.own.owntv.core.sync.local.DeviceIdentity.of(context).id.takeIf { it.isNotBlank() }
        }.getOrNull()
        if (id != null) cached = id
        return id
    }
}
