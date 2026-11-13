package tv.own.owntv.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import tv.own.owntv.core.database.entity.WatchHistoryEntity

/**
 * Watch history, one row per item **per device** — the key is (itemId, deviceId), so the household's
 * devices never overwrite each other's timestamps. Reads that draw a list collapse devices with
 * GROUP BY/MAX; the single row used by the Continue chip takes the latest one overall.
 */
@Dao
interface HistoryDao {

    /**
     * The local write of "you watched this". Stamps the writing device on the way in —
     * [WatchHistoryEntity.deviceId] is part of the key — and keeps `watchedAt` as the per-row LWW
     * stamp. REPLACE bumps the row for this device.
     */
    @Transaction
    suspend fun record(entry: WatchHistoryEntity) {
        insert(entry.copy(deviceId = entry.deviceId.ifBlank { HistoryDeviceStamp.deviceId() ?: "" }))
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: WatchHistoryEntity)

    /**
     * The merge write, and deliberately not [record]: a row that arrives from another device must
     * never move `watchedAt` **backwards**. Insert when absent, then move the time forward only when
     * the incoming one is actually later.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entry: WatchHistoryEntity)

    /** Moves one device's row forward, never back. The `<` is the whole rule. */
    @Query(
        "UPDATE watch_history SET watchedAt = :at " +
            "WHERE itemId = :itemId AND deviceId = :deviceId AND watchedAt < :at",
    )
    suspend fun bumpIfNewer(itemId: String, deviceId: String, at: Long)

    /** "Remove from history": the item goes for every device, not just this one. */
    @Query("DELETE FROM watch_history WHERE itemId = :itemId")
    suspend fun remove(itemId: String)

    /**
     * Deletes the item's rows that are older than [at] — the local-sync merge rule for an incoming
     * deletion. Rows re-created after the other device deleted the item are newer and survive.
     */
    @Query("DELETE FROM watch_history WHERE itemId = :itemId AND watchedAt <= :at")
    suspend fun removeIfOlderThan(itemId: String, at: Long): Int

    @Query("DELETE FROM watch_history")
    suspend fun clear()

    /** When it was last watched on any device — the dry run compares it against a deletion. */
    @Query("SELECT MAX(watchedAt) FROM watch_history WHERE itemId = :itemId")
    suspend fun watchedAt(itemId: String): Long?

    /** Does this item have history at all? The dry run before a sync counts what is genuinely new. */
    @Query("SELECT EXISTS(SELECT 1 FROM watch_history WHERE itemId = :itemId)")
    suspend fun exists(itemId: String): Boolean

    /** Distinct items, not rows: a channel watched on two devices is one history entry. */
    @Query("SELECT COUNT(DISTINCT itemId) FROM watch_history")
    fun count(): Flow<Int>

    /** The single most-recently-watched row — drives the top-bar Continue chip. */
    @Query("SELECT * FROM watch_history ORDER BY watchedAt DESC LIMIT 1")
    fun observeMostRecent(): Flow<WatchHistoryEntity?>

    /** Every history row, for backup and for "clear" deletion markers. */
    @Query("SELECT * FROM watch_history")
    suspend fun getAllOnce(): List<WatchHistoryEntity>

    /** The row is an orphan when its channel no longer exists. */
    @Query("DELETE FROM watch_history WHERE itemId = :itemId AND itemId NOT IN (SELECT remoteId FROM channels)")
    suspend fun purgeSnapshotOrphan(itemId: String)

    /** Drops history rows orphaned by a re-sync. */
    @Query("DELETE FROM watch_history WHERE itemId NOT IN (SELECT remoteId FROM channels)")
    suspend fun purgeOrphans()
}

/**
 * The device id new watch-history rows are stamped with ([HistoryDao.record]). Read once per
 * process through the application context registered with Koin — the DAO layer has no Context of
 * its own, and [tv.own.owntv.core.sync.local.DeviceIdentity.of] needs one to read `ANDROID_ID`.
 *
 * Rows written before Koin is up (or in tests without it) get `deviceId = ""`, the "this device, id
 * unknown" key value — never a different device.
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
