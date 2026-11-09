package tv.own.owntv.core.sync

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import tv.own.owntv.core.database.BulkInsertHelper
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.parser.XtCategory
import java.time.LocalDate
import java.time.ZoneId

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
 *  - category priority [CATEGORY_PRIORITY] (Federal and News first);
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
) {
    suspend fun sync(s: SourceEntity, progress: SyncCounters, stats: SyncStatsCollector) =
        coroutineScope {
            syncLive(s, progress, stats)
        }

    private suspend fun syncLive(s: SourceEntity, progress: SyncCounters, stats: SyncStatsCollector) {
        val ctx = currentCoroutineContext()
        val freshSource = s.lastSyncAt == null
        val label = SyncPhase.LIVE.name
        Log.i(TAG, "$label phase start sourceId=${s.id} fresh=$freshSource ruTube")
        progress.update(SyncPhase.LIVE, 0)

        val allChannels = rutube.channels()
        // Verified non-playable live channels (hls404 / empty master / player stub), mirroring the
        // SmartRuTube filter. Filtered before naming so duplicate-name disambiguation sees the
        // channels that actually remain. Reviewed 2026-11-09 with the mobile UA; drop an id here
        // once the channel plays again.
        val channels = allChannels.filterNot { it.videoId in KNOWN_BROKEN_IDS }
        val rawCategories = rutube.categories()
        if (channels.isEmpty()) {
            throw IllegalStateException("RuTube returned no live channels — refusing to wipe the catalogue")
        }
        val categoryNameById = rawCategories.associateBy({ it.id }, { it.name.ifBlank { it.alias } })

        // Map to XtCategory for refreshCategories (id/name). RuTube category 1 = "all live"
        // is skipped (it mirrors "Everything"); the rest are real genre buckets.
        val xtCats = rawCategories
            .filter { it.id != 1 }
            .map { XtCategory(id = it.id.toString(), name = it.name.ifBlank { it.alias }) }
        val categoryRefresh = support.refreshCategories(s, MediaType.LIVE, xtCats, stats)
        val catMap = categoryRefresh.idsByRemoteId // "4" -> db row id

        // Disambiguation needs the full list first (duplicate author-name groups).
        val duplicateNames = channels.groupBy { it.name }.filterValues { it.size > 1 }.keys
        val sortedChannels = channels.sortedWith(
            compareBy<RutubeChannel> { it.orderNumber?.takeIf { n -> n != 999 } ?: Int.MAX_VALUE }
                .thenBy { it.name },
        )

        var order = 0
        val entities = sortedChannels.map { ch ->
            val displayName = if (ch.name in duplicateNames) {
                val cleaned = cleanTitle(ch.title)
                if (cleaned != null) "%s · %s".format(ch.name, cleaned) else ch.name
            } else {
                ch.name
            }
            val primaryCategoryId = ch.categoryIds
                .filter { it != 1 }
                .minWithOrNull(compareBy<Int> { CATEGORY_PRIORITY.indexOf(it).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE })
            ChannelEntity(
                sourceId = s.id,
                categoryId = primaryCategoryId?.let { catMap[it.toString()] },
                name = displayName,
                logoUrl = ch.logoUrl,
                // Marker — resolved at tune time; never handed to a player as a URL.
                streamUrl = "rutube://${s.id}/${ch.videoId}",
                epgChannelId = ch.videoId,
                number = ch.orderNumber?.takeIf { it != 999 },
                remoteId = ch.videoId,
                sortOrder = order++,
                isPaid = ch.isPaid,
            )
        }

        // Hash-diffed stable upsert via chunked(), same as Xtream. The fresh-import path
        // takes insertFresh (no hash load).
        val hashDeferred = if (!freshSource) {
            coroutineScope {
                support.asyncHashLoad(this, label, s.id) { support.channelAdapter.loadHashes(s.id) }
            }
        } else null

        val insertFn: suspend (List<ChannelEntity>) -> UpsertStats = if (freshSource) {
            { rows -> support.insertFresh(rows, support.channelAdapter) }
        } else {
            { rows -> support.upsertStable(rows, hashDeferred!!, support.channelAdapter) }
        }

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
                remoteIds, support.channelAdapter.remoteIdOf, chunkSize,
            ) { add ->
                entities.forEach { add(it) }
            }
            if (!freshSource) {
                support.pruneRemoteIds(label, s.id, remoteIds, stats, support.channelAdapter.remoteIdsForSource, support.channelAdapter.deleteByRemoteIds)
                support.pruneCategories(s.id, MediaType.LIVE, categoryRefresh.seenRemoteIds, label, stats)
            }
        }
        progress.update(SyncPhase.LIVE, total[0])
        stats.processedCounts["channels"] = total[0]
        Log.i(TAG, "$label phase end sourceId=${s.id} unique=${total[0]} ruTube")
    }

    internal companion object {
        private const val TAG = SyncSupport.TAG

        /** RuTube category ids in primary-bucket order (Federal and News first). */
        val CATEGORY_PRIORITY = listOf(4, 3, 9, 5, 6, 7, 15, 8, 10, 2, 11, 14, 12, 1)

        /**
         * Channels whose stream is confirmed broken upstream, so they are not inserted at all.
         * Source: SmartRuTube `RuTubeChannelFilter.BAD_CHANNEL_IDS` (verified 2026-10-29 with the
         * mobile UA). Temporary — remove an id when the channel plays again.
         */
        val KNOWN_BROKEN_IDS: Set<String> = setOf(
            // hls404
            "75957f55ed1e9b4e0ec00f8dd9594fcc", // Прямой эфир РБК-Уфа
            "f785d738a845cc80658e887843a18ed9", // Прямой эфир телеканала CTV
            // empty_hls (master exists, child playlist is empty)
            "eda2da42eea89951e4135141a30915f7", // Прямой эфир ИЛЛИ ТВ
            // no_hls
            "49848b06c0fe2dae124419ffce1a1bab", // Домашний
            "b2b32a7d6f2323f3a113ff44b0404bf2", // Пятый канал
            "54395b96ad1a7b49966f46a6eee370a4", // РЕН ТВ
            "ffc110c6785e57f4c329179b555547b4", // СТС
            "92198c96552e3ee4a62cc1735a98ee54", // ТВ Центр
            // player_stub
            "21452175b89b01e0fb3ef72878d7259d", // КИНОПРЕМЬЕРА
            "d1318878c58e59ecb5b3d167f2c33f6d", // КИНОСВИДАНИЕ
            "aa94512eff79ba985e79d473c2a1e90b", // КИНОСЕМЬЯ
            "f1c3b0bbf26ad010d999e19147e3924f", // Кинохит
            "d5a20f38240b01ab8e4019a78f7d6a81", // Мужское кино
        )

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
 * low-level DAO — never the XMLTV stream path ([EpgRepository.refreshUrl] stays for XMLTV).
 *
 * Strategy (TVTube, live-tested):
 *  1. Brief-schedule seed phase: one `tvprogramm=true` pass seeds now/next for all channels.
 *     Cheap and never worse than nothing — failures are logged, never fatal.
 *  2. Full grid: per channel/day `pangolin` fetch (concurrency 6), 7 days out.
 *     A 404 = "no upstream schedule" — empty, not an error, and it stops further days
 *     for that channel without hitting the heavy mobile fallback.
 *  3. Mobile all-days fallback ONLY for real fetch errors (not 404).
 *  4. Room upserts + age pruning.
 */
class RuTubeEpgSyncer(
    private val rutube: RutubeApi,
    private val epgDao: tv.own.owntv.core.database.dao.EpgDao,
) {
    /** How far out the full grid is fetched, in days. */
    var guideDays: Int = 7

    suspend fun sync(sourceId: Long, channelIds: List<String>): EpgSyncSummary {
        var briefOk = 0
        var briefFail = 0
        runCatching {
            val brief = rutube.briefSchedules()
            // Write brief programmes as EpgChannelEntity + EpgProgrammeEntity.
            writeBrief(sourceId, brief)
            briefOk = brief.size
        }.onFailure { e -> briefFail++; Log.w(TAG, "RuTube brief seed failed: ${e.message}") }

        var channelsWithGuide = 0
        var programmesWritten = 0
        val semaphore = Semaphore(CONCURRENCY)
        val today = LocalDate.now(ZoneId.of("UTC"))
        coroutineScope {
            val jobs = channelIds.map { videoId ->
                async {
                    semaphore.withPermit {
                        runCatching {
                            var dayZeroEmpty = false
                            var ok = true
                            for (offset in 0 until guideDays) {
                                if (dayZeroEmpty) break
                                val date = today.plusDays(offset.toLong())
                                val programmes = try {
                                    rutube.scheduleDay(videoId, date)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    // Real fetch error → mobile all-days fallback, once.
                                    ok = false
                                    try { rutube.scheduleAllDays(videoId) } catch (fallback: Exception) {
                                        Log.w(TAG, "RuTube guide fallback failed $videoId: ${fallback.message}")
                                        emptyList()
                                    }
                                }
                                if (offset == 0 && programmes.isEmpty()) {
                                    // No schedule upstream at all — skip the remaining days.
                                    dayZeroEmpty = true
                                    continue
                                }
                                if (programmes.isNotEmpty()) {
                                    val written = writeChannel(sourceId, videoId, programmes)
                                    programmesWritten += written
                                }
                            }
                            if (ok && !dayZeroEmpty) channelsWithGuide++
                        }.onFailure { Log.w(TAG, "RuTube guide $videoId failed: ${it.message}") }
                    }
                }
            }
            jobs.forEach { it.await() }
        }
        // Prune old programmes (> 2 days back) for this source.
        pruneOld(sourceId)
        return EpgSyncSummary(
            briefOk = briefOk,
            briefFail = briefFail,
            channelsWithGuide = channelsWithGuide,
            channelsTotal = channelIds.size,
            programmesWritten = programmesWritten,
        )
    }

    private suspend fun writeBrief(sourceId: Long, brief: Map<String, List<RutubeProgramme>>) {
        // EpgChannelEntity rows for every channel in the brief map (has a schedule).
        val channels = brief.keys.map { videoId ->
            tv.own.owntv.core.database.entity.EpgChannelEntity(
                sourceId = sourceId,
                epgChannelId = videoId,
                displayName = null,
            )
        }
        if (channels.isNotEmpty()) {
            // Upsert in chunks to keep Room happy.
            channels.chunked(500).forEach { epgDao.upsertChannels(it) }
        }
        val programmes = brief.flatMap { (videoId, programmes) ->
            programmes.map { p ->
                tv.own.owntv.core.database.entity.EpgProgrammeEntity(
                    sourceId = sourceId,
                    epgChannelId = videoId,
                    startMs = p.startUtcMillis,
                    stopMs = p.endUtcMillis,
                    title = p.title,
                    description = p.description,
                    contentHash = 0,
                )
            }
        }
        programmes.chunked(500).forEach { epgDao.upsertProgrammes(it) }
    }

    private suspend fun writeChannel(sourceId: Long, videoId: String, programmes: List<RutubeProgramme>): Int {
        val rows = programmes.map { p ->
            tv.own.owntv.core.database.entity.EpgProgrammeEntity(
                sourceId = sourceId,
                epgChannelId = videoId,
                startMs = p.startUtcMillis,
                stopMs = p.endUtcMillis,
                title = p.title,
                description = p.description,
                contentHash = 0,
            )
        }
        rows.chunked(500).forEach { epgDao.upsertProgrammes(it) }
        return rows.size
    }

    private suspend fun pruneOld(sourceId: Long) {
        // 2-day-old cutoff, in millis.
        val cutoff = System.currentTimeMillis() - 2 * 24 * 60 * 60 * 1000L
        // EpgDao.prune deletes globally; for a single-source app this is equivalent.
        runCatching { epgDao.prune(cutoff) }
            .onFailure { Log.w(TAG, "RuTube EPG prune failed: ${it.message}") }
    }

    companion object {
        private const val TAG = SyncSupport.TAG
        private const val CONCURRENCY = 6
    }
}

data class EpgSyncSummary(
    val briefOk: Int,
    val briefFail: Int,
    val channelsWithGuide: Int,
    val channelsTotal: Int,
    val programmesWritten: Int,
)
