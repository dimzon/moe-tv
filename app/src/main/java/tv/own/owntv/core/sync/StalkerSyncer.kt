package tv.own.owntv.core.sync

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.own.owntv.core.database.BulkInsertHelper
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.computeContentHash
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.parser.XtCategory
import tv.own.owntv.core.stalker.StalkerAuthManager
import tv.own.owntv.core.stalker.StalkerClient
import tv.own.owntv.core.stalker.StalkerCredentials
import tv.own.owntv.core.stalker.StalkerSession
import tv.own.owntv.core.stalker.stalkerCredentials
import java.io.IOException

/**
 * Stalker/Ministra portal import. Unlike Xtream there is no single bulk endpoint —
 * content is inherently paged per genre — so this walks `get_genres` → per-genre `get_ordered_list`
 * pages, but reuses the shared [SyncSupport] machinery (chunked streaming insert with remoteId
 * dedupe, hash-diffed stable upsert / fresh insert, category refresh, pruning). The per-item `cmd`
 * is stored in `streamUrl` and resolved to a real URL at play time by StreamUrlResolver.
 *
 * LIVE uses a bulk `get_all_channels` fast path with per-genre paging fallback. If a portal turns
 * out to serve a bulk/all-genre dump, the `bulk`/`per-genre fallback` logs will show it — probe
 * `get_ordered_list&category=*` before assuming paging is the only path.
 */
internal class StalkerSyncer(
    private val client: StalkerClient,
    private val auth: StalkerAuthManager,
    private val bulkInsertHelper: BulkInsertHelper,
    private val support: SyncSupport,
    private val sourceDao: tv.own.owntv.core.database.dao.SourceDao,
    private val ceilings: PortalCeilingStore,
) {
    /**
     * Live alone: it has a one-shot bulk `get_all_channels` dump — seconds, not minutes — and
     * draws on one bounded request budget (the adaptive limiter below), so the phase never doubles
     * the number of simultaneous requests the portal sees. Guarded, so one failure keeps the rest
     * of the import.
     */
    suspend fun sync(s: SourceEntity, progress: SyncCounters, stats: SyncStatsCollector, contentTypes: SyncContentTypes) {
        val creds = credentialsFor(s)
        // First choice: if the portal profile advertises an XMLTV feed, adopt it as the
        // source's guide URL so the existing (stream-parsed, rolling-window) XMLTV pipeline can sync
        // it from Settings → EPG Sources. Best effort — a failure here must never fail the catalog sync.
        runCatching { adoptPortalXmltvUrl(s, creds) }
            .onFailure { Log.w(TAG, "portal XMLTV probe failed sourceId=${s.id}: ${it.message}") }
        // Shared adaptive budget: every phase draws from one gate, so the portal never sees more than
        // the learned limit regardless of how many are in flight. Live shares it too — it used to page
        // through a fixed six-wide window that learned nothing, which on a strict panel is precisely
        // the burst that earns a 403, and live is the phase everybody syncs.
        withLearnedCeiling(s) { budget ->
            if (contentTypes.live) syncLive(s, progress, stats, creds, budget)
        }
    }

    /**
     * Run [block] with a gate that starts below the limit this portal last refused, and store what
     * the run learned — also when it failed part-way, since a refusal is most worth remembering then.
     */
    private suspend fun <T> withLearnedCeiling(s: SourceEntity, block: suspend (AdaptivePortalLimiter) -> T): T {
        val learned = ceilings.get(s.id)
        val budget = AdaptivePortalLimiter.forCeiling(learned?.ceiling, ::isPortalThrottle)
        if (learned != null) Log.i(TAG, "portal ceiling sourceId=${s.id} learned=${learned.ceiling} cleanRuns=${learned.cleanRuns} max=${budget.max}")
        try {
            return block(budget)
        } finally {
            withContext(NonCancellable) {
                runCatching { ceilings.record(s.id, learned, budget) }
                Log.i(TAG, "portal limit sourceId=${s.id} throttledAt=${budget.throttledAt} reachedMax=${budget.reachedMax} final=${budget.currentLimit}")
            }
        }
    }

    private suspend fun syncLive(
        s: SourceEntity, progress: SyncCounters, stats: SyncStatsCollector, creds: StalkerCredentials,
        budget: AdaptivePortalLimiter,
    ) = coroutineScope {
        val ctx = currentCoroutineContext()
        val freshSource = s.lastSyncAt == null
        val label = SyncPhase.LIVE.name
        val phaseStart = System.currentTimeMillis()
        val elapsedStart = SystemClock.elapsedRealtime()
        Log.i(TAG, "$label phase start sourceId=${s.id} fresh=$freshSource")
        progress.update(SyncPhase.LIVE, 0)

        // The cmd's per-call play_token is left out of the hash ([StalkerClient.withoutPlayToken]),
        // or an unchanged channel list would rewrite every channel on every re-sync.
        val adapter = support.channelAdapter.copy(hashOf = ::stalkerChannelHash)
        val hashDeferred = if (!freshSource) support.asyncHashLoad(this, label, s.id) { adapter.loadHashes(s.id) } else null

        // Genres first (one auth round trip, reused for every page below).
        val genres = auth.withAuthRetry(creds) { session ->
            client.getGenres(session.apiBase, creds.mac, session.token, creds.userAgent)
        }.filter { it.id.isNotBlank() && it.id != "*" }
        Log.i(TAG, "$label genres sourceId=${s.id} count=${genres.size}")

        val categories = support.refreshCategories(s, MediaType.LIVE, genres.map { XtCategory(it.id, it.title) }, stats)
        val catMap = categories.idsByRemoteId

        val insertFn: suspend (List<ChannelEntity>) -> UpsertStats = if (freshSource) {
            { rows -> support.insertFresh(rows, adapter) }
        } else {
            { rows ->
                support.upsertStable(rows, hashDeferred!!, adapter).also {
                    // A channel whose token alone is new was skipped above. Its newest token is still
                    // stored, URL only, so a portal that does check it gets one as fresh as the last
                    // re-sync — as before, without rewriting the whole row.
                    bulkInsertHelper.updateChannelStreamUrls(
                        s.id,
                        rows.filter { StalkerClient.hasPlayToken(it.streamUrl) }
                            .mapNotNull { row -> row.remoteId?.let { it to row.streamUrl } }
                            .toMap(),
                    )
                }
            }
        }
        val total = intArrayOf(0)
        val remoteIds = if (freshSource) null else HashSet<String>()
        // Genres/pages this pass could not fetch. A non-zero count means remoteIds is incomplete,
        // so pruning against it would delete the missing genres' channels as "stale".
        val pageFailures = java.util.concurrent.atomic.AtomicInteger(0)
        // Diagnostic: the first few channels this pass builds, so their per-field
        // fingerprints can be compared across two consecutive syncs — see [logFieldFingerprints].
        val fieldDiffSample = ArrayList<ChannelEntity>(FIELD_DIFF_SAMPLE)
        var order = 0
        // Small flush chunk (vs Xtream's 10k fresh) so the count appears quickly instead of the UI
        // sitting on "Connecting to source…" until the first big insert — Stalker catalogs are small
        // (tens of thousands), so the extra inserts cost nothing.
        val chunkSize = STALKER_CHUNK

        bulkInsertHelper.withOptimizedBulkInsert("channels", "channels_fts", eligible = freshSource, ftsOnly = true) {
            support.chunked<ChannelEntity, Unit>(ctx, SyncPhase.LIVE, label, progress, insertFn, total, remoteIds, adapter.remoteIdOf, chunkSize) { add ->
                val emit: suspend (StalkerClient.Channel, String?) -> Unit = { ch, fallbackGenreId ->
                    val entity = tv.own.owntv.core.customize.ChannelNameCleanup.apply(s, ChannelEntity(
                            sourceId = s.id,
                            // A channel's own tv_genre_id wins; fall back to the genre we asked for.
                            categoryId = ch.genreId?.let { catMap[it] } ?: fallbackGenreId?.let { catMap[it] },
                            name = ch.name,
                            logoUrl = ch.logo,
                            streamUrl = ch.cmd, // portal command — resolved to a real URL at play time
                            // The portal's XMLTV id when it publishes one, else its own channel id —
                            // which is the key the portal's guide (`get_epg_info`) is written under,
                            // so a portal with no XMLTV still resolves now/next, the Guide and
                            // catch-up. A user's manual EPG match still wins over both at read time.
                            epgChannelId = ch.xmltvId ?: ch.id,
                            number = ch.number?.toIntOrNull(),
                            remoteId = ch.id,
                            sortOrder = order++,
                            catchup = ch.archive,
                            catchupDays = ch.archiveDuration,
                    ))
                    if (fieldDiffSample.size < FIELD_DIFF_SAMPLE) fieldDiffSample.add(entity)
                    add(entity)
                }

                // FAST PATH: one bulk get_all_channels download (13k channels served ~14/page would
                // otherwise be ~1000 tiny requests). Buffered so it's all-or-nothing — a mid-download
                // failure emits nothing and drops to the per-genre fallback (no partial duplicates).
                val bulkStart = SystemClock.elapsedRealtime()
                val bulk = try {
                    auth.withAuthRetry(creds) { session ->
                        client.getAllChannels(session.apiBase, creds.mac, session.token, creds.userAgent)
                    }
                } catch (c: kotlinx.coroutines.CancellationException) {
                    throw c
                } catch (e: Exception) {
                    Log.w(TAG, "$label get_all_channels failed (${e.message}) — falling back to per-genre paging")
                    null
                }
                // A non-empty bulk is not the same as a COMPLETE bulk: some portals answer
                // get_all_channels with a truncated list. Cross-check the size against the portal's
                // own declared total (the synthetic "*" genre's page 1) and only trust the dump
                // when it covers that total. When
                // the probe itself fails we can't judge, so the dump is accepted as before — the
                // catalog-shrink guard in SyncSupport still protects the prune.
                val declaredTotal = if (bulk != null && bulk.isNotEmpty()) {
                    try {
                        fetchPage(creds, "*", 1, budget).totalItems
                    } catch (c: kotlinx.coroutines.CancellationException) {
                        throw c
                    } catch (e: Exception) {
                        Log.w(TAG, "$label bulk verification probe failed (${e.message}) — accepting dump unverified")
                        0
                    }
                } else 0
                val bulkComplete = bulk != null && bulk.isNotEmpty() && bulk.size >= declaredTotal
                // Remote ids already emitted from the bulk dump, so the genre backfill below cannot
                // insert one of them a second time. A FRESH source passes no `seenKeys` to chunked()
                // — its duplicate filter is off — so this is the only thing standing between a portal
                // that omits `tv_genre_id` from its dump and a doubled first sync. Stays empty (and
                // costs nothing) on the fallback path, where no bulk dump was accepted.
                val bulkEmittedIds = HashSet<String>()
                // Per-genre paged fetch. Pages fetched CONCURRENTLY in windows; add() runs
                // single-threaded after each window's awaitAll. Used for the whole catalog when the
                // portal denies/empties the bulk list, and for the few genres a bulk dump left empty.
                // Returns the genres whose first page could not be fetched; the caller gives them one
                // more pass at the end, when the portal has had time to calm down.
                val pageGenres: suspend (List<StalkerClient.Genre>, Boolean) -> List<StalkerClient.Genre> = { list, lastChance ->
                    val unreached = ArrayList<StalkerClient.Genre>()
                    list.forEachIndexed { gi, genre ->
                        ctx.ensureActive()
                        // A genre that can't be fetched must not silently shrink the pass: count the
                        // failure so the prune below is skipped.
                        val first = try {
                            fetchPage(creds, genre.id, 1, budget)
                        } catch (c: kotlinx.coroutines.CancellationException) {
                            throw c
                        } catch (e: Exception) {
                            if (lastChance) pageFailures.incrementAndGet() else unreached.add(genre)
                            Log.w(TAG, "$label genre=${genre.id} page1 failed (${e.message})")
                            return@forEachIndexed
                        }
                        first.items.forEach { if (it.id !in bulkEmittedIds) emit(it, genre.id) }
                        val maxPer = first.maxPageItems.takeIf { it > 0 } ?: first.items.size
                        val pages = (if (maxPer > 0) (first.totalItems + maxPer - 1) / maxPer else 1)
                            .coerceAtMost(MAX_PAGES_PER_GENRE)
                        Log.i(TAG, "$label genre ${gi + 1}/${list.size} id=${genre.id} '${genre.title}' total=${first.totalItems} maxPer=$maxPer pages=$pages")
                        var page = 2
                        val missed = ArrayList<Int>()
                        while (page <= pages) {
                            ctx.ensureActive()
                            // Spawned at the limiter's MAX; the adaptive gate inside fetchPage decides
                            // how many of them actually reach the portal at any moment.
                            val windowEnd = minOf(page + budget.max - 1, pages)
                            val window = coroutineScope {
                                (page..windowEnd).map { p ->
                                    async {
                                        try {
                                            fetchPage(creds, genre.id, p, budget)
                                        } catch (c: kotlinx.coroutines.CancellationException) {
                                            throw c
                                        } catch (e: Exception) {
                                            Log.w(TAG, "$label genre=${genre.id} page=$p failed (${e.message}) — retrying at the end of the genre")
                                            synchronized(missed) { missed.add(p) }
                                            null
                                        }
                                    }
                                }.awaitAll()
                            }
                            window.forEach { pageResult ->
                                pageResult?.items?.forEach { if (it.id !in bulkEmittedIds) emit(it, genre.id) }
                            }
                            page = windowEnd + 1
                        }
                        // One serial pass over what the windows dropped, rather than losing the pages.
                        missed.sorted().forEach { p ->
                            val again = try {
                                fetchPage(creds, genre.id, p, budget)
                            } catch (c: kotlinx.coroutines.CancellationException) {
                                throw c
                            } catch (e: Exception) {
                                pageFailures.incrementAndGet()
                                Log.w(TAG, "$label genre=${genre.id} page=$p failed again (${e.message})")
                                null
                            }
                            again?.items?.forEach { if (it.id !in bulkEmittedIds) emit(it, genre.id) }
                        }
                    }
                    unreached
                }
                val pageAllGenres: suspend (List<StalkerClient.Genre>) -> Unit = { list ->
                    val unreached = pageGenres(list, false)
                    if (unreached.isNotEmpty()) {
                        Log.i(TAG, "$label retrying ${unreached.size} genre(s) whose first page failed")
                        pageGenres(unreached, true)
                    }
                }

                if (bulkComplete) {
                    Log.i(TAG, "$label get_all_channels ok count=${bulk.size} declaredTotal=$declaredTotal ms=${SystemClock.elapsedRealtime() - bulkStart}")
                    bulk.forEach { emit(it, null) }
                    // A complete-looking dump can still be missing whole genres — see
                    // [genresMissingFromBulk]. Ask for those directly; costs nothing when the portal
                    // hid nothing.
                    val absent = genresMissingFromBulk(genres, bulk)
                    if (absent.isNotEmpty()) {
                        bulk.forEach { bulkEmittedIds.add(it.id) }
                        Log.i(TAG, "$label bulk omitted ${absent.size} genre(s) ${absent.joinToString { it.id }} — fetching per-genre")
                        pageAllGenres(absent)
                    }
                } else {
                    if (bulk != null && bulk.isNotEmpty()) {
                        Log.w(TAG, "$label get_all_channels truncated count=${bulk.size} declaredTotal=$declaredTotal — falling back to per-genre paging")
                    }
                    Log.i(TAG, "$label per-genre fallback begin genres=${genres.size}")
                    pageAllGenres(genres)
                }
            }
            if (pageFailures.get() > 0) {
                stats.addWarning(SyncWarning(SyncPhase.LIVE.name, kind = SyncWarningKind.PAGE_FAILURE, count = pageFailures.get()))
            }
            if (!freshSource && remoteIds != null) {
                if (pageFailures.get() == 0) {
                    support.pruneRemoteIds(label, s.id, remoteIds, stats, adapter.remoteIdsForSource, adapter.deleteByRemoteIds)
                    support.pruneCategories(s.id, MediaType.LIVE, categories.seenRemoteIds, label, stats)
                } else {
                    Log.i(TAG, "$label prune skipped sourceId=${s.id} reason=page_failures failures=${pageFailures.get()}")
                }
            }
        }

        logFieldFingerprints(label, s.id, fieldDiffSample)
        progress.update(SyncPhase.LIVE, total[0])
        stats.phaseTiming["channels"] = System.currentTimeMillis() - phaseStart
        stats.processedCounts["channels"] = total[0]
        Log.i(TAG, "$label phase end sourceId=${s.id} unique=${total[0]} ms=${SystemClock.elapsedRealtime() - elapsedStart}")
    }

    /**
     * Diagnostic — which channel field makes Stalker rewrite ~99% of its rows every sync.
     *
     * Stalker resyncs report `dbUpdated≈1500 dbSkipped=0` with identical counts run after run, i.e.
     * the hash differs for nearly every channel even though the catalog plainly hasn't changed. The
     * audit's instruction is to identify the volatile field with a real field diff *before* changing
     * anything — excluding the wrong field from the hash would mask genuine provider updates.
     *
     * Each field is logged as a **hash, never a value**: a Stalker `cmd` can embed the portal host,
     * the MAC or a play token, and these lines get pasted into bug reports. Run a sync twice and diff
     * the two blocks — whichever `field=` number moves for the same `remoteId` is the culprit.
     */
    private fun stalkerChannelHash(c: ChannelEntity): Int =
        c.copy(streamUrl = StalkerClient.withoutPlayToken(c.streamUrl)).computeContentHash()

    private fun logFieldFingerprints(label: String, sourceId: Long, sample: List<ChannelEntity>) {
        sample.forEach { c ->
            Log.i(
                TAG,
                "$label fieldDiff sourceId=$sourceId remoteId=${c.remoteId} hash=${stalkerChannelHash(c)} " +
                    "categoryId=${c.categoryId} name=${c.name.hashCode()} logoUrl=${c.logoUrl.hashCode()} " +
                    "streamUrl=${StalkerClient.withoutPlayToken(c.streamUrl).hashCode()} epgChannelId=${c.epgChannelId.hashCode()} " +
                    "number=${c.number} catchup=${c.catchup} catchupDays=${c.catchupDays} " +
                    "catchupSource=${c.catchupSource.hashCode()} sortOrder=${c.sortOrder}",
            )
        }
    }

    private suspend inline fun guardStep(phase: String, stats: SyncStatsCollector, block: suspend () -> Unit) {
        val start = System.currentTimeMillis()
        try {
            block()
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            Log.w(TAG, "$phase import failed — keeping the rest of the import", e)
            stats.phaseErrors[phase] = e.message ?: "unknown"
        } finally {
            stats.phaseTiming[phase] = System.currentTimeMillis() - start
        }
    }

    /**
     * Retry transient portal errors (429/5xx **and broken connections**) after a backoff ([backoffMs]). The
     * portal occasionally 503s under concurrent paging; without a retry a failed page 1 silently
     * drops its WHOLE category, and on the next re-sync those items are pruned as stale — real data
     * loss, not just a gap.
     *
     * An overloaded portal does not always answer 503: just as often it resets the connection or
     * closes it mid-response, which surfaces as a socket-level [IOException] rather than a status
     * code. That is the same condition with the same cost, so it retries the same way.
     */
    private suspend fun <T> retryTransient(what: String, block: suspend () -> T): T {
        var attempt = 1
        while (true) {
            try {
                return block()
            } catch (e: IOException) {
                if (!isTransientPortalError(e) || attempt >= PAGE_ATTEMPTS) throw e
                val reason = (e as? StalkerClient.StalkerHttpException)?.let { "HTTP ${it.code}" }
                    ?: "${e.javaClass.simpleName}: ${e.message}"
                val wait = backoffMs(attempt, (e as? StalkerClient.StalkerHttpException)?.retryAfterMs, kotlin.random.Random.nextDouble())
                Log.w(TAG, "$what transient $reason — retrying in ${wait}ms (attempt $attempt/${PAGE_ATTEMPTS - 1})")
                delay(wait)
                attempt++
            }
        }
    }

    /**
     * One page fetch with the shared auth (re-handshakes once on token expiry), taking a slot from the
     * shared [budget]. `retryTransient` stays OUTSIDE `withPermit` so a backoff delay never holds a
     * slot and every throttled attempt teaches the limiter.
     */
    private suspend fun fetchPage(
        creds: StalkerCredentials, genreId: String, page: Int, budget: AdaptivePortalLimiter,
    ): StalkerClient.Page<StalkerClient.Channel> =
        retryTransient("${SyncPhase.LIVE.name} genre=$genreId page=$page") {
            budget.withPermit {
                auth.withAuthRetry(creds) { session ->
                    client.getLiveChannelsPage(session.apiBase, creds.mac, session.token, creds.userAgent, genreId, page)
                }
            }
        }

    private fun credentialsFor(s: SourceEntity): StalkerCredentials {
        val mac = s.mac?.let { StalkerClient.canonicalizeMac(it) }
            ?: throw IOException("Stalker source ${s.id} has no valid MAC address")
        return s.stalkerCredentials(mac)
    }

    /**
     * Some portals advertise an XMLTV feed in their `get_profile` payload. If this
     * source has no guide URL yet, persist the advertised one into `epgUrl` — `EpgRepository.guideUrl`
     * then treats the source exactly like an M3U with `url-tvg`. A user-entered URL always wins
     * (never overwritten), and EPG sync itself stays user-initiated (Settings → EPG Sources).
     */
    private suspend fun adoptPortalXmltvUrl(s: SourceEntity, creds: StalkerCredentials) {
        if (!s.epgUrl.isNullOrBlank()) return
        val profile = auth.sessionFor(creds).profile
        val url = XMLTV_PROFILE_KEYS.firstNotNullOfOrNull { key ->
            profile[key]?.trim()?.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
        } ?: return
        Log.i(TAG, "adopting portal-advertised XMLTV url for sourceId=${s.id}")
        sourceDao.update(s.copy(epgUrl = url))
    }

    companion object {
        private const val TAG = SyncSupport.TAG

        /**
         * Portal answers worth one more attempt after a backoff — "ask again in a moment", not
         * "the answer is no". 429 and 5xx are the obvious ones; **403 belongs here too**, because
         * Ministra and its reseller panels use it for "this MAC has too many connections open"
         * rather than for a refused login (see `StalkerClient.httpFailure`). Retrying it after a
         * short wait is what a set-top box does, and it is what makes a portal that refuses a burst
         * of pages recover instead of dropping a whole category.
         *
         * Connection-level faults are handled by [isTransientNetworkError]; a
         * [StalkerClient.StalkerAuthException] is not retried here at all, having already been
         * re-handshaked one level down by [StalkerAuthManager].
         */
        internal fun isTransientPortalError(e: IOException): Boolean = when (e) {
            is StalkerClient.StalkerHttpException -> e.code == 403 || e.code == 429 || e.code in 500..599
            is StalkerClient.StalkerRefusedException -> true
            else -> isTransientNetworkError(e)
        }

        /**
         * Connection-level faults worth a second attempt — "the link broke", not "the portal said no".
         *
         * Deliberately narrow rather than a blanket `IOException`: a [StalkerClient.StalkerAuthException]
         * has already been re-handshaked one level down by [StalkerAuthManager], and a malformed payload
         * is deterministic, so repeating either only costs the portal two more pointless requests.
         */
        internal fun isTransientNetworkError(e: IOException): Boolean = when {
            e is StalkerClient.StalkerAuthException -> false
            e is java.net.SocketException -> true          // "Connection reset", "Broken pipe"
            e is java.io.InterruptedIOException -> true    // includes SocketTimeoutException
            e is javax.net.ssl.SSLException -> true
            e is java.net.UnknownHostException -> true     // DNS blip part-way through a long sync
            // A connection closed mid-response reaches us as a plain IOException ("unexpected end of
            // stream on …"), so the type alone cannot identify it — the wrapped EOFException can.
            else -> generateSequence(e as Throwable) { it.cause }.take(CAUSE_CHAIN_LIMIT)
                .any { it is java.io.EOFException }
        }

        /**
         * Throttle signals that shrink the adaptive budget: rate-limit/overload HTTP codes + timeouts.
         * **403 counts**, for the same reason it is retried — a panel refusing a burst is telling us
         * our concurrency is too high, and halving it is the only reply that helps.
         */
        internal fun isPortalThrottle(e: Throwable): Boolean = when (e) {
            is StalkerClient.StalkerHttpException -> e.code == 403 || e.code == 429 || e.code in 500..599
            is StalkerClient.StalkerRefusedException -> true
            is java.net.SocketTimeoutException -> true
            else -> false
        }

        /** `get_profile` fields observed in the wild to carry a portal's XMLTV feed URL. */
        private val XMLTV_PROFILE_KEYS = listOf("xmltv_url", "epg_url", "tv_guide_url", "guide_url")

        /** Flush every N channels so import progress shows early (see [chunkSize] note). */
        private const val STALKER_CHUNK = 1_500

        /** How many channels the field-diff diagnostic fingerprints per sync (see [logFieldFingerprints]). */
        private const val FIELD_DIFF_SAMPLE = 3

        /** Total attempts per page for transient 429/5xx errors (1 original + 3 retries). */
        private const val PAGE_ATTEMPTS = 4

        /** First backoff between page retries; doubled per retry (3, 6, 12 s). */
        private const val PAGE_RETRY_DELAY_MS = 3_000L

        /** The longest a portal's own `Retry-After` is honoured for. */
        private const val MAX_RETRY_AFTER_MS = 60_000L

        /**
         * How long to wait before retry [attempt] (1-based). Retries used to come back in 0.75 and
         * 1.5 s, which a portal refusing a burst counts as the same burst. The portal's `Retry-After`
         * wins when it sent one; otherwise 3 / 6 / 12 s, spread ±20 % by [jitter] (0..1) so the pages
         * refused together do not all return in the same instant.
         */
        internal fun backoffMs(attempt: Int, retryAfterMs: Long?, jitter: Double): Long {
            retryAfterMs?.let { return it.coerceAtMost(MAX_RETRY_AFTER_MS) }
            val base = PAGE_RETRY_DELAY_MS shl (attempt - 1).coerceIn(0, 4)
            return (base * (0.8 + 0.4 * jitter)).toLong()
        }

        /** Guard against a self-referential `cause` chain while classifying a network failure. */
        private const val CAUSE_CHAIN_LIMIT = 5

        /** Safety cap: ignore an absurd total_items from a portal that mis-reports or ignores `p=`. */
        private const val MAX_PAGES_PER_GENRE = 5_000

        /**
         * The genres [bulk] never mentioned — the ones a `get_all_channels` dump left out entirely.
         * Portals omit censored genres (adult, in practice) from that dump *and* from the `"*"` total
         * the completeness check compares against, so the dump looks whole while a whole genre is
         * missing; `get_ordered_list` still serves them. Channels with no `tv_genre_id` say nothing
         * about which genres are covered, so they are ignored here.
         */
        internal fun genresMissingFromBulk(
            genres: List<StalkerClient.Genre>,
            bulk: List<StalkerClient.Channel>,
        ): List<StalkerClient.Genre> {
            val seen = HashSet<String>()
            bulk.forEach { ch -> ch.genreId?.takeIf { it.isNotBlank() }?.let { seen.add(it) } }
            return genres.filter { it.id !in seen }
        }
    }
}
