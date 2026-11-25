package tv.own.owntv.core.sync

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import tv.own.owntv.core.database.BulkInsertHelper
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.parser.XtCategory

/**
 * RuTube live-only syncer — the RuTube counterpart of [XtreamSyncer] (single-phase LIVE).
 *
 * Reuses the full [SyncSupport] machinery: [SyncSupport.refreshCategories],
 * [SyncSupport.chunked], [SyncSupport.upsertStable]/[SyncSupport.insertFresh] and
 * [SyncSupport.pruneRemoteIds]. The provider identity is RuTube's `videoId` in both
 * [ChannelEntity.remoteId] and [ChannelEntity.epgChannelId]; `streamUrl` is a
 * `rutube://{sourceId}/{videoId}` marker resolved at tune time by [RutubePlaybackResolver]
 * (or by the built-in source helper) — the marker must never reach the player.
 *
 * Channel naming follows TVTube's live-tested rules:
 *  - a broadcaster whose name is unique keeps the author name;
 *  - duplicated broadcaster names disambiguate by the cleaned live title
 *    (strip "Прямой эфир" / "Прямая трансляция" / "Прямое включение" prefixes).
 *
 * EPG is handled separately by [RuTubeEpgSyncer], which writes directly into the
 * shared [tv.own.owntv.core.database.dao.EpgDao] tables — never the XMLTV stream path.
 */
internal class RuTubeSyncer(
    private val rutube: RutubeApi,
    private val bulkInsertHelper: BulkInsertHelper,
    private val support: SyncSupport,
    private val channelCategoryDao: tv.own.owntv.core.database.ChannelCategoryDao,
    private val channelRenameDao: tv.own.owntv.core.database.ChannelRenameDao,
    private val renameSeeds: Map<String, String>,
) {
    suspend fun sync(s: SourceEntity, progress: SyncCounters, stats: SyncStatsCollector): Boolean =
        coroutineScope {
            syncLive(s, progress, stats)
        }

    private suspend fun syncLive(s: SourceEntity, progress: SyncCounters, stats: SyncStatsCollector): Boolean {
        val ctx = currentCoroutineContext()
        val freshSource = s.lastSyncAt == null
        val label = SyncPhase.LIVE.name
        Log.i(TAG, "$label phase start sourceId=${s.id} fresh=$freshSource ruTube")
        progress.update(SyncPhase.LIVE, 0)

        // Categories first: they also drive the two catalogue passes below.
        val rawCategories = rutube.categories()
        // The curated TV list carries RuTube's numbering and genres; the official TV feed carries
        // the channels that list omits (SKY SPORTS TENNIS, radio streams, regional channels, …).
        val tvCatalogue = rutube.channels(rawCategories.map { it.id })
        val feedCatalogue = rutube.liveChannels(rawCategories)
        val tvChannels = tvCatalogue.channels
        val feedChannels = feedCatalogue.channels
        val catalogueComplete = tvCatalogue.isComplete && feedCatalogue.isComplete && rawCategories.isNotEmpty()
        val allChannels = mergeRutubeChannels(tvChannels, feedChannels)
        Log.i(
            TAG,
            "$label catalogue tv=${tvChannels.size} feed=${feedChannels.size} merged=${allChannels.size} " +
                "complete=$catalogueComplete ruTube",
        )
        // Subscription channels (Premier/SPORT/…) never enter the catalogue: `is_paid` is false
        // everywhere, the real signal is `common_subscription_product_codes`; play/options answers
        // 244/404 and nothing plays without the package. The app has no purchase flow.
        // Iframe rows are editorial placeholders, not streams: they exist to carry order/category
        // for a playable feed twin and must never reach the catalogue themselves.
        // The curated-only tail gets a liveness probe: RuTube keeps deleted videos in the guide
        // (e.g. the old CTV card), while feed-backed channels are live by definition.
        val feedIds = feedChannels.mapTo(HashSet()) { it.videoId }
        val channels = allChannels
            .filterNot { it.isPaid || it.originType == "ifrm" }
            .filter {
                it.videoId in feedIds ||
                    (feedCatalogue.isComplete && feedChannels.isEmpty()) ||
                    rutube.isVideoAvailable(it.videoId)
            }
        // The shipped rename dictionary (hex videoId → name): mirrored wholesale, then applied at
        // both ends — to the fresh rows below, and to already-stored rows by the apply pass after
        // the commit, so a dictionary change reaches a channel whose content did not change.
        channelRenameDao.replaceAll(
            renameSeeds.map { (hex, name) -> tv.own.owntv.core.database.ChannelRenameEntity(remoteId = hex, name = name) },
        )
        val renames = renameSeeds
        if (channels.isEmpty()) {
            throw IllegalStateException("RuTube returned no live channels — refusing to wipe the catalogue")
        }
        if (!catalogueComplete) {
            stats.addWarning(
                SyncWarning(
                    phase = label,
                    message = "RuTube catalogue incomplete; pruning and membership rebuild skipped",
                    kind = SyncWarningKind.PAGE_FAILURE,
                ),
            )
        }
        val categoryNameById = rawCategories.associateBy({ it.id }, { it.name.ifBlank { it.alias } })

        // Map to XtCategory for refreshCategories (id/name/alias). RuTube category 1 = "all live"
        // is skipped (it mirrors "Everything"); the rest are real genre buckets.
        val xtCats = rawCategories
            .filter { it.id != 1 }
            .map {
                XtCategory(
                    id = it.id.toString(),
                    name = it.name.ifBlank { it.alias },
                    alias = it.alias.ifBlank { null },
                )
            }
        val categoryRefresh = support.refreshCategories(s, MediaType.LIVE, xtCats, stats)
        val catMap = categoryRefresh.idsByRemoteId // "4" -> provider category id

        // Disambiguation needs the full list first (duplicate author-name groups).
        val duplicateNames = channels.groupBy { it.name }.filterValues { it.size > 1 }.keys
        val sortedChannels = channels.sortedWith(
            compareBy<RutubeChannel> { it.providerNumber() ?: Int.MAX_VALUE }
                .thenBy { it.name },
        )
        val numbers = rutubeChannelNumbers(sortedChannels)

        var order = 0
        val entities = sortedChannels.map { ch ->
            val displayName = if (ch.name in duplicateNames) {
                val cleaned = cleanTitle(ch.title)
                if (cleaned != null) "%s · %s".format(ch.name, cleaned) else ch.name
            } else {
                ch.name
            }
            ChannelEntity(
                // A curated rename wins over the provider's name and the disambiguation suffix.
                name = renames[ch.videoId] ?: displayName,
                logoUrl = ch.logoUrl,
                number = numbers[ch.videoId],
                remoteId = ch.videoId,
                sortOrder = order++,
            )
        }

        // REPLACE-by-PK upsert via chunked(): the row's key IS the provider's hex videoId, so there
        // is no local id to preserve and no hash to diff — every sync writes exactly what arrived.
        val insertFn: suspend (List<ChannelEntity>) -> UpsertStats = { rows -> support.upsertChannels(rows) }

        val total = intArrayOf(0)
        val remoteIds = HashSet<String>()
        bulkInsertHelper.withOptimizedBulkInsert(
            "channels",
            "channels_fts",
            eligible = freshSource,
            ftsOnly = true,
        ) {
            val chunkSize = if (freshSource) BulkInsertHelper.CHUNK_FRESH else BulkInsertHelper.CHUNK
            support.chunked<ChannelEntity, Unit>(
                ctx, SyncPhase.LIVE, label, progress, insertFn, total,
                remoteIds, { it.remoteId }, chunkSize,
            ) { add ->
                entities.forEach { add(it) }
            }
            if (!freshSource && catalogueComplete) {
                support.pruneChannels(label, remoteIds, stats)
                support.pruneCategories(categoryRefresh.seenIds, label, stats)
            }
        }
        // Rebuild the many-to-many link only from a complete catalogue. Clearing it from a partial
        // response would silently hide channels whose genre bucket failed to load.
        if (catalogueComplete) rebuildChannelCategories(channels, catMap)
        // Rewrite the stored names of rows the dictionary covers — a no-op for the fresh rows the
        // upsert already renamed, the fix for rows whose content hash was untouched, and an inert
        // pass over orphans (no FK, a deleted channel's rename just waits).
        channelRenameDao.applyToChannels()
        progress.update(SyncPhase.LIVE, total[0])
        stats.processedCounts["channels"] = total[0]
        Log.i(TAG, "$label phase end sourceId=${s.id} unique=${total[0]} ruTube")
        return catalogueComplete
    }

    /**
     * Fills `channel_category` from each channel's full `broadcast_category[]` (redesign §2). The old
     * model kept only a single "primary" category on the channel; this records every genre it airs in,
     * keyed by the hex videoId so it survives a re-sync's id churn. Only channels present in the DB are
     * linked — the FK targets `channels.remoteId`, so a row for a filtered/broken channel would be
     * rejected. RuTube category 1 ("all live") is the Everything mirror, never a real bucket.
     */
    private suspend fun rebuildChannelCategories(
        channels: List<RutubeChannel>,
        catMap: Map<String, Long>,
    ) {
        channelCategoryDao.clearAll()
        val present = channelCategoryDao.presentHexIds().toSet()
        val rows = ArrayList<tv.own.owntv.core.database.ChannelCategoryEntity>()
        for (ch in channels) {
            if (ch.videoId !in present) continue
            for (rc in ch.categoryIds) {
                if (rc == 1) continue
                val dbCat = catMap[rc.toString()] ?: continue
                rows += tv.own.owntv.core.database.ChannelCategoryEntity(channelHexId = ch.videoId, categoryId = dbCat)
            }
        }
        if (rows.isNotEmpty()) channelCategoryDao.insertAll(rows)
    }

    internal companion object {
        private const val TAG = SyncSupport.TAG

        private val PREFIXES = listOf(
            "Прямой эфир",
            "Прямая трансляция",
            "Прямое включение",
        )

        /** Strip "Прямой эфир Матч! Футбол 1" → "Матч! Футбол 1"; null when nothing useful remains. */
        fun cleanTitle(title: String?): String? {
            if (title.isNullOrBlank()) return null
            var t = title.trim()
            for (prefix in PREFIXES) {
                if (t.startsWith(prefix)) {
                    t = t.substring(prefix.length).trim()
                        .trimStart(':', '—', '-', '–', '«', '"')
                        .trim()
                    break
                }
            }
            return t.takeIf { it.isNotBlank() }
        }
    }
}

/**
 * Totally public, tune-time HLS resolver. [playUrl] returns the live HLS URL; the 60 s cache
 * avoids repeated play-options calls during zapping. Must be shared between the preview player
 * and the fullscreen player (one instance, one cache) — Koin `single { }`.
 */
class RutubePlaybackResolver(private val rutube: RutubeApi) {
    private data class CacheEntry(val url: String, val atMs: Long)
    private val cache = java.util.concurrent.ConcurrentHashMap<String, CacheEntry>()

    /** Resolve a real HLS/media URL for [videoId], or null (paid/iframe/no-stream/network). */
    suspend fun resolveUrl(videoId: String): String? {
        val cached = cache[videoId]
        if (cached != null && System.currentTimeMillis() - cached.atMs < CACHE_TTL_MS) return cached.url
        return when (val result = rutube.playUrl(videoId)) {
            is RutubePlayback.Ready -> {
                cache[videoId] = CacheEntry(result.url, System.currentTimeMillis())
                result.url
            }
            is RutubePlayback.Unavailable -> null
        }
    }

    /** Resolve a channel's stream URL. Markers pass through only if they are real URLs. */
    suspend fun resolveChannelUrl(channel: ChannelEntity): String? {
        val url = channel.streamUrl
        if (url.startsWith("rutube://")) {
            val videoId = channel.remoteId ?: channel.epgChannelId ?: return null
            return resolveUrl(videoId)
        }
        return url.takeIf { it.isNotBlank() }
    }

    companion object {
        private const val CACHE_TTL_MS = 60_000L
    }
}

/**
 * EPG writer for RuTube. Writes directly into the shared EPG tables via [EpgRepository]'s
 * low-level DAO — there is no XMLTV stream path any more.
 *
 * Strategy (TVTube, live-tested):
 *  1. Brief-schedule seed phase: one `tvprogramm=true` pass seeds now/next for all channels.
 *     Cheap and never worse than nothing — failures are logged, never fatal.
 *  2. Full grid: per channel/day `pangolin` fetch (concurrency 6), 7 days out.
 *     A 404 = "no upstream schedule" — empty, not an error, and it stops further days
 *     for that channel without hitting the heavy mobile fallback.
 *  3. Mobile all-days fallback ONLY for real fetch errors (not 404).
 *  4. Room upserts + age pruning only for channels that completed without a fetch/write failure.
 */
class RuTubeEpgSyncer internal constructor(
    private val epgDao: tv.own.owntv.core.database.dao.EpgDao,
    private val fetchBrief: suspend () -> Map<String, List<RutubeProgramme>>,
    private val fetchDay: suspend (videoId: String, date: String) -> List<RutubeProgramme>,
    private val fetchAllDays: suspend (videoId: String) -> List<RutubeProgramme>,
) {
    constructor(rutube: RutubeApi, epgDao: tv.own.owntv.core.database.dao.EpgDao) : this(
        epgDao,
        rutube::briefSchedules,
        rutube::scheduleDay,
        rutube::scheduleAllDays,
    )

    /** How far out the full grid is fetched, in days. */
    var guideDays: Int = 7

    private data class ChannelOutcome(
        val programmesWritten: Int,
        val hasGuide: Boolean,
        val failed: Boolean,
        val failedWrites: Int,
    )

    suspend fun sync(channelIds: List<String>): EpgSyncSummary {
        var failedSeed = false
        var failedWrites = 0
        var briefWritten = 0
        val brief = try {
            fetchBrief()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failedSeed = true
            Log.w(TAG, "RuTube brief seed failed: ${e.message}")
            null
        }
        if (brief != null) {
            try {
                // Write brief programmes now/next; the full grid replaces them with the same rows plus
                // category/artwork the brief does not carry.
                briefWritten = writeBrief(brief)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failedWrites++
                Log.w(TAG, "RuTube brief seed write failed: ${e.message}")
            }
        }

        val semaphore = Semaphore(CONCURRENCY)
        val today = UtcDates.today()
        val ids = channelIds.distinct()
        // Each coroutine owns its counters and returns an immutable outcome. Awaiting and folding on
        // one coroutine removes the lost-update race on shared Ints; cancellation is never swallowed.
        val outcomes = coroutineScope {
            ids.map { videoId ->
                async {
                    semaphore.withPermit {
                        var dayZeroEmpty = false
                        var failed = false
                        var writesFailed = 0
                        var written = 0
                        for (offset in 0 until guideDays.coerceAtLeast(1)) {
                            if (dayZeroEmpty) break
                            currentCoroutineContext().ensureActive()
                            val date = UtcDates.iso(today + offset)
                            val programmes = try {
                                fetchDay(videoId, date)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                // Real fetch error → mobile all-days fallback, once. A successful
                                // fallback recovers the channel; only an unrecovered error fails it.
                                try {
                                    fetchAllDays(videoId)
                                } catch (fallback: CancellationException) {
                                    throw fallback
                                } catch (fallback: Exception) {
                                    failed = true
                                    Log.w(TAG, "RuTube guide fallback failed $videoId: ${fallback.message}")
                                    emptyList()
                                }
                            }
                            if (offset == 0 && programmes.isEmpty()) {
                                // 404/no schedule upstream is a successful empty channel, not a failure.
                                dayZeroEmpty = true
                                continue
                            }
                            if (programmes.isNotEmpty()) {
                                try {
                                    written += writeChannel(videoId, programmes)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    failed = true
                                    writesFailed++
                                    Log.w(TAG, "RuTube guide write failed $videoId: ${e.message}")
                                }
                            }
                        }
                        ChannelOutcome(
                            programmesWritten = written,
                            hasGuide = !failed && !dayZeroEmpty,
                            failed = failed,
                            failedWrites = writesFailed,
                        )
                    }
                }
            }.awaitAll()
        }

        val pruneIds = outcomes.indices.filter { !outcomes[it].failed }.map(ids::get)
        failedWrites += outcomes.sumOf { it.failedWrites }
        failedWrites += pruneSuccessfulChannels(pruneIds)
        return EpgSyncSummary(
            channelsWithGuide = outcomes.count { it.hasGuide },
            programmesWritten = briefWritten + outcomes.sumOf { it.programmesWritten },
            failedChannels = outcomes.count { it.failed },
            failedWrites = failedWrites,
            failedSeed = failedSeed,
        )
    }

    private suspend fun writeBrief(brief: Map<String, List<RutubeProgramme>>): Int {
        val programmes = brief.flatMap { (videoId, programmes) ->
            programmes.map { p ->
                tv.own.owntv.core.database.entity.EpgProgrammeEntity(
                    epgChannelId = videoId,
                    startMs = p.startUtcMillis,
                    stopMs = p.endUtcMillis,
                    title = p.title,
                    subtitle = p.subtitle,
                    description = p.description,
                    ageRating = p.ageRating,
                )
            }
        }
        programmes.chunked(500).forEach { epgDao.upsertProgrammes(it) }
        return programmes.size
    }

    private suspend fun writeChannel(videoId: String, programmes: List<RutubeProgramme>): Int {
        val rows = programmes.map { p ->
            tv.own.owntv.core.database.entity.EpgProgrammeEntity(
                epgChannelId = videoId,
                startMs = p.startUtcMillis,
                stopMs = p.endUtcMillis,
                title = p.title,
                subtitle = p.subtitle,
                description = p.description,
                category = p.category,
                ageRating = p.ageRating,
                iconUrl = p.iconUrl,
            )
        }
        rows.chunked(500).forEach { epgDao.upsertProgrammes(it) }
        return rows.size
    }

    private suspend fun pruneSuccessfulChannels(channelIds: List<String>): Int {
        if (channelIds.isEmpty()) return 0
        // 2-day-old cutoff, in millis. Chunk below SQLite's API-23 host-parameter limit.
        val cutoff = System.currentTimeMillis() - 2 * 24 * 60 * 60 * 1000L
        var failures = 0
        channelIds.chunked(PRUNE_QUERY_CHUNK).forEach { ids ->
            try {
                epgDao.pruneChannels(cutoff, ids)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failures++
                Log.w(TAG, "RuTube EPG prune failed: ${e.message}")
            }
        }
        return failures
    }

    companion object {
        private const val TAG = SyncSupport.TAG
        private const val CONCURRENCY = 6
        private const val PRUNE_QUERY_CHUNK = 500
    }
}

data class EpgSyncSummary(
    val channelsWithGuide: Int,
    val programmesWritten: Int,
    val failedChannels: Int = 0,
    val failedWrites: Int = 0,
    val failedSeed: Boolean = false,
) {
    /** An all-404 pass has no rows but no failures, and therefore still counts as synced. */
    val hasFailures: Boolean
        get() = failedChannels > 0 || failedWrites > 0 || failedSeed
}
