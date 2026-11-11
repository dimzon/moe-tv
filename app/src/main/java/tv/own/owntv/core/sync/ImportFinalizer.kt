package tv.own.owntv.core.sync

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tv.own.owntv.core.database.BulkInsertHelper
import androidx.room.execSQL
import androidx.room.useWriterConnection
import tv.own.owntv.core.database.OwnTVDatabase
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.entity.SourceEntity

/** Per-type counts after a playlist import, for the success breakdown. */
data class SyncCounts(val channels: Int, val epg: Int = 0)

/**
 * Runs after a playlist syncs: returns the per-type content counts for the success message. EPG is
 * NOT auto-created/synced here — the RuTube guide arrives through its own refresh path
 * ([EpgRepository.refreshRutube] / [RuTubeEpgSyncer]), user- or schedule-initiated, never as a
 * side effect of a catalogue sync.
 */
class ImportFinalizer(
    private val channelDao: ChannelDao,
    private val db: OwnTVDatabase,
    private val bulkInsertHelper: BulkInsertHelper,
) {
    suspend fun finalize(source: SourceEntity, deferIndexes: Boolean = false): SyncCounts {
        val counts = contentCounts(source.id)
        if (!deferIndexes) {
            // A sync does REPLACE on 100k+ rows, which invalidates SQLite's planner statistics
            // (sqlite_stat1). With stale stats the query planner IGNORES the single-column and
            // composite indices on channels and falls back to a full table scan + temp B-tree
            // sort — the grid's 2–3s cold-open. ANALYZE refreshes the stats so the existing
            // indices get used again, dropping the open back to <300ms. Same trick the EPG bulk
            // sync uses. Safe, idempotent, <1s even on 100k rows.
            ensureContentIndexes()
        }
        return counts
    }

    /** Current content counts for a source (no EPG) — for the success message and the Playlists list rows. */
    suspend fun contentCounts(sourceId: Long): SyncCounts = SyncCounts(
        channels = channelDao.countForSourceOnce(sourceId),
    )

    /**
     * Ensure the channel grid read-indices exist and the planner statistics are fresh.
     * Fresh imports may drop the non-unique content indices to speed up bulk inserts, so this rebuilds the
     * single-column and composite read paths, then refreshes `sqlite_stat1` with ANALYZE so SQLite keeps
     * choosing them instead of reverting to a full-table sort. Mirrors `EpgRepository.ensureEpgIndexes`.
     * Called inline for normal re-syncs and from the deferred background worker for first-ever imports.
     */
    suspend fun ensureContentIndexes() = withContext(Dispatchers.IO) {
        val startedAt = SystemClock.elapsedRealtime()
        Log.i(TAG, "ensureContentIndexes start")
        runCatching {
            val indexesStartedAt = SystemClock.elapsedRealtime()
            // Idempotent (IF NOT EXISTS): no-op once the indices exist. Covers DBs that somehow lack
            // them. Canonical list shared with BulkInsertHelper's restore and the migration heal, so
            // this pass can never miss an index those paths expect (the old hand-copied list omitted
            // the rating-sort indexes — drift that crashed the next migration's schema validation).
            db.useWriterConnection { connection ->
                listOf("channels").forEach { table ->
                    OwnTVDatabase.EXPECTED_NON_UNIQUE_INDEXES.getValue(table).forEach { connection.execSQL(it) }
                }
            }
            Log.d(TAG, "ensureContentIndexes create indexes ms=${SystemClock.elapsedRealtime() - indexesStartedAt}")

            // Refresh planner stats so the indices above are chosen for the grids' "WHERE … ORDER BY …" —
            // without this, stale stats from a bulk REPLACE make SQLite ignore them and full-sort.
            val analyzeStartedAt = SystemClock.elapsedRealtime()
            bulkInsertHelper.analyzeTables("channels", "categories")
            Log.d(TAG, "ensureContentIndexes analyze ms=${SystemClock.elapsedRealtime() - analyzeStartedAt}")
        }.onSuccess {
            Log.i(TAG, "ensureContentIndexes end ms=${SystemClock.elapsedRealtime() - startedAt}")
        }.onFailure {
            Log.w(TAG, "ensureContentIndexes failed ms=${SystemClock.elapsedRealtime() - startedAt}", it)
        }
    }

    companion object {
        private const val TAG = "ImportFinalizer"

    }
}
