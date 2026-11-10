package tv.own.owntv.core.sync

import android.os.SystemClock
import android.util.Log
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import tv.own.owntv.core.database.BulkInsertHelper
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.CategoryEntity
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.database.entity.computeContentHash
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.network.HttpClient
import tv.own.owntv.core.drm.DrmConfig
import tv.own.owntv.core.player.ManifestType
import tv.own.owntv.core.network.StreamHeaders
import tv.own.owntv.core.parser.M3uParser

/**
 * The M3U import flow (split out of SyncManager). M3U playlists carry
 * no provider item ids, so a stable key is synthesized per item — `name|group` (with an `#n` suffix
 * for in-playlist duplicates) stored in `remoteId` — and resyncs run the same hash-diffed stable
 * upsert as Xtream/Stalker: unchanged rows are skipped, changed rows keep their local id (so
 * favorites/history/manual-order survive resyncs), vanished rows are pruned. `sortOrder`
 * is compared alongside the hash, so playlist reordering still propagates. Pruning only touches a
 * content type that actually appeared in the playlist, so a failed download or a live-only playlist
 * never wipes previously-imported rows of other types.
 */
internal class M3uSyncer(
    context: android.content.Context,
    private val sourceDao: SourceDao,
    private val categoryDao: CategoryDao,
    private val channelDao: ChannelDao,
    private val m3u: M3uParser,
    private val http: HttpClient,
    private val bulkInsertHelper: BulkInsertHelper,
    private val support: SyncSupport,
) {
    // Channels use the shared adapter: sortOrder no longer needs folding into the
    // M3U hash, because upsertStable now compares the stored sortOrder separately, so a reordered
    // playlist still counts as changed without a bespoke hash here.
    private val channelAdapter get() = support.channelAdapter
    private val localFile = LocalPlaylistFile(context)

    suspend fun sync(s: SourceEntity, progress: SyncCounters, stats: SyncStatsCollector) {
        val channelsStart = System.currentTimeMillis()
        val elapsedStart = SystemClock.elapsedRealtime()
        val ctx = currentCoroutineContext()
        val freshSource = s.lastSyncAt == null
        val chunkSize = if (freshSource) BulkInsertHelper.CHUNK_FRESH else BulkInsertHelper.CHUNK
        val reportBytes = SyncSupport.IgnoreByteProgress
        // A locally-picked playlist file (in-app StorageBrowser gives an absolute path; also tolerate
        // file://content:// URIs) is read straight from the device; a normal URL is downloaded. Same parser.
        val isLocal = s.url.startsWith("/") || s.url.startsWith("file://") || s.url.startsWith("content://")
        val localBytes = if (isLocal) localFile.size(s.url) else null
        Log.i(TAG, "M3U phase start sourceId=${s.id} local=$isLocal fresh=$freshSource bytesTotal=${localBytes ?: -1}")
        progress.update(SyncPhase.LIVE, 0)

        var processed = 0
        val header = bulkInsertHelper.withOptimizedBulkInsert(
            "channels",
            "channels_fts",
            eligible = freshSource,
            ftsOnly = true,
        ) {
            // Existing rows' (remoteId -> id, contentHash) lookup — loaded lazily so a
            // live-only playlist never pays for the queries. Loaded even on a fresh source:
            // a retried first sync may have leftover rows from the failed attempt, and diffing
            // against them (instead of blind-inserting) keeps the path single and idempotent.
            var channelHashes: StoredRows? = null
            suspend fun channelHashLookup() = channelHashes ?: loadHashLookup("M3U live", s.id) {
                channelDao.contentHashesForSource(it)
            }.also { channelHashes = it }

            // Stable-key bookkeeping: occurrence counters make in-playlist duplicate keys unique and
            // deterministic; seen sets drive the end-of-parse prune.
            val channelKeyCounters = HashMap<String, Int>()
            // Parallel counters reproducing the pre-1.0.30 truncated keys, for [rescueTruncatedKeys].
            val legacyChannelKeyCounters = HashMap<String, Int>()
            val seenChannelKeys = HashSet<String>()
            val seenGroupsByType = HashMap<MediaType, MutableSet<String>>()
            val newCategoriesByType = HashMap<MediaType, MutableList<CategoryEntity>>()

            // Categories are per-mediaType: the same group-title can exist for live and other types.
            val groupToCategoryId = HashMap<Pair<MediaType, String>, Long>()
            val pendingCategoryKeys = LinkedHashSet<Pair<MediaType, String>>()
            val pendingCategories = ArrayList<CategoryEntity>(chunkSize)
            val buffer = ArrayList<PendingM3uChannel>(chunkSize)
            var order = 0 // playlist position — lets "Playlist order" sorting replay the file's order
            // For PlaylistNumbering: did any channel carry tvg-chno, and which channels did not.
            var providerNumbered = false
            val unnumberedKeys = HashSet<String>()
            var categoryOrder = 0

            fun queueCategory(type: MediaType, group: String) {
                seenGroupsByType.getOrPut(type) { HashSet() }.add(group)
                val key = type to group
                if (groupToCategoryId.containsKey(key) || !pendingCategoryKeys.add(key)) return
                pendingCategories.add(
                    CategoryEntity(
                        sourceId = s.id,
                        mediaType = type,
                        name = group,
                        remoteId = group,
                        sortOrder = categoryOrder++,
                    ),
                )
            }

            // Stable category upsert (mirrors SyncSupport.upsertCategoriesStable, but incremental —
            // M3U discovers groups mid-stream): existing groups keep their category id, so content
            // rows' categoryId — and the hashes derived from it — stay stable across resyncs.
            suspend fun flushCategories() {
                if (pendingCategories.isEmpty()) return
                ctx.ensureActive()
                val start = SystemClock.elapsedRealtime()
                pendingCategories.groupBy { it.mediaType }.forEach { (type, cats) ->
                    val existing = cats.mapNotNull { it.remoteId }.chunked(SyncSupport.QUERY_CHUNK)
                        .flatMap { categoryDao.findByRemoteIds(s.id, type, it) }
                        .associateBy { it.remoteId }
                    val inserts = ArrayList<CategoryEntity>()
                    val updates = ArrayList<CategoryEntity>()
                    cats.forEach { row ->
                        val current = existing[row.remoteId]
                        when {
                            current == null -> inserts.add(row)
                            row.name != current.name || row.sortOrder != current.sortOrder -> {
                                updates.add(row.copy(id = current.id))
                                groupToCategoryId[type to row.remoteId!!] = current.id
                            }
                            else -> groupToCategoryId[type to row.remoteId!!] = current.id
                        }
                    }
                    if (updates.isNotEmpty()) categoryDao.updateAll(updates)
                    if (inserts.isNotEmpty()) {
                        val ids = categoryDao.insertAll(inserts)
                        val missed = ArrayList<String>()
                        inserts.forEachIndexed { i, row ->
                            val rid = row.remoteId ?: return@forEachIndexed
                            val id = ids.getOrNull(i) ?: -1L
                            if (id > 0) groupToCategoryId[type to rid] = id else missed.add(rid)
                        }
                        // IGNOREd conflicts return −1 (shouldn't happen — inserts were pre-checked by remoteId);
                        // heal by re-fetching just those rows.
                        if (missed.isNotEmpty()) {
                            missed.chunked(SyncSupport.QUERY_CHUNK)
                                .flatMap { categoryDao.findByRemoteIds(s.id, type, it) }
                                .forEach { cat -> cat.remoteId?.let { groupToCategoryId[type to it] = cat.id } }
                        }
                        if (!freshSource) newCategoriesByType.getOrPut(type) { ArrayList() }.addAll(inserts)
                    }
                }
                Log.d(TAG, "M3U categories flush sourceId=${s.id} rows=${pendingCategories.size} ms=${SystemClock.elapsedRealtime() - start}")
                pendingCategoryKeys.clear()
                pendingCategories.clear()
            }

            suspend fun flushChannels() {
                if (buffer.isEmpty()) return
                flushCategories()
                ctx.ensureActive()
                val channels = buffer.map { item ->
                    val entry = item.entry
                    val group = entry.groupTitle
                    tv.own.owntv.core.customize.ChannelNameCleanup.apply(s, ChannelEntity(
                        sourceId = s.id,
                        categoryId = group?.let { groupToCategoryId[MediaType.LIVE to it] },
                        name = entry.name,
                        logoUrl = entry.logo,
                        streamUrl = entry.streamUrl,
                        epgChannelId = entry.tvgId,
                        number = entry.tvgChno,
                        remoteId = stableKey(channelKeyCounters, entry.name, group),
                        sortOrder = item.order,
                        catchup = entry.catchup != null,
                        catchupDays = entry.catchupDays ?: 0,
                        catchupSource = entry.catchupSource,
                        catchupType = entry.catchup,
                        catchupCorrectionMin = entry.catchupCorrectionMin,
                        httpHeaders = StreamHeaders.encode(entry.headers),
                        drmConfig = DrmConfig.encode(entry.drm),
                        manifestType = ManifestType.encode(entry.manifestType),
                    ))
                }
                channels.forEach { seenChannelKeys.add(it.remoteId!!) }
                channels.forEach { if (it.number == null) unnumberedKeys.add(it.remoteId!!) else providerNumbered = true }
                val legacyKeys = buffer.map { legacyStableKey(legacyChannelKeyCounters, it.entry.name, it.entry.groupTitle) }
                val start = SystemClock.elapsedRealtime()
                val lookup = channelHashLookup()
                val stored = StoredRows(rescueTruncatedKeys(lookup.byRemoteId, channels.map { it.remoteId!! }.zip(legacyKeys)), lookup.occupied)
                val upsert = support.upsertStable(channels, CompletableDeferred(stored), channelAdapter)
                processed += channels.size
                Log.d(
                    TAG,
                    "M3U channel flush sourceId=${s.id} rows=${channels.size} dbInserted=${upsert.inserted} " +
                        "dbUpdated=${upsert.updated} dbSkipped=${upsert.skippedUnchanged} processed=$processed " +
                        "ms=${SystemClock.elapsedRealtime() - start}",
                )
                buffer.clear()
                progress.update(SyncPhase.LIVE, processed)
            }

            val onEntry: suspend (tv.own.owntv.core.parser.M3uEntry) -> Unit = { e ->
                // The VOD grids are gone; a playlist that tags an entry `type="vod"`/`"series"` (or
                // points at a plain video file) simply imports it as a live channel, the way the app
                // read such lists before those grids existed.
                e.groupTitle?.let { queueCategory(MediaType.LIVE, it) }
                buffer.add(PendingM3uChannel(order = order++, entry = e))
                if (buffer.size >= chunkSize) {
                    flushChannels()
                }
            }
            val header = if (isLocal) {
                localFile.open(s.url).use { input -> m3u.parse(input, onEntry) }
            } else {
                http.get(s.url, s.userAgent, reportBytes, maxAttempts = M3U_DOWNLOAD_ATTEMPTS) { input -> m3u.parse(input, onEntry) }
            }
            if (buffer.isNotEmpty()) {
                flushChannels()
            }

            // Prune — only for a content type that actually appeared, so a failed parse never wipes
            // previously-imported rows of other types. Runs only after a fully successful parse (an
            // exception above skips it), and also drops legacy null-remoteId rows from the
            // clear-then-insert era (one-time upgrade cleanup).
            if (processed > 0) {
                support.pruneRemoteIds("M3U live", s.id, seenChannelKeys, stats, { channelDao.remoteIdsForSource(it) }) { src, ids ->
                    channelDao.deleteByRemoteIds(src, ids)
                }
                channelDao.deleteNullRemoteIds(s.id)
                support.pruneCategories(s.id, MediaType.LIVE, seenGroupsByType[MediaType.LIVE].orEmpty(), "M3U live", stats)
                val renumbered = PlaylistNumbering.apply(channelDao, s.id, providerNumbered, unnumberedKeys)
                Log.d(TAG, "M3U playlist numbering sourceId=${s.id} providerNumbered=$providerNumbered renumbered=$renumbered")
            }

            // "Hide new categories on resync" — same behavior as Xtream/Stalker's refreshCategories.
            newCategoriesByType.forEach { (type, cats) ->
                if (cats.isNotEmpty()) {
                    stats.processedCounts.merge(SyncSupport.CATEGORIES_ADDED_KEY, cats.size, Int::plus)
                    support.applyHideNewCategoriesDefault(s.id, type, cats)
                }
            }
            header
        }
        // Persist the playlist's EPG url (url-tvg) for the EPG engine if the source didn't have one.
        if (!header.urlTvg.isNullOrBlank() && s.epgUrl.isNullOrBlank()) {
            sourceDao.update(s.copy(epgUrl = header.urlTvg))
        }
        progress.update(SyncPhase.LIVE, processed)
        stats.phaseTiming["channels"] = System.currentTimeMillis() - channelsStart
        stats.processedCounts["channels"] = processed
        Log.i(TAG, "M3U phase end sourceId=${s.id} processed=$processed ms=${SystemClock.elapsedRealtime() - elapsedStart}")
    }

    private suspend fun loadHashLookup(
        label: String,
        sourceId: Long,
        load: suspend (Long) -> List<tv.own.owntv.core.database.entity.ContentHashProjection>,
    ): StoredRows {
        val start = SystemClock.elapsedRealtime()
        return StoredRows(load(sourceId).associateBy({ it.remoteId }, { StoredRow(it.id, it.contentHash, it.sortOrder) })).also {
            Log.d(TAG, "$label hash map loaded sourceId=$sourceId size=${it.byRemoteId.size} ms=${SystemClock.elapsedRealtime() - start}")
        }
    }

    private data class PendingM3uChannel(
        val order: Int,
        val entry: tv.own.owntv.core.parser.M3uEntry,
    )

    companion object {
        private const val TAG = SyncSupport.TAG

        /** Separates name from group in synthesized keys; unlikely in real channel names. */
        private const val KEY_SEPARATOR = "\u0001"

        /**
         * One retry for the playlist download. The shared client leaves connection-failure recovery to
         * its callers, so a server that closed the kept-alive socket while it sat in the pool fails the
         * next refresh at once with "unexpected end of stream", before any byte arrives. [HttpClient]
         * only retries when no response came back, so nothing parsed is ever emitted twice.
         */
        internal const val M3U_DOWNLOAD_ATTEMPTS = 2

        /**
         * Synthesized stable id for an M3U item: name plus group joined by a control character, with an `#n` suffix for the 2nd+
         * occurrence of the same name+group in one playlist (true duplicates, kept distinct so the
         * unique `(sourceId, remoteId)` index can't silently drop them). Deterministic across
         * resyncs as long as the playlist keeps its duplicates in file order.
         */
        /**
         * The key the pre-1.0.30 rule would have produced for the same entry: the name cut at its
         * last comma. Equal to the real key for any name without a comma, which is nearly all of them.
         */
        private fun legacyStableKey(counters: HashMap<String, Int>, name: String, group: String?): String =
            stableKey(counters, name.substringAfterLast(',').trim(), group)

        /**
         * One-time rescue for rows stored under a pre-1.0.30 truncated name.
         *
         * Until 1.0.30 the display name was `substringAfterLast(',')` of the `#EXTINF` line, so
         * `Movie, The (1999)` was stored as `The (1999)`. [stableKey] derives the row's stable id from
         * the name, so correcting the name also changes the key: left alone, the corrected entry is
         * inserted as a *new* row and the truncated one is pruned, taking that title's favourites,
         * history and resume position with it.
         *
         * So every current key the database doesn't know is tried once under the old rule. A hit maps
         * the new key onto the stored row, and the upsert then updates that row in place — same local
         * id, so everything pinned to it survives. After that sync the row carries the new key and
         * this does nothing.
         *
         * Deliberately skipped when the answer would be a guess: two current names collapsing onto one
         * legacy key, or a legacy key that is itself a name in this playlist (a real channel "Music"
         * alongside "Live, Love, Music"). Those keep the old behaviour — a new row, and the stale one
         * pruned.
         */
        @VisibleForTesting
        internal fun rescueTruncatedKeys(
            stored: Map<String, StoredRow>,
            keys: List<Pair<String, String>>,
        ): Map<String, StoredRow> {
            val candidates = keys.filter { (now, was) -> now != was && now !in stored && was in stored }
            if (candidates.isEmpty()) return stored
            val currentNames = keys.mapTo(HashSet()) { it.first }
            val claims = candidates.groupingBy { it.second }.eachCount()
            val rescued = candidates.filter { (_, was) -> claims[was] == 1 && was !in currentNames }
            if (rescued.isEmpty()) return stored
            Log.i(TAG, "M3U truncated-name rescue: ${rescued.size} row(s) relinked to their full name")
            return stored + rescued.map { (now, was) -> now to stored.getValue(was) }
        }

        private fun stableKey(counters: HashMap<String, Int>, name: String, group: String?): String {
            val base = "$name$KEY_SEPARATOR${group.orEmpty()}"
            val n = counters.merge(base, 1, Int::plus)!!
            return if (n == 1) base else "$base$KEY_SEPARATOR#$n"
        }
    }
}
