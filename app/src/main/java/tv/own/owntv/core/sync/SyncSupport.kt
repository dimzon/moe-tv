package tv.own.owntv.core.sync

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.database.BulkInsertHelper
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.entity.CategoryEntity
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.settings.SettingsRepository
import kotlin.coroutines.CoroutineContext

/**
 * Shared building blocks for the source syncer ([RuTubeSyncer]): the chunked streaming inserter
 * with cross-pass dedupe, the REPLACE-by-PK channel upsert (the PK IS the provider's natural key,
 * so there is no local id to preserve and no hash to diff), category refresh + stable upsert, and
 * stale-row pruning. Extracted from SyncManager so the syncer stays focused on its own protocol
 * flow while any fix to the shared machinery lands once.
 */
internal class SyncSupport(
    private val categoryDao: CategoryDao,
    private val channelDao: ChannelDao,
    private val customize: CustomizationStore,
    private val settings: SettingsRepository,
) {
    /** REPLACE by the hex PK: a resync rewrites only what the provider sent, and a row's key never
     *  changes under it — favorites, history and orders keyed by the hex need no relink. */
    suspend fun upsertChannels(rows: List<ChannelEntity>): UpsertStats {
        if (rows.isEmpty()) return UpsertStats()
        channelDao.upsertAll(rows)
        return UpsertStats(inserted = rows.size)
    }

    /** The channel prune, wired to the (source-less) channel DAO. */
    suspend fun pruneChannels(label: String, seenRemoteIds: Set<String>, stats: SyncStatsCollector) {
        pruneRemoteIds(label, seenRemoteIds, stats, { channelDao.remoteIdsForSource() }, { channelDao.deleteByRemoteIds(it) })
    }

    /**
     * Catalog-shrink guard. A pass that *looks* complete but saw only a fraction of the catalog (a
     * provider serving a truncated list, a panel briefly answering with a tiny payload) would
     * otherwise delete most of the user's content — and with it every favorite, history entry and
     * resume position keyed to those rows. So when a source already holds a meaningful number of
     * rows and this pass would remove more than half of them, the prune is skipped and the run
     * reports a warning instead. A fresh/small source (≤ [PRUNE_MIN_ROWS]) prunes normally, and a
     * user-requested force-clean sync ([SyncStatsCollector.forcePrune]) bypasses the guard entirely
     * for the case where the catalog really did shrink.
     *
     * Returns true when the prune may proceed.
     */
    private fun pruneAllowed(label: String, stored: Int, stale: Int, stats: SyncStatsCollector): Boolean {
        if (shouldPrune(stored, stale, stats.forcePrune)) return true
        val percent = (stale * 100) / stored
        Log.w(TAG, "$label prune skipped reason=catalog_shrink stored=$stored stale=$stale")
        stats.addWarning(
            SyncWarning(
                phase = label,
                kind = SyncWarningKind.CATALOG_SHRINK(stored = stored, percentFewer = percent),
            ),
        )
        return false
    }

    suspend fun pruneCategories(type: MediaType, seenRemoteIds: Set<String>, label: String, stats: SyncStatsCollector) {
        val start = SystemClock.elapsedRealtime()
        val existing = categoryDao.remoteIds(type)
        val stale = existing.filterNot(seenRemoteIds::contains)
        if (!pruneAllowed("$label categories", existing.size, stale.size, stats)) return
        stale.chunked(QUERY_CHUNK).forEach { categoryDao.deleteByRemoteIds(type, it) }
        if (stale.isNotEmpty()) stats.processedCounts.merge(CATEGORIES_REMOVED_KEY, stale.size, Int::plus)
        Log.i(TAG, "$label category prune type=$type stale=${stale.size} ms=${SystemClock.elapsedRealtime() - start}")
    }

    suspend fun pruneRemoteIds(
        label: String,
        seenRemoteIds: Set<String>,
        stats: SyncStatsCollector,
        loadExisting: suspend () -> List<String>,
        deleteRemoteIds: suspend (List<String>) -> Unit,
    ) {
        val start = System.currentTimeMillis()
        val existing = loadExisting()
        val stale = existing.filterNot(seenRemoteIds::contains)
        if (!pruneAllowed(label, existing.size, stale.size, stats)) return
        stale.chunked(QUERY_CHUNK).forEach { deleteRemoteIds(it) }
        Log.i(TAG, "$label content prune stale=${stale.size} ms=${System.currentTimeMillis() - start}")
    }

    suspend fun refreshCategories(
        s: SourceEntity,
        type: MediaType,
        parsed: List<tv.own.owntv.core.parser.XtCategory>,
        stats: SyncStatsCollector,
        /** Categories that come back as new rows without being new to the user: not counted, not hidden. */
        quietRemoteIds: Set<String> = emptySet(),
    ): CategoryRefresh {
        val start = SystemClock.elapsedRealtime()
        // s.lastSyncAt never changes during a sync run (SyncManager stamps it only after the syncer
        // returns), so this is safe to derive here rather than threading a freshSource param through
        // every call site.
        val freshSource = s.lastSyncAt == null
        Log.d(TAG, "refreshCategories start sourceId=${s.id} type=$type count=${parsed.size}")
        val uniqueCategories = parsed.distinctBy { it.id }
        val existing = existingCategoriesByRemoteId(type, uniqueCategories.map { it.id })
        // sortOrder = provider index, so the rail follows the provider's category order.
        val entities = uniqueCategories.mapIndexed { i, c ->
            CategoryEntity(
                id = existing[c.id]?.id ?: 0,
                mediaType = type,
                name = c.name,
                remoteId = c.id,
                sortOrder = i,
            )
        }
        val upsertStart = SystemClock.elapsedRealtime()
        val upsert = upsertCategoriesStable(type, entities, existing)
        Log.d(
            TAG,
            "refreshCategories upsert sourceId=${s.id} type=$type rows=${entities.size} " +
                "dbInserted=${upsert.stats.inserted} dbUpdated=${upsert.stats.updated} " +
                "dbSkipped=${upsert.stats.skippedUnchanged} ms=${SystemClock.elapsedRealtime() - upsertStart}",
        )
        // Everything is technically "new" on a fresh source's first sync — neither the hide-by-default
        // preference nor the sync summary is meaningful there, only on a genuine resync.
        val announced = upsert.newRows.filter { it.remoteId !in quietRemoteIds }
        if (!freshSource && announced.isNotEmpty()) {
            stats.processedCounts.merge(CATEGORIES_ADDED_KEY, announced.size, Int::plus)
            applyHideNewCategoriesDefault(type, announced)
        }
        // Ids come straight from the upsert (existing rows + returned insert rowids) — the old
        // second existingCategoriesByRemoteId round-trip only re-fetched just-upserted rows.
        return CategoryRefresh(idsByRemoteId = upsert.idsByRemoteId, seenRemoteIds = uniqueCategories.mapTo(HashSet()) { it.id }).also {
            Log.d(TAG, "refreshCategories end sourceId=${s.id} type=$type mapped=${it.idsByRemoteId.size} totalMs=${SystemClock.elapsedRealtime() - start}")
        }
    }

    private suspend fun existingCategoriesByRemoteId(type: MediaType, remoteIds: List<String>): Map<String, CategoryEntity> =
        remoteIds.distinct().chunked(QUERY_CHUNK).flatMap { categoryDao.findByRemoteIds(type, it) }
            .mapNotNull { category -> category.remoteId?.let { it to category } }
            .toMap()

    private class CategoryUpsert(val stats: UpsertStats, val idsByRemoteId: Map<String, Long>, val newRows: List<CategoryEntity>)

    private suspend fun upsertCategoriesStable(
        type: MediaType,
        rows: List<CategoryEntity>,
        existingByRemoteId: Map<String, CategoryEntity>,
    ): CategoryUpsert {
        val inserts = ArrayList<CategoryEntity>()
        val updates = ArrayList<CategoryEntity>()
        var skipped = 0
        val ids = HashMap<String, Long>()
        rows.forEach { row ->
            val current = row.remoteId?.let(existingByRemoteId::get)
            when {
                current == null -> inserts.add(row)
                row != current -> { updates.add(row); ids[row.remoteId] = current.id }
                else -> { skipped++; ids[row.remoteId] = current.id }
            }
        }
        if (updates.isNotEmpty()) categoryDao.updateAll(updates)
        if (inserts.isNotEmpty()) {
            val rowIds = categoryDao.insertAll(inserts)
            val missed = ArrayList<String>()
            inserts.forEachIndexed { i, row ->
                val rid = row.remoteId ?: return@forEachIndexed
                val id = rowIds.getOrNull(i) ?: -1L
                if (id > 0) ids[rid] = id else missed.add(rid)
            }
            // IGNOREd conflicts return −1 (shouldn't happen — inserts were pre-checked by remoteId);
            // heal by re-fetching just those rows rather than everything.
            if (missed.isNotEmpty()) {
                existingCategoriesByRemoteId(type, missed).forEach { (rid, cat) -> ids[rid] = cat.id }
            }
        }
        return CategoryUpsert(
            stats = UpsertStats(inserted = inserts.size, updated = updates.size, skippedUnchanged = skipped),
            idsByRemoteId = ids,
            newRows = inserts,
        )
    }

    /** Applies the "hide new categories" preference to categories just discovered on a resync. */
    private suspend fun applyHideNewCategoriesDefault(type: MediaType, newRows: List<CategoryEntity>) {
        if (newRows.isEmpty() || !settings.hideNewCategoriesDefault().first()) return
        customize.setCategoriesHidden(type, newRows.map { CustomizeKeys.category(it) }, hidden = true)
    }

    /**
     * Drives a push-stream [producer] that feeds items into [add]; flushes to the DB via [insert] in
     * chunks of [BulkInsertHelper.CHUNK], reporting progress. Cancellation is checked each chunk.
     *
     * The database write runs in its own coroutine, fed batches over a channel, so downloading and
     * parsing the provider's response OVERLAPS with writing the previous batch instead of the two
     * taking turns. This used to be strictly sequential: every time the buffer filled, the parse
     * stopped dead — socket idle — until the insert, the dedupe filter and (on a fresh import) the
     * content hashing of ten thousand rows had all finished. On a 170k-title first sync that is
     * seventeen full stop-the-world flushes, and the same again in reverse while the database sits
     * waiting for the next batch to be parsed.
     *
     * A RENDEZVOUS channel, not a buffered one, deliberately. The producer refills its buffer while
     * the writer works and only the hand-off itself blocks, which is all the overlap that is wanted:
     * back-pressure survives exactly as before (a slow database still throttles the parse, so a huge
     * catalog cannot pile up in memory), and peak memory is unchanged at two chunks — the buffer
     * being refilled, plus the batch being written.
     */
    suspend fun <T, R> chunked(
        ctx: CoroutineContext,
        phase: SyncPhase,
        label: String,
        progress: SyncCounters,
        insert: suspend (List<T>) -> UpsertStats,
        total: IntArray, // shared [0] running unique count for the whole media type, so progress never resets
        seenKeys: MutableSet<String>? = null,
        uniqueKey: ((T) -> String?)? = null,
        chunkSize: Int = BulkInsertHelper.CHUNK,
        producer: suspend (add: suspend (T) -> Unit) -> R,
    ): R = coroutineScope {
        var chunkIndex = 0
        var skippedDuplicates = 0
        // These are the numbers that diagnosed the full-rewrite resync and they are the numbers that
        // prove the fix, so they are reported at info level — the per-chunk line below is debug and
        // release builds strip it.
        var totalInserted = 0
        var totalUpdated = 0
        var totalMoved = 0
        var totalMoveRanges = 0
        var totalMoveScattered = 0
        val chunkRunStart = SystemClock.elapsedRealtime()
        val batches = Channel<List<T>>(Channel.RENDEZVOUS)
        // A single writer, so `seenKeys`, `total` and the counters stay confined to one coroutine and
        // need no synchronisation; the `writer.join()` below happens-before the caller reads them.
        val writer = launch {
            for (batch in batches) {
                ctx.ensureActive()
                chunkIndex++
                val rawCount = batch.size
                val flushStart = SystemClock.elapsedRealtime()
                val pendingKeys = ArrayList<String>()
                val rows = batch.filterNewItems(seenKeys, uniqueKey, pendingKeys)
                val filterMs = SystemClock.elapsedRealtime() - flushStart
                val skipped = rawCount - rows.size
                skippedDuplicates += skipped
                if (rows.isEmpty()) {
                    Log.d(
                        TAG,
                        "$label chunk skipped phase=${phase.name} chunk=$chunkIndex raw=$rawCount skipped=$skipped " +
                            "totalSkipped=$skippedDuplicates totalUnique=${total[0]} filterMs=$filterMs elapsedMs=${SystemClock.elapsedRealtime() - chunkRunStart}",
                    )
                    continue
                }
                val insertStart = SystemClock.elapsedRealtime()
                val upsertStats = insert(rows)
                val insertMs = SystemClock.elapsedRealtime() - insertStart
                totalInserted += upsertStats.inserted
                totalUpdated += upsertStats.updated
                totalMoved += upsertStats.moved
                totalMoveRanges += upsertStats.movedByRange
                totalMoveScattered += upsertStats.movedScattered
                seenKeys?.addAll(pendingKeys)
                total[0] += rows.size
                if (shouldLogChunk(chunkIndex, insertMs, skipped)) {
                    Log.d(
                        TAG,
                        "$label chunk applied phase=${phase.name} chunk=$chunkIndex raw=$rawCount accepted=${rows.size} " +
                            "dbInserted=${upsertStats.inserted} dbUpdated=${upsertStats.updated} dbSkipped=${upsertStats.skippedUnchanged} " +
                            "dbMoved=${upsertStats.moved} dbMoveRanges=${upsertStats.movedByRange} " +
                            "dedupeSkipped=$skipped totalDedupeSkipped=$skippedDuplicates totalUnique=${total[0]} " +
                            "filterMs=$filterMs applyMs=$insertMs elapsedMs=${SystemClock.elapsedRealtime() - chunkRunStart}",
                    )
                }
                progress.update(phase, total[0])
            }
        }
        val buffer = ArrayList<T>(chunkSize)
        // No try/finally around the producer: a throw here leaves the channel open, but structured
        // concurrency cancels the writer along with this scope, so nothing is left waiting on it. A
        // throw from the *writer* (a database failure) cancels the scope the other way round and the
        // producer's send unblocks — which is what keeps a broken insert surfacing as a failed phase
        // rather than as a hang.
        val result = producer { item ->
            buffer.add(item)
            if (buffer.size >= chunkSize) {
                batches.send(ArrayList(buffer))
                buffer.clear()
            }
        }
        if (buffer.isNotEmpty()) batches.send(buffer)
        batches.close()
        writer.join()
        Log.i(
            TAG,
            "$label stream done phase=${phase.name} chunks=$chunkIndex totalUnique=${total[0]} " +
                "inserted=$totalInserted updated=$totalUpdated moved=$totalMoved moveRanges=$totalMoveRanges " +
                "moveScattered=$totalMoveScattered " +
                "skippedDuplicates=$skippedDuplicates elapsedMs=${SystemClock.elapsedRealtime() - chunkRunStart}",
        )
        result
    }

    private fun shouldLogChunk(chunkIndex: Int, insertMs: Long, skipped: Int): Boolean =
        chunkIndex <= 3 || chunkIndex % 20 == 0 || insertMs >= SLOW_INSERT_LOG_MS || skipped > 0

    private fun <T> List<T>.filterNewItems(
        seenKeys: MutableSet<String>?,
        uniqueKey: ((T) -> String?)?,
        pendingKeys: MutableList<String>,
    ): List<T> {
        if (seenKeys == null || uniqueKey == null) return this
        val rows = ArrayList<T>(size)
        val batchKeys = HashSet<String>()
        forEach { item ->
            val key = uniqueKey(item)
            if (key == null) {
                rows.add(item)
            } else if (!seenKeys.contains(key) && batchKeys.add(key)) {
                pendingKeys.add(key)
                rows.add(item)
            }
        }
        return rows
    }

    companion object {
        /** Shared log tag — kept as "SyncManager" across the split so existing logcat filters still work. */
        const val TAG = "SyncManager"
        const val QUERY_CHUNK = 500


        /** Below this many stored rows a prune is always allowed (fresh/small sources). */
        private const val PRUNE_MIN_ROWS = 100

        /** A prune may remove at most this fraction of the stored rows before the guard trips. */
        private const val PRUNE_MAX_SHRINK = 0.5

        fun shouldPrune(stored: Int, stale: Int, force: Boolean): Boolean =
            force || stored <= PRUNE_MIN_ROWS || stale <= stored * PRUNE_MAX_SHRINK
        private const val SLOW_INSERT_LOG_MS = 250L

        /** [SyncStatsCollector.processedCounts] keys aggregating category changes across all phases,
         *  for the sync-complete summary — not shown at all when a fresh source makes every category "new". */
        const val CATEGORIES_ADDED_KEY = "categoriesAdded"
        const val CATEGORIES_REMOVED_KEY = "categoriesRemoved"
    }
}

internal data class UpsertStats(
    val inserted: Int = 0,
    val updated: Int = 0,
    val skippedUnchanged: Int = 0,
    /**
     * Rows repositioned without being rewritten. No longer a subset of [updated] — since positions
     * these go through `moveAll`/`moveRanges` and touch one column, so counting them as updates
     * would hide the very thing that keeps a resync cheap.
     */
    val moved: Int = 0,
    /** How many of [moved] were served by a range statement rather than one write each. */
    val movedByRange: Int = 0,
    /**
     * Moves a range could not collapse, which is exactly the population the
     * `UPDATE … FROM (VALUES …)` batch exists to serve. Reported so it can be measured rather than
     * assumed — a re-sync where this is small does not exercise it at all.
     */
    val movedScattered: Int = 0,
)

internal data class CategoryRefresh(
    val idsByRemoteId: Map<String, Long>,
    val seenRemoteIds: Set<String>,
)

internal class SyncCounters(
    contentTypes: SyncContentTypes,
    private val onProgress: (ImportStage) -> Unit,
) {
    private val lock = Any()
    private val liveActive = contentTypes.live
    private var liveProcessed = 0

    fun update(phase: SyncPhase, count: Int): ImportStage {
        val snapshot = synchronized(lock) {
            when (phase) {
                SyncPhase.LIVE -> liveProcessed = count
            }
            snapshotLocked()
        }
        onProgress(snapshot)
        return snapshot
    }

    fun completeAll(): ImportStage {
        val snapshot = synchronized(lock) { snapshotLocked() }
        onProgress(snapshot)
        return snapshot
    }

    private fun snapshotLocked() = ImportStage(
        liveProcessed = liveProcessed,
        liveActive = liveActive,
    )
}

internal class SyncStatsCollector(val sourceId: Long) {
    val startedAt = System.currentTimeMillis()
    val phaseTiming = java.util.concurrent.ConcurrentHashMap<String, Long>()
    val processedCounts = java.util.concurrent.ConcurrentHashMap<String, Int>()
    val phaseErrors = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val warningFacts = java.util.concurrent.CopyOnWriteArrayList<SyncWarning>()

    /**
     * Set by a user-requested "force clean sync": bypasses the catalog-shrink prune guard so a
     * genuinely shrunken provider catalog can be trimmed down. Off for every automatic sync.
     */
    @Volatile var forcePrune = false

    fun addWarning(warning: SyncWarning) { warningFacts += warning }

    fun warnings(): List<SyncWarning> = warningFacts.toList() + phaseErrors.map { (phase, message) -> SyncWarning(phase, message) }

    fun build(result: SyncResult) = SyncRunStats(
        sourceId = sourceId,
        startedAt = startedAt,
        finishedAt = System.currentTimeMillis(),
        result = result,
        phaseTiming = phaseTiming.toMap(),
        processedCounts = processedCounts.toMap(),
        phaseErrors = phaseErrors.toMap(),
    )
}
