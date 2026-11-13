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
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.parser.XtEpgEntry
import tv.own.owntv.core.stalker.StreamUrlResolver

/**
 * How long a resolved now/next stays usable. Long enough that scrolling back to a channel costs
 * nothing, short enough that a programme changing over is noticed.
 */
private const val CACHE_TTL_MS = 5 * 60_000L

/** How many entries the provider's short-EPG endpoint is asked for. Its own practical maximum. */

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
 * database (falling back to the provider's short-EPG API) and maps rows. The guide shift is passed in
 * rather than observed, so the caller stays the single place that knows a customization changed.
 */
class LiveEpgReader(
    private val epgDao: EpgDao,
    private val streamUrlResolver: StreamUrlResolver,
) {
    private data class CachedEpg(val at: Long, val data: EpgNowNext)

    /** Written from the IO dispatcher (the now/next fetch) and cleared from Main (EPG mapping/shift
     *  changes), so it cannot be a plain HashMap — concurrent structural modification of one is
     *  undefined, up to a corrupt table or an infinite loop inside `get`. */
    private val cache = java.util.concurrent.ConcurrentHashMap<String, CachedEpg>()

    private data class CachedRows(val at: Long, val rows: List<EpgProgrammeEntity>)

    /** Provider-fetched guide rows per channel, so the grid and the preview share one request. */
    private val providerRows = java.util.concurrent.ConcurrentHashMap<String, CachedRows>()

    /** Drop every cached now/next — the global guide offset moved, so all of them are stale. */
    fun clearCache() {
        cache.clear()
    }

    /** Drop one channel's cached now/next (its EPG match or per-channel shift changed). */
    fun invalidate(channelId: String) {
        cache.remove(channelId)
    }

    /** Now/next for [ch], cached ~5 min. Prefers a manual EPG match / stored bulk guide, then falls
     *  database. */
    suspend fun nowNext(ch: ChannelEntity, cust: SectionCustomizations, globalShiftMinutes: Int): EpgNowNext? =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            cache[ch.id]?.takeIf { now - it.at < CACHE_TTL_MS }?.let { return@withContext it.data }

            // 1) Bulk guide via the effective EPG id (manual match overrides the channel's own id).
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
                    // Days of stored guide — accurate for bulk-guide channels (the short-EPG API path
                    // leaves this null, since its ~8 entries only span a few hours).
                    val days = runCatching { epgDao.coverageDays(epgKey) }.getOrNull()?.takeIf { it > 0 }
                    fun EpgProgrammeEntity.shifted() = EpgShift.apply(this, shift).toXt()
                    val result = EpgNowNext(nowProg?.shifted(), nextProg?.shifted(), future.drop(1).take(4).map { it.shifted() }, previous = prevProg?.shifted(), coverageDays = days, nowDetails = nowProg?.details)
                    cache[ch.id] = CachedEpg(now, result)
                    return@withContext result
                }
            }

            val entries = providerEntries(ch, shift) ?: return@withContext null
            // A gap in the provider's own guide data around "now" (nothing covers this instant) must leave
            // current null — picking the next entry that simply hasn't ended yet would mislabel an upcoming
            // programme as live. "Next"/"Later" are computed independently below, so a genuine
            // gap correctly shows no "Now" while the upcoming programme still appears as "Next".
            val current = entries.firstOrNull { it.startMs <= now && it.stopMs > now }
            val future = entries.filter { it.startMs > (current?.startMs ?: now) }.sortedBy { it.startMs }
            // Short-EPG responses sometimes include the just-finished programme — surface it as "Before".
            val previous = entries.filter { it.stopMs <= (current?.startMs ?: now) }.maxByOrNull { it.stopMs }
            val result = EpgNowNext(current, future.firstOrNull(), future.drop(1).take(4), previous = previous)
            cache[ch.id] = CachedEpg(now, result)
            result
        }

    /**
     * The channel's guide as the **provider** reports it right now, on the user's clock.
     *
     * This is the second of the two guides OwnTV reads, and the reason the preview pane could show a
     * programme while the grid and the channel row beside it stayed blank: these entries are fetched
     * live and are never written to `epg_programmes`, so anything reading only the table sees nothing.
     * Pulled out of [nowNext] so the guide grid and the channel rows can read exactly the same answer.
     *
     * Null — as distinct from empty — means there is no provider guide to ask for at all: no stream
     * id, no source, or a source type with no short-EPG endpoint. Callers use that to tell "the
     * provider says nothing is on" from "there was nobody to ask".
     *
     * **It is only ever a few hours.** `get_short_epg` returns about eight entries, so this fills now
     * and the rest of the evening, never a whole scrollable day.
     */
    private suspend fun providerEntries(ch: ChannelEntity, shift: Int): List<XtEpgEntry>? {
        // RuTube has no per-channel short-EPG endpoint: its guide arrives in bulk (RuTubeEpgSyncer),
        // so there is nothing for this provider fallback to ask. Kept for a future provider that
        // publishes one.
        return null
    }

    /**
     * The provider's guide for [channel] as guide rows, ready to draw — the grid's half of the same
     * fallback the preview pane has always had.
     *
     * The rows are **synthetic** — they exist nowhere in the database — so anything that looks a
     * programme up by id must fall back to the row it already holds, which is why the description
     * travels with it rather than being fetched later.
     *
     * Their ids are the **negated start time**: negative, so they can never collide with a real row's
     * (Room generates those upwards from 1), and distinct within a channel, so a caller that gathers
     * ids into a set — the grid marks its catch-up cells that way — cannot have one synthetic row
     * stand for all of them.
     *
     * Cached per channel for [CACHE_TTL_MS], so a row scrolling in and out of view, and the list's
     * own periodic refresh, cost one request rather than one per look.
     */
    suspend fun providerProgrammes(
        channel: ChannelEntity,
        cust: SectionCustomizations,
        globalShiftMinutes: Int,
    ): List<EpgProgrammeEntity> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        providerRows[channel.id]?.takeIf { now - it.at < CACHE_TTL_MS }?.let { return@withContext it.rows }
        val shift = EpgShift.minutesFor(cust, channel, globalShiftMinutes)
        val key = (cust.epgMatchResolver.epgIdFor(channel) ?: channel.epgChannelId)
            ?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: channel.streamUrl
        val rows = providerEntries(channel, shift).orEmpty().map {
            EpgProgrammeEntity(
                id = -it.startMs,
                sourceId = channel.sourceId,
                epgChannelId = key,
                startMs = it.startMs,
                stopMs = it.stopMs,
                title = it.title,
                description = it.description,
            )
        }.sortedBy { it.startMs }
        providerRows[channel.id] = CachedRows(now, rows)
        rows
    }

    /**
     * The programme currently airing on each of [channels] (channel id → title), looked up in ONE batch
     * against the stored bulk guide — same query the Home "On Now" rail uses. This powers the small
     * "current programme" subtitle under each channel row in the Live list and the in-player channel
     * overlay. Returns only channels that actually have something airing right now.
     *
     * **Two passes, because there are two guides.** The stored bulk guide answers first, in one query,
     * and covers most channels for the cost of a single round trip. Whatever it cannot answer then goes
     * through [nowNext] — *the very function that fills the preview pane* — which checks its own cache,
     * then the stored guide, then falls back to the provider's short-EPG API.
     *
     * That second pass is the whole point. Those short-EPG programmes are never written to the table, so
     * a channel served that way showed a full guide in the preview pane and a blank second line in the
     * list right beside it, permanently — the two were reading different guides. Now there is one answer
     * for one channel, and the row and the pane agree by construction rather than by coincidence: if the
     * preview has a programme the row shows it, and if the preview has none the row is blank too.
     *
     * **Nothing here goes to the network.** That is a hard rule, not an optimisation: see the note at
     * the second pass for what filling a list of hundreds by asking the provider actually did.
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
        // could not answer — **from cache, never from the network**.
        //
        // Fetching here was tried and was a mistake worth recording. A list holds hundreds of
        // channels, so "ask the provider for the ones we cannot answer" became hundreds of
        // `get_short_epg` requests, most of them returning nothing because that provider has no short
        // guide for those channels at all. Worse, the batch could not return until the last one
        // finished, so the list that used to fill from one query instantly now filled from nothing:
        // EVERY row went blank, which is the opposite of the bug it set out to fix.
        //
        // The provider is still asked — just never in bulk. The channel under the cursor is fetched
        // by the preview pane itself, and a guide row fetches as it scrolls into view. Both land in
        // this cache, so the list fills in behind them for free.
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
     * The current programme already resolved for [channelId], if it is still fresh — from either
     * cache, because the preview pane and the guide grid fill different ones.
     *
     * Deliberately never fetches. See the note in [nowPlayingFor] for what happened when it did.
     */
    private fun cachedNow(channelId: String, now: Long): XtEpgEntry? {
        cache[channelId]?.takeIf { now - it.at < CACHE_TTL_MS }?.data?.now
            ?.takeIf { it.title.isNotBlank() }?.let { return it.copy(description = null) }
        return providerRows[channelId]
            ?.takeIf { now - it.at < CACHE_TTL_MS }
            ?.rows?.firstOrNull { it.startMs <= now && it.stopMs > now }
            ?.takeIf { it.title.isNotBlank() }
            ?.let { XtEpgEntry(it.title, null, it.startMs, it.stopMs) }
    }

    /**
     * What was on [ch] at [atMs], and what followed it — the archive counterpart of [nowNext], for the
     * "PLAYING / THEN" card shown while a recording is playing.
     *
     * Stored-guide only, deliberately: the provider short-EPG APIs report *now and upcoming*, so they
     * can say nothing about yesterday afternoon. A channel whose guide comes only from that API gets
     * no archive card, which is correct — there is genuinely no data for the moment being watched.
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
