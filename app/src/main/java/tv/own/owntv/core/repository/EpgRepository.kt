package tv.own.owntv.core.repository

import android.os.SystemClock
import android.util.Log
import androidx.room.execSQL
import androidx.room.useWriterConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.EpgDao
import tv.own.owntv.core.database.entity.SourceEntity

/**
 * The one provider's guide: the built-in RuTube source's schedule, fetched from its API and written
 * straight into the shared EPG tables by [tv.own.owntv.core.sync.RuTubeEpgSyncer]. There is no XMLTV
 * downloader any more — RuTube has no `.xmltv` feed, and the standalone-EPG-source system that used
 * to carry one is gone. The grid reads from [EpgDao]; finished rows are pruned by the syncer's own
 * window, so the guide stays bounded.
 */
class EpgRepository(
    private val epgDao: EpgDao,
    private val channelDao: ChannelDao,
    private val db: tv.own.owntv.core.database.OwnTVDatabase,
    /** RuTube's own API; null only in unit-test fixtures that never refresh. */
    private val rutube: tv.own.owntv.core.sync.RutubeApi? = null,
) {

    /**
     * Ensure every non-unique `epg_programmes` index exists — the Guide read-index
     * `(sourceId, epgChannelId)` turns the "which channels have guide data" lookup from a
     * multi-second full scan into a fast index scan; the others are Room-declared and get dropped
     * by an eligible fresh EPG bulk import, so this pass must recreate ALL of them (the canonical
     * list is shared with BulkInsertHelper's restore and the migration heal — a missing index would
     * crash the next migration's schema validation). Indexes are **permanent** and
     * **auto-maintained** by SQLite, so this builds them exactly once (in the background) and is an
     * instant no-op every call after. Run at startup (for users who already have EPG) and after
     * each EPG sync (for brand-new EPG).
     */
    suspend fun ensureEpgIndexes() = withContext(Dispatchers.IO) {
        try {
            val startedAt = SystemClock.elapsedRealtime()
            db.useWriterConnection { connection ->
                tv.own.owntv.core.database.OwnTVDatabase.EXPECTED_NON_UNIQUE_INDEXES
                    .getValue("epg_programmes")
                    .forEach { connection.execSQL(it) }
            }
            Log.d("EpgRepository", "ensureEpgIndexes ms=${SystemClock.elapsedRealtime() - startedAt}")
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            Log.w("EpgRepository", "Unable to create EPG guide index", e)
        }
    }

    /**
     * Refresh one source's guide. RuTube is the only source, so this is [refreshRutube]; the alias
     * keeps the callers' shape stable.
     */
    suspend fun refresh(source: SourceEntity, onProgress: (channels: Int, programmes: Int) -> Unit = { _, _ -> }): Int =
        refreshRutube(source, onProgress)

    /**
     * The built-in RuTube guide: per-channel/day grid from the API, written straight into the shared
     * EPG tables by [tv.own.owntv.core.sync.RuTubeEpgSyncer]. Only the channels the catalogue holds
     * (with an EPG id) are asked for.
     */
    suspend fun refreshRutube(source: SourceEntity, onProgress: (channels: Int, programmes: Int) -> Unit = { _, _ -> }): Int {
        val api = rutube ?: return 0
        val keys = channelDao.allEpgChannelIds()
        if (keys.isEmpty()) return 0
        onProgress(keys.size, 0)
        val summary = tv.own.owntv.core.sync.RuTubeEpgSyncer(api, epgDao).sync(source.id, keys)
        onProgress(summary.channelsWithGuide, summary.programmesWritten)
        return summary.programmesWritten
    }

    /** Whether the source can have a guide at all. RuTube always can — its API is the feed. */
    fun hasGuide(source: SourceEntity): Boolean = source.type == tv.own.owntv.core.model.SourceType.RUTUBE

    /** Drop a source's stored programmes and guide-channel rows. */
    suspend fun clear(storeId: Long) = withContext(Dispatchers.IO) {
        epgDao.clearSource(storeId)
        epgDao.clearChannelsForSource(storeId)
    }
}
