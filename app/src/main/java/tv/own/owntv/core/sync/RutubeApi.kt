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
     * The full live catalogue: RuTube's unfiltered TV list **plus** every genre list.
     *
     * The unfiltered `tvbroadcastinfo` list is a curated TV line-up and can lag behind the genre
     * buckets: on 2026-11-14 «ЛУЧШИЕ ФАНТАСТИЧЕСКИЕ ФИЛЬМЫ» (`54de765aeea96a73d23a24b4e2cab07d`,
     * live and on air) was in `category=5` but absent from the unfiltered list of every client
     * (`tv`/`web`/`androidtv`). Merging the genre lists by `videoId` — first occurrence wins, so the
     * curated order is preserved and duplicates collapse — makes the catalogue complete.
     *
     * `per_page=500` returns each list in a single page today (the largest is ~90 rows); the loop
     * still follows `next` so a future server-side cap cannot truncate the catalogue. A genre
     * failure is logged and skipped: the base list must never be lost to one bad bucket.
     */
    suspend fun channels(categoryIds: Collection<Int> = emptyList()): List<RutubeChannel> =
        withContext(Dispatchers.IO) {
            val out = LinkedHashMap<String, RutubeChannel>()
            suspend fun collect(url: String) {
                var next: String? = url
                val visited = HashSet<String>()
                while (!next.isNullOrBlank() && visited.size < MAX_PAGES && visited.add(next)) {
                    val page = RutubeParser.parseChannelPage(getText(next))
                    for (channel in page.results) out.putIfAbsent(channel.videoId, channel)
                    next = page.nextUrl
                }
            }
            collect("$baseUrl/api/v2/video/stream/tvbroadcastinfo/?client=tv&tvprogramm=false&per_page=$PER_PAGE")
            for (id in categoryIds) {
                if (id == ALL_CATEGORY_ID) continue
                try {
                    collect(
                        "$baseUrl/api/v2/video/stream/tvbroadcastinfo/?client=tv" +
                            "&tvprogramm=false&category=$id&per_page=$PER_PAGE",
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "RuTube category $id catalogue failed: ${e.message}")
                }
            }
            out.values.toList()
        }

    suspend fun briefSchedules(): Map<String, List<RutubeProgramme>> = withContext(Dispatchers.IO) {
        val out = HashMap<String, List<RutubeProgramme>>()
        var url: String? = "$baseUrl/api/v2/video/stream/tvbroadcastinfo/?client=tv&tvprogramm=true"
        val visited = HashSet<String>()
        while (!url.isNullOrBlank() && visited.size < MAX_PAGES && visited.add(url)) {
            val page = RutubeParser.parseBriefPage(getText(url))
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
    }
}
