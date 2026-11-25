package tv.own.owntv.core.sync

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tv.own.owntv.core.network.HttpClient

/**
 * Client for RuTube's public API — ported from TVTube's
 * [app.tvtube.data.remote.RutubeApi], adapted to core's [HttpClient].
 *
 * Anonymous: the TV catalogue, the guide and play options are open. Two quirks:
 *  - play options is gated on a **mobile User-Agent** — a desktop UA gets 404 for every video;
 *  - requests must **not** go through a system proxy — the caller must provide an OkHttpClient
 *    built with `Proxy.NO_PROXY` (see [RutubeHttpClients] in DataModule).
 *
 * [http] must be a raw OkHttp-backed client for 404 handling: [HttpClient.get] throws
 * [HttpClient.HttpStatusException] on non-2xx, which is exactly the signal we need for
 * "no schedule upstream" (HTTP 404) versus a real failure.
 */
class RutubeApi(
    private val http: HttpClient,
    private val baseUrl: String = BASE_URL,
    private val userAgent: String = USER_AGENT,
) {
    class RutubeException(message: String, cause: Throwable? = null) : Exception(message, cause)

    suspend fun categories(): List<RutubeCategory> = withContext(Dispatchers.IO) {
        RutubeParser.parseCategories(getText("$baseUrl/api/livestream/categories/"))
    }

    /**
     * The curated TV list plus its genre buckets — the part of the catalogue that carries RuTube's
     * channel numbering and `broadcast_category` membership.
     *
     * The unfiltered `tvbroadcastinfo` list is a curated TV line-up and can lag behind the genre
     * buckets: on 2026-11-14 «ЛУЧШИЕ ФАНТАСТИЧЕСКИЕ ФИЛЬМЫ» (`54de765aeea96a73d23a24b4e2cab07d`,
     * live and on air) was in `category=5` but absent from the unfiltered list of every client
     * (`tv`/`web`/`androidtv`). Merging the genre lists by `videoId` — first occurrence wins, so the
     * curated order is preserved and duplicates collapse — keeps that part complete.
     *
     * The official TV feed in [liveChannels] has many more channels; the syncer merges both.
     *
     * `per_page=500` returns each list in a single page today (the largest is ~90 rows); the loop
     * still follows `next` so a future server-side cap cannot truncate the catalogue. A genre
     * failure is logged and skipped: the base list must never be lost to one bad bucket. The returned
     * [RutubeCatalogue.isComplete] is false for any such failure, malformed page or pagination cap.
     */
    suspend fun channels(categoryIds: Collection<Int> = emptyList()): RutubeCatalogue =
        withContext(Dispatchers.IO) {
            val out = LinkedHashMap<String, RutubeChannel>()
            var complete = true
            suspend fun collect(url: String): Boolean {
                var next: String? = url
                val visited = HashSet<String>()
                while (!next.isNullOrBlank()) {
                    // A page cap or a next-page cycle means the provider did not expose an endpoint.
                    if (visited.size >= MAX_PAGES || !visited.add(next)) return false
                    val page = RutubeParser.parseChannelPage(getText(next))
                    // Map.putIfAbsent is API 24; keep the first occurrence explicitly.
                    for (channel in page.results) if (channel.videoId !in out) out[channel.videoId] = channel
                    if (!page.isValid) return false
                    next = page.nextUrl
                }
                return true
            }
            try {
                complete = collect(
                    "$baseUrl/api/v2/video/stream/tvbroadcastinfo/?client=wdp&tvprogramm=false&per_page=$PER_PAGE",
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                complete = false
                Log.w(TAG, "RuTube base catalogue failed: ${e.message}")
            }
            for (id in categoryIds) {
                if (id == ALL_CATEGORY_ID) continue
                try {
                    if (!collect(
                            "$baseUrl/api/v2/video/stream/tvbroadcastinfo/?client=wdp" +
                                "&tvprogramm=false&category=$id&per_page=$PER_PAGE",
                        )
                    ) complete = false
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    complete = false
                    Log.w(TAG, "RuTube category $id catalogue failed: ${e.message}")
                }
            }
            RutubeCatalogue(out.values.toList(), complete)
        }

    /**
     * The official TV feed the RUTUBE app itself renders on its «ТВ-эфиры» screen:
     * `pangolin/api/tv/feeds/live_smarttv/` → tabs → rows (`resource/<slug>/<id>`), each row a
     * group of live channel cards. It returns ~325 channels against ~240 in the curated
     * `tvbroadcastinfo` list, including channels that never reach that list (SKY SPORTS TENNIS,
     * «Прямой эфир Record | Радио Супердискотека 90-х», regional and radio channels).
     *
     * Feed cards carry no `broadcast_category`, so the row name is translated to the same genre id
     * the rest of the app uses ([LIVE_GROUP_ALIAS]); a channel in several rows keeps every genre.
     * Rows without a genre mapping still yield channels — they appear in «Все» only. A failed row is
     * logged and skipped, exactly like a failed genre list above.
     */
    suspend fun liveChannels(categories: List<RutubeCategory>): RutubeCatalogue =
        withContext(Dispatchers.IO) {
            val rows = try {
                RutubeParser.parseLiveFeedRows(getText("$baseUrl$LIVE_FEED_PATH"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "RuTube live feed failed: ${e.message}")
                return@withContext RutubeCatalogue(emptyList(), isComplete = false)
            }
            // The official feed is never legitimately empty; an empty/malformed response is not
            // evidence that every formerly feed-backed channel disappeared.
            if (rows.isEmpty()) return@withContext RutubeCatalogue(emptyList(), isComplete = false)

            val byId = LinkedHashMap<String, RutubeChannel>()
            var complete = true
            for (row in rows) {
                val categoryId = categoryIdFor(row.name, categories)
                var next: String? = withClient(row.url)
                val visited = HashSet<String>()
                var rowComplete = true
                while (!next.isNullOrBlank()) {
                    if (visited.size >= MAX_PAGES || !visited.add(next)) {
                        rowComplete = false
                        break
                    }
                    val page = try {
                        RutubeParser.parseChannelPage(getText(next))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "RuTube feed row «${row.name}» failed: ${e.message}")
                        rowComplete = false
                        break
                    }
                    for (channel in page.results) {
                        val existing = byId[channel.videoId]
                        byId[channel.videoId] = when {
                            existing == null ->
                                if (categoryId != null) channel.copy(categoryIds = listOf(categoryId)) else channel
                            categoryId != null ->
                                existing.copy(categoryIds = (existing.categoryIds + categoryId).distinct())
                            else -> existing
                        }
                    }
                    if (!page.isValid) {
                        rowComplete = false
                        break
                    }
                    next = page.nextUrl?.let(::withClient)
                }
                if (!rowComplete) complete = false
            }
            RutubeCatalogue(byId.values.toList(), complete)
        }

    suspend fun briefSchedules(): Map<String, List<RutubeProgramme>> = withContext(Dispatchers.IO) {
        val out = HashMap<String, List<RutubeProgramme>>()
        var url: String? = "$baseUrl/api/v2/video/stream/tvbroadcastinfo/?client=tv&tvprogramm=true"
        val visited = HashSet<String>()
        while (!url.isNullOrBlank() && visited.size < MAX_PAGES && visited.add(url)) {
            val page = try {
                RutubeParser.parseBriefPage(getText(url))
            } catch (e: HttpClient.HttpStatusException) {
                // Like a channel-day 404, this means RuTube has no brief schedule to seed.
                if (e.code == 404) return@withContext emptyMap() else throw e
            }
            for ((k, v) in page.schedules) if (v.isNotEmpty()) out[k] = v
            url = page.nextUrl
        }
        out
    }

    /**
     * One channel's grid for one day (the bulk loader, ~40 KB per channel-day). [date] is
     * "yyyy-MM-dd" (UTC) — see [UtcDates].
     * HTTP 404 means "no schedule upstream" and returns an empty list — never retry the
     * 2 MB mobile fallback for it.
     */
    suspend fun scheduleDay(videoId: String, date: String): List<RutubeProgramme> =
        withContext(Dispatchers.IO) {
            val url = "$baseUrl/pangolin/api/tv/tvprogram/$videoId?client=tv&programDate=$date"
            try {
                RutubeParser.parsePangolinDay(getText(url))
            } catch (e: HttpClient.HttpStatusException) {
                if (e.code == 404) emptyList() else throw e
            }
        }

    suspend fun scheduleAllDays(videoId: String): List<RutubeProgramme> = withContext(Dispatchers.IO) {
        RutubeParser.parseMobileSchedule(getText("$baseUrl/api/v1/livestream/video/schedule/$videoId/"))
    }

    /**
     * Lightweight liveness probe for a curated-only channel that was not present in the official
     * feed. `api/video/<id>/` answers 403/404 for a deleted/removed video; other failures return
     * true so a transient network error never hides a channel. This mirrors SmartRuTube's
     * curated-only check and is called for the small tail only, not the whole catalogue.
     */
    suspend fun isVideoAvailable(videoId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            getText("$baseUrl/api/video/$videoId/")
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpClient.HttpStatusException) {
            e.code != 403 && e.code != 404
        } catch (e: Exception) {
            true
        }
    }

    suspend fun playUrl(videoId: String): RutubePlayback = withContext(Dispatchers.IO) {
        // The TV client's own query: `pver=v2` asks for the larger play-options payload and
        // `referer=https://tv-androidtv.rutube.ru` turns the server-side ad schedule off
        // (`advert: []`). `no_404` keeps a missing stream from spamming 404s. The referer is a
        // QUERY parameter, not the HTTP header — the header does not have this effect.
        val url = "$baseUrl/api/play/options/$videoId/?no_404=true&pver=v2" +
            "&referer=https%3A%2F%2Ftv-androidtv.rutube.ru"
        try {
            val body = getText(url)
            RutubeParser.parsePlayOptions(200, body)
        } catch (e: HttpClient.HttpStatusException) {
            // Paid channels answer 404 with detail.name = login_required — present in the
            // body, which HttpClient discards. Re-parse best-effort: without the body we
            // cannot distinguish, so treat any play-options 404 as login_required (the
            // dominant cause) and let the UI show the paid message. Iframe/no-stream
            // channels answer 200 with no stream, handled above.
            // 404 = login_required; 244 = RuTube's "payment required" answer for subscription
            // channels when the TV query parameters are used (Premier/SPORT packages).
            if (e.code == 404 || e.code == 244) {
                RutubePlayback.Unavailable(UnavailableReason.LOGIN_REQUIRED, e.message)
            } else {
                RutubePlayback.Unavailable(UnavailableReason.NETWORK, e.message)
            }
        } catch (e: java.io.IOException) {
            RutubePlayback.Unavailable(UnavailableReason.NETWORK, e.message)
        }
    }

    /** Feed row name → genre alias of `api/livestream/categories/` (server-provided names). */
    private fun categoryIdFor(name: String, categories: List<RutubeCategory>): Int? {
        val alias = LIVE_GROUP_ALIAS[name.trim().lowercase()] ?: return null
        return categories.firstOrNull { it.alias.equals(alias, ignoreCase = true) }?.id
    }

    /** Feed rows come without `client=tv`; the TV query is required for the Pangolin TV routes. */
    private fun withClient(url: String): String =
        if (url.contains("client=")) url else url + (if ('?' in url) "&" else "?") + "client=tv"

    private suspend fun getText(url: String): String = http.getText(
        url = url,
        userAgent = userAgent,
        headers = mapOf("Accept" to "application/json", "Referer" to "$baseUrl/"),
    )

    companion object {
        const val BASE_URL = "https://rutube.ru"
        const val USER_AGENT = "com.rutube.mobile/31.18.0 (Android 13)"
        private const val TAG = "RutubeApi"
        private const val MAX_PAGES = 60

        /** RuTube honours `per_page`; 500 covers every catalogue list in one request today. */
        private const val PER_PAGE = 500

        /** Category 1 is "Все прямые эфиры" — the unfiltered list, already fetched as the base. */
        private const val ALL_CATEGORY_ID = 1

        /** The official app's «ТВ-эфиры» feed; `client=tv` is the only accepted TV client. */
        private const val LIVE_FEED_PATH = "/pangolin/api/tv/feeds/live_smarttv/?client=tv"

        /**
         * Feed group names → genre aliases, parsed once from `rutube_live_groups.json` in this
         * class's static initializer (see [RutubeLiveGroups]).
         */
        private val LIVE_GROUP_ALIAS: Map<String, String> = RutubeLiveGroups.load()
    }
}
