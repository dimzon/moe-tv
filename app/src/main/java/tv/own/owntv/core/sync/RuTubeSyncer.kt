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

        // Categories first: they also drive the per-genre catalogue pass — RuTube's unfiltered list
        // can omit a live channel that its genre bucket still carries (see RutubeApi.channels).
        val rawCategories = rutube.categories()
        val allChannels = rutube.channels(rawCategories.map { it.id })
        // Two kinds of row never enter the catalogue:
        //  - subscription channels (Premier/SPORT/…): `is_paid` is false everywhere, the real signal
        //    is `common_subscription_product_codes`; play/options answers 244/404 and nothing plays
        //    without the package. The app has no purchase flow, so they are simply not offered.
        //  - verified broken channels (hls404 / empty master / player stub), mirroring SmartRuTube's
        //    filter. Both filters run before naming, so duplicate-name disambiguation only sees the
        //    channels that remain.
        val channels = allChannels
            .filterNot { it.isPaid }
            .filterNot { it.videoId in KNOWN_BROKEN_IDS }
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
            ChannelEntity(
                // A curated rename wins over the provider's name and the disambiguation suffix.
                name = renames[ch.videoId] ?: displayName,
                logoUrl = ch.logoUrl,
                number = ch.orderNumber?.takeIf { it != 999 },
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
            if (!freshSource) {
                support.pruneChannels(label, remoteIds, stats)
                support.pruneCategories(categoryRefresh.seenIds, label, stats)
            }
        }
        // Rebuild the many-to-many link (redesign §2): every genre in broadcast_category[], not just
        // the primary bucket. Keyed by the channel's hex videoId (= channels.remoteId), so a re-sync's
        // clear-then-insert never orphans a membership. Runs after the channels are committed.
        rebuildChannelCategories(channels, catMap)
        // Rewrite the stored names of rows the dictionary covers — a no-op for the fresh rows the
        // upsert already renamed, the fix for rows whose content hash was untouched, and an inert
        // pass over orphans (no FK, a deleted channel's rename just waits).
        channelRenameDao.applyToChannels()
        progress.update(SyncPhase.LIVE, total[0])
        stats.processedCounts["channels"] = total[0]
        Log.i(TAG, "$label phase end sourceId=${s.id} unique=${total[0]} ruTube")
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
 * low-level DAO — there is no XMLTV stream path any more.
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

    suspend fun sync(channelIds: List<String>): EpgSyncSummary {
        runCatching {
            val brief = rutube.briefSchedules()
            // Write brief programmes now/next; the full grid replaces them with the same rows plus
            // category/artwork the brief does not carry.
            writeBrief(brief)
        }.onFailure { e -> Log.w(TAG, "RuTube brief seed failed: ${e.message}") }

        var channelsWithGuide = 0
        var programmesWritten = 0
        val semaphore = Semaphore(CONCURRENCY)
        val today = UtcDates.today()
        coroutineScope {
            val jobs = channelIds.map { videoId ->
                async {
                    semaphore.withPermit {
                        runCatching {
                            var dayZeroEmpty = false
                            var ok = true
                            for (offset in 0 until guideDays) {
                                if (dayZeroEmpty) break
                                val date = UtcDates.iso(today + offset)
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
                                    val written = writeChannel(videoId, programmes)
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
        pruneOld()
        return EpgSyncSummary(
            channelsWithGuide = channelsWithGuide,
            programmesWritten = programmesWritten,
        )
    }

    private suspend fun writeBrief(brief: Map<String, List<RutubeProgramme>>) {
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

    private suspend fun pruneOld() {
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
    val channelsWithGuide: Int,
    val programmesWritten: Int,
)
