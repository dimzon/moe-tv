package tv.own.owntv.core.live

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import tv.own.owntv.core.CorePerf
import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.database.dao.EpgDao
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.epg.EpgShift
import tv.own.owntv.core.parser.XtEpgEntry

/**
 * How long a resolved now/next stays usable. Long enough that scrolling back to a channel costs
 * nothing, short enough that a programme changing over is noticed.
 */
private const val CACHE_TTL_MS = 5 * 60_000L

/**
 * How many stored rows to read for Now/Next/Later. Well over the six those show, because a second
 * feed carrying the channel interleaves its own rows, and the lower-priority ones are dropped
 * afterwards ([tv.own.owntv.core.epg.GuidePriority]) and would otherwise eat the list.
 */
private const val UPCOMING_LIMIT = 18

/**
 * Every guide read Live TV performs: now/next for the focused channel and the batched "what's on now"
 * for a list of channels.
 *
 * Split out of the TV app's Live view model, which kept none of this state beyond the now/next cache this class now
 * owns. Nothing here touches playback, the engine ladder or the tune state machine — it reads the
 * database and maps rows. The guide shift is passed in
 * rather than observed, so the caller stays the single place that knows a customization changed.
 */
class LiveEpgReader(private val epgDao: EpgDao) {
    private data class CachedEpg(val at: Long, val data: EpgNowNext)

    /** Written from the IO dispatcher (the now/next fetch) and cleared from Main (EPG mapping/shift
     *  changes), so it cannot be a plain HashMap — concurrent structural modification of one is
     *  undefined, up to a corrupt table or an infinite loop inside `get`. */
    private val cache = java.util.concurrent.ConcurrentHashMap<String, CachedEpg>()

    /** Drop every cached now/next — the global guide offset moved, so all of them are stale. */
    fun clearCache() {
        cache.clear()
    }

    /** Drop one channel's cached now/next (its EPG match or per-channel shift changed). */
    fun invalidate(channelId: String) {
        cache.remove(channelId)
    }

    /** Now/next for [ch], cached ~5 min, from the stored bulk guide. */
    suspend fun nowNext(ch: ChannelEntity, cust: SectionCustomizations, globalShiftMinutes: Int): EpgNowNext? =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            cache[ch.id]?.takeIf { now - it.at < CACHE_TTL_MS }?.let { return@withContext it.data }

            // The bulk guide via the effective EPG id (manual match overrides the channel's own id).
            val epgKey = (cust.epgMatchResolver.epgIdFor(ch) ?: ch.epgChannelId)?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
            // Guide shift (global or per-channel): the stored rows keep the feed's own clock, so we look
            // up the SHIFTED "now" and move what comes back — display and catch-up then agree.
            val shift = EpgShift.minutesFor(cust, ch, globalShiftMinutes)
            if (epgKey != null) {
                val at = EpgShift.toStored(now, shift)
                val rows = epgDao.upcoming(epgKey, at, UPCOMING_LIMIT).first()
                val nowProg = rows.onAirAt(at)
                val future = rows.filter { it.startMs > (nowProg?.startMs ?: 0) }
                val nextProg = future.firstOrNull()
                if (nowProg != null || nextProg != null) {
                    val prevProg = epgDao.previousProgramme(epgKey, nowProg?.startMs ?: at)
                    // Days of stored guide — accurate for bulk-guide channels.
                    val days = runCatching { epgDao.coverageDays(epgKey) }.getOrNull()?.takeIf { it > 0 }
                    fun EpgProgrammeEntity.shifted() = EpgShift.apply(this, shift).toXt()
                    val result = EpgNowNext(nowProg?.shifted(), nextProg?.shifted(), future.drop(1).take(4).map { it.shifted() }, previous = prevProg?.shifted(), coverageDays = days, nowDetails = nowProg?.details)
                    cache[ch.id] = CachedEpg(now, result)
                    return@withContext result
                }
            }
            null
        }

    /**
     * The programme currently airing on each of [channels] (channel id → title), looked up in ONE batch
     * against the stored bulk guide — same query the Home "On Now" rail uses. This powers the small
     * "current programme" subtitle under each channel row in the Live list and the in-player channel
     * overlay. Returns only channels that actually have something airing right now.
     *
     * Whatever the batch cannot answer is taken from [nowNext]'s cache — the channel under the cursor
     * is resolved by the preview pane itself, so the row beside it fills in behind it.
     *
     * **Nothing here goes to the network.** That is a hard rule, not an optimisation: filling a list
     * of hundreds by asking the provider one request at a time is exactly what this replaced.
     */
    suspend fun nowPlayingFor(
        channels: List<ChannelEntity>,
        cust: SectionCustomizations,
        globalShiftMinutes: Int,
    ): Map<String, String> = nowProgrammesFor(channels, cust, globalShiftMinutes).mapValues { it.value.title }

    /**
     * [nowPlayingFor] with the programme's start and stop as well, in display time (the channel's
     * guide offset applied), for the rows that show "1 h 23 min left" and a progress bar. Same two
     * passes, same no-network rule; the description is always null.
     */
    suspend fun nowProgrammesFor(
        channels: List<ChannelEntity>,
        cust: SectionCustomizations,
        globalShiftMinutes: Int,
    ): Map<String, XtEpgEntry> = withContext(Dispatchers.IO) {
        if (channels.isEmpty()) return@withContext emptyMap()
        val now = System.currentTimeMillis()
                // Manual EPG-match overrides take precedence over the channel's own epgChannelId, mirroring nowNext.
        // Shifted channels look up a different instant, so the batch is grouped by shift — with no
        // offsets configured (the normal case) that's still exactly one query group.
        val channelKeys = channels.mapNotNull { ch ->
            val key = (cust.epgMatchResolver.epgIdFor(ch) ?: ch.epgChannelId)
                ?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
            if (key != null) Triple(ch.id, key, EpgShift.minutesFor(cust, ch, globalShiftMinutes)) else null
        }
        if (channelKeys.isEmpty()) return@withContext emptyMap()
        val startedAt = android.os.SystemClock.elapsedRealtime()
        var chunks = 0
        val result = HashMap<String, XtEpgEntry>()
        for ((shift, group) in channelKeys.groupBy { it.third }) {
            val delta = shift * 60_000L
            val at = EpgShift.toStored(now, shift)
            val rowsByKey = group
                .map { it.second }.distinct()
                .chunked(400)
                .flatMap { keys ->
                    chunks++
                    epgDao.programmeSummariesForChannels(keys, at, at + 1)
                }
                .groupBy { it.epgChannelId }
            for ((channelId, epgKey, _) in group) {
                rowsByKey[epgKey]
                    
                    ?.firstOrNull { at in it.startMs until it.stopMs }
                    ?.let { result[channelId] = XtEpgEntry(it.title, null, it.startMs + delta, it.stopMs + delta) }
            }
        }
        // Second pass: whatever the preview pane has already resolved for a channel the stored guide
        // could not answer — **from cache, never from the network**. A list holds hundreds of
        // channels; asking one request at a time made every row wait for the last answer, so the
        // batch that used to fill from one query filled from nothing.
        val fromStored = result.size
        for (ch in channels) {
            if (ch.id in result) continue
            cachedNow(ch.id, now)?.let { result[ch.id] = it }
        }
        CorePerf.log {
            "live_nowplaying channels=${channels.size} keyed=${channelKeys.size} " +
                "keys=${channelKeys.map { it.second }.distinct().size} chunks=$chunks " +
                "fromStored=$fromStored fromCache=${result.size - fromStored} " +
                "totalMs=${android.os.SystemClock.elapsedRealtime() - startedAt}"
        }
        result
    }

    /**
     * The current programme already resolved for [channelId], if it is still fresh — from the
     * preview pane's cache. Deliberately never fetches.
     */
    private fun cachedNow(channelId: String, now: Long): XtEpgEntry? =
        cache[channelId]?.takeIf { now - it.at < CACHE_TTL_MS }?.data?.now
            ?.takeIf { it.title.isNotBlank() }?.copy(description = null)

    /**
     * What was on [ch] at [atMs], and what followed it — the archive counterpart of [nowNext], for the
     * "PLAYING / THEN" card shown while a recording is playing.
     *
     * Stored-guide only, deliberately: an archive row for a moment the guide never covered would be a
     * guess, and there is genuinely no data for it.
     *
     * Returns null when nothing is known at [atMs], so the caller can simply omit the card.
     */
    suspend fun nowNextAt(
        ch: ChannelEntity,
        atMs: Long,
        cust: SectionCustomizations,
        globalShiftMinutes: Int,
    ): EpgNowNext? = withContext(Dispatchers.IO) {
        val epgKey = (cust.epgMatchResolver.epgIdFor(ch) ?: ch.epgChannelId)
            ?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return@withContext null
        // Same shift dance as [nowNext]: stored rows keep the feed's own clock, so look up the shifted
        // instant and move what comes back, or the card would disagree with the archive it describes.
        val shift = EpgShift.minutesFor(cust, ch, globalShiftMinutes)
        val at = EpgShift.toStored(atMs, shift)
        val rows = epgDao.upcoming(epgKey, at, UPCOMING_LIMIT).first()
        val playing = rows.onAirAt(at)
        val then = rows.firstOrNull { it.startMs > (playing?.startMs ?: 0) }
        if (playing == null && then == null) return@withContext null
        fun EpgProgrammeEntity.shifted() = EpgShift.apply(this, shift).toXt()
        EpgNowNext(playing?.shifted(), then?.shifted())
    }

    /** The programme airing at [at] in start-ordered rows: the latest to have started, if two overlap. */
    private fun List<EpgProgrammeEntity>.onAirAt(at: Long): EpgProgrammeEntity? =
        lastOrNull { it.startMs <= at && it.stopMs > at }
}

/** A stored programme as a schedule row; its id lets a screen open the full programme (details, synopsis). */
internal fun EpgProgrammeEntity.toXt() =
    XtEpgEntry(title = title, description = description, startMs = startMs, stopMs = stopMs, programmeId = id)
